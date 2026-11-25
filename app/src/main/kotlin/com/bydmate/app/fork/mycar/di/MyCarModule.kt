package com.bydmate.app.fork.mycar.di

import android.content.Context
import com.bydmate.app.fork.mycar.MyCarSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MyCarModule {

    @Provides @Singleton
    fun provideMyCarSettings(@ApplicationContext ctx: Context): MyCarSettings =
        MyCarSettings(ctx.getSharedPreferences(MyCarSettings.PREFS_NAME, Context.MODE_PRIVATE))
}
