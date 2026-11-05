package com.scenedeck.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal class HistoryMigration : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history_sessions` (" +
                "`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `generation` INTEGER NOT NULL, " +
                "`connectionProfileId` INTEGER NOT NULL, `sceneCollectionName` TEXT NOT NULL, " +
                "`startedAtMs` INTEGER NOT NULL, `lastObservedAtMs` INTEGER NOT NULL, " +
                "`elapsedMs` INTEGER NOT NULL, `endedAtMs` INTEGER, `endReason` TEXT, " +
                "`lastOrdinal` INTEGER NOT NULL, `omittedEvents` INTEGER NOT NULL, `missingEvents` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_history_sessions_sessionId` " +
                "ON `history_sessions` (`sessionId`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history_events` (" +
                "`sessionId` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, " +
                "`observedAtMs` INTEGER NOT NULL, `elapsedMs` INTEGER NOT NULL, " +
                "`missingBefore` INTEGER NOT NULL, `payload` TEXT NOT NULL, " +
                "PRIMARY KEY(`sessionId`, `ordinal`), " +
                "FOREIGN KEY(`sessionId`) REFERENCES `history_sessions`(`sessionId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history_preferences` (" +
                "`id` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `sessionLimit` INTEGER NOT NULL, " +
                "`generation` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}
