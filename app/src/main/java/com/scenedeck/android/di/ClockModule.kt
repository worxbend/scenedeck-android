package com.scenedeck.android.di

import android.os.SystemClock
import com.scenedeck.android.core.data.MonotonicClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun provideMonotonicClock(): MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }
}
