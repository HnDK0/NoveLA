package my.noveldokusha.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import my.noveldokusha.scraper.LuaSourceProvider
import my.noveldokusha.scraper.LuaSourceProviderImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LuaSourceModule {

    @Binds
    @Singleton
    abstract fun bindLuaSourceProvider(impl: LuaSourceProviderImpl): LuaSourceProvider
}