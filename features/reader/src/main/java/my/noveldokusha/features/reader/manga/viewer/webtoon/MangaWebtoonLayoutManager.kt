package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Layout manager вебтун-ленты — порт tachiyomisy WebtoonLayoutManager.
 *
 * Префетч выключен: за счёт extra layout space холдеры создаются раньше,
 * чем попадают в зону видимости, и картинка успевает загрузиться
 * (меньше «чёрных» кадров при скролле).
 *
 * Упрощение: не портирован findLastEndVisibleItemPosition (в tachiyomisy
 * он лезет в package-private поля androidx через трюк с пакетом);
 * текущая страница определяется публичными findFirst*VisibleItemPosition.
 */
internal class MangaWebtoonLayoutManager(
    context: Context,
    private val extraLayoutSpace: Int,
) : LinearLayoutManager(context) {

    init {
        isItemPrefetchEnabled = false
    }

    @Deprecated("Deprecated in Java")
    override fun getExtraLayoutSpace(state: RecyclerView.State): Int {
        return extraLayoutSpace
    }
}