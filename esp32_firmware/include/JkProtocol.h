#pragma once
#include <Arduino.h>
#include <vector>
#include "Config.h"

struct CellData {
    uint8_t index = 0;
    float voltage = 0.0f;
    float resistance = 0.0f;
};

struct BmsData {
    String name = "";
    String macAddress = "";
    bool isConnected = false;
    uint32_t lastSeenMs = 0;

    float voltage = 0.0f;
    float current = 0.0f;
    float power = 0.0f;
    int soc = 0;
    float remainingCapacityAh = 0.0f;
    float nominalCapacityAh = 100.0f;

    float tempMos = 0.0f;
    float tempBatt = 0.0f;
    int deltaMv = 0;
    uint32_t cycleCount = 0;

    std::vector<CellData> cells;
};

class JkProtocol {
public:
    static inline uint16_t getUint16(const uint8_t* d, int o) {
        return (uint16_t)d[o] | ((uint16_t)d[o + 1] << 8);
    }

    static inline int16_t getInt16(const uint8_t* d, int o) {
        return (int16_t)getUint16(d, o);
    }

    static inline uint32_t getUint32(const uint8_t* d, int o) {
        return (uint32_t)d[o] | ((uint32_t)d[o + 1] << 8) | ((uint32_t)d[o + 2] << 16) | ((uint32_t)d[o + 3] << 24);
    }

    static inline int32_t getInt32(const uint8_t* d, int o) {
        return (int32_t)getUint32(d, o);
    }

    static uint8_t computeChecksum(const uint8_t* buf, int offset, int len) {
        uint32_t sum = 0;
        for (int i = offset; i < offset + len; i++) {
            sum += buf[i];
        }
        return (uint8_t)(sum & 0xFF);
    }

    static void buildReadCommand(uint8_t* outCmd20, uint8_t cmd = 0x96) {
        memset(outCmd20, 0, 20);
        outCmd20[0] = 0xAA;
        outCmd20[1] = 0x55;
        outCmd20[2] = 0x90;
        outCmd20[3] = 0xEB;
        outCmd20[4] = cmd;
        outCmd20[5] = 0x00;
        outCmd20[19] = computeChecksum(outCmd20, 0, 19);
    }

