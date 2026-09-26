package com.nexus.launcher.reader.doc

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Isolated Room database for the Document Reader library and reading positions.
 */
@Database(
    entities = [DocumentRecord::class, CollectionRecord::class],
    version = 3,
    exportSchema = false
)
abstract class DocumentDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        @Volatile
        private var INSTANCE: DocumentDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `nexus_collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `colorTag` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE `nexus_documents` ADD COLUMN `collectionId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `nexus_documents` ADD COLUMN `displayName` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `nexus_documents` ADD COLUMN `coverPath` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `nexus_documents` ADD COLUMN `addedTimestamp` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `nexus_documents` ADD COLUMN `readingMillis` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): DocumentDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DocumentDatabase::class.java,
                    "nexus_document_library.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // No destructive fallback: this database is the user's library and their place in
                // every book. A missing migration must fail loudly in development rather than
                // quietly empty someone's shelf on update.
                .build().also { INSTANCE = it }
            }
        }
    }
}
