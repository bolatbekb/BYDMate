package com.bydmate.app.fork.ledger.data

import android.content.Context
import com.bydmate.app.demo.DemoMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LedgerModule {

    @Provides @Singleton
    fun provideLedgerDatabase(@ApplicationContext ctx: Context): LedgerDatabase =
        LedgerDatabase.build(
            ctx,
            if (DemoMode.isOn(ctx)) LedgerDatabase.DEMO_FILE_NAME else LedgerDatabase.FILE_NAME,
        )
}
