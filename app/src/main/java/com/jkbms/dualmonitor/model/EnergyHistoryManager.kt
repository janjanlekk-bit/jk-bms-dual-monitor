package com.jkbms.dualmonitor.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class EnergyHistoryManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("jk_energy_history", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val _todayEnergy = MutableStateFlow(DailyEnergyRecord())
    val todayEnergy: StateFlow<DailyEnergyRecord> = _todayEnergy.asStateFlow()

    private val _historyList = MutableStateFlow<List<DailyEnergyRecord>>(emptyList())
    val historyList: StateFlow<List<DailyEnergyRecord>> = _historyList.asStateFlow()

    // Internal daily state
    private var currentDate: String = ""
    private var b1StartRemainingAh: Float = 0f
    private var b2StartRemainingAh: Float = 0f
    private var b1HasBaseline: Boolean = false
    private var b2HasBaseline: Boolean = false
    private var startRemainingTotal: Float = 0f
    private var lastKnownRemainingAh: Float = 0f

    // Live continuous accumulation (while connected via Bluetooth)
    private var liveChargedAh: Float = 0f
    private var liveDischargedAh: Float = 0f
    private var liveChargedKwh: Float = 0f
    private var liveDischargedKwh: Float = 0f

    // Offline catch-up accumulation (battery capacity delta while disconnected/app closed)
    private var offlineChargedAh: Float = 0f
    private var offlineDischargedAh: Float = 0f
    private var offlineChargedKwh: Float = 0f
    private var offlineDischargedKwh: Float = 0f

    // Daily extremes
    private var startSoc: Int = 0
    private var minSoc: Int = 0
    private var maxSoc: Int = 0
    private var minAh: Float = 0f
    private var maxAh: Float = 0f

    private var lastIntegrationTimeMs: Long = 0L
    private var lastSaveTimeMs: Long = 0L

    init {
        loadFromPreferences()
    }

    private fun getTodayDateString(): String {
        return dateFormat.format(Date())
    }

    @Synchronized
    fun update(b1: BmsData, b2: BmsData, bank: TotalBankData, isDualConfigured: Boolean = false) {
        val todayStr = getTodayDateString()
        val now = System.currentTimeMillis()

        val b1Online = b1.voltage > 10f
        val b2Online = b2.voltage > 10f
        val bothOnline = b1Online && b2Online

        // If dual BMS is configured, do not calculate bank metrics until BOTH packs are online
        val isBankReady = if (isDualConfigured) bothOnline else (b1Online || b2Online)
        if (!isBankReady) {
            return
        }

        // 1. Check for midnight date change / day rollover
        if (currentDate.isNotBlank() && currentDate != todayStr) {
            archivePreviousDay()
            startNewDay(todayStr, b1, b2, bank, isDualConfigured)
        } else if (currentDate.isBlank()) {
            startNewDay(todayStr, b1, b2, bank, isDualConfigured)
        }

        val curRemainingTotal = (if (b1Online) b1.remainingCapacityAh else 0f) +
                                (if (b2Online) b2.remainingCapacityAh else 0f)

        // 2. Establish baseline if first time connected today
        if (b1Online && !b1HasBaseline) {
            b1StartRemainingAh = b1.remainingCapacityAh
            b1HasBaseline = true
        }
        if (b2Online && !b2HasBaseline) {
            b2StartRemainingAh = b2.remainingCapacityAh
            b2HasBaseline = true
        }
        if (startRemainingTotal <= 0.1f && curRemainingTotal > 0.1f) {
            startRemainingTotal = curRemainingTotal
            if (lastKnownRemainingAh <= 0.1f) {
                lastKnownRemainingAh = curRemainingTotal
            }
        }

        // 3. Energy Integration (Live Shunt Current vs Offline Capacity Catch-up)
        val isReconnectionGap = (lastIntegrationTimeMs == 0L) || ((now - lastIntegrationTimeMs) > 15_000L)
        val avgV = if (bank.voltage in 20f..150f) bank.voltage else 52.0f

        if (isReconnectionGap) {
            // Reconnected after being disconnected or app was closed
            if (lastKnownRemainingAh > 0.1f && curRemainingTotal > 0.1f) {
                val deltaAh = curRemainingTotal - lastKnownRemainingAh
                if (deltaAh > 0.2f) { // Capacity gained while disconnected (solar charging into battery)
                    offlineChargedAh += deltaAh
                    offlineChargedKwh += (deltaAh * avgV) / 1000f
                    Log.d("EnergyHistoryManager", "Offline charge catch-up: +${deltaAh}Ah (+${(deltaAh * avgV) / 1000f}kWh)")
                } else if (deltaAh < -0.2f) { // Capacity lost while disconnected (loads discharging from battery)
                    val dropAh = -deltaAh
                    offlineDischargedAh += dropAh
                    offlineDischargedKwh += (dropAh * avgV) / 1000f
                    Log.d("EnergyHistoryManager", "Offline discharge catch-up: -${dropAh}Ah (-${(dropAh * avgV) / 1000f}kWh)")
                }
            }
            lastKnownRemainingAh = curRemainingTotal
        } else {
            // Active continuous connection (interval <= 15 seconds): Live Coulomb counting
            val dtSeconds = (now - lastIntegrationTimeMs) / 1000f
            if (dtSeconds in 0.1f..15f) {
                val currentA = bank.current
                val powerW = bank.power

                if (currentA > 0.05f) { // Charging into battery (solar yield)
                    val dAh = (currentA * dtSeconds) / 3600f
                    val dKwh = (powerW * dtSeconds) / (3600f * 1000f)
                    liveChargedAh += dAh
                    liveChargedKwh += max(0f, dKwh)
                } else if (currentA < -0.05f) { // Discharging from battery (house load)
                    val absCurrent = -currentA
                    val absPower = -powerW
                    val dAh = (absCurrent * dtSeconds) / 3600f
                    val dKwh = (absPower * dtSeconds) / (3600f * 1000f)
                    liveDischargedAh += dAh
                    liveDischargedKwh += max(0f, dKwh)
                }
            }
            if (curRemainingTotal > 0.1f) {
                lastKnownRemainingAh = curRemainingTotal
            }
        }
        lastIntegrationTimeMs = now

        // Total nominal capacity of the active bank
        val totalNominal = (if (b1Online) b1.nominalCapacityAh else 0f) +
                           (if (b2Online) b2.nominalCapacityAh else 0f)
        val bankCap = if (totalNominal > 20f) totalNominal else 200f

        // 4. Update min and max SOC & Ah for today
        val currentSoc = bank.capacityWeightedSoc
        if (currentSoc in 1..100) {
            if (startSoc == 0) startSoc = currentSoc
            minSoc = if (minSoc == 0) currentSoc else min(minSoc, currentSoc)
            maxSoc = if (maxSoc == 0) currentSoc else max(maxSoc, currentSoc)
        }
        if (curRemainingTotal > 0.1f) {
            minAh = if (minAh <= 0.1f) curRemainingTotal else min(minAh, curRemainingTotal)
            maxAh = if (maxAh <= 0.1f) curRemainingTotal else max(maxAh, curRemainingTotal)
        }

        // 5. Compute unified energy totals
        val effectiveChargedAh = liveChargedAh + offlineChargedAh
        val effectiveDischargedAh = liveDischargedAh + offlineDischargedAh

        val effectiveChargedKwh = max(liveChargedKwh + offlineChargedKwh, (effectiveChargedAh * avgV) / 1000f)
        val effectiveDischargedKwh = max(liveDischargedKwh + offlineDischargedKwh, (effectiveDischargedAh * avgV) / 1000f)

        val effectiveMinAh = if (minAh > 0.1f) minAh else if (minSoc in 1..100) (minSoc.toFloat() / 100f) * bankCap else 0f
        val effectiveMaxAh = if (maxAh > 0.1f) maxAh else if (maxSoc in 1..100) (maxSoc.toFloat() / 100f) * bankCap else 0f

        val updatedRecord = DailyEnergyRecord(
            date = todayStr,
            chargedAh = effectiveChargedAh,
            dischargedAh = effectiveDischargedAh,
            chargedKwh = effectiveChargedKwh,
            dischargedKwh = effectiveDischargedKwh,
            minSoc = minSoc,
            maxSoc = maxSoc,
            minAh = effectiveMinAh,
            maxAh = effectiveMaxAh,
            lastUpdated = now
        )

        _todayEnergy.value = updatedRecord

        // 6. Debounced persist to SharedPreferences every 10 seconds
        if (now - lastSaveTimeMs > 10_000L) {
            lastSaveTimeMs = now
            saveToPreferences()
        }
    }

    private fun archivePreviousDay() {
        val prev = _todayEnergy.value
        if (prev.date.isNotBlank() && (prev.chargedAh > 0.05f || prev.dischargedAh > 0.05f || prev.chargedKwh > 0.01f || prev.dischargedKwh > 0.01f)) {
            val currentList = _historyList.value.filter { it.date != prev.date }.toMutableList()
            currentList.add(0, prev) // newest first
            val trimmed = currentList.take(30)
            _historyList.value = trimmed
            saveHistoryList(trimmed)
        }
    }

    private fun startNewDay(todayStr: String, b1: BmsData, b2: BmsData, bank: TotalBankData, isDualConfigured: Boolean = false) {
        currentDate = todayStr
        liveChargedAh = 0f
        liveDischargedAh = 0f
        liveChargedKwh = 0f
        liveDischargedKwh = 0f
        offlineChargedAh = 0f
        offlineDischargedAh = 0f
        offlineChargedKwh = 0f
        offlineDischargedKwh = 0f

        val b1Online = b1.voltage > 10f
        val b2Online = b2.voltage > 10f
        val bothOnline = b1Online && b2Online

        if (b1Online) {
            b1StartRemainingAh = b1.remainingCapacityAh
            b1HasBaseline = true
        } else {
            b1StartRemainingAh = 0f
            b1HasBaseline = false
        }

        if (b2Online) {
            b2StartRemainingAh = b2.remainingCapacityAh
            b2HasBaseline = true
        } else {
            b2StartRemainingAh = 0f
            b2HasBaseline = false
        }

        val curRemaining = (if (b1Online) b1.remainingCapacityAh else 0f) +
                           (if (b2Online) b2.remainingCapacityAh else 0f)
        startRemainingTotal = curRemaining
        lastKnownRemainingAh = curRemaining

        val currentSoc = bank.capacityWeightedSoc
        if ((!isDualConfigured || bothOnline) && currentSoc in 1..100) {
            startSoc = currentSoc
            minSoc = currentSoc
            maxSoc = currentSoc
        } else {
            startSoc = 0
            minSoc = 0
            maxSoc = 0
        }

        minAh = curRemaining
        maxAh = curRemaining
        lastIntegrationTimeMs = System.currentTimeMillis()

        _todayEnergy.value = DailyEnergyRecord(
            date = todayStr,
            chargedAh = 0f,
            dischargedAh = 0f,
            chargedKwh = 0f,
            dischargedKwh = 0f,
            minSoc = minSoc,
            maxSoc = maxSoc,
            minAh = minAh,
            maxAh = maxAh,
            lastUpdated = System.currentTimeMillis()
        )
        saveToPreferences()
    }

    private fun saveToPreferences() {
        try {
            val record = _todayEnergy.value
            val json = JSONObject().apply {
                put("date", currentDate)
                put("chargedAh", record.chargedAh.toDouble())
                put("dischargedAh", record.dischargedAh.toDouble())
                put("chargedKwh", record.chargedKwh.toDouble())
                put("dischargedKwh", record.dischargedKwh.toDouble())
                put("minSoc", minSoc)
                put("maxSoc", maxSoc)
                put("minAh", minAh.toDouble())
                put("maxAh", maxAh.toDouble())
                put("startSoc", startSoc)
                put("startRemainingTotal", startRemainingTotal.toDouble())
                put("lastKnownRemainingAh", lastKnownRemainingAh.toDouble())
                put("b1StartRemainingAh", b1StartRemainingAh.toDouble())
                put("b2StartRemainingAh", b2StartRemainingAh.toDouble())
                put("b1HasBaseline", b1HasBaseline)
                put("b2HasBaseline", b2HasBaseline)
                put("liveChargedAh", liveChargedAh.toDouble())
                put("liveDischargedAh", liveDischargedAh.toDouble())
                put("liveChargedKwh", liveChargedKwh.toDouble())
                put("liveDischargedKwh", liveDischargedKwh.toDouble())
                put("offlineChargedAh", offlineChargedAh.toDouble())
                put("offlineDischargedAh", offlineDischargedAh.toDouble())
                put("offlineChargedKwh", offlineChargedKwh.toDouble())
                put("offlineDischargedKwh", offlineDischargedKwh.toDouble())
                put("lastUpdated", record.lastUpdated)
            }
            prefs.edit().putString("today_record", json.toString()).apply()
        } catch (e: Exception) {
            Log.e("EnergyHistoryManager", "Error saving today's record: ${e.message}")
        }
    }

    private fun saveHistoryList(list: List<DailyEnergyRecord>) {
        try {
            val array = JSONArray()
            for (rec in list) {
                val obj = JSONObject().apply {
                    put("date", rec.date)
                    put("chargedAh", rec.chargedAh.toDouble())
                    put("dischargedAh", rec.dischargedAh.toDouble())
                    put("chargedKwh", rec.chargedKwh.toDouble())
                    put("dischargedKwh", rec.dischargedKwh.toDouble())
                    put("minSoc", rec.minSoc)
                    put("maxSoc", rec.maxSoc)
                    put("minAh", rec.minAh.toDouble())
                    put("maxAh", rec.maxAh.toDouble())
                    put("lastUpdated", rec.lastUpdated)
                }
                array.put(obj)
            }
            prefs.edit().putString("history_records", array.toString()).apply()
        } catch (e: Exception) {
            Log.e("EnergyHistoryManager", "Error saving history list: ${e.message}")
        }
    }

    private fun loadFromPreferences() {
        try {
            // Load historical records
            val historyJson = prefs.getString("history_records", null)
            if (!historyJson.isNullOrBlank()) {
                val array = JSONArray(historyJson)
                val list = mutableListOf<DailyEnergyRecord>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        DailyEnergyRecord(
                            date = obj.optString("date", ""),
                            chargedAh = obj.optDouble("chargedAh", 0.0).toFloat(),
                            dischargedAh = obj.optDouble("dischargedAh", 0.0).toFloat(),
                            chargedKwh = obj.optDouble("chargedKwh", 0.0).toFloat(),
                            dischargedKwh = obj.optDouble("dischargedKwh", 0.0).toFloat(),
                            minSoc = obj.optInt("minSoc", 0),
                            maxSoc = obj.optInt("maxSoc", 0),
                            minAh = obj.optDouble("minAh", 0.0).toFloat(),
                            maxAh = obj.optDouble("maxAh", 0.0).toFloat(),
                            lastUpdated = obj.optLong("lastUpdated", 0L)
                        )
                    )
                }
                _historyList.value = list
            }

            // Load today's record
            val todayJson = prefs.getString("today_record", null)
            if (!todayJson.isNullOrBlank()) {
                val obj = JSONObject(todayJson)
                val savedDate = obj.optString("date", "")
                val todayStr = getTodayDateString()

                if (savedDate == todayStr) {
                    currentDate = savedDate

                    // Discard legacy buggy odometer values if present from prior builds
                    val isLegacyBuggy = obj.has("b1StartCycleAh") && !obj.has("offlineChargedAh")

                    if (!isLegacyBuggy) {
                        liveChargedAh = obj.optDouble("liveChargedAh", 0.0).toFloat()
                        liveDischargedAh = obj.optDouble("liveDischargedAh", 0.0).toFloat()
                        liveChargedKwh = obj.optDouble("liveChargedKwh", 0.0).toFloat()
                        liveDischargedKwh = obj.optDouble("liveDischargedKwh", 0.0).toFloat()
                        offlineChargedAh = obj.optDouble("offlineChargedAh", 0.0).toFloat()
                        offlineDischargedAh = obj.optDouble("offlineDischargedAh", 0.0).toFloat()
                        offlineChargedKwh = obj.optDouble("offlineChargedKwh", 0.0).toFloat()
                        offlineDischargedKwh = obj.optDouble("offlineDischargedKwh", 0.0).toFloat()
                        lastKnownRemainingAh = obj.optDouble("lastKnownRemainingAh", 0.0).toFloat()
                        startRemainingTotal = obj.optDouble("startRemainingTotal", 0.0).toFloat()
                    }

                    b1StartRemainingAh = obj.optDouble("b1StartRemainingAh", 0.0).toFloat()
                    b2StartRemainingAh = obj.optDouble("b2StartRemainingAh", 0.0).toFloat()
                    b1HasBaseline = obj.optBoolean("b1HasBaseline", false)
                    b2HasBaseline = obj.optBoolean("b2HasBaseline", false)
                    minSoc = obj.optInt("minSoc", 0)
                    maxSoc = obj.optInt("maxSoc", 0)
                    minAh = obj.optDouble("minAh", 0.0).toFloat()
                    maxAh = obj.optDouble("maxAh", 0.0).toFloat()
                    startSoc = obj.optInt("startSoc", minSoc)

                    val loadedChargedAh = if (isLegacyBuggy) 0f else obj.optDouble("chargedAh", 0.0).toFloat()
                    val loadedDischargedAh = if (isLegacyBuggy) 0f else obj.optDouble("dischargedAh", 0.0).toFloat()
                    val loadedChargedKwh = if (isLegacyBuggy) 0f else obj.optDouble("chargedKwh", 0.0).toFloat()
                    val loadedDischargedKwh = if (isLegacyBuggy) 0f else obj.optDouble("dischargedKwh", 0.0).toFloat()

                    _todayEnergy.value = DailyEnergyRecord(
                        date = savedDate,
                        chargedAh = loadedChargedAh,
                        dischargedAh = loadedDischargedAh,
                        chargedKwh = loadedChargedKwh,
                        dischargedKwh = loadedDischargedKwh,
                        minSoc = minSoc,
                        maxSoc = maxSoc,
                        minAh = minAh,
                        maxAh = maxAh,
                        lastUpdated = obj.optLong("lastUpdated", 0L)
                    )
                } else if (savedDate.isNotBlank()) {
                    // It's from a previous day that was not yet archived
                    val oldRecord = DailyEnergyRecord(
                        date = savedDate,
                        chargedAh = obj.optDouble("chargedAh", 0.0).toFloat(),
                        dischargedAh = obj.optDouble("dischargedAh", 0.0).toFloat(),
                        chargedKwh = obj.optDouble("chargedKwh", 0.0).toFloat(),
                        dischargedKwh = obj.optDouble("dischargedKwh", 0.0).toFloat(),
                        minSoc = obj.optInt("minSoc", 0),
                        maxSoc = obj.optInt("maxSoc", 0),
                        minAh = obj.optDouble("minAh", 0.0).toFloat(),
                        maxAh = obj.optDouble("maxAh", 0.0).toFloat(),
                        lastUpdated = obj.optLong("lastUpdated", 0L)
                    )
                    if (oldRecord.chargedAh > 0.05f || oldRecord.dischargedAh > 0.05f) {
                        val currentList = _historyList.value.filter { it.date != savedDate }.toMutableList()
                        currentList.add(0, oldRecord)
                        _historyList.value = currentList.take(30)
                        saveHistoryList(_historyList.value)
                    }
                    currentDate = todayStr
                    _todayEnergy.value = DailyEnergyRecord(date = todayStr)
                }
            } else {
                currentDate = getTodayDateString()
                _todayEnergy.value = DailyEnergyRecord(date = currentDate)
            }
        } catch (e: Exception) {
            Log.e("EnergyHistoryManager", "Error loading preferences: ${e.message}")
        }
    }
}
