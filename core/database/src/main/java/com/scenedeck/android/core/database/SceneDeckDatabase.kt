package com.scenedeck.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities =
        [
            ConnectionProfileEntity::class,
            SceneRegistryEntity::class,
            NamedDeckEntity::class,
            NamedDeckMemberEntity::class,
            WorkspaceDeckSelectionEntity::class,
            PreflightConfigEntity::class,
            BookmarkEntity::class,
            HistorySessionEntity::class,
            HistoryEventEntity::class,
            HistoryPreferencesEntity::class,
            HistoryBookmarkEntity::class,
            ActionPresetEntity::class,
            HistoryPresetEntity::class,
            ShortcutBindingEntity::class,
        ],
    version = 12,
    exportSchema = true,
)
@Suppress("TooManyFunctions") // Room requires one typed DAO accessor per persisted aggregate.
abstract class SceneDeckDatabase : RoomDatabase() {
    abstract fun connectionProfileDao(): ConnectionProfileDao

    abstract fun sceneRegistryDao(): SceneRegistryDao

    abstract fun workspaceSceneDao(): WorkspaceSceneDao

    abstract fun namedDeckDao(): NamedDeckDao

    abstract fun preflightConfigDao(): PreflightConfigDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun historySessionDao(): HistorySessionDao

    abstract fun historyEventDao(): HistoryEventDao

    abstract fun historyPreferencesDao(): HistoryPreferencesDao

    abstract fun historyBookmarkDao(): HistoryBookmarkDao

    abstract fun actionPresetDao(): ActionPresetDao

    abstract fun historyPresetDao(): HistoryPresetDao

    abstract fun shortcutBindingDao(): ShortcutBindingDao
}
