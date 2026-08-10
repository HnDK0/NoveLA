package my.noveldokusha.features.reader.manga.viewer.pager

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.manga.setting.MangaReadingMode
import my.noveldokusha.features.reader.manga.viewer.Viewer
import my.noveldokusha.features.reader.tools.PageImageLoader

/**
 * Фабрика пейджер-вьюеров — порт tachiyomisy PagerViewers.
 * WEBTOON-режим обслуживается webtoon-вьюером и в фабрику не попадает.
 */
internal fun createPagerViewer(
    mode: MangaReadingMode,
    context: Context,
    pageImageLoader: PageImageLoader,
    config: AppPreferences,
    scope: CoroutineScope,
): Viewer {
    return when (mode) {
        MangaReadingMode.LEFT_TO_RIGHT ->
            MangaL2RPagerViewer(context, pageImageLoader, config, scope)
        MangaReadingMode.RIGHT_TO_LEFT ->
            MangaR2LPagerViewer(context, pageImageLoader, config, scope)
        MangaReadingMode.VERTICAL ->
            MangaVerticalPagerViewer(context, pageImageLoader, config, scope)
        MangaReadingMode.WEBTOON ->
            error("WEBTOON mode is handled by the webtoon viewer, not the pager factory")
    }
}

/** Горизонтальный слева-направо пейджер. */
internal class MangaL2RPagerViewer(
    context: Context,
    pageImageLoader: PageImageLoader,
    appPreferences: AppPreferences,
    scope: CoroutineScope,
) : MangaPagerViewer(context, pageImageLoader, appPreferences, scope) {

    override val isHorizontal: Boolean = true
}

/**
 * Горизонтальный справа-налево пейджер (reverseLayout — первая страница
 * справа). Тап-зоны: левая = следующая страница, правая = предыдущая
 * (как в tachiyomisy R2L), поэтому moveLeft/moveRight развёрнуты.
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

/** Вертикальный (сверху-вниз) пейджер. */
internal class MangaVerticalPagerViewer(
    context: Context,
    pageImageLoader: PageImageLoader,
    appPreferences: AppPreferences,
    scope: CoroutineScope,
) : MangaPagerViewer(context, pageImageLoader, appPreferences, scope) {

    override val isHorizontal: Boolean = false
}