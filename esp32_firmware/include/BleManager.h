#pragma once
#include <Arduino.h>
#include <NimBLEDevice.h>
#include "Config.h"
#include "JkProtocol.h"

class BleManager;

class BmsClientCallback : public NimBLEClientCallbacks {
private:
    BleManager* mgr;
    int devIdx;
public:
    BmsClientCallback(BleManager* m, int idx) : mgr(m), devIdx(idx) {}

    void onConnect(NimBLEClient* pClient) override {
        Serial.printf("[BLE] B%d onConnect event\n", devIdx);
    }

    void onDisconnect(NimBLEClient* pClient) override;
};

class BleManager : public NimBLEAdvertisedDeviceCallbacks {
public:
    BmsData bms1;
    BmsData bms2;

    JkFrameAssembler assembler1;
    JkFrameAssembler assembler2;

    NimBLEClient* client1 = nullptr;
    NimBLEClient* client2 = nullptr;

    BmsClientCallback* cb1 = nullptr;
    BmsClientCallback* cb2 = nullptr;

    NimBLERemoteCharacteristic* chr1 = nullptr;
    NimBLERemoteCharacteristic* chr2 = nullptr;

    String mac1 = DEFAULT_B1_MAC;
    String mac2 = DEFAULT_B2_MAC;

    NimBLEAddress addr1 = NimBLEAddress(DEFAULT_B1_MAC, BLE_ADDR_PUBLIC);
    NimBLEAddress addr2 = NimBLEAddress(DEFAULT_B2_MAC, BLE_ADDR_RANDOM);
    volatile bool hasAddr1 = true;
    volatile bool hasAddr2 = false;

    uint8_t b1FailCount = 0;
    uint8_t b2FailCount = 0;
    uint32_t b1TotalAttempts = 0;
    uint32_t b2TotalAttempts = 0;
    String b2LastStatus = "Init";

    unsigned long lastWatchdogMs = 0;
    unsigned long lastScanMs = 0;
    unsigned long lastB1ConnectAttempt = 0;
    unsigned long lastB2ConnectAttempt = 0;
    bool isConnecting = false;

    SemaphoreHandle_t dataMutex = nullptr;

    void begin() {
        dataMutex = xSemaphoreCreateMutex();

        bms1.name = "48V 100Ah #1";
        bms1.macAddress = mac1;
        bms1.nominalCapacityAh = B1_NOMINAL_AH;

        bms2.name = "48V 100Ah #2";
        bms2.macAddress = mac2;
        bms2.nominalCapacityAh = B2_NOMINAL_AH;

        addr1 = NimBLEAddress(mac1.c_str(), BLE_ADDR_PUBLIC);
        addr2 = NimBLEAddress(mac2.c_str(), BLE_ADDR_RANDOM); // Start B2 with RANDOM (type 1)
        hasAddr1 = true;
        hasAddr2 = false;

        NimBLEDevice::init("ESP32_JK_MONITOR");
        NimBLEDevice::setPower(ESP_PWR_LVL_P9); // Maximum BLE TX power (+9dBm)
        NimBLEDevice::setMTU(517);

        client1 = NimBLEDevice::createClient();
        client2 = NimBLEDevice::createClient();

        cb1 = new BmsClientCallback(this, 1);
        cb2 = new BmsClientCallback(this, 2);

        client1->setClientCallbacks(cb1, false);
        client2->setClientCallbacks(cb2, false);

        client1->setConnectTimeout(4);
        client2->setConnectTimeout(4);

        // Quick 4-second initial discovery scan at boot
        NimBLEScan* pScan = NimBLEDevice::getScan();
        pScan->setAdvertisedDeviceCallbacks(this, false);
        pScan->setActiveScan(true);
        pScan->setInterval(120);
        pScan->setWindow(60);
        pScan->start(4, false);
    }

