#pragma once
#include <Arduino.h>
#include <Preferences.h>
#include <time.h>
#include "Config.h"

struct DailyRecord {
    String date = "";
    float solarChargedKwh = 0.0f;
    float solarChargedAh = 0.0f;
    float loadConsumedKwh = 0.0f;
    float loadConsumedAh = 0.0f;
    int minSoc = 0;
    int maxSoc = 0;
    float minAh = 0.0f;
    float maxAh = 0.0f;
    int startSoc = 0;
    int endSoc = 0;
};

class EnergyTracker {
private:
    Preferences prefs;
    unsigned long lastIntegrationMs = 0;
    unsigned long lastSaveMs = 0;

public:
    DailyRecord today;
    DailyRecord yesterday;
    bool isCycleStarted = false;
    String currentDate = "";

    void begin() {
        prefs.begin("jk_energy", false);
        loadState();
        lastIntegrationMs = millis();
        lastSaveMs = millis();
    }

    String getSystemDateString() {
        struct tm timeinfo;
        if (!getLocalTime(&timeinfo, 100)) {
            // If NTP not yet synced, return last known date or default
            return currentDate.length() > 0 ? currentDate : "2026-10-10";
        }
        char buf[16];
        strftime(buf, sizeof(buf), "%Y-%m-%d", &timeinfo);
        return String(buf);
    }

    void update(float bankV, float bankI, float bankW, int currentSoc, float currentAh) {
        unsigned long now = millis();
        if (lastIntegrationMs == 0) {
            lastIntegrationMs = now;
            return;
        }

        float dt = (now - lastIntegrationMs) / 1000.0f;
        lastIntegrationMs = now;

        if (dt < 0.05f || dt > 10.0f) {
            // Guard against large time jumps or clock updates
            return;
        }

        // 1. Day Rollover Check
        String nowDat = getSystemDateString();
        if (currentDate.length() > 0 && nowDat != currentDate) {
            archiveDay();
            currentDate = nowDat;
            today = DailyRecord();
            today.date = currentDate;
            today.startSoc = currentSoc;
            today.minSoc = currentSoc;
            today.maxSoc = currentSoc;
            today.minAh = currentAh;
            today.maxAh = currentAh;
            isCycleStarted = false;
            saveState();
        } else if (currentDate.length() == 0) {
            currentDate = nowDat;
            today.date = currentDate;
        }

        // 2. Coulomb Counting Integration
        if (bankI > 0.05f) { // Solar charging into battery
            float dAh = (bankI * dt) / 3600.0f;
            float dKwh = (bankW * dt) / (3600.0f * 1000.0f);
            today.solarChargedAh += dAh;
            today.solarChargedKwh += (dKwh > 0.0f) ? dKwh : (dAh * bankV) / 1000.0f;
        } else if (bankI < -0.05f) { // Discharging to house loads
            float absI = -bankI;
            float absW = -bankW;
            float dAh = (absI * dt) / 3600.0f;
            float dKwh = (absW * dt) / (3600.0f * 1000.0f);
            today.loadConsumedAh += dAh;
            today.loadConsumedKwh += (dKwh > 0.0f) ? dKwh : (dAh * bankV) / 1000.0f;
        }

        // 3. Morning Solar Charging Cycle Detection
        if (today.solarChargedAh > 0.05f && !isCycleStarted) {
            isCycleStarted = true;
            // Roll pre-dawn midnight-to-morning consumption into yesterday's record
            if (today.loadConsumedAh > 0.05f) {
                yesterday.loadConsumedAh += today.loadConsumedAh;
                yesterday.loadConsumedKwh += today.loadConsumedKwh;
                today.loadConsumedAh = 0.0f;
                today.loadConsumedKwh = 0.0f;
            }
            // Lock morning low
            today.minSoc = currentSoc;
            today.maxSoc = currentSoc;
            today.minAh = currentAh;
            today.maxAh = currentAh;
        }

        // 4. Daily SOC & Ah Range
        if (currentSoc > 0 && currentSoc <= 100) {
            if (today.startSoc == 0) today.startSoc = currentSoc;

            if (!isCycleStarted) {
                // Pre-dawn night: follow current SOC down so midnight residual doesn't create false daily highs
                today.minSoc = currentSoc;
                today.maxSoc = currentSoc;
                today.minAh = currentAh;
                today.maxAh = currentAh;
            } else {
                // Daytime solar cycle: track from morning bottom upwards
                if (currentSoc < today.minSoc) today.minSoc = currentSoc;
                if (currentSoc > today.maxSoc) today.maxSoc = currentSoc;
                if (currentAh > 0.0f) {
                    if (today.minAh <= 0.1f || currentAh < today.minAh) today.minAh = currentAh;
                    if (currentAh > today.maxAh) today.maxAh = currentAh;
                }
            }
            today.endSoc = currentSoc;
        }

        // 5. Periodic NVS Auto-save (every 60 seconds)
        if (now - lastSaveMs > 60000) {
            lastSaveMs = now;
            saveState();
        }
    }

