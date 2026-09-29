package com.scenedeck.android.di

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scenedeck.android.core.data.KeystoreSecretsStore
import com.scenedeck.android.core.data.SecretsStore
import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.database.ConnectionProfileDao
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.core.obs.ObsClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** v1 → v2: adds the local scene registry (profiles are preserved). */
    @VisibleForTesting
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `scene_registry` (" +
                    "`sceneName` TEXT NOT NULL, " +
                    "`role` TEXT NOT NULL, " +
                    "`accentColorArgb` INTEGER, " +
                    "`iconName` TEXT, " +
                    "`sortOrder` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`sceneName`))",
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SceneDeckDatabase =
        Room.databaseBuilder(context, SceneDeckDatabase::class.java, "scenedeck.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideConnectionProfileDao(database: SceneDeckDatabase): ConnectionProfileDao =
        database.connectionProfileDao()

    @Provides
    fun provideSceneRegistryDao(database: SceneDeckDatabase): SceneRegistryDao =
        database.sceneRegistryDao()

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SceneDeckSettingsStore =
        SceneDeckSettingsStore(context)

    @Provides
    @Singleton
    fun provideSecretsStore(@ApplicationContext context: Context): SecretsStore =
        KeystoreSecretsStore(context)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideObsClient(@ApplicationScope scope: CoroutineScope): ObsClient = ObsClient(scope)
}
