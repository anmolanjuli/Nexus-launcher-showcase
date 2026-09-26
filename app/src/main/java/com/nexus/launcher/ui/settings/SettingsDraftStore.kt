package com.nexus.launcher.ui.settings

import com.nexus.launcher.data.prefs.NexusDefaults
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SettingsDraftStore(
    private val settingsRepository: SettingsRepository,
    private val preferenceManager: PreferenceManager,
    private val scope: CoroutineScope
) {
    private val lock = Mutex()

    private val _draft = MutableStateFlow<NexusSettingsData?>(null)
    val draft: StateFlow<NexusSettingsData?> = _draft.asStateFlow()

    private val _animSpeed = MutableStateFlow(1.0f)
    val animSpeed: StateFlow<Float> = _animSpeed.asStateFlow()

    private val _unsaved = MutableStateFlow(false)
    val unsaved: StateFlow<Boolean> = _unsaved.asStateFlow()

    private val _bindEpoch = MutableStateFlow(0)
    val bindEpoch: StateFlow<Int> = _bindEpoch.asStateFlow()

    private var baseline: NexusSettingsData? = null
    private var baselineAnim = 1.0f

    /** What is actually persisted, which diverges from [_draft] only by the staged changes that
     *  are still waiting for Apply. */
    private var committed: NexusSettingsData? = null
    private var committedAnim = 1.0f
    private var commitJob: Job? = null

    suspend fun ensureLoaded() {
        lock.withLock {
            if (_draft.value != null) return@withLock
            val s = settingsRepository.settingsFlow.first()
            baseline = s
            committed = s
            _draft.value = s
            baselineAnim = preferenceManager.getAnimSpeedMultiplier()
            committedAnim = baselineAnim
            _animSpeed.value = baselineAnim
            _unsaved.value = false
            _bindEpoch.value = _bindEpoch.value + 1
        }
    }

    /**
     * Applies [transform] to the draft, and unless [staged] commits it.
     *
     * Settings here used to be uniformly staged behind the Apply bar, while the theme controller's
     * own settings (mode, palette, accent, background layer) and the UI Style picker wrote
     * straight through — so whether a change needed confirming depended on which mechanism the
     * control happened to use, sometimes within one screen. Immediate is the right default for a
     * launcher: every one of these is visible the moment it lands and reversible by changing it
     * back, and the Apply bar's only real job was making that harder.
     *
     * [staged] is for the exception: changes that rearrange the home grid. Applying a new column
     * or row count reflows placements and can move a user's icons, which is the one genuinely
     * destructive thing in the hub and worth an explicit confirmation.
     *
     * Commits are debounced, because a slider emits continuously while dragging and each commit
     * is a DataStore write. The draft updates on every tick, so previews stay live at the
     * finger's rate regardless.
     */
    fun patch(staged: Boolean = false, transform: (NexusSettingsData) -> NexusSettingsData) {
        val current = _draft.value ?: return
        _draft.value = transform(current)
        if (staged) {
            _unsaved.value = true
        } else {
            // Applied to the committed copy too, so a pending staged change cannot ride along on
            // an unrelated immediate one — the two snapshots stay independent until apply().
            committed = committed?.let(transform)
            scheduleCommit()
        }
    }

    fun patchAnimSpeed(value: Float, staged: Boolean = false) {
        _animSpeed.value = value
        if (staged) {
            _unsaved.value = true
        } else {
            committedAnim = value
            scheduleCommit()
        }
    }

    private fun scheduleCommit() {
        commitJob?.cancel()
        commitJob = scope.launch {
            delay(COMMIT_DEBOUNCE_MS)
            lock.withLock {
                val snapshot = committed ?: return@withLock
                settingsRepository.applySnapshot(snapshot)
                preferenceManager.setAnimSpeedMultiplier(committedAnim)
                baseline = snapshot
                baselineAnim = committedAnim
            }
        }
    }

    /** Drops staged changes only — anything already committed is live and stays. */
    fun discard() {
        val base = committed ?: baseline ?: return
        _draft.value = base
        _animSpeed.value = committedAnim
        _unsaved.value = false
        _bindEpoch.value++
    }

    suspend fun apply() {
        val pending = _draft.value ?: return
        if (!_unsaved.value) return
        commitJob?.cancel()
        settingsRepository.applySnapshot(pending)
        preferenceManager.setAnimSpeedMultiplier(_animSpeed.value)
        baseline = pending
        committed = pending
        baselineAnim = _animSpeed.value
        committedAnim = _animSpeed.value
        _unsaved.value = false
        _bindEpoch.value++
    }

    private companion object {
        /** Long enough to coalesce a slider drag, short enough that letting go feels committed. */
        const val COMMIT_DEBOUNCE_MS = 250L
    }
}
