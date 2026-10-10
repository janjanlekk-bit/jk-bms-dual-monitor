#pragma once
#include <Arduino.h>
#include <WebServer.h>
#include <ArduinoJson.h>
#include "BleManager.h"
#include "EnergyTracker.h"

struct BmsSnap {
    bool connected = false;
    float voltage = 0;
    float current = 0;
    float power = 0;
    int soc = 0;
    float remainingAh = 0;
    float nominalAh = 100;
    int deltaMv = 0;
    float tempBatt = 0;
    float tempMos = 0;
    uint32_t cycles = 0;
    float cells[32];
    int cellCount = 0;
};

class WebServerManager {
public:
    WebServer server{80};
    BleManager* ble = nullptr;
    EnergyTracker* energy = nullptr;

    void begin(BleManager* b, EnergyTracker* e) {
        ble = b;
        energy = e;

        server.on("/", HTTP_GET, [this]() { handleRoot(); });
        server.on("/api/status", HTTP_GET, [this]() { handleApiStatus(); });
        server.on("/api/history", HTTP_GET, [this]() { handleApiHistory(); });
        server.on("/api/reset_wifi", HTTP_POST, [this]() { handleResetWifi(); });

        server.on("/api/ble/release", HTTP_POST, [this]() {
            int sec = 600;
            if (server.hasArg("sec")) sec = server.arg("sec").toInt();
            if (sec <= 0) sec = 600;
            ble->pauseBle(sec);
            server.send(200, "application/json", "{\"status\":\"ok\",\"paused\":true,\"remainingSec\":" + String(sec) + "}");
        });

        server.on("/api/ble/resume", HTTP_POST, [this]() {
            ble->resumeBle();
            server.send(200, "application/json", "{\"status\":\"ok\",\"paused\":false}");
        });

        server.begin();
        Serial.println("[HTTP] Web server started on port 80");
    }

    void update() {
        server.handleClient();
    }

private:
    void copySnap(const BmsData& src, BmsSnap& dst, bool isClientConnected) {
        unsigned long now = millis();
        // Online if client is actively connected AND packets arrived in the last 20 seconds
        dst.connected = src.isConnected && isClientConnected && (src.lastSeenMs > 0) && (now - src.lastSeenMs < 20000);
        dst.voltage = src.voltage;
        dst.current = src.current;
        dst.power = src.power;
        dst.soc = src.soc;
        dst.remainingAh = src.remainingCapacityAh;
        dst.nominalAh = src.nominalCapacityAh;
        dst.deltaMv = src.deltaMv;
        dst.tempBatt = src.tempBatt;
        dst.tempMos = src.tempMos;
        dst.cycles = src.cycleCount;
        dst.cellCount = min((int)src.cells.size(), 32);
        for (int i = 0; i < dst.cellCount; i++) {
            dst.cells[i] = src.cells[i].voltage;
        }
    }

    void handleApiStatus() {
        JsonDocument doc;

        float bankV, bankI, bankW, totalRemAh, totalNomAh;
        int weightedSoc;
        ble->getBankMetrics(bankV, bankI, bankW, weightedSoc, totalRemAh, totalNomAh);

        // Bank metrics
        JsonObject bank = doc["bank"].to<JsonObject>();
        bank["voltage"] = bankV;
        bank["current"] = bankI;
        bank["power"] = bankW;
        bank["weightedSoc"] = weightedSoc;
        bank["remainingAh"] = totalRemAh;
        bank["nominalAh"] = totalNomAh;

        bank["solarChargedKwh"] = energy->today.solarChargedKwh;
        bank["solarChargedAh"] = energy->today.solarChargedAh;
        bank["loadConsumedKwh"] = energy->today.loadConsumedKwh;
        bank["loadConsumedAh"] = energy->today.loadConsumedAh;
        bank["minSoc"] = energy->today.minSoc;
        bank["maxSoc"] = energy->today.maxSoc;
        bank["minAh"] = energy->today.minAh;
        bank["maxAh"] = energy->today.maxAh;
        bank["isCycleStarted"] = energy->isCycleStarted;

        // Take lightweight snapshots under mutex
        BmsSnap s1, s2;
        if (xSemaphoreTake(ble->dataMutex, pdMS_TO_TICKS(50)) == pdTRUE) {
            bool c1 = ble->client1 != nullptr && ble->client1->isConnected();
            bool c2 = ble->client2 != nullptr && ble->client2->isConnected();
            copySnap(ble->bms1, s1, c1);
            copySnap(ble->bms2, s2, c2);
            xSemaphoreGive(ble->dataMutex);
        }

        // B1
        JsonObject b1 = doc["b1"].to<JsonObject>();
        b1["name"] = "48V 100Ah #1";
        b1["connected"] = s1.connected;
        b1["voltage"] = s1.voltage;
        b1["current"] = s1.current;
        b1["power"] = s1.power;
        b1["soc"] = s1.soc;
        b1["remainingAh"] = s1.remainingAh;
        b1["nominalAh"] = s1.nominalAh;
        b1["deltaMv"] = s1.deltaMv;
        b1["tempBatt"] = s1.tempBatt;
        b1["tempMos"] = s1.tempMos;
        b1["cycles"] = s1.cycles;

        JsonArray cells1 = b1["cells"].to<JsonArray>();
        for (int i = 0; i < s1.cellCount; i++) {
            cells1.add(s1.cells[i]);
        }

        // B2
        JsonObject b2 = doc["b2"].to<JsonObject>();
        b2["name"] = "48V 100Ah #2";
        b2["connected"] = s2.connected;
        b2["voltage"] = s2.voltage;
        b2["current"] = s2.current;
        b2["power"] = s2.power;
        b2["soc"] = s2.soc;
        b2["remainingAh"] = s2.remainingAh;
        b2["nominalAh"] = s2.nominalAh;
        b2["deltaMv"] = s2.deltaMv;
        b2["tempBatt"] = s2.tempBatt;
        b2["tempMos"] = s2.tempMos;
        b2["cycles"] = s2.cycles;

        JsonArray cells2 = b2["cells"].to<JsonArray>();
        for (int i = 0; i < s2.cellCount; i++) {
            cells2.add(s2.cells[i]);
        }

        JsonObject bleObj = doc["ble"].to<JsonObject>();
        bleObj["paused"] = ble->isBlePaused();
        bleObj["remainingSec"] = ble->getBlePauseRemainingSec();

        String json;
        serializeJson(doc, json);
        server.send(200, "application/json", json);
    }

