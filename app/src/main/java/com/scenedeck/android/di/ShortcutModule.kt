package com.scenedeck.android.di

import com.scenedeck.android.core.data.SharedShortcutExecutionSource
import com.scenedeck.android.core.data.ShortcutExecutionSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ShortcutModule {
    @Provides
    fun execution(implementation: SharedShortcutExecutionSource): ShortcutExecutionSource =
        implementation
}
