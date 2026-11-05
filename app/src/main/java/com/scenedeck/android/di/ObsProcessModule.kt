package com.scenedeck.android.di

import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.scenedeck.android.core.data.di.ApplicationScope
import com.scenedeck.android.core.obs.ObsClient
import com.scenedeck.android.core.obs.ObsClientFactory
import com.scenedeck.android.core.obs.ObsConnectionTestingUnavailableException
import com.scenedeck.android.core.obs.demo.DemoObsClient
import com.scenedeck.android.demo.AppProcess
import com.scenedeck.android.demo.DemoPreviewFrames
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object ObsProcessModule {
    @Provides
    @Singleton
    fun provideProcess(@ApplicationContext context: Context): AppProcess =
        AppProcess.resolve(context.packageName, Application.getProcessName())

    @Provides
    @Singleton
    fun provideObsClient(@ApplicationScope scope: CoroutineScope, process: AppProcess): ObsClient =
        if (process == AppProcess.DEMO)
            DemoObsClient(scope, SystemClock::elapsedRealtime, DemoPreviewFrames::capture)
        else ObsClient(scope)

    @Provides
    @Singleton
    fun provideObsClientFactory(process: AppProcess): ObsClientFactory = ObsClientFactory { scope ->
        if (process != AppProcess.LIVE) throw ObsConnectionTestingUnavailableException()
        ObsClient(scope)
    }
}