    void handleApiHistory() {
        JsonDocument doc;

        JsonObject t = doc["today"].to<JsonObject>();
        t["date"] = energy->today.date;
        t["solarChargedKwh"] = energy->today.solarChargedKwh;
        t["solarChargedAh"] = energy->today.solarChargedAh;
        t["loadConsumedKwh"] = energy->today.loadConsumedKwh;
        t["loadConsumedAh"] = energy->today.loadConsumedAh;
        t["minSoc"] = energy->today.minSoc;
        t["maxSoc"] = energy->today.maxSoc;
        t["minAh"] = energy->today.minAh;
        t["maxAh"] = energy->today.maxAh;

        JsonObject y = doc["yesterday"].to<JsonObject>();
        y["date"] = energy->yesterday.date;
        y["solarChargedKwh"] = energy->yesterday.solarChargedKwh;
        y["solarChargedAh"] = energy->yesterday.solarChargedAh;
        y["loadConsumedKwh"] = energy->yesterday.loadConsumedKwh;
        y["loadConsumedAh"] = energy->yesterday.loadConsumedAh;
        y["minSoc"] = energy->yesterday.minSoc;
        y["maxSoc"] = energy->yesterday.maxSoc;
        y["minAh"] = energy->yesterday.minAh;
        y["maxAh"] = energy->yesterday.maxAh;

        String json;
        serializeJson(doc, json);
        server.send(200, "application/json", json);
    }

    void handleResetWifi() {
        server.send(200, "text/plain", "Clearing Wi-Fi credentials and rebooting...");
        delay(1000);
        WiFi.disconnect(true, true);
        delay(500);
        ESP.restart();
    }

    void handleRoot();
};

