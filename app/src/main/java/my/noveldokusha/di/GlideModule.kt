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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.InputStream

@Excludes(OkHttpLibraryGlideModule::class)
@GlideModule
class GlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setImageDecoderEnabledForBitmaps(false)
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Create a simple OkHttpClient for Glide without Hilt dependencies
        val glideClient = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .build()

        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(glideClient)
        )
    }
}
