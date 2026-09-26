package com.nexus.launcher.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One notification the launcher saw, kept so the notification sheet can show what has already
 * been cleared as well as what is still showing.
 *
 * Only what can be drawn is stored — app, title, text, when it arrived, its accent colour. A
 * notification's tap action cannot outlive it (the system owns the pending intent), so a record
 * whose notification is gone opens its app instead; [NotificationRecord.key] matches a live one
 * back up with its own action while it is still posted.
 *
 * Rows are deleted once they are older than the retention the user chose.
 */
@Entity(
    tableName = "notification_records",
    indices = [Index(value = ["postedAt"]), Index(value = ["packageName"])],
)
data class NotificationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The system's key for the notification, while it is posted. */
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    /** When it left the shade, or 0 while it is still there. */
    val clearedAt: Long = 0,
    /** The notification's own colour, for the dot beside it; 0 when it had none. */
    val color: Int = 0,
    /** True for notifications that sit in the shade permanently (music, downloads). */
    val ongoing: Boolean = false,
)
