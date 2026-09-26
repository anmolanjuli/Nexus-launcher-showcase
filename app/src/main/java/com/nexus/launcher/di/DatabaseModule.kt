package com.nexus.launcher.di

import android.content.Context
import androidx.room.Room
import com.nexus.launcher.data.LauncherDao
import com.nexus.launcher.data.LauncherDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface DaoEntryPoint {
    fun homeScreenDao(): com.nexus.launcher.data.HomeScreenDao
    fun launcherDatabase(): com.nexus.launcher.data.LauncherDatabase
    fun feedDao(): com.nexus.launcher.data.FeedDao
    fun recentSearchDao(): com.nexus.launcher.data.RecentSearchDao
    fun itemPositionDao(): com.nexus.launcher.data.ItemPositionDao
    fun notificationDao(): com.nexus.launcher.data.NotificationDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "launcher_database"

    private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `recent_searches` (`query` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`query`))")
        }
    }

    private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `recent_searches`")
            db.execSQL("CREATE TABLE IF NOT EXISTS `recent_searches` (`packageName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`packageName`))")
        }
    }

    private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `recent_searches`")
            db.execSQL("CREATE TABLE IF NOT EXISTS `recent_searches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
        }
    }

    private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `home_screen_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`packageName` TEXT NOT NULL, " +
                "`page` INTEGER NOT NULL, " +
                "`column` INTEGER NOT NULL, " +
                "`row` INTEGER NOT NULL)")
        }
    }

    private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN xFraction REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN yFraction REAL NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN itemType INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN appWidgetId INTEGER NOT NULL DEFAULT -1")
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN spanX INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN spanY INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN zIndex INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN snapToGrid INTEGER NOT NULL DEFAULT 1")
        }
    }

    /** v8 added snapToGrid; entity no longer persists it — rebuild table to match current schema. */
    private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `home_screen_items_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`packageName` TEXT NOT NULL, " +
                    "`page` INTEGER NOT NULL, " +
                    "`column` INTEGER NOT NULL, " +
                    "`row` INTEGER NOT NULL, " +
                    "`xFraction` REAL NOT NULL, " +
                    "`yFraction` REAL NOT NULL, " +
                    "`itemType` INTEGER NOT NULL, " +
                    "`appWidgetId` INTEGER NOT NULL, " +
                    "`spanX` INTEGER NOT NULL, " +
                    "`spanY` INTEGER NOT NULL, " +
                    "`zIndex` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `home_screen_items_new` " +
                    "(`id`, `packageName`, `page`, `column`, `row`, `xFraction`, `yFraction`, " +
                    "`itemType`, `appWidgetId`, `spanX`, `spanY`, `zIndex`) " +
                    "SELECT `id`, `packageName`, `page`, `column`, `row`, `xFraction`, `yFraction`, " +
                    "`itemType`, `appWidgetId`, `spanX`, `spanY`, `zIndex` FROM `home_screen_items`"
            )
            db.execSQL("DROP TABLE `home_screen_items`")
            db.execSQL("ALTER TABLE `home_screen_items_new` RENAME TO `home_screen_items`")
        }
    }

    private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN paddingEnabled INTEGER NOT NULL DEFAULT 1")
        }
    }

    /** v10 entity dropped isHidden — rebuild table so Room identity hash matches [HomeScreenItem]. */
    private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `home_screen_items_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`packageName` TEXT NOT NULL, " +
                    "`page` INTEGER NOT NULL, " +
                    "`column` INTEGER NOT NULL, " +
                    "`row` INTEGER NOT NULL, " +
                    "`xFraction` REAL NOT NULL, " +
                    "`yFraction` REAL NOT NULL, " +
                    "`itemType` INTEGER NOT NULL, " +
                    "`appWidgetId` INTEGER NOT NULL, " +
                    "`spanX` INTEGER NOT NULL, " +
                    "`spanY` INTEGER NOT NULL, " +
                    "`zIndex` INTEGER NOT NULL, " +
                    "`paddingEnabled` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `home_screen_items_new` " +
                    "(`id`, `packageName`, `page`, `column`, `row`, `xFraction`, `yFraction`, " +
                    "`itemType`, `appWidgetId`, `spanX`, `spanY`, `zIndex`, `paddingEnabled`) " +
                    "SELECT `id`, `packageName`, `page`, `column`, `row`, `xFraction`, `yFraction`, " +
                    "`itemType`, `appWidgetId`, `spanX`, `spanY`, `zIndex`, `paddingEnabled` " +
                    "FROM `home_screen_items`"
            )
            db.execSQL("DROP TABLE `home_screen_items`")
            db.execSQL("ALTER TABLE `home_screen_items_new` RENAME TO `home_screen_items`")
        }
    }

    private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN containerId INTEGER NOT NULL DEFAULT -1")
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN folderTitle TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN folderConfigJson TEXT NOT NULL DEFAULT '{}'")
        }
    }

    /** v13: nullable widget provider class for full ComponentName restore. */
    private val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN providerClassName TEXT DEFAULT NULL")
        }
    }

    /** v14: RSS feed sources and cached feed articles. */
    private val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `feed_sources` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`url` TEXT NOT NULL, " +
                    "`category` TEXT NOT NULL, " +
                    "`isDefault` INTEGER NOT NULL, " +
                    "`isEnabled` INTEGER NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, " +
                    "`addedAt` INTEGER NOT NULL, " +
                    "`lastFetchedAt` INTEGER NOT NULL, " +
                    "`lastError` TEXT DEFAULT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_feed_sources_url` ON `feed_sources` (`url`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `feed_articles` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sourceId` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`link` TEXT NOT NULL, " +
                    "`imageUrl` TEXT DEFAULT NULL, " +
                    "`imageUrlFallback` TEXT DEFAULT NULL, " +
                    "`sourceName` TEXT NOT NULL, " +
                    "`publishedAt` INTEGER NOT NULL, " +
                    "`cachedAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`sourceId`) REFERENCES `feed_sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_articles_sourceId` ON `feed_articles` (`sourceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_articles_publishedAt` ON `feed_articles` (`publishedAt`)")
        }
    }

    /** v15: uiMode column on home_screen_items for per-mode layout isolation (classic | minimal). */
    private val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE home_screen_items ADD COLUMN uiMode TEXT NOT NULL DEFAULT 'classic'")
        }
    }

    /** v16: item_positions table, index, and delete trigger for per-shape home screen layouts. */
    /**
     * v17: `isManual` on item_positions. Positions in non-base shapes are now derived live and
     * only stored once placed by hand; every row written before this was filled in
     * automatically on first rotation, so they are dropped and re-derived.
     */
    private val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `item_positions` ADD COLUMN `isManual` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("DELETE FROM `item_positions` WHERE `isManual` = 0")
        }
    }

    private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `item_positions` (" +
                    "`itemId` INTEGER NOT NULL, " +
                    "`shape` TEXT NOT NULL, " +
                    "`page` INTEGER NOT NULL, " +
                    "`column` INTEGER NOT NULL, " +
                    "`row` INTEGER NOT NULL, " +
                    "`xFraction` REAL NOT NULL, " +
                    "`yFraction` REAL NOT NULL, " +
                    "`spanX` INTEGER NOT NULL, " +
                    "`spanY` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`itemId`, `shape`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_positions_itemId` ON `item_positions` (`itemId`)")
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `item_positions_cleanup` " +
                    "AFTER DELETE ON `home_screen_items` " +
                    "BEGIN " +
                    "DELETE FROM `item_positions` WHERE `itemId` = OLD.id; " +
                    "END"
            )
        }
    }

    /** v18: notification_records, the history behind the launcher's own notification sheet. */
    private val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notification_records` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`key` TEXT NOT NULL, " +
                    "`packageName` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`text` TEXT NOT NULL, " +
                    "`postedAt` INTEGER NOT NULL, " +
                    "`clearedAt` INTEGER NOT NULL DEFAULT 0, " +
                    "`color` INTEGER NOT NULL DEFAULT 0, " +
                    "`ongoing` INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_records_postedAt` ON `notification_records` (`postedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_records_packageName` ON `notification_records` (`packageName`)")
        }
    }

    @Provides
    @Singleton
    fun provideLauncherDatabase(
        @ApplicationContext context: Context,
    ): LauncherDatabase =
        Room.databaseBuilder(
            context,
            LauncherDatabase::class.java,
            DATABASE_NAME,
        )
        .addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
            MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
            MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18
        )
        .addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `item_positions_cleanup` " +
                        "AFTER DELETE ON `home_screen_items` " +
                        "BEGIN " +
                        "DELETE FROM `item_positions` WHERE `itemId` = OLD.id; " +
                        "END"
                )
            }
        })
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()

    @Provides
    fun provideLauncherDao(database: LauncherDatabase): LauncherDao =
        database.launcherDao()

    @Provides
    fun provideRecentSearchDao(database: LauncherDatabase): com.nexus.launcher.data.RecentSearchDao =
        database.recentSearchDao()

    @Provides
    fun provideHomeScreenDao(database: LauncherDatabase): com.nexus.launcher.data.HomeScreenDao =
        com.nexus.launcher.data.ShapeAwareHomeScreenDao(database.homeScreenDao(), database.itemPositionDao())

    @Provides
    fun provideFeedDao(database: LauncherDatabase): com.nexus.launcher.data.FeedDao =
        database.feedDao()

    @Provides
    fun provideItemPositionDao(database: LauncherDatabase): com.nexus.launcher.data.ItemPositionDao =
        database.itemPositionDao()

    @Provides
    fun provideNotificationDao(database: LauncherDatabase): com.nexus.launcher.data.NotificationDao =
        database.notificationDao()

}