    void onResult(NimBLEAdvertisedDevice* dev) override {
        String addrStr = dev->getAddress().toString().c_str();
        if (addrStr.equalsIgnoreCase(mac1)) {
            addr1 = dev->getAddress();
            hasAddr1 = true;
            Serial.printf("[BLE] Discovered B1: %s (type %u, RSSI %d)\n",
                          addrStr.c_str(), dev->getAddress().getType(), dev->getRSSI());
        } else if (addrStr.equalsIgnoreCase(mac2)) {
            addr2 = dev->getAddress();
            hasAddr2 = true;
            Serial.printf("[BLE] Discovered B2: %s (type %u, RSSI %d)\n",
                          addrStr.c_str(), dev->getAddress().getType(), dev->getRSSI());
        }
    }

    void onDeviceDisconnect(int devIdx) {
        if (devIdx == 1) {
            bms1.isConnected = false;
            bms1.lastSeenMs = 0;
            chr1 = nullptr;
            assembler1.reset();
            Serial.println("[BLE] B1 disconnected");
        } else {
            bms2.isConnected = false;
            bms2.lastSeenMs = 0;
            chr2 = nullptr;
            assembler2.reset();
            Serial.println("[BLE] B2 disconnected");
        }
    }

    unsigned long blePausedUntilMs = 0;
    unsigned long allBatteriesDisconnectedSinceMs = 0;
    const unsigned long WATCHDOG_OFFLINE_TIMEOUT_MS = 180000; // 3 minutes offline triggers auto-reboot
    const unsigned long BOOT_GRACE_PERIOD_MS = 90000;         // 90 seconds boot grace period

    void pauseBle(uint32_t durationSeconds = 600) {
        blePausedUntilMs = millis() + (durationSeconds * 1000UL);
        allBatteriesDisconnectedSinceMs = 0;
        disconnectAll();
        Serial.printf("[BLE] Bluetooth released for %u seconds\n", durationSeconds);
    }

    void resumeBle() {
        blePausedUntilMs = 0;
        allBatteriesDisconnectedSinceMs = 0;
        Serial.println("[BLE] Bluetooth resumed!");
    }

    void forceReconnect() {
        blePausedUntilMs = 0;
        allBatteriesDisconnectedSinceMs = 0;
        disconnectAll();
        b1FailCount = 0;
        b2FailCount = 0;
        lastB1ConnectAttempt = 0;
        lastB2ConnectAttempt = 0;
        isConnecting = false;
        Serial.println("[BLE] Force reconnect requested: connection state reset.");
    }

    bool isBlePaused() const {
        return (blePausedUntilMs > 0 && millis() < blePausedUntilMs);
    }

    uint32_t getBlePauseRemainingSec() const {
        if (!isBlePaused()) return 0;
        return (uint32_t)((blePausedUntilMs - millis()) / 1000UL);
    }

    uint32_t getOfflineSec() const {
        if (allBatteriesDisconnectedSinceMs == 0) return 0;
        return (uint32_t)((millis() - allBatteriesDisconnectedSinceMs) / 1000UL);
    }

    void disconnectAll() {
        if (client1 && client1->isConnected()) {
            client1->disconnect();
        }
        if (client2 && client2->isConnected()) {
            client2->disconnect();
        }
        chr1 = nullptr;
        chr2 = nullptr;
        assembler1.reset();
        assembler2.reset();
        bms1.isConnected = false;
        bms2.isConnected = false;
        bms1.lastSeenMs = 0;
        bms2.lastSeenMs = 0;
    }