static const char INDEX_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>JK BMS Dual Monitor</title>
<style>
:root{--bg:#12151C;--card:#1A1F29;--border:#262C3A;--teal:#00E676;--orange:#FF9100;--blue:#2979FF;--text:#ECEFF1;--sub:#90A4AE}
*{box-sizing:border-box;margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
body{background:var(--bg);color:var(--text);padding:14px;max-width:600px;margin:auto}
h1{font-size:18px;text-align:center;margin-bottom:12px;letter-spacing:1px;color:#fff}
.grid2{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:10px}
.card{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:14px}
.card-header{font-size:11px;font-weight:700;letter-spacing:0.8px;margin-bottom:6px;display:flex;align-items:center;justify-content:space-between}
.solar-title{color:var(--teal)}
.load-title{color:var(--orange)}
.kwh{font-size:22px;font-weight:800}
.ah{font-size:12px;color:var(--sub);margin-top:2px}
.soc-range-bar{background:var(--card);border:1px solid var(--border);border-radius:10px;padding:12px;margin-bottom:10px;text-align:center}
.soc-range-title{font-size:11px;color:var(--sub);font-weight:600}
.soc-range-val{font-size:16px;font-weight:700;color:#fff;margin-top:3px}
.badge{padding:3px 8px;border-radius:6px;font-size:10px;font-weight:700}
.online{background:#1B5E20;color:#A5D6A7}
.offline{background:#B71C1C;color:#FFCDD2}
.metric-row{display:flex;justify-content:space-between;margin:6px 0;font-size:13px}
.metric-row span:first-child{color:var(--sub)}
.metric-row span:last-child{font-weight:600}
.cell-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:4px;margin-top:8px}
.cell-box{background:#232A38;padding:4px 2px;border-radius:4px;text-align:center;font-size:10px}
.cell-box small{display:block;color:var(--sub);font-size:8px}
.status-pill{font-size:11px;padding:2px 8px;border-radius:12px;background:#2A3142;color:#90CAF9}
</style>
</head>
<body>
<h1>☀️ JK BMS DUAL MONITOR ⚡</h1>

<div class="grid2">
  <div class="card">
    <div class="card-header solar-title">☀️ SOLAR CHARGED</div>
    <div class="kwh" id="solarKwh" style="color:var(--teal)">+0.00 kWh</div>
    <div class="ah" id="solarAh">+0.0 Ah</div>
  </div>
  <div class="card">
    <div class="card-header load-title">💡 LOAD CONSUMED</div>
    <div class="kwh" id="loadKwh" style="color:var(--orange)">-0.00 kWh</div>
    <div class="ah" id="loadAh">-0.0 Ah</div>
  </div>
</div>

<div class="soc-range-bar">
  <div class="soc-range-title">DAILY SOC & CAPACITY RANGE</div>
  <div class="soc-range-val" id="socRange">--% → --%</div>
  <div class="ah" id="ahRange">-- Ah → -- Ah</div>
</div>

<div class="card" style="margin-bottom:10px">
  <div class="card-header" style="color:#90CAF9">
    <span>TOTAL BATTERY BANK</span>
    <span class="status-pill" id="cycleStatus">NIGHT</span>
  </div>
  <div class="metric-row"><span>Weighted SOC:</span><span id="bankSoc" style="font-size:18px;color:#fff">--%</span></div>
  <div class="metric-row"><span>Bus Voltage:</span><span id="bankV">-- V</span></div>
  <div class="metric-row"><span>Total Current:</span><span id="bankI">-- A</span></div>
  <div class="metric-row"><span>Total Power:</span><span id="bankW">-- W</span></div>
  <div class="metric-row"><span>Bank Capacity:</span><span id="bankCap">-- Ah / -- Ah</span></div>
</div>

<div class="card" style="margin-bottom:10px">
  <div class="card-header">
    <span id="b1Name">BATTERY 1 (24S)</span>
    <span class="badge" id="b1Status">CONNECTING</span>
  </div>
  <div class="metric-row"><span>Voltage / Current:</span><span id="b1VI">-- V / -- A</span></div>
  <div class="metric-row"><span>Power / SOC:</span><span id="b1PSoc">-- W / --%</span></div>
  <div class="metric-row"><span>Cell Delta / Temp:</span><span id="b1DeltaTemp">-- mV / -- °C</span></div>
  <div class="cell-grid" id="b1Cells"></div>
</div>

<div class="card" style="margin-bottom:10px">
  <div class="card-header">
    <span id="b2Name">BATTERY 2 (20S)</span>
    <span class="badge" id="b2Status">CONNECTING</span>
  </div>
  <div class="metric-row"><span>Voltage / Current:</span><span id="b2VI">-- V / -- A</span></div>
  <div class="metric-row"><span>Power / SOC:</span><span id="b2PSoc">-- W / --%</span></div>
  <div class="metric-row"><span>Cell Delta / Temp:</span><span id="b2DeltaTemp">-- mV / -- °C</span></div>
  <div class="cell-grid" id="b2Cells"></div>
</div>

<div class="card" style="margin-bottom:10px;text-align:center">
  <div id="bleNormalView">
    <button onclick="releaseBle(600)" style="background:#2A3142;color:#90CAF9;border:1px solid #3E495F;padding:12px;border-radius:8px;font-weight:700;font-size:13px;cursor:pointer;width:100%">
      🔓 RELEASE BLUETOOTH FOR 10 MINUTES
    </button>
    <div style="font-size:11px;color:var(--sub);margin-top:6px">Disconnects ESP32 so you can use the official JK BMS app</div>
  </div>
  <div id="blePausedView" style="display:none;background:#3E2723;border:1px solid #FF5722;padding:12px;border-radius:8px">
    <div style="color:#FFAB91;font-weight:700;font-size:13px;margin-bottom:6px">⚠️ BLUETOOTH RELEASED FOR OFFICIAL APP</div>
    <div style="color:#FFF;font-size:20px;font-weight:800;margin-bottom:8px" id="bleCountdown">10:00</div>
    <button onclick="resumeBle()" style="background:#00E676;color:#12151C;border:none;padding:8px 20px;border-radius:6px;font-weight:800;font-size:12px;cursor:pointer">
      RESUME ESP32 NOW
    </button>
  </div>
</div>

<script>
async function releaseBle(sec) {
  if (!confirm("Release Bluetooth for 10 minutes?\n\nThis will disconnect the ESP32 so you can connect via the official JK BMS app.")) return;
  await fetch('/api/ble/release?sec=' + sec, { method: 'POST' });
  update();
}
async function resumeBle() {
  await fetch('/api/ble/resume', { method: 'POST' });
  update();
}

async function update() {
  try {
    const res = await fetch('/api/status');
    const d = await res.json();

    if (d.ble && d.ble.paused) {
      document.getElementById('bleNormalView').style.display = 'none';
      document.getElementById('blePausedView').style.display = 'block';
      const m = Math.floor(d.ble.remainingSec / 60);
      const s = d.ble.remainingSec % 60;
      document.getElementById('bleCountdown').textContent = m + ':' + (s < 10 ? '0' : '') + s;
    } else {
      document.getElementById('bleNormalView').style.display = 'block';
      document.getElementById('blePausedView').style.display = 'none';
    }

    document.getElementById('solarKwh').textContent = '+' + d.bank.solarChargedKwh.toFixed(2) + ' kWh';
    document.getElementById('solarAh').textContent = '+' + d.bank.solarChargedAh.toFixed(1) + ' Ah';
    document.getElementById('loadKwh').textContent = '-' + d.bank.loadConsumedKwh.toFixed(2) + ' kWh';
    document.getElementById('loadAh').textContent = '-' + d.bank.loadConsumedAh.toFixed(1) + ' Ah';

    document.getElementById('socRange').textContent = d.bank.minSoc + '% → ' + d.bank.maxSoc + '%';
    document.getElementById('ahRange').textContent = d.bank.minAh.toFixed(1) + ' Ah → ' + d.bank.maxAh.toFixed(1) + ' Ah';

    document.getElementById('cycleStatus').textContent = d.bank.isCycleStarted ? 'SOLAR CYCLE' : 'PRE-DAWN';
    document.getElementById('bankSoc').textContent = d.bank.weightedSoc + '%';
    document.getElementById('bankV').textContent = d.bank.voltage.toFixed(2) + ' V';
    document.getElementById('bankI').textContent = (d.bank.current > 0 ? '+' : '') + d.bank.current.toFixed(2) + ' A';
    document.getElementById('bankW').textContent = (d.bank.power > 0 ? '+' : '') + d.bank.power.toFixed(0) + ' W';
    document.getElementById('bankCap').textContent = d.bank.remainingAh.toFixed(1) + ' Ah / ' + d.bank.nominalAh.toFixed(0) + ' Ah';

    renderBms('b1', d.b1);
    renderBms('b2', d.b2);
  } catch(e) {
    console.error(e);
  }
}

function renderBms(prefix, b) {
  const badge = document.getElementById(prefix + 'Status');
  if (b.connected) {
    badge.textContent = 'ONLINE';
    badge.className = 'badge online';
  } else {
    badge.textContent = 'OFFLINE';
    badge.className = 'badge offline';
  }
  document.getElementById(prefix + 'VI').textContent = b.voltage.toFixed(2) + ' V / ' + (b.current > 0 ? '+' : '') + b.current.toFixed(2) + ' A';
  document.getElementById(prefix + 'PSoc').textContent = b.power.toFixed(0) + ' W / ' + b.soc + '%';
  document.getElementById(prefix + 'DeltaTemp').textContent = b.deltaMv + ' mV / ' + b.tempBatt.toFixed(1) + ' °C';

  const cg = document.getElementById(prefix + 'Cells');
  if (b.cells && b.cells.length > 0 && cg.children.length !== b.cells.length) {
    cg.innerHTML = '';
    b.cells.forEach((v, idx) => {
      const box = document.createElement('div');
      box.className = 'cell-box';
      box.innerHTML = '<small>C' + (idx+1) + '</small><span>' + v.toFixed(3) + '</span>';
      cg.appendChild(box);
    });
  } else if (b.cells) {
    b.cells.forEach((v, idx) => {
      if (cg.children[idx]) {
        cg.children[idx].querySelector('span').textContent = v.toFixed(3);
      }
    });
  }
}

setInterval(update, 1500);
update();
</script>
</body>
</html>
)rawliteral";

inline void WebServerManager::handleRoot() {
    server.send_P(200, "text/html", INDEX_HTML);
}
