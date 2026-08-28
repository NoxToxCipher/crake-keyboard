/*
 * Copyright (C) 2021-2026 The Crake Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.dictionary

import android.content.Context
import androidx.room.Room
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.FlorisLocale
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DictionaryManager private constructor(context: Context) {
    private val applicationContext: WeakReference<Context> = WeakReference(context.applicationContext ?: context)
    private val prefs by FlorisPreferenceStore

    private var florisUserDictionaryDatabase: FlorisUserDictionaryDatabase? = null
    private var systemUserDictionaryDatabase: SystemUserDictionaryDatabase? = null

    private val userDictionaryCache = UserDictionaryCache()
    @Volatile
    private var isCacheLoaded = false

    companion object {
        private var defaultInstance: DictionaryManager? = null

        fun init(applicationContext: Context): DictionaryManager {
            val instance = DictionaryManager(applicationContext)
            defaultInstance = instance
            instance.loadUserDictionariesIfNecessary()
            return instance
        }

        fun default(): DictionaryManager {
            val instance = defaultInstance
            if (instance != null) {
                return instance
            } else {
                throw UninitializedPropertyAccessException(
                    "${DictionaryManager::class.simpleName} has not been initialized previously. Make sure to call init(applicationContext) before using default()."
                )
            }
        }
    }

    fun queryUserDictionary(word: String, locale: FlorisLocale? = null): List<SuggestionCandidate> {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Dynamic Timestamp / Date Macros (Instant, in-memory, 0 allocations)
        val macroCandidates = UserDictionaryCache.evaluateMacros(trimmed)
        if (macroCandidates.isNotEmpty()) {
            return macroCandidates
        }

        // 2. Ensure in-memory cache is warmed
        if (!isCacheLoaded) {
            warmUserDictionaryCache()
        }

        // 3. Ultra-fast in-memory index query (0 SQLite disk I/O)
        val cachedShortcuts = userDictionaryCache.queryShortcuts(trimmed, locale)

        // 4. Optional System User Dictionary (if enabled in settings)
        val systemCandidates = if (prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDao()?.queryShortcut(trimmed, locale)?.map { entry ->
                WordSuggestionCandidate(
                    text = entry.word,
                    secondaryText = "Snippet",
                    confidence = 1.0,
                    isEligibleForAutoCommit = true,
                )
            } ?: emptyList()
        } else {
            emptyList()
        }

        return cachedShortcuts + systemCandidates
    }

    @Synchronized
    fun warmUserDictionaryCache() {
        try {
            val dao = florisUserDictionaryDao()
            val entries = dao?.queryAll() ?: emptyList()
            userDictionaryCache.updateEntries(entries)
            isCacheLoaded = true
        } catch (e: Exception) {
            // Keep safe
        }
    }

    fun invalidateUserDictionaryCache() {
        warmUserDictionaryCache()
    }

    fun spell(word: String, locale: FlorisLocale): Boolean {
        val florisDao = florisUserDictionaryDao()
        val systemDao = systemUserDictionaryDao()
        if (florisDao == null && systemDao == null) {
            return false
        }
        var ret = false
        ret = ret || (florisDao?.queryExactFuzzyLocale(word, locale)?.isNotEmpty() ?: false)
        ret = ret || (florisDao?.queryShortcut(word, locale)?.isNotEmpty() ?: false)
        if (prefs.dictionary.enableSystemUserDictionary.get()) {
            ret = ret || (systemDao?.queryExactFuzzyLocale(word, locale)?.isNotEmpty() ?: false)
            ret = ret || (systemDao?.queryShortcut(word, locale)?.isNotEmpty() ?: false)
        }
        return ret
    }

    @Synchronized
    fun florisUserDictionaryDao(): UserDictionaryDao? {
        if (florisUserDictionaryDatabase == null) {
            val context = applicationContext.get() ?: return null
            florisUserDictionaryDatabase = Room.databaseBuilder(
                context,
                FlorisUserDictionaryDatabase::class.java,
                FlorisUserDictionaryDatabase.DB_FILE_NAME
            ).allowMainThreadQueries().build()
        }
        return florisUserDictionaryDatabase?.userDictionaryDao()
    }

    @Synchronized
    fun florisUserDictionaryDatabase(): FlorisUserDictionaryDatabase? {
        if (florisUserDictionaryDatabase == null) {
            florisUserDictionaryDao()
        }
        return florisUserDictionaryDatabase
    }

    @Synchronized
    fun systemUserDictionaryDao(): UserDictionaryDao? {
        if (prefs.dictionary.enableSystemUserDictionary.get()) {
            if (systemUserDictionaryDatabase == null) {
                val context = applicationContext.get() ?: return null
                systemUserDictionaryDatabase = SystemUserDictionaryDatabase(context)
            }
            return systemUserDictionaryDatabase?.userDictionaryDao()
        }
        return null
    }

    @Synchronized
    fun systemUserDictionaryDatabase(): SystemUserDictionaryDatabase? {
        return if (prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase
        } else {
            null
        }
    }

    @Synchronized
    fun loadUserDictionariesIfNecessary() {
        val context = applicationContext.get() ?: return
        if (florisUserDictionaryDatabase == null) {
            florisUserDictionaryDatabase = Room.databaseBuilder(
                context,
                FlorisUserDictionaryDatabase::class.java,
                FlorisUserDictionaryDatabase.DB_FILE_NAME
            ).allowMainThreadQueries().build()
        }
        if (systemUserDictionaryDatabase == null && prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase = SystemUserDictionaryDatabase(context)
        }
    }

    @Synchronized
    fun unloadUserDictionariesIfNecessary() {
        if (florisUserDictionaryDatabase != null) {
            florisUserDictionaryDatabase?.close()
            florisUserDictionaryDatabase = null
        }
        if (systemUserDictionaryDatabase != null) {
            systemUserDictionaryDatabase = null
        }
    }
}
