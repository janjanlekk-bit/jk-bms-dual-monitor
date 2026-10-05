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
    private var b1StartCycleAh: Float = 0f
    private var b2StartCycleAh: Float = 0f
    private var b1StartRemainingAh: Float = 0f
    private var b2StartRemainingAh: Float = 0f
    private var b1HasBaseline: Boolean = false
    private var b2HasBaseline: Boolean = false

    private var liveChargedAh: Float = 0f
    private var liveDischargedAh: Float = 0f
    private var liveChargedKwh: Float = 0f
    private var liveDischargedKwh: Float = 0f

    private var minSoc: Int = 0
    private var maxSoc: Int = 0
    private var lastIntegrationTimeMs: Long = 0L
    private var lastSaveTimeMs: Long = 0L

    init {
        loadFromPreferences()
    }

    private fun getTodayDateString(): String {
        return dateFormat.format(Date())
    }

    @Synchronized
    fun update(b1: BmsData, b2: BmsData, bank: TotalBankData) {
        val todayStr = getTodayDateString()
        val now = System.currentTimeMillis()

        // 1. Check for midnight date change / day rollover
        if (currentDate.isNotBlank() && currentDate != todayStr) {
            archivePreviousDay()
            startNewDay(todayStr, b1, b2, bank)
        } else if (currentDate.isBlank()) {
            startNewDay(todayStr, b1, b2, bank)
        }

        // 2. Establish baseline for B1 if first time connected today
        if (b1.voltage > 10f && !b1HasBaseline) {
            b1StartCycleAh = b1.totalChargingCycleAh
            b1StartRemainingAh = b1.remainingCapacityAh
            b1HasBaseline = true
        }

        // 3. Establish baseline for B2 if first time connected today
        if (b2.voltage > 10f && !b2HasBaseline) {
            b2StartCycleAh = b2.totalChargingCycleAh
            b2StartRemainingAh = b2.remainingCapacityAh
            b2HasBaseline = true
        }

        // 4. Live coulomb counting and energy integration while connected
        if (lastIntegrationTimeMs > 0L) {
            val dtSeconds = (now - lastIntegrationTimeMs) / 1000f
            if (dtSeconds in 0.1f..15f) { // Valid integration interval
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
        }
        lastIntegrationTimeMs = now

        // 5. Calculate offline catch-up via BMS Hardware Odometer
        var odoChargedAh = 0f
        if (b1HasBaseline && b1.totalChargingCycleAh > b1StartCycleAh) {
            odoChargedAh += (b1.totalChargingCycleAh - b1StartCycleAh)
        }
        if (b2HasBaseline && b2.totalChargingCycleAh > b2StartCycleAh) {
            odoChargedAh += (b2.totalChargingCycleAh - b2StartCycleAh)
        }

        // Remaining capacity delta across the day
        val curRemainingTotal = (if (b1.voltage > 10f) b1.remainingCapacityAh else 0f) +
                                (if (b2.voltage > 10f) b2.remainingCapacityAh else 0f)
        val startRemainingTotal = (if (b1HasBaseline) b1StartRemainingAh else 0f) +
                                 (if (b2HasBaseline) b2StartRemainingAh else 0f)
        val deltaRemainingAh = curRemainingTotal - startRemainingTotal

        // Discharged Ah via odometer formula: Discharged = Charged - DeltaRemaining
        val odoDischargedAh = if (odoChargedAh > 0f) {
            max(0f, odoChargedAh - deltaRemainingAh)
        } else 0f

        // Best unified numbers (taking whichever is higher between live integration and odometer)
        val effectiveChargedAh = max(liveChargedAh, odoChargedAh)
        val effectiveDischargedAh = max(liveDischargedAh, odoDischargedAh)

        val avgV = if (bank.voltage in 20f..150f) bank.voltage else 52.0f
        val effectiveChargedKwh = max(liveChargedKwh, (effectiveChargedAh * avgV) / 1000f)
        val effectiveDischargedKwh = max(liveDischargedKwh, (effectiveDischargedAh * avgV) / 1000f)

        // 6. Update min and max SOC
        val currentSoc = bank.capacityWeightedSoc
        if (currentSoc in 1..100) {
            minSoc = if (minSoc == 0) currentSoc else min(minSoc, currentSoc)
            maxSoc = if (maxSoc == 0) currentSoc else max(maxSoc, currentSoc)
        }

        val updatedRecord = DailyEnergyRecord(
            date = todayStr,
            chargedAh = effectiveChargedAh,
            dischargedAh = effectiveDischargedAh,
            chargedKwh = effectiveChargedKwh,
            dischargedKwh = effectiveDischargedKwh,
            minSoc = minSoc,
            maxSoc = maxSoc,
            lastUpdated = now
        )

        _todayEnergy.value = updatedRecord

        // 7. Debounced persist to SharedPreferences every 10 seconds
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

    private fun startNewDay(todayStr: String, b1: BmsData, b2: BmsData, bank: TotalBankData) {
        currentDate = todayStr
        liveChargedAh = 0f
        liveDischargedAh = 0f
        liveChargedKwh = 0f
        liveDischargedKwh = 0f

        if (b1.voltage > 10f) {
            b1StartCycleAh = b1.totalChargingCycleAh
            b1StartRemainingAh = b1.remainingCapacityAh
            b1HasBaseline = true
        } else {
            b1StartCycleAh = 0f
            b1StartRemainingAh = 0f
            b1HasBaseline = false
        }

        if (b2.voltage > 10f) {
            b2StartCycleAh = b2.totalChargingCycleAh
            b2StartRemainingAh = b2.remainingCapacityAh
            b2HasBaseline = true
        } else {
            b2StartCycleAh = 0f
            b2StartRemainingAh = 0f
            b2HasBaseline = false
        }

        val currentSoc = bank.capacityWeightedSoc
        minSoc = if (currentSoc in 1..100) currentSoc else 0
        maxSoc = if (currentSoc in 1..100) currentSoc else 0

        _todayEnergy.value = DailyEnergyRecord(
            date = todayStr,
            chargedAh = 0f,
            dischargedAh = 0f,
            chargedKwh = 0f,
            dischargedKwh = 0f,
            minSoc = minSoc,
            maxSoc = maxSoc,
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
                put("b1StartCycleAh", b1StartCycleAh.toDouble())
                put("b2StartCycleAh", b2StartCycleAh.toDouble())
                put("b1StartRemainingAh", b1StartRemainingAh.toDouble())
                put("b2StartRemainingAh", b2StartRemainingAh.toDouble())
                put("b1HasBaseline", b1HasBaseline)
                put("b2HasBaseline", b2HasBaseline)
                put("liveChargedAh", liveChargedAh.toDouble())
                put("liveDischargedAh", liveDischargedAh.toDouble())
                put("liveChargedKwh", liveChargedKwh.toDouble())
                put("liveDischargedKwh", liveDischargedKwh.toDouble())
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
                    b1StartCycleAh = obj.optDouble("b1StartCycleAh", 0.0).toFloat()
                    b2StartCycleAh = obj.optDouble("b2StartCycleAh", 0.0).toFloat()
                    b1StartRemainingAh = obj.optDouble("b1StartRemainingAh", 0.0).toFloat()
                    b2StartRemainingAh = obj.optDouble("b2StartRemainingAh", 0.0).toFloat()
                    b1HasBaseline = obj.optBoolean("b1HasBaseline", false)
                    b2HasBaseline = obj.optBoolean("b2HasBaseline", false)
                    liveChargedAh = obj.optDouble("liveChargedAh", 0.0).toFloat()
                    liveDischargedAh = obj.optDouble("liveDischargedAh", 0.0).toFloat()
                    liveChargedKwh = obj.optDouble("liveChargedKwh", 0.0).toFloat()
                    liveDischargedKwh = obj.optDouble("liveDischargedKwh", 0.0).toFloat()
                    minSoc = obj.optInt("minSoc", 0)
                    maxSoc = obj.optInt("maxSoc", 0)

                    _todayEnergy.value = DailyEnergyRecord(
                        date = savedDate,
                        chargedAh = obj.optDouble("chargedAh", 0.0).toFloat(),
                        dischargedAh = obj.optDouble("dischargedAh", 0.0).toFloat(),
                        chargedKwh = obj.optDouble("chargedKwh", 0.0).toFloat(),
                        dischargedKwh = obj.optDouble("dischargedKwh", 0.0).toFloat(),
                        minSoc = minSoc,
                        maxSoc = maxSoc,
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
