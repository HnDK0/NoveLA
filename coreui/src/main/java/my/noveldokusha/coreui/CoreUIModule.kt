package my.noveldokusha.coreui

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import my.noveldokusha.coreui.theme.ThemeProvider
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class CoreUIModule {

    @Binds
    @Singleton
    internal abstract fun themeProviderBinder(appThemeProvider: AppThemeProvider): ThemeProvider

}