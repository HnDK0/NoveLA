package my.noveldokusha.di

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import dagger.hilt.android.EntryPointAccessors
import java.io.InputStream

@Excludes(OkHttpLibraryGlideModule::class)
@GlideModule
class GlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setImageDecoderEnabledForBitmaps(false)
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // ponytail: was a standalone OkHttpClient with hardcoded UA — no cookie jar, no CF
        // interceptor, no shared connection pool. Cover art on CF-protected sources would fail.
        // Now uses the shared ScraperNetworkClient.client via Hilt EntryPointAccessors.
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HiltAppEntryPoint::class.java
        )
        val sharedClient = entryPoint.scraperNetworkClient().client

        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(sharedClient)
        )
    }
}