    void archiveDay() {
        yesterday = today;
        prefs.putString("y_date", yesterday.date);
        prefs.putFloat("y_chg_kwh", yesterday.solarChargedKwh);
        prefs.putFloat("y_chg_ah", yesterday.solarChargedAh);
        prefs.putFloat("y_load_kwh", yesterday.loadConsumedKwh);
        prefs.putFloat("y_load_ah", yesterday.loadConsumedAh);
        prefs.putInt("y_min_soc", yesterday.minSoc);
        prefs.putInt("y_max_soc", yesterday.maxSoc);
        prefs.putFloat("y_min_ah", yesterday.minAh);
        prefs.putFloat("y_max_ah", yesterday.maxAh);
    }

    void saveState() {
        prefs.putString("t_date", currentDate);
        prefs.putFloat("t_chg_kwh", today.solarChargedKwh);
        prefs.putFloat("t_chg_ah", today.solarChargedAh);
        prefs.putFloat("t_load_kwh", today.loadConsumedKwh);
        prefs.putFloat("t_load_ah", today.loadConsumedAh);
        prefs.putInt("t_min_soc", today.minSoc);
        prefs.putInt("t_max_soc", today.maxSoc);
        prefs.putFloat("t_min_ah", today.minAh);
        prefs.putFloat("t_max_ah", today.maxAh);
        prefs.putInt("t_start_soc", today.startSoc);
        prefs.putBool("t_cycle_ok", isCycleStarted);
    }

    void loadState() {
        currentDate = prefs.getString("t_date", "");
        today.date = currentDate;
        today.solarChargedKwh = prefs.getFloat("t_chg_kwh", 0.0f);
        today.solarChargedAh = prefs.getFloat("t_chg_ah", 0.0f);
        today.loadConsumedKwh = prefs.getFloat("t_load_kwh", 0.0f);
        today.loadConsumedAh = prefs.getFloat("t_load_ah", 0.0f);
        today.minSoc = prefs.getInt("t_min_soc", 0);
        today.maxSoc = prefs.getInt("t_max_soc", 0);
        today.minAh = prefs.getFloat("t_min_ah", 0.0f);
        today.maxAh = prefs.getFloat("t_max_ah", 0.0f);
        today.startSoc = prefs.getInt("t_start_soc", 0);
        isCycleStarted = prefs.getBool("t_cycle_ok", false);

        yesterday.date = prefs.getString("y_date", "");
        yesterday.solarChargedKwh = prefs.getFloat("y_chg_kwh", 0.0f);
        yesterday.solarChargedAh = prefs.getFloat("y_chg_ah", 0.0f);
        yesterday.loadConsumedKwh = prefs.getFloat("y_load_kwh", 0.0f);
        yesterday.loadConsumedAh = prefs.getFloat("y_load_ah", 0.0f);
        yesterday.minSoc = prefs.getInt("y_min_soc", 0);
        yesterday.maxSoc = prefs.getInt("y_max_soc", 0);
        yesterday.minAh = prefs.getFloat("y_min_ah", 0.0f);
        yesterday.maxAh = prefs.getFloat("y_max_ah", 0.0f);
    }
};
