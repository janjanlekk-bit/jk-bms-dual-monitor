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
    float newCyclePendingChargedAh = 0.0f;

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

        String nowDat = getSystemDateString();
        if (currentDate.length() == 0) {
            currentDate = nowDat;
            today.date = currentDate;
        }

        // Check if calendar date has passed since this cycle started
        bool isNewCalendarDay = (currentDate.length() > 0 && nowDat != currentDate);

        // 1. Coulomb Counting Integration
        if (bankI > 0.05f) { // Solar charging into battery
            float dAh = (bankI * dt) / 3600.0f;
            float dKwh = (bankW * dt) / (3600.0f * 1000.0f);
            float actualKwh = (dKwh > 0.0f) ? dKwh : (dAh * bankV) / 1000.0f;

            if (isNewCalendarDay) {
                // Past midnight on a new day: accumulate morning charging current
                newCyclePendingChargedAh += dAh;

                // When confirmed morning solar charging begins (>= 0.05 Ah):
                // The previous 24h cycle is complete, and the new charging cycle starts!
                if (newCyclePendingChargedAh >= 0.05f) {
                    archiveDay(); // Save completed full-cycle load & solar to yesterday

                    // Reset today for the new charging cycle
                    currentDate = nowDat;
                    today = DailyRecord();
                    today.date = currentDate;
                    today.solarChargedAh = newCyclePendingChargedAh;
                    today.solarChargedKwh = (newCyclePendingChargedAh * bankV) / 1000.0f;
                    today.loadConsumedAh = 0.0f; // Resets to zero upon next charging cycle!
                    today.loadConsumedKwh = 0.0f;
                    today.startSoc = currentSoc;
                    today.minSoc = currentSoc;
                    today.maxSoc = currentSoc;
                    today.minAh = currentAh;
                    today.maxAh = currentAh;
                    today.endSoc = currentSoc;
                    isCycleStarted = true;
                    newCyclePendingChargedAh = 0.0f;
                    saveState();
                }
            } else {
                // Normal daytime charging during the active cycle
                today.solarChargedAh += dAh;
                today.solarChargedKwh += actualKwh;
            }
        } else if (bankI < -0.05f) { // Discharging to house loads
            float absI = -bankI;
            float absW = -bankW;
            float dAh = (absI * dt) / 3600.0f;
            float dKwh = (absW * dt) / (3600.0f * 1000.0f);
            float actualKwh = (dKwh > 0.0f) ? dKwh : (dAh * bankV) / 1000.0f;

            // Load consumed CONTINUES to accumulate through the day and all night!
            today.loadConsumedAh += dAh;
            today.loadConsumedKwh += actualKwh;
            newCyclePendingChargedAh = 0.0f; // Reset noise counter on load discharge
        }

        // 2. Midday Safety Fallback:
        // If calendar day changed and it is already noon (12:00 PM) with 0 solar charging,
        // archive and rollover so cycle does not remain un-archived indefinitely.
        if (isNewCalendarDay) {
            struct tm timeinfo;
            if (getLocalTime(&timeinfo, 10)) {
                if (timeinfo.tm_hour >= 12) {
                    archiveDay();
                    currentDate = nowDat;
                    today = DailyRecord();
                    today.date = currentDate;
                    today.startSoc = currentSoc;
                    today.minSoc = currentSoc;
                    today.maxSoc = currentSoc;
                    today.minAh = currentAh;
                    today.maxAh = currentAh;
                    today.endSoc = currentSoc;
                    isCycleStarted = false;
                    newCyclePendingChargedAh = 0.0f;
                    saveState();
                }
            }
        }

        // 3. Cycle SOC & Ah Range tracking
        if (currentSoc > 0 && currentSoc <= 100) {
            if (today.startSoc == 0) today.startSoc = currentSoc;
            if (today.minSoc == 0 || currentSoc < today.minSoc) today.minSoc = currentSoc;
            if (today.maxSoc == 0 || currentSoc > today.maxSoc) today.maxSoc = currentSoc;
            if (currentAh > 0.0f) {
                if (today.minAh <= 0.1f || currentAh < today.minAh) today.minAh = currentAh;
                if (currentAh > today.maxAh) today.maxAh = currentAh;
            }
            today.endSoc = currentSoc;
        }

        // 4. Periodic NVS Auto-save (every 60 seconds)
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
