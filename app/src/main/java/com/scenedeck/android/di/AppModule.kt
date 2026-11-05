package com.scenedeck.android.di

import android.content.Context
import androidx.room.Room
import com.scenedeck.android.background.BackgroundSettingsStore
import com.scenedeck.android.core.data.KeystoreSecretsStore
import com.scenedeck.android.core.data.SecretsStore
import com.scenedeck.android.core.data.WorkspaceContext
import com.scenedeck.android.core.data.WorkspaceRepository
import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.database.ConnectionProfileDao
import com.scenedeck.android.core.database.SceneDeckDatabase
import com.scenedeck.android.core.database.SceneDeckMigrations
import com.scenedeck.android.core.database.SceneRegistryDao
import com.scenedeck.android.core.database.WorkspaceSceneDao
import com.scenedeck.android.core.datastore.SceneDeckSettingsStore
import com.scenedeck.android.demo.AppProcess
import com.scenedeck.android.demo.DemoSecretsStore
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

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        process: AppProcess,
    ): SceneDeckDatabase =
        if (process == AppProcess.DEMO) {
            Room.inMemoryDatabaseBuilder(context, SceneDeckDatabase::class.java).build()
        } else
            Room.databaseBuilder(context, SceneDeckDatabase::class.java, "scenedeck.db")
                .addMigrations(
                    SceneDeckMigrations.MIGRATION_1_2,
                    SceneDeckMigrations.MIGRATION_2_3,
                    SceneDeckMigrations.MIGRATION_3_4,
                    SceneDeckMigrations.MIGRATION_4_5,
                    SceneDeckMigrations.MIGRATION_5_6,
                    SceneDeckMigrations.MIGRATION_6_7,
                    SceneDeckMigrations.MIGRATION_7_8,
                    SceneDeckMigrations.MIGRATION_8_9,
                    SceneDeckMigrations.MIGRATION_9_10,
                    SceneDeckMigrations.MIGRATION_10_11,
                    SceneDeckMigrations.MIGRATION_11_12,
                )
                .build()

    @Provides
    fun provideConnectionProfileDao(database: SceneDeckDatabase): ConnectionProfileDao =
        database.connectionProfileDao()

    @Provides
    fun provideSceneRegistryDao(database: SceneDeckDatabase): SceneRegistryDao =
        database.sceneRegistryDao()

    @Provides
    fun provideWorkspaceSceneDao(database: SceneDeckDatabase): WorkspaceSceneDao =
        database.workspaceSceneDao()

    @Provides
    fun provideWorkspaceContext(repository: WorkspaceRepository): WorkspaceContext = repository

    @Provides
    @Singleton
    fun provideSettingsStore(
        @ApplicationContext context: Context,
        process: AppProcess,
    ): SceneDeckSettingsStore =
        if (process == AppProcess.DEMO) SceneDeckSettingsStore.ephemeral()
        else SceneDeckSettingsStore(context)

    @Provides
    @Singleton
    fun provideSecretsStore(
        @ApplicationContext context: Context,
        process: AppProcess,
    ): SecretsStore =
        if (process == AppProcess.DEMO) DemoSecretsStore else KeystoreSecretsStore(context)

    @Provides
    @Singleton
    fun provideBackgroundSettingsStore(
        @ApplicationContext context: Context,
        process: AppProcess,
    ): BackgroundSettingsStore =
        if (process == AppProcess.DEMO) BackgroundSettingsStore.ephemeral()
        else BackgroundSettingsStore(context)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
