#pragma once
#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include "Config.h"
#include "WebServerManager.h"

class FirebaseManager {
public:
    String host = FIREBASE_HOST;
    String auth = FIREBASE_AUTH;
    bool enabled = true;
    bool isSyncPaused = false;

    WiFiClientSecure client;

    unsigned long lastTelemetrySyncMs = 0;
    unsigned long lastHistorySyncMs = 0;
    unsigned long lastCommandCheckMs = 0;

    const unsigned long telemetryIntervalMs = 3000; // 3 seconds interval for live data
    const unsigned long historyIntervalMs = 60000;  // 60 seconds interval for daily history
    const unsigned long commandIntervalMs = 4000;  // 4 seconds interval to poll cloud commands

    WebServerManager* webServer = nullptr;

    void begin(WebServerManager* ws) {
        webServer = ws;
        host.trim();
        while (host.endsWith("/")) {
            host.remove(host.length() - 1);
        }

        client.setInsecure();
        client.setTimeout(3000);

        // Register OTA callbacks to cleanly stop HTTPS operations before flash write
        if (webServer) {
            webServer->onOtaStart = [this]() {
                pauseSync();
            };
            webServer->onOtaFail = [this]() {
                resumeSync();
            };
        }
    }

    void pauseSync() {
        isSyncPaused = true;
        Serial.println("[FIREBASE] Sync paused for OTA");
    }

    void resumeSync() {
        isSyncPaused = false;
        Serial.println("[FIREBASE] Sync resumed");
    }

    void update() {
        if (!enabled || isSyncPaused || host.length() == 0 || WiFi.status() != WL_CONNECTED) {
            return;
        }

        unsigned long now = millis();

        // 1. Live telemetry push (every 3 seconds, skip if BLE paused by user)
        if (now - lastTelemetrySyncMs >= telemetryIntervalMs) {
            lastTelemetrySyncMs = now;
            if (!(webServer && webServer->ble && webServer->ble->isBlePaused())) {
                syncTelemetry();
            }
        }

        // 2. Remote cloud commands check (every 4 seconds, ALWAYS checked even if BLE paused)
        if (now - lastCommandCheckMs >= commandIntervalMs) {
            lastCommandCheckMs = now;
            checkCommands();
        }

        // 3. Daily history push (every 60 seconds)
        if (now - lastHistorySyncMs >= historyIntervalMs) {
            lastHistorySyncMs = now;
            if (!(webServer && webServer->ble && webServer->ble->isBlePaused())) {
                syncHistory();
            }
        }
    }

private:
    void syncTelemetry() {
        if (!webServer) return;
        String json = webServer->buildStatusJson();
        if (json.length() == 0) return;

        String url = host + "/telemetry.json";
        if (auth.length() > 0) {
            url += "?auth=" + auth;
        }

        HTTPClient http;
        http.setTimeout(3000);
        if (http.begin(client, url)) {
            http.addHeader("Content-Type", "application/json");
            int httpCode = http.PUT(json);
            if (httpCode > 0) {
                http.getString(); // drain response body
            } else {
                Serial.printf("[FIREBASE] Telemetry PUT error: %s\n", http.errorToString(httpCode).c_str());
            }
            http.end();
        }
    }

    void syncHistory() {
        if (!webServer) return;
        String json = webServer->buildHistoryJson();
        if (json.length() == 0) return;

        String url = host + "/history.json";
        if (auth.length() > 0) {
            url += "?auth=" + auth;
        }

        HTTPClient http;
        http.setTimeout(3000);
        if (http.begin(client, url)) {
            http.addHeader("Content-Type", "application/json");
            int httpCode = http.PUT(json);
            if (httpCode > 0) {
                http.getString(); // drain response body
            } else {
                Serial.printf("[FIREBASE] History PUT error: %s\n", http.errorToString(httpCode).c_str());
            }
            http.end();
        }

        // Permanently archive completed days under /daily_history/<date>.json in Firebase
        if (webServer && webServer->energy) {
            archiveDailyRecord(webServer->energy->today);
            if (webServer->energy->yesterday.date.length() > 0) {
                archiveDailyRecord(webServer->energy->yesterday);
            }
        }
    }

    void archiveDailyRecord(const DailyRecord& rec) {
        if (rec.date.length() == 0) return;
        if (rec.solarChargedAh < 0.05f && rec.loadConsumedAh < 0.05f) return;

        JsonDocument doc;
        doc["date"] = rec.date;
        doc["solarChargedKwh"] = rec.solarChargedKwh;
        doc["solarChargedAh"] = rec.solarChargedAh;
        doc["loadConsumedKwh"] = rec.loadConsumedKwh;
        doc["loadConsumedAh"] = rec.loadConsumedAh;
        doc["minSoc"] = rec.minSoc;
        doc["maxSoc"] = rec.maxSoc;
        doc["minAh"] = rec.minAh;
        doc["maxAh"] = rec.maxAh;

        String json;
        serializeJson(doc, json);

        String url = host + "/daily_history/" + rec.date + ".json";
        if (auth.length() > 0) {
            url += "?auth=" + auth;
        }

        HTTPClient http;
        http.setTimeout(3000);
        if (http.begin(client, url)) {
            http.addHeader("Content-Type", "application/json");
            int httpCode = http.PUT(json);
            if (httpCode > 0) {
                http.getString();
            }
            http.end();
        }
    }

    void checkCommands() {
        if (!webServer || !webServer->ble) return;

        String url = host + "/command.json";
        if (auth.length() > 0) {
            url += "?auth=" + auth;
        }

        HTTPClient http;
        http.setTimeout(3000);
        if (http.begin(client, url)) {
            int httpCode = http.GET();
            if (httpCode == 200) {
                String payload = http.getString();
                if (payload.length() > 0 && payload != "null") {
                    handleCommand(payload);
                }
            }
            http.end();
        }
    }

    void handleCommand(const String& payload) {
        JsonDocument doc;
        DeserializationError err = deserializeJson(doc, payload);
        if (err) return;

        const char* action = doc["action"];
        if (!action || strlen(action) == 0) return;

        Serial.printf("[FIREBASE] Cloud command received: %s\n", action);

        // Always clear the command in Firebase first to prevent duplicate execution
        clearCommand();

        if (strcmp(action, "reboot") == 0) {
            Serial.println("[FIREBASE] Remote reboot command accepted! Restarting ESP32 in 500ms...");
            delay(500);
            ESP.restart();
        } else if (strcmp(action, "reconnect_ble") == 0) {
            Serial.println("[FIREBASE] Remote reconnect_ble command accepted! Forcing BLE reset...");
            if (webServer && webServer->ble) {
                webServer->ble->resumeBle();
                webServer->ble->forceReconnect();
            }
        } else if (strcmp(action, "pause_ble") == 0) {
            int sec = doc["sec"] | 600;
            Serial.printf("[FIREBASE] Remote pause_ble command accepted (%d sec)!\n", sec);
            if (webServer && webServer->ble) {
                webServer->ble->pauseBle(sec);
            }
        } else if (strcmp(action, "resume_ble") == 0) {
            Serial.println("[FIREBASE] Remote resume_ble command accepted!");
            if (webServer && webServer->ble) {
                webServer->ble->resumeBle();
                webServer->ble->forceReconnect();
            }
        }
    }

    void clearCommand() {
        String url = host + "/command.json";
        if (auth.length() > 0) {
            url += "?auth=" + auth;
        }

        HTTPClient http;
        http.setTimeout(3000);
        if (http.begin(client, url)) {
            http.addHeader("Content-Type", "application/json");
            http.PUT("null");
            http.getString();
            http.end();
        }
    }
};
