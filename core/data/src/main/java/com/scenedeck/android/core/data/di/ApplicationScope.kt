package com.scenedeck.android.core.data.di

import javax.inject.Qualifier

/** Application-lifetime [kotlinx.coroutines.CoroutineScope] (Singleton component). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
