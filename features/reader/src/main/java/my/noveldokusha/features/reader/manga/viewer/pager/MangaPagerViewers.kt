package my.noveldokusha.features.reader.manga.viewer.pager

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.manga.viewer.Viewer
import my.noveldokusha.features.reader.tools.PageImageLoader

/**
 * Фабрика пейджер-вьюеров — порт tachiyomisy PagerViewers.
 * WEBTOON-режим обслуживается webtoon-вьюером и в фабрику не попадает.
 * Направление листания фиксировано: R2L (reverseLayout — первая страница
 * справа), как принято для манги.
 */
internal fun createPagerViewer(
    context: Context,
    pageImageLoader: PageImageLoader,
    config: AppPreferences,
    scope: CoroutineScope,
): Viewer {
    return MangaR2LPagerViewer(context, pageImageLoader, config, scope)
}

/**
 * Горизонтальный справа-налево пейджер (reverseLayout — первая страница
 * справа). Тап-зоны семантические (PREV/NEXT из навигатора), поэтому
 * moveLeft/moveRight развёрнуты: левая = следующая страница, правая =
 * предыдущая (как в tachiyomisy R2L).
 */
internal class MangaR2LPagerViewer(
    context: Context,
    pageImageLoader: PageImageLoader,
    appPreferences: AppPreferences,
    scope: CoroutineScope,
) : MangaPagerViewer(context, pageImageLoader, appPreferences, scope) {

    override val isHorizontal: Boolean = true

    override val reverseLayout: Boolean = true

    override fun moveRight(): Boolean = prevPage()

    override fun moveLeft(): Boolean = nextPage()
}