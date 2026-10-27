package io.github.bryancassell.bluecard.di

import org.koin.core.annotation.Qualifier

/** Marks the dispatcher for disk and other blocking work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Marks the scope for work that must finish even if the screen that started it closes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
