package com.scenedeck.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ConnectionProfileEntity::class, SceneRegistryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SceneDeckDatabase : RoomDatabase() {
    abstract fun connectionProfileDao(): ConnectionProfileDao

    abstract fun sceneRegistryDao(): SceneRegistryDao
}
