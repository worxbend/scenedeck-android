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
}
