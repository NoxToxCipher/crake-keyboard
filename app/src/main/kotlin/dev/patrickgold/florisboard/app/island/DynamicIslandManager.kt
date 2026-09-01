/*
 * Copyright (C) 2026 The Crake Contributors
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

package dev.patrickgold.florisboard.app.island

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class IslandPriority(val weight: Int) {
    LOW(1),
    NORMAL(2),
    HIGH(3),
    URGENT(4),
}

data class IslandNotification(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val emoji: String? = null,
    val icon: ImageVector? = null,
    val accentColor: Color = Color(0xFF10B981), // Default CyberEmerald
    val durationMs: Long = 4000L, // 0L means persistent (until dismissed or progress reaches 1f)
    val progress: Float? = null, // 0.0f .. 1.0f
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val priority: IslandPriority = IslandPriority.NORMAL,
    val timestamp: Long = System.currentTimeMillis(),
)

object DynamicIslandManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentNotification = MutableStateFlow<IslandNotification?>(null)
    val currentNotification: StateFlow<IslandNotification?> = _currentNotification.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private var autoDismissJob: Job? = null

    fun post(notification: IslandNotification) {
        val current = _currentNotification.value
        // If current notification has higher priority and is still active, don't preempt unless urgent
        if (current != null && current.priority.weight > notification.priority.weight && current.id != notification.id) {
            return
        }

        autoDismissJob?.cancel()
        _currentNotification.value = notification
        _isExpanded.value = false

        if (notification.durationMs > 0L) {
            autoDismissJob = scope.launch {
                delay(notification.durationMs)
                if (_currentNotification.value?.id == notification.id) {
                    dismiss(notification.id)
                }
            }
        }
    }

    fun updateProgress(id: String, progress: Float, subtitle: String? = null) {
        val current = _currentNotification.value ?: return
        if (current.id == id) {
            val updated = current.copy(
                progress = progress.coerceIn(0f, 1f),
                subtitle = subtitle ?: current.subtitle,
            )
            _currentNotification.value = updated
            if (progress >= 1f && current.durationMs == 0L) {
                // Auto-dismiss 2.5s after 100% completion
                autoDismissJob?.cancel()
                autoDismissJob = scope.launch {
                    delay(2500L)
                    if (_currentNotification.value?.id == id) {
                        dismiss(id)
                    }
                }
            }
        }
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
        if (expanded) {
            // Cancel auto-dismiss while user is inspecting expanded card
            autoDismissJob?.cancel()
        } else {
            val current = _currentNotification.value
            if (current != null && current.durationMs > 0L) {
                autoDismissJob = scope.launch {
                    delay(current.durationMs)
                    if (_currentNotification.value?.id == current.id) {
                        dismiss(current.id)
                    }
                }
            }
        }
    }

    fun toggleExpanded() {
        setExpanded(!_isExpanded.value)
    }

    fun dismiss(id: String? = null) {
        val current = _currentNotification.value
        if (id == null || current?.id == id) {
            autoDismissJob?.cancel()
            _currentNotification.value = null
            _isExpanded.value = false
        }
    }
}