    void checkOfflineWatchdog(unsigned long now) {
        if (now < BOOT_GRACE_PERIOD_MS || isBlePaused()) {
            allBatteriesDisconnectedSinceMs = 0;
            return;
        }

        bool b1Alive = bms1.isConnected && (bms1.lastSeenMs > 0) && (now - bms1.lastSeenMs < 25000) && (bms1.voltage > 10.0f);
        bool b2Alive = bms2.isConnected && (bms2.lastSeenMs > 0) && (now - bms2.lastSeenMs < 25000) && (bms2.voltage > 10.0f);

        if (b1Alive || b2Alive) {
            allBatteriesDisconnectedSinceMs = 0;
        } else {
            if (allBatteriesDisconnectedSinceMs == 0) {
                allBatteriesDisconnectedSinceMs = now;
                Serial.println("[WATCHDOG] Both batteries offline. Starting 3-minute auto-heal timer.");
            } else if (now - allBatteriesDisconnectedSinceMs >= WATCHDOG_OFFLINE_TIMEOUT_MS) {
                Serial.println("[WATCHDOG] CRITICAL: Both batteries offline for > 3 minutes!");
                Serial.println("[WATCHDOG] Auto-healing: restarting ESP32 to recover Bluetooth...");
                delay(500);
                ESP.restart();
            }
        }
    }

    void update() {
        if (isBlePaused()) {
            allBatteriesDisconnectedSinceMs = 0;
            return;
        }
        if (blePausedUntilMs > 0 && millis() >= blePausedUntilMs) {
            blePausedUntilMs = 0;
            Serial.println("[BLE] Pause period expired, resuming auto-connect.");
        }

        unsigned long now = millis();

        // 1. Check auto-heal watchdog
        checkOfflineWatchdog(now);

        if (isConnecting) return;

        bool b1NeedsConn = !client1->isConnected();
        bool b2NeedsConn = !client2->isConnected();

        // 2. Connect to B1 if disconnected (retry every 7s, recording attempt AFTER finish)
        if (b1NeedsConn && (now - lastB1ConnectAttempt > 7000)) {
            connectToDevice(1, addr1);
            lastB1ConnectAttempt = millis();
            return;
        }

        // 3. Connect to B2 if disconnected (retry every 7s, recording attempt AFTER finish)
        if (b2NeedsConn && (now - lastB2ConnectAttempt > 7000)) {
            connectToDevice(2, addr2);
            lastB2ConnectAttempt = millis();
            return;
        }

        // 4. Keepalive and stream watchdog: check every 2.5 seconds
        if (now - lastWatchdogMs >= 2500) {
            lastWatchdogMs = now;
            checkSilentStreams(now);
        }
    }

