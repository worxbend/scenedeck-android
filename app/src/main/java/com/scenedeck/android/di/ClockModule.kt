package com.scenedeck.android.di

import android.os.SystemClock
import com.scenedeck.android.core.data.MonotonicClock
import com.scenedeck.android.core.data.WallClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides fun provideWallClock(): WallClock = WallClock(System::currentTimeMillis)

    @Provides
    fun provideMonotonicClock(): MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }
}
