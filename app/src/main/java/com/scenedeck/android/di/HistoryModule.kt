package com.scenedeck.android.di

import com.scenedeck.android.core.data.HistoryBookmarkOrigin
import com.scenedeck.android.core.data.HistoryRecorder
import com.scenedeck.android.core.data.HistorySource
import com.scenedeck.android.core.data.SharedHistorySource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object HistoryModule {
    @Provides fun source(implementation: SharedHistorySource): HistorySource = implementation

    @Provides fun bookmarks(recorder: HistoryRecorder): HistoryBookmarkOrigin = recorder
}