    void connectToDevice(int devIndex, NimBLEAddress targetAddr) {
        isConnecting = true;

        NimBLEClient* client = (devIndex == 1) ? client1 : client2;
        String name = (devIndex == 1) ? "B1" : "B2";

        if (devIndex == 1) b1TotalAttempts++; else b2TotalAttempts++;

        // Clean up any stale connection handle before reconnecting
        if (client->isConnected()) {
            client->disconnect();
            delay(50);
        }

        Serial.printf("[BLE] Connecting to %s (%s, type %s)...\n",
                      name.c_str(), targetAddr.toString().c_str(),
                      targetAddr.getType() == BLE_ADDR_RANDOM ? "RANDOM" : "PUBLIC");

        if (client->connect(targetAddr, false)) {
            Serial.printf("[BLE] Connected to %s!\n", name.c_str());
            if (devIndex == 2) {
                b2FailCount = 0;
                b2LastStatus = "Connected";
                hasAddr2 = true;
            }
            client->setConnectionParams(24, 40, 0, 400);

            NimBLERemoteService* pSvc = client->getService(JK_SERVICE_UUID);
            if (pSvc) {
                NimBLERemoteCharacteristic* pChr = pSvc->getCharacteristic(JK_CHAR_UUID);
                if (pChr && pChr->canNotify()) {
                    if (devIndex == 1) {
                        chr1 = pChr;
                        pChr->subscribe(true, [this](NimBLERemoteCharacteristic*, uint8_t* data, size_t len, bool) {
                            this->handleNotify(1, data, len);
                        });
                        bms1.isConnected = true;
                        bms1.lastSeenMs = millis();
                    } else {
                        chr2 = pChr;
                        pChr->subscribe(true, [this](NimBLERemoteCharacteristic*, uint8_t* data, size_t len, bool) {
                            this->handleNotify(2, data, len);
                        });
                        bms2.isConnected = true;
                        bms2.lastSeenMs = millis();
                    }
                    Serial.printf("[BLE] Subscribed to %s notifications!\n", name.c_str());

                    // Handshake ONCE on connection to activate passive telemetry stream
                    uint8_t handshakeCmd[20];
                    JkProtocol::buildReadCommand(handshakeCmd, 0x97);
                    pChr->writeValue(handshakeCmd, 20, pChr->canWrite());
                    delay(300);
                    JkProtocol::buildReadCommand(handshakeCmd, 0x96);
                    pChr->writeValue(handshakeCmd, 20, pChr->canWrite());
                    Serial.printf("[BLE] Handshake sent to %s!\n", name.c_str());
                }
            }
        } else {
            Serial.printf("[BLE] Connection to %s failed\n", name.c_str());
            if (devIndex == 2) {
                b2FailCount++;
                // Toggle between RANDOM and PUBLIC every failed attempt
                uint8_t curType = addr2.getType();
                uint8_t nextType = (curType == BLE_ADDR_PUBLIC) ? BLE_ADDR_RANDOM : BLE_ADDR_PUBLIC;
                addr2 = NimBLEAddress(mac2.c_str(), nextType);
                b2LastStatus = String("Failed (toggled to ") + (nextType == BLE_ADDR_RANDOM ? "RANDOM)" : "PUBLIC)");
                Serial.printf("[BLE] Switched B2 target address type to %s\n", nextType == BLE_ADDR_RANDOM ? "RANDOM" : "PUBLIC");
            }
        }

        isConnecting = false;
    }

    void checkSilentStreams(unsigned long now) {
        uint8_t reqCmd[20];
        JkProtocol::buildReadCommand(reqCmd, 0x96);

        if (client1->isConnected()) {
            if (chr1 != nullptr) {
                // Keepalive ping if silent for > 5s
                if (bms1.lastSeenMs > 0 && (now - bms1.lastSeenMs > 5000)) {
                    chr1->writeValue(reqCmd, 20, chr1->canWrite());
                }
                // Watchdog stall recovery: if silent for > 20s, force disconnect & reconnect
                if (bms1.lastSeenMs > 0 && (now - bms1.lastSeenMs > 20000)) {
                    Serial.println("[BLE] B1 stream dead > 20s, restarting connection...");
                    client1->disconnect();
                    onDeviceDisconnect(1);
                }
            } else {
                // Zombie connection with null characteristic
                Serial.println("[BLE] B1 zombie connection detected, forcing disconnect...");
                client1->disconnect();
                onDeviceDisconnect(1);
            }
        }

        if (client2->isConnected()) {
            if (chr2 != nullptr) {
                // Keepalive ping if silent for > 5s
                if (bms2.lastSeenMs > 0 && (now - bms2.lastSeenMs > 5000)) {
                    chr2->writeValue(reqCmd, 20, chr2->canWrite());
                }
                // Watchdog stall recovery: if silent for > 20s, force disconnect & reconnect
                if (bms2.lastSeenMs > 0 && (now - bms2.lastSeenMs > 20000)) {
                    Serial.println("[BLE] B2 stream dead > 20s, restarting connection...");
                    client2->disconnect();
                    onDeviceDisconnect(2);
                }
            } else {
                // Zombie connection with null characteristic
                Serial.println("[BLE] B2 zombie connection detected, forcing disconnect...");
                client2->disconnect();
                onDeviceDisconnect(2);
            }
        }
    }

