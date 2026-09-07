package my.noveldokusha.coreui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import my.noveldokusha.core.utils.refererFor
import my.noveldokusha.coreui.R

@Composable
fun ImageView(
    imageModel: Any?,
    modifier: Modifier = Modifier,
    fadeInDurationMillis: Int = 250,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    @DrawableRes error: Int = R.drawable.default_book_cover,
    @DrawableRes placeholder: Int? = null,
    colorFilter: ColorFilter? = null,
    forceCache: Boolean = false,
) {
    val model by remember(imageModel, error) {
        derivedStateOf {
            when (imageModel) {
                is String -> imageModel.ifBlank { error }
                null -> error
                else -> imageModel
            }
        }
    }
    if (LocalInspectionMode.current) {
        val res = when (val modelCopy = model) {
            is Int -> modelCopy
            else -> placeholder ?: error
        }
        Image(
            painter = painterResource(res),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            colorFilter = colorFilter,
        )
    } else {
        val context by rememberUpdatedState(LocalContext.current)

        // ponytail: crossfade, allowHardware, allowRgb565 — задаются глобально в App.kt.
        val placeholderPainter = placeholder?.let { painterResource(it) }
        val imageRequest by remember(model, forceCache) {
            derivedStateOf {
                val referer = (model as? String)?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let(::refererFor)
                ImageRequest
                    .Builder(context)
                    .data(model)
                    .precision(Precision.INEXACT)
                    .apply {
                        if (!referer.isNullOrEmpty()) {
                            httpHeaders(
                                NetworkHeaders.Builder()
                                    .set("Referer", referer)
                                    .build()
                            )
                        }
                        if (forceCache) {
                            diskCachePolicy(CachePolicy.ENABLED)
                            memoryCachePolicy(CachePolicy.ENABLED)
                        }
                    }
                    .build()
            }
        }
        val imageErrorRequest by remember(error) {
            derivedStateOf {
                ImageRequest
                    .Builder(context)
                    .data(error)
                    .precision(Precision.INEXACT)
                    .build()
            }
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            colorFilter = colorFilter,
            placeholder = placeholderPainter,
            error = rememberAsyncImagePainter(
                model = imageErrorRequest,
                contentScale = contentScale
            )
        )
    }
}
