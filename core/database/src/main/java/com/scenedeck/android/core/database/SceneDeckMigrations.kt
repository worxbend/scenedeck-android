package com.scenedeck.android.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Explicit, lossless migrations shared by the production builder and migration tests. */
object SceneDeckMigrations {

    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scene_registry` (" +
                        "`sceneName` TEXT NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`accentColorArgb` INTEGER, " +
                        "`iconName` TEXT, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`sceneName`))"
                )
            }
        }

    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `scene_registry_scoped` (" +
                        "`sceneName` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                        "`accentColorArgb` INTEGER, `iconName` TEXT, `sortOrder` INTEGER NOT NULL, " +
                        "`connectionProfileId` INTEGER NOT NULL, " +
                        "`sceneCollectionName` TEXT NOT NULL, " +
                        "PRIMARY KEY(`connectionProfileId`, `sceneCollectionName`, `sceneName`))"
                )
                db.execSQL(
                    "INSERT INTO `scene_registry_scoped` " +
                        "SELECT sceneName, role, accentColorArgb, iconName, sortOrder, 0, '' " +
                        "FROM `scene_registry`"
                )
                db.execSQL("DROP TABLE `scene_registry`")
                db.execSQL("ALTER TABLE `scene_registry_scoped` RENAME TO `scene_registry`")
            }
        }
    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `named_decks` (" +
                        "`connectionProfileId` INTEGER NOT NULL, `sceneCollectionName` TEXT NOT NULL, " +
                        "`deckId` TEXT NOT NULL, `name` TEXT NOT NULL, `nameKey` TEXT NOT NULL, " +
                        "PRIMARY KEY(`connectionProfileId`, `sceneCollectionName`, `deckId`))"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_named_decks_connectionProfileId_sceneCollectionName_nameKey` ON " +
                        "`named_decks` (`connectionProfileId`, `sceneCollectionName`, `nameKey`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `named_deck_members` (" +
                        "`connectionProfileId` INTEGER NOT NULL, `sceneCollectionName` TEXT NOT NULL, " +
                        "`deckId` TEXT NOT NULL, `sceneName` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`connectionProfileId`, `sceneCollectionName`, `deckId`, `sceneName`), " +
                        "FOREIGN KEY(`connectionProfileId`, `sceneCollectionName`, `deckId`) " +
                        "REFERENCES `named_decks`(`connectionProfileId`, `sceneCollectionName`, `deckId`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workspace_deck_selection` (" +
                        "`connectionProfileId` INTEGER NOT NULL, `sceneCollectionName` TEXT NOT NULL, " +
                        "`selectedDeckId` TEXT NOT NULL, PRIMARY KEY(`connectionProfileId`, `sceneCollectionName`))"
                )
            }
        }
    val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `scene_registry` ADD COLUMN `localAlias` TEXT")
            }
        }
    val MIGRATION_5_6 =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `preflight_config` (" +
                        "`connectionProfileId` INTEGER NOT NULL, `sceneCollectionName` TEXT NOT NULL, " +
                        "`payload` TEXT NOT NULL, PRIMARY KEY(`connectionProfileId`, `sceneCollectionName`))"
                )
            }
        }
    val MIGRATION_6_7 =
        object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                        "`sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bookmarkId` TEXT NOT NULL, `connectionProfileId` INTEGER NOT NULL, " +
                        "`sceneCollectionName` TEXT NOT NULL, `observationId` TEXT NOT NULL, " +
                        "`createdAtMs` INTEGER NOT NULL, `label` TEXT NOT NULL, `times` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_bookmarkId` ON `bookmarks` (`bookmarkId`)"
                )
            }
        }

    val MIGRATION_7_8: Migration = HistoryMigration()

    val MIGRATION_8_9 =
        object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history_bookmarks` (" +
                        "`bookmarkId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `elapsedMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`bookmarkId`), " +
                        "FOREIGN KEY(`bookmarkId`) REFERENCES `bookmarks`(`bookmarkId`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `history_sessions`(`sessionId`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_history_bookmarks_sessionId` " +
                        "ON `history_bookmarks` (`sessionId`)"
                )
            }
        }
}
