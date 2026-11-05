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
        ],
    version = 4,
    exportSchema = true,
)
abstract class SceneDeckDatabase : RoomDatabase() {
    abstract fun connectionProfileDao(): ConnectionProfileDao

    abstract fun sceneRegistryDao(): SceneRegistryDao

    abstract fun workspaceSceneDao(): WorkspaceSceneDao

    abstract fun namedDeckDao(): NamedDeckDao
}