    void handleNotify(int devIndex, const uint8_t* data, size_t len) {
        if (devIndex == 1) {
            assembler1.pushBytes(data, len, [this](uint8_t frameType, const uint8_t* frame, size_t frameLen) {
                if (frameType == 0x02) {
                    if (xSemaphoreTake(this->dataMutex, pdMS_TO_TICKS(50)) == pdTRUE) {
                        JkProtocol::parseType02(frame, frameLen, this->bms1);
                        this->bms1.isConnected = true;
                        xSemaphoreGive(this->dataMutex);
                    }
                }
            });
        } else {
            assembler2.pushBytes(data, len, [this](uint8_t frameType, const uint8_t* frame, size_t frameLen) {
                if (frameType == 0x02) {
                    if (xSemaphoreTake(this->dataMutex, pdMS_TO_TICKS(50)) == pdTRUE) {
                        JkProtocol::parseType02(frame, frameLen, this->bms2);
                        this->bms2.isConnected = true;
                        xSemaphoreGive(this->dataMutex);
                    }
                }
            });
        }
    }

    void getBankMetrics(float& bankV, float& bankI, float& bankW, int& weightedSoc, float& totalRemAh, float& totalNomAh) {
        bool b1Active = false;
        bool b2Active = false;
        float v1 = 0, v2 = 0, i1 = 0, i2 = 0, cap1 = 0, cap2 = 0, nom1 = 0, nom2 = 0;
        int s1 = 0, s2 = 0;

        if (xSemaphoreTake(dataMutex, pdMS_TO_TICKS(50)) == pdTRUE) {
            unsigned long now = millis();
            b1Active = bms1.isConnected && (bms1.lastSeenMs > 0) && (now - bms1.lastSeenMs < 25000) && bms1.voltage > 10.0f;
            b2Active = bms2.isConnected && (bms2.lastSeenMs > 0) && (now - bms2.lastSeenMs < 25000) && bms2.voltage > 10.0f;

            v1 = b1Active ? bms1.voltage : 0.0f;
            v2 = b2Active ? bms2.voltage : 0.0f;
            i1 = b1Active ? bms1.current : 0.0f;
            i2 = b2Active ? bms2.current : 0.0f;

            cap1 = b1Active ? bms1.remainingCapacityAh : 0.0f;
            cap2 = b2Active ? bms2.remainingCapacityAh : 0.0f;

            nom1 = b1Active ? bms1.nominalCapacityAh : 0.0f;
            nom2 = b2Active ? bms2.nominalCapacityAh : 0.0f;

            s1 = b1Active ? bms1.soc : 0;
            s2 = b2Active ? bms2.soc : 0;
            xSemaphoreGive(dataMutex);
        }

        if (b1Active && b2Active) {
            bankV = (v1 + v2) * 0.5f;
            bankI = i1 + i2;
            bankW = (v1 * i1) + (v2 * i2);
            totalRemAh = cap1 + cap2;
            totalNomAh = nom1 + nom2;
            if (nom1 + nom2 > 0.0f) {
                weightedSoc = (int)roundf((nom1 * s1 + nom2 * s2) / (nom1 + nom2));
            } else {
                weightedSoc = (s1 + s2) / 2;
            }
        } else if (b1Active) {
            bankV = v1;
            bankI = i1;
            bankW = v1 * i1;
            totalRemAh = cap1;
            totalNomAh = nom1;
            weightedSoc = s1;
        } else if (b2Active) {
            bankV = v2;
            bankI = i2;
            bankW = v2 * i2;
            totalRemAh = cap2;
            totalNomAh = nom2;
            weightedSoc = s2;
        } else {
            bankV = 0.0f;
            bankI = 0.0f;
            bankW = 0.0f;
            totalRemAh = 0.0f;
            totalNomAh = B1_NOMINAL_AH + B2_NOMINAL_AH;
            weightedSoc = 0;
        }
    }
};

inline void BmsClientCallback::onDisconnect(NimBLEClient* pClient) {
    mgr->onDeviceDisconnect(devIdx);
}
