package my.noveldokusha.coreui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        val scope = rememberCoroutineScope()
        val retryCount = remember { mutableIntStateOf(0) }
        var retryJob by remember { mutableStateOf<Job?>(null) }
        var isError by remember { mutableStateOf(false) }

        // ponytail: crossfade, allowHardware, allowRgb565 — задаются глобально в App.kt.
        val placeholderPainter = placeholder?.let { painterResource(it) }
        val imageRequest by remember(model, forceCache, retryCount.intValue) {
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
        // ponytail: error-painter не задействован — retry button показывается
        // поверх AsyncImage через isError state.
        Box(modifier = modifier) {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = contentScale,
                // fillMaxSize вместо matchParentSize: Box должен получать высоту
                // от AsyncImage, когда внешний модификатор задаёт только ширину
                // (диалог обложки) — иначе Box нулевой высоты и картинки не видно.
                modifier = Modifier.fillMaxSize(),
                colorFilter = colorFilter,
                placeholder = placeholderPainter,
                error = painterResource(error),
                onSuccess = { isError = false },
                onError = {
                    isError = true
                    if (retryCount.intValue < 2) {
                        retryJob?.cancel()
                        retryJob = scope.launch {
                            delay(1000)
                            retryCount.intValue++
                        }
                    }
                }
            )
            if (isError && retryCount.intValue >= 2 && model is String) {
                FilledIconButton(
                    onClick = {
                        isError = false
                        retryCount.intValue = 0
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
