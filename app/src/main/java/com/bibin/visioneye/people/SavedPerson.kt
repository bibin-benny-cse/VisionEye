package com.bibin.visioneye.people

import java.util.UUID

/**
 * Domain entity representing an enrolled individual saved locally on-device.
 *
 * @property id Unique identifier (UUID).
 * @property name Display name spoken to the visually impaired user (e.g. "Father", "Mother", "Arun").
 * @property embeddings List of normalized 192-dimensional embedding vectors extracted during enrollment.
 * @property enrolledAt Epoch timestamp in milliseconds when the person was enrolled.
 */
data class SavedPerson(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val embeddings: List<FloatArray>,
    val enrolledAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SavedPerson
        if (id != other.id) return false
        if (name != other.name) return false
        if (embeddings.size != other.embeddings.size) return false
        for (i in embeddings.indices) {
            if (!embeddings[i].contentEquals(other.embeddings[i])) return false
        }
        return enrolledAt == other.enrolledAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + embeddings.hashCode()
        result = 31 * result + enrolledAt.hashCode()
        return result
    }
}
