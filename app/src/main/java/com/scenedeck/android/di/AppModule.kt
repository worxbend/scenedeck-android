package com.scenedeck.android.di

import android.content.Context
import androidx.room.Room
import com.scenedeck.android.core.data.KeystoreSecretsStore
import com.scenedeck.android.core.data.SecretsStore
import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.database.ConnectionProfileDao
import com.scenedeck.android.core.database.SceneDeckDatabase
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SceneDeckDatabase =
        Room.databaseBuilder(context, SceneDeckDatabase::class.java, "scenedeck.db").build()

    @Provides
    fun provideConnectionProfileDao(database: SceneDeckDatabase): ConnectionProfileDao =
        database.connectionProfileDao()

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
