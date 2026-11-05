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
        ],
    version = 7,
    exportSchema = true,
)
abstract class SceneDeckDatabase : RoomDatabase() {
    abstract fun connectionProfileDao(): ConnectionProfileDao

    abstract fun sceneRegistryDao(): SceneRegistryDao

    abstract fun workspaceSceneDao(): WorkspaceSceneDao

    abstract fun namedDeckDao(): NamedDeckDao

    abstract fun preflightConfigDao(): PreflightConfigDao

    abstract fun bookmarkDao(): BookmarkDao
}
