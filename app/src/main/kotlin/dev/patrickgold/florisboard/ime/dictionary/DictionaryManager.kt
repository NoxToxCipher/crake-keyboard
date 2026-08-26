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
        val florisDao = florisUserDictionaryDao()
        val systemDao = systemUserDictionaryDao()
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return emptyList()

        return buildList {
            val lowerWord = trimmed.lowercase()

            // 1. Dynamic Timestamp / Date Macros
            if (lowerWord == "!time" || lowerWord == "!t") {
                val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                add(WordSuggestionCandidate(formattedTime, secondaryText = "Snippet • Time", confidence = 1.0, isEligibleForAutoCommit = true))
            } else if (lowerWord == "!date" || lowerWord == "!d") {
                val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                add(WordSuggestionCandidate(formattedDate, secondaryText = "Snippet • Date", confidence = 1.0, isEligibleForAutoCommit = true))
            } else if (lowerWord == "!now" || lowerWord == "!datetime") {
                val formattedDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                add(WordSuggestionCandidate(formattedDateTime, secondaryText = "Snippet • Now", confidence = 1.0, isEligibleForAutoCommit = true))
            }

            // 2. Custom User Defined Shortcuts / Snippets
            florisDao?.let { dao ->
                val matches = try {
                    val direct = dao.queryShortcut(trimmed)
                    val lower = if (trimmed != lowerWord) dao.queryShortcut(lowerWord) else emptyList()
                    (direct + lower).distinctBy { it.id }
                } catch (e: Exception) {
                    emptyList()
                }

                for (entry in matches) {
                    add(0, WordSuggestionCandidate(
                        text = entry.word,
                        secondaryText = "Snippet • " + (entry.shortcut ?: "!"),
                        confidence = 1.0,
                        isEligibleForAutoCommit = true,
                    ))
                }
            }

            // 3. System User Dictionary Shortcuts
            if (prefs.dictionary.enableSystemUserDictionary.get()) {
                systemDao?.queryShortcut(trimmed, locale)?.let { entries ->
                    for (entry in entries) {
                        add(0, WordSuggestionCandidate(
                            text = entry.word,
                            secondaryText = "Snippet",
                            confidence = 1.0,
                            isEligibleForAutoCommit = true,
                        ))
                    }
                }
            }
        }
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
