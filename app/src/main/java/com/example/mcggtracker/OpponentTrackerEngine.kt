package com.example.mcggtracker

import kotlin.math.max
import kotlin.math.min

data class TrackerState(
    val roundCount: Int = 0,
    val selfName: String = "-",
    val currentOpponent: String = "-",
    val nextCandidates: List<String> = emptyList(),
    val facedInCycle: List<String> = emptyList(),
    val statusText: String = "Menunggu battle dimulai..."
)

class OpponentTrackerEngine {
    private var roundCount = 0
    private var selfName: String? = null
    private val knownOpponents = mutableListOf<String>()
    private val facedInCurrentCycle = mutableListOf<String>()
    private var lastOpponent: String? = null

    fun reset() {
        roundCount = 0
        selfName = null
        knownOpponents.clear()
        facedInCurrentCycle.clear()
        lastOpponent = null
    }

    fun processBattleSnapshot(slots: List<SlotReadResult>): TrackerState {
        if (slots.isEmpty()) return buildState("Gagal membaca nama pada frame ini")
        roundCount++

        val selfSlot = slots.firstOrNull { it.isSelfYellow }
        if (selfSlot != null) {
            selfName = normalizeOrMatch(selfSlot.name, isSelf = true)
        }

        val activeThisRound = mutableSetOf<String>()
        for (slot in slots) {
            if (!slot.isSelfYellow && (selfName == null || !isSamePlayer(slot.name, selfName!!))) {
                activeThisRound.add(normalizeOrMatch(slot.name, isSelf = false))
            }
        }

        var detectedOpponent: String? = null
        if (selfSlot != null) {
            val partnerIndex = if (selfSlot.slotIndex % 2 == 0) selfSlot.slotIndex + 1 else selfSlot.slotIndex - 1
            val partnerSlot = slots.firstOrNull { it.slotIndex == partnerIndex }
            if (partnerSlot != null) {
                detectedOpponent = normalizeOrMatch(partnerSlot.name, isSelf = false)
            }
        }

        if (detectedOpponent != null) {
            lastOpponent = detectedOpponent
            if (facedInCurrentCycle.none { isSamePlayer(it, detectedOpponent) }) {
                facedInCurrentCycle.add(detectedOpponent)
            }
        }

        val activePool = if (activeThisRound.size >= 2) activeThisRound.toList() else knownOpponents
        var candidates = activePool.filter { player ->
            facedInCurrentCycle.none { faced -> isSamePlayer(faced, player) }
        }

        if (candidates.isEmpty() && activePool.isNotEmpty()) {
            facedInCurrentCycle.clear()
            if (lastOpponent != null) facedInCurrentCycle.add(lastOpponent!!)
            candidates = activePool.filter { lastOpponent == null || !isSamePlayer(it, lastOpponent!!) }
        }

        return buildState(
            status = if (detectedOpponent != null) "Ronde PvP #$roundCount tercatat" else "Ronde #$roundCount",
            currentOpponent = detectedOpponent ?: "-",
            candidates = candidates
        )
    }

    private fun buildState(
        status: String,
        currentOpponent: String = lastOpponent ?: "-",
        candidates: List<String> = emptyList()
    ) = TrackerState(roundCount, selfName ?: "-", currentOpponent, candidates, facedInCurrentCycle.toList(), status)

    private fun normalizeOrMatch(rawName: String, isSelf: Boolean): String {
        val clean = rawName.trim()
        if (isSelf) return selfName ?: clean
        return knownOpponents.firstOrNull { isSamePlayer(it, clean) } ?: clean.also { knownOpponents.add(it) }
    }

    private fun isSamePlayer(a: String, b: String): Boolean {
        val s1 = a.lowercase().replace("...", "").trim()
        val s2 = b.lowercase().replace("...", "").trim()
        if (s1 == s2) return true
        if (s1.length >= 4 && s2.length >= 4 && (s1.contains(s2) || s2.contains(s1))) return true
        return similarity(s1, s2) >= 0.68
    }

    private fun similarity(s1: String, s2: String): Double {
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        return (maxLen - levenshtein(s1, s2)).toDouble() / maxLen.toDouble()
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }
}
