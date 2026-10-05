package com.bibin.visioneye.people

import java.io.File

/**
 * Architectural contract for local storage and retrieval of enrolled individuals.
 *
 * All face embeddings and profile metadata are persisted strictly on-device.
 * No data is ever transmitted to cloud or external servers.
 */
interface PeopleRepository {
    /**
     * Retrieves all saved people currently enrolled on-device.
     */
    fun getSavedPeople(): List<SavedPerson>

    /**
     * Looks up an enrolled person by unique [id].
     */
    fun getPersonById(id: String): SavedPerson?

    /**
     * Saves or updates an enrolled person profile.
     */
    fun savePerson(person: SavedPerson)

    /**
     * Deletes an enrolled person by [id].
     * @return true if found and removed, false otherwise.
     */
    fun deletePerson(id: String): Boolean

    /**
     * Clears all enrolled people.
     */
    fun clearAll()
}

/**
 * Thread-safe, on-device file-based persistence for enrolled people.
 *
 * Stores profiles and embeddings in a local delimited file (`people_profiles.dat`) inside
 * internal app storage. Zero third-party dependencies.
 *
 * @param storageDir Local directory where the persistence file is stored.
 */
class LocalFilePeopleRepository(
    private val storageDir: File
) : PeopleRepository {

    private val lock = Any()
    private val profilesFile = File(storageDir, "people_profiles.dat")
    private val memoryCache = mutableMapOf<String, SavedPerson>()

    init {
        loadFromDisk()
    }

    override fun getSavedPeople(): List<SavedPerson> = synchronized(lock) {
        memoryCache.values.toList()
    }

    override fun getPersonById(id: String): SavedPerson? = synchronized(lock) {
        memoryCache[id]
    }

    override fun savePerson(person: SavedPerson) = synchronized(lock) {
        memoryCache[person.id] = person
        persistToDisk()
    }

    override fun deletePerson(id: String): Boolean = synchronized(lock) {
        val removed = memoryCache.remove(id) != null
        if (removed) {
            persistToDisk()
        }
        removed
    }

    override fun clearAll() = synchronized(lock) {
        memoryCache.clear()
        persistToDisk()
    }

    private fun loadFromDisk() = synchronized(lock) {
        memoryCache.clear()
        if (!profilesFile.exists()) return

        try {
            var currentId = ""
            var currentName = ""
            var currentEnrolledAt = 0L
            var currentEmbeddings = mutableListOf<FloatArray>()
            var inPerson = false

            profilesFile.forEachLine(Charsets.UTF_8) { rawLine ->
                val line = rawLine.trim()
                if (line.startsWith("PERSON|")) {
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        currentId = parts[1]
                        currentName = parts[2]
                        currentEnrolledAt = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                        currentEmbeddings = mutableListOf()
                        inPerson = true
                    }
                } else if (line == "END_PERSON") {
                    if (inPerson && currentId.isNotBlank()) {
                        memoryCache[currentId] = SavedPerson(
                            id = currentId,
                            name = currentName,
                            embeddings = currentEmbeddings.toList(),
                            enrolledAt = currentEnrolledAt
                        )
                    }
                    inPerson = false
                } else if (inPerson && line.isNotEmpty()) {
                    val floats = line.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
                    if (floats.isNotEmpty()) {
                        currentEmbeddings.add(floats)
                    }
                }
            }
        } catch (e: Exception) {
            memoryCache.clear()
        }
    }

    private fun persistToDisk() {
        try {
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            val tempFile = File(storageDir, "people_profiles.dat.tmp")
            tempFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                for (person in memoryCache.values) {
                    writer.write("PERSON|${person.id}|${person.name}|${person.enrolledAt}\n")
                    for (vec in person.embeddings) {
                        writer.write(vec.joinToString(","))
                        writer.write("\n")
                    }
                    writer.write("END_PERSON\n")
                }
            }
            if (tempFile.exists()) {
                if (profilesFile.exists()) {
                    profilesFile.delete()
                }
                tempFile.renameTo(profilesFile)
            }
        } catch (e: Exception) {
            // Ignore persistence errors in read-only environments
        }
    }
}
