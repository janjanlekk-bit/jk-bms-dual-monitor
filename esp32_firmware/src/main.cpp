#include <Arduino.h>
#include <WiFi.h>
#include <WiFiManager.h>
#include <ESPmDNS.h>
#include <time.h>
#include "Config.h"
#include "BleManager.h"
#include "EnergyTracker.h"
#include "WebServerManager.h"

BleManager bleManager;
EnergyTracker energyTracker;
WebServerManager webServerManager;

unsigned long lastEnergyUpdateMs = 0;

void bleTask(void* pvParameters) {
    while (true) {
        bleManager.update();
        vTaskDelay(pdMS_TO_TICKS(50));
    }
}

void setup() {
    Serial.begin(115200);
    delay(500);
    Serial.println("\n========================================");
    Serial.println("  JK BMS DUAL MONITOR - ESP32 GATEWAY  ");
    Serial.println("========================================\n");

    // 1. Initialize Wi-Fi via Captive Portal
    WiFiManager wm;
    wm.setConnectTimeout(15); // Give 15 seconds to connect to Wi-Fi router
    wm.setConnectRetries(3);
    wm.setConfigPortalTimeout(180); // 3 minutes timeout if unconfigured
    
    Serial.println("[WIFI] Connecting to Wi-Fi or starting setup hotspot...");
    bool res = wm.autoConnect(AP_SETUP_SSID, AP_SETUP_PASS);

    if (!res) {
        Serial.println("[WIFI] Portal timed out, continuing in AP / Offline mode.");
    } else {
        Serial.printf("[WIFI] Connected! IP Address: %s\n", WiFi.localIP().toString().c_str());
        
        // Setup mDNS responder: http://jkbms.local
        if (MDNS.begin("jkbms")) {
            Serial.println("[mDNS] Responder started at http://jkbms.local");
            MDNS.addService("http", "tcp", 80);
        }

        // Configure NTP time for GMT+8
        configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER);
        Serial.println("[NTP] Time synchronization requested");
    }

    // 2. Initialize Energy Accounting Engine
    energyTracker.begin();
    Serial.println("[ENERGY] Coulomb counting engine initialized");

    // 3. Initialize Bluetooth (NimBLE)
    bleManager.begin();
    Serial.println("[BLE] Bluetooth Low Energy initialized for dual JK BMS");

    // Start BLE worker task pinned to Core 0
    xTaskCreatePinnedToCore(bleTask, "bleTask", 8192, NULL, 1, NULL, 0);

    // 4. Start Web Server and REST API (Runs on Core 1)
    webServerManager.begin(&bleManager, &energyTracker);
    Serial.println("[SYSTEM] Setup completed successfully!");
}

void loop() {
    // WebServer client handling on Core 1
    webServerManager.update();

    unsigned long now = millis();
    if (now - lastEnergyUpdateMs >= 1000) {
        lastEnergyUpdateMs = now;

        float bankV, bankI, bankW, totalRemAh, totalNomAh;
        int weightedSoc;
        bleManager.getBankMetrics(bankV, bankI, bankW, weightedSoc, totalRemAh, totalNomAh);

        if (bankV > 10.0f) {
            energyTracker.update(bankV, bankI, bankW, weightedSoc, totalRemAh);
        }
    }

    delay(10); // Small yield
}
