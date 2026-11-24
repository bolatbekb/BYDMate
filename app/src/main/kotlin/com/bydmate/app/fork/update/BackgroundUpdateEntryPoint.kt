package com.bydmate.app.fork.update

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt access to the singleton manager from a composable (no constructor injection in Compose). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackgroundUpdateEntryPoint {
    fun backgroundUpdateManager(): BackgroundUpdateManager
}
