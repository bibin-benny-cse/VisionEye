package com.bibin.visioneye.fusion.decision

/**
 * Priority levels for alert candidates and system-wide perception events.
 *
 * In Milestone 5 (Context-Aware Decision Engine for NAVIGATE mode), priority
 * dictates presentation ordering among simultaneous detections. It does NOT
 * represent collision risk or safety hazard.
 *
 * Higher [rank] values represent higher priority during alert selection and arbitration.
 */
enum class AlertPriority(val rank: Int) {
    /** Low precedence (common small household objects, e.g. cups, books). */
    LOW(10),

    /** Medium precedence (common furniture, carryable bags, e.g. chairs, tables, backpacks). */
    MEDIUM(20),

    /** High precedence (transit items, vehicles, street infrastructure, e.g. bicycles, cars). */
    HIGH(30),

    /** Urgent precedence (people, active dynamic agents). */
    URGENT(40),

    // --- Architectural System Ranks (Retained for system-wide compatibility with AGENTS.md) ---
    GENERAL_OBJECT_INFO(1),
    NAVIGATION_INSTRUCTION(2),
    IMMEDIATE_OBSTACLE(3),
    COLLISION_WARNING(4);

    companion object {
        fun fromRank(rank: Int): AlertPriority {
            return when {
                rank >= URGENT.rank -> URGENT
                rank >= HIGH.rank -> HIGH
                rank >= MEDIUM.rank -> MEDIUM
                else -> LOW
            }
        }
    }
}
