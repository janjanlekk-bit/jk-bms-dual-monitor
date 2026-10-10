#pragma once
#include <Arduino.h>

// Default JK BMS Bluetooth MAC addresses
#define DEFAULT_B1_MAC "c8:47:80:1b:76:00"
#define DEFAULT_B2_MAC "c8:47:80:1c:14:68"

// Nominal Capacities (Ah)
#define B1_NOMINAL_AH 100.0f
#define B2_NOMINAL_AH 100.0f

// Frame specifications
#define JK_FRAME_SIZE 300
#define JK_SERVICE_UUID "ffe0"
#define JK_CHAR_UUID    "ffe1"

// Timezone offset for Philippines (GMT+8 in seconds: 8 * 3600 = 28800)
#define NTP_SERVER "pool.ntp.org"
#define GMT_OFFSET_SEC (8 * 3600)
#define DAYLIGHT_OFFSET_SEC 0

// WiFi AP Setup Portal Name
#define AP_SETUP_SSID "JK-BMS-SETUP"
#define AP_SETUP_PASS "" // Open AP for easy initial setup
