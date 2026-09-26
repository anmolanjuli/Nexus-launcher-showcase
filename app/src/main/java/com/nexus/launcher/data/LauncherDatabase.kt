package com.nexus.launcher.data

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

@Entity(tableName = "schema_placeholder")
internal data class SchemaPlaceholderEntity(
    @PrimaryKey val id: Int = 0,
)

@Database(
    entities = [
        SchemaPlaceholderEntity::class,
        RecentSearchEntity::class,
        HomeScreenItem::class,
        FeedSource::class,
        FeedArticle::class,
        ItemPosition::class,
        NotificationRecord::class
    ],
    version = 18,
    exportSchema = false,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun launcherDao(): LauncherDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun homeScreenDao(): HomeScreenDao
    abstract fun feedDao(): FeedDao
    abstract fun itemPositionDao(): ItemPositionDao
    abstract fun notificationDao(): NotificationDao
}
