package com.scenedeck.android.di

import com.scenedeck.android.core.data.HistoryBookmarkOrigin
import com.scenedeck.android.core.data.HistoryRecorder
import com.scenedeck.android.core.data.HistorySource
import com.scenedeck.android.core.data.PresetExecutionSource
import com.scenedeck.android.core.data.PresetRunHistory
import com.scenedeck.android.core.data.SharedHistorySource
import com.scenedeck.android.core.data.SharedPresetExecutionSource
import com.scenedeck.android.core.data.StoredPresetRunHistory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object HistoryModule {
    @Provides fun source(implementation: SharedHistorySource): HistorySource = implementation

    @Provides fun bookmarks(recorder: HistoryRecorder): HistoryBookmarkOrigin = recorder

    @Provides fun presets(implementation: StoredPresetRunHistory): PresetRunHistory = implementation

    @Provides
    fun presetExecution(implementation: SharedPresetExecutionSource): PresetExecutionSource =
        implementation
}