    static bool parseType02(const uint8_t* data, int len, BmsData& out) {
        if (len < JK_FRAME_SIZE) return false;

        out.cells.clear();
        float minV = 99.0f;
        float maxV = 0.0f;
        float sumV = 0.0f;

        // 1. Cell Voltages (bytes 6 to 69 in 32S layout)
        for (int i = 0; i < 32; i++) {
            int vOffset = 6 + (i * 2);
            if (vOffset + 1 >= len) break;
            uint16_t rawMv = getUint16(data, vOffset);
            float v = rawMv * 0.001f;

            if (v >= 0.5f && v <= 5.0f) {
                CellData c;
                c.index = i + 1;
                c.voltage = v;
                
                uint16_t r32 = (80 + i * 2 + 1 < len) ? getUint16(data, 80 + i * 2) : 0;
                uint16_t r24 = (64 + i * 2 + 1 < len) ? getUint16(data, 64 + i * 2) : 0;
                uint16_t rawR = (r32 >= 1 && r32 <= 65000) ? r32 : r24;
                c.resistance = (rawR >= 1 && rawR <= 65000) ? rawR * 0.001f : 0.0f;

                out.cells.push_back(c);

                if (v < minV) minV = v;
                if (v > maxV) maxV = v;
                sumV += v;
            }
        }

        if (out.cells.size() > 0) {
            out.deltaMv = (int)((maxV - minV) * 1000.0f);
        } else {
            out.deltaMv = 0;
        }

        // 2. Base Offset Identification (matches Android Jk02Parser)
        struct VCandidate { int base; float factor; float val; };
        VCandidate cands[6] = {
            {150, 0.01f,  getUint32(data, 150) * 0.01f},
            {150, 0.001f, getUint32(data, 150) * 0.001f},
            {118, 0.01f,  getUint32(data, 118) * 0.01f},
            {118, 0.001f, getUint32(data, 118) * 0.001f},
            {134, 0.01f,  getUint32(data, 134) * 0.01f},
            {134, 0.001f, getUint32(data, 134) * 0.001f}
        };

        int baseOffset = 150;
        float vFactor = 0.01f;
        float packV = sumV;

        for (int i = 0; i < 6; i++) {
            if (sumV > 0.0f && fabs(cands[i].val - sumV) < 2.0f) {
                baseOffset = cands[i].base;
                vFactor = cands[i].factor;
                packV = cands[i].val;
                break;
            }
        }

        out.voltage = (packV >= 5.0f && packV <= 160.0f) ? packV : sumV;

        // 3. Current
        int curOffset = (baseOffset == 150) ? 158 : ((baseOffset == 134) ? 142 : 126);
        int32_t rawCur = getInt32(data, curOffset);
        float cFactor = (vFactor == 0.001f || fabs(rawCur * 0.01f) > 500.0f) ? 0.001f : 0.01f;
        out.current = rawCur * cFactor;
        out.power = out.voltage * out.current;

        // 4. SOC
        int socOffset = baseOffset + 23;
        int rawSoc = (socOffset < len) ? data[socOffset] : -1;
        if (rawSoc >= 0 && rawSoc <= 100) {
            out.soc = rawSoc;
        } else {
            int altCandidates[3] = {173, 141, 157};
            for (int k = 0; k < 3; k++) {
                int idx = altCandidates[k];
                if (idx < len && data[idx] <= 100) {
                    out.soc = data[idx];
                    break;
                }
            }
        }

        // 5. Temperatures
        int mosOffset = baseOffset + 12;
        int battOffset = baseOffset + 14;
        out.tempMos = getInt16(data, mosOffset) * 0.1f;
        out.tempBatt = getInt16(data, battOffset) * 0.1f;

        // 6. Remaining Capacity
        int capOffset = baseOffset + 24;
        uint32_t rawCap = getUint32(data, capOffset);
        if (out.soc == 0) {
            out.remainingCapacityAh = 0.0f;
        } else if (rawCap * 0.001f >= 0.01f && rawCap * 0.001f <= 2000.0f) {
            out.remainingCapacityAh = rawCap * 0.001f;
        } else if (rawCap * 0.01f >= 0.01f && rawCap * 0.01f <= 2000.0f) {
            out.remainingCapacityAh = rawCap * 0.01f;
        } else {
            out.remainingCapacityAh = out.nominalCapacityAh * (out.soc / 100.0f);
        }

        // 7. Cycle count
        int cycOffset = baseOffset + 32;
        if (cycOffset + 3 < len) {
            out.cycleCount = getUint32(data, cycOffset);
        }

        out.lastSeenMs = millis();
        return true;
    }
};

class JkFrameAssembler {
private:
    std::vector<uint8_t> buffer;

public:
    template<typename Callback>
    void pushBytes(const uint8_t* chunk, size_t len, Callback onFrame) {
        buffer.insert(buffer.end(), chunk, chunk + len);

        while (buffer.size() >= JK_FRAME_SIZE) {
            int headerIdx = -1;
            for (size_t i = 0; i <= buffer.size() - 4; i++) {
                if (buffer[i] == 0x55 && buffer[i+1] == 0xAA &&
                    buffer[i+2] == 0xEB && buffer[i+3] == 0x90) {
                    headerIdx = i;
                    break;
                }
            }

            if (headerIdx == -1) {
                if (buffer.size() >= 3) {
                    std::vector<uint8_t> tail(buffer.end() - 3, buffer.end());
                    buffer = tail;
                } else {
                    buffer.clear();
                }
                return;
            }

            if (headerIdx > 0) {
                buffer.erase(buffer.begin(), buffer.begin() + headerIdx);
            }

            if (buffer.size() < JK_FRAME_SIZE) return;

            uint8_t frame[JK_FRAME_SIZE];
            memcpy(frame, buffer.data(), JK_FRAME_SIZE);

            uint8_t expectedCrc = frame[299];
            uint8_t calculatedCrc = JkProtocol::computeChecksum(frame, 0, 299);

            if (expectedCrc == calculatedCrc) {
                uint8_t frameType = frame[4];
                onFrame(frameType, frame, JK_FRAME_SIZE);
            }

            buffer.erase(buffer.begin(), buffer.begin() + JK_FRAME_SIZE);
        }
    }

    void reset() {
        buffer.clear();
    }
};
