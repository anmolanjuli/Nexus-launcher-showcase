package com.nexus.launcher.data.repository

import com.nexus.launcher.domain.model.AppModel

interface AppRepository {
    suspend fun getInstalledApps(): List<AppModel>

    /**
     * The list the most recent [getInstalledApps] produced, or empty before the first one finishes.
     *
     * Exists so a new screen can start populated. The repository lives for the whole process, but
     * `MainViewModel` does not — every new `MainActivity` instance gets a fresh one — and a fresh
     * view model used to start from an empty list and wait for every icon to be resolved again.
     * That wait is what showed as the drawer's folders appearing instantly while its apps took a
     * second or two: folders come straight from the database, apps only from this list.
     *
     * Always a snapshot, never the source of truth: callers still run [getInstalledApps] and
     * replace it, so installs, removals and icon changes are picked up exactly as before.
     */
    val lastKnownApps: List<AppModel>
}
