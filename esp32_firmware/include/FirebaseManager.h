#pragma once
#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include "Config.h"
#include "WebServerManager.h"

class FirebaseManager {
public:
    String host = FIREBASE_HOST;
    String auth = FIREBASE_AUTH;
    bool enabled = true;
    bool isSyncPaused = false;

    WiFiClientSecure client;
    HTTPClient httpTelemetry;
    HTTPClient httpHistory;

    unsigned long lastTelemetrySyncMs = 0;
    unsigned long lastHistorySyncMs = 0;
    const unsigned long telemetryIntervalMs = 3000; // 3 seconds interval for live data
    const unsigned long historyIntervalMs = 60000;  // 60 seconds interval for daily history

    WebServerManager* webServer = nullptr;

    void begin(WebServerManager* ws) {
        webServer = ws;
        host.trim();
        while (host.endsWith("/")) {
            host.remove(host.length() - 1);
        }

        client.setInsecure();
        client.setBufferSizes(2048, 1024);
        client.setTimeout(3000);

        httpTelemetry.setReuse(true);
        httpTelemetry.setTimeout(3000);

        httpHistory.setReuse(true);
        httpHistory.setTimeout(3000);

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
        httpTelemetry.end();
        httpHistory.end();
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

        // Avoid syncing if BLE is explicitly paused/released
        if (webServer && webServer->ble && webServer->ble->isBlePaused()) {
            return;
        }

        unsigned long now = millis();

        // 1. Live telemetry push (every 3 seconds)
        if (now - lastTelemetrySyncMs >= telemetryIntervalMs) {
            lastTelemetrySyncMs = now;
            syncTelemetry();
        }

        // 2. Daily history push (every 60 seconds)
        if (now - lastHistorySyncMs >= historyIntervalMs) {
            lastHistorySyncMs = now;
            syncHistory();
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

        if (httpTelemetry.begin(client, url)) {
            httpTelemetry.addHeader("Content-Type", "application/json");
            int httpCode = httpTelemetry.PUT(json);
            if (httpCode > 0) {
                // Success
            } else {
                Serial.printf("[FIREBASE] Telemetry PUT error: %s\n", httpTelemetry.errorToString(httpCode).c_str());
                httpTelemetry.end();
            }
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

        if (httpHistory.begin(client, url)) {
            httpHistory.addHeader("Content-Type", "application/json");
            int httpCode = httpHistory.PUT(json);
            if (httpCode <= 0) {
                Serial.printf("[FIREBASE] History PUT error: %s\n", httpHistory.errorToString(httpCode).c_str());
                httpHistory.end();
            }
        }
    }
};
