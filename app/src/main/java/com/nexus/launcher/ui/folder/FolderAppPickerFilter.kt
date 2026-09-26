package com.nexus.launcher.ui.folder

import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem

object FolderAppPickerFilter {

    /** Drawer folders live on this sentinel page; everything else is a home-screen folder. */
    private const val DRAWER_PAGE = -2

    /**
     * Packages the picker should hide because they are already foldered **in the same space** as
     * [folderId].
     *
     * This used to be `dao.getFolderedPackageNames()` — every foldered app anywhere — so opening
     * the picker for a drawer folder hid every app sitting in a home-screen folder, and vice
     * versa. The home screen and the drawer are independent surfaces: an app can legitimately be
     * in a folder on both, exactly as it can sit loose on the home screen while still appearing
     * in the drawer. [HomeScreenDao.getFolderedPackageNamesFlow] already drew this line for the
     * drawer's own list; the picker just wasn't drawing it too.
     *
     * The "show apps already in folders" switch still overrides this entirely.
     */
    suspend fun folderedPackageNames(dao: HomeScreenDao, folderId: Long): Set<String> =
        if (dao.getItemPage(folderId) == DRAWER_PAGE) {
            dao.getDrawerFolderedPackageNamesSync().toSet()
        } else {
            dao.getHomeFolderedPackageNamesSync().toSet()
        }

    suspend fun targetFolderPackages(dao: HomeScreenDao, folderId: Long): Set<String> =
        dao.getItemsInFolderSync(folderId).map { it.packageName }.toSet()

    fun filterVisible(
        allApps: List<HomeScreenItem>,
        folderedPackages: Set<String>,
        targetFolderPackages: Set<String>,
        showAlreadyInFolders: Boolean
    ): List<HomeScreenItem> {
        return if (showAlreadyInFolders) {
            allApps.filter { it.packageName !in targetFolderPackages }
        } else {
            allApps.filter { it.packageName !in folderedPackages }
        }
    }
}
