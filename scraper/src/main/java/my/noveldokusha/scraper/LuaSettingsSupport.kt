package my.noveldokusha.scraper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.theme.colorAccent
import org.luaj.vm2.LuaValue

/**
 * Расширение LuaSourceAdapter для поддержки настроек плагина через getSettingsSchema().
 *
 * Lua-плагин может объявить функцию getSettingsSchema(), возвращающую таблицу с
 * описанием настроек. LuaSourceAdapter реализует SourceInterface.Configurable,
 * если функция присутствует.
 *
 * Схема настройки (один элемент массива):
 * {
 *   key     = "pref_key",           -- ключ для get_preference / set_preference
 *   type    = "select",             -- тип виджета (только "select" поддерживается сейчас)
 *   label   = "Display Label",      -- подпись
 *   current = "current_value",      -- текущее значение
 *   options = {
 *     { value = "opt1", label = "Option One" },
 *     { value = "opt2", label = "Option Two" },
 *   }
 * }
 *
 * Хранение: SharedPreferences "lua_preferences" (тот же, что get_preference/set_preference)
 */

// ── Модели данных схемы ───────────────────────────────────────────────────────

data class LuaSettingOption(val value: String, val label: String)

sealed class LuaSetting {
    abstract val key: String
    abstract val label: String

    data class Select(
        override val key: String,
        override val label: String,
        val current: String,
        val options: List<LuaSettingOption>
    ) : LuaSetting()
}

// ── Парсинг схемы из Lua ──────────────────────────────────────────────────────

/**
 * Читает getSettingsSchema() из глобалей Lua и возвращает список настроек.
 * Возвращает null если функция не определена.
 */
fun parseLuaSettingsSchema(luaScript: LuaValue): List<LuaSetting>? {
    val fn = try { luaScript.get("getSettingsSchema") } catch (_: Exception) { return null }
    if (fn == null || fn.isnil()) return null

    val result = try { fn.call() } catch (_: Exception) { return null }
    if (!result.istable()) return null

    val settings = mutableListOf<LuaSetting>()
    val table = result.checktable()

    for (i in 1..table.length()) {
        val item = table.get(org.luaj.vm2.LuaValue.valueOf(i))
        if (!item.istable()) continue
        val t = item.checktable()

        val key = t.get("key").optjstring("")
        if (key.isEmpty()) continue
        val type  = t.get("type").optjstring("select")
        val label = t.get("label").optjstring(key)

        if (type == "select") {
            val current = t.get("current").optjstring("")
            val optTable = t.get("options").opttable(null) ?: continue
            val options = mutableListOf<LuaSettingOption>()

            for (j in 1..optTable.length()) {
                val opt = optTable.get(org.luaj.vm2.LuaValue.valueOf(j))
                if (!opt.istable()) continue
                val ot = opt.checktable()
                val v = ot.get("value").optjstring("")
                if (v.isEmpty()) continue
                val l  = ot.get("label").optjstring(v)
                options.add(LuaSettingOption(v, l))
            }

            if (options.isNotEmpty()) {
                settings.add(LuaSetting.Select(key, label, current, options))
            }
        }
    }

    return settings.ifEmpty { null }
}

// ── Composable UI для схемы ──────────────────────────────────────────────────

/**
 * Универсальный экран настроек для Lua-плагинов.
 * Рендерит виджеты на основе схемы, хранит значения в SharedPreferences
 * через set_preference (ключ тот же).
 *
 * Использование в LuaSourceAdapter:
 *
 *   class LuaSourceAdapter ... : SourceInterface.Catalog, SourceInterface.Configurable {
 *     private val settingsSchema by lazy { parseLuaSettingsSchema(luaScript) }
 *     val hasSettings: Boolean get() = settingsSchema != null
 *
 *     @Composable
 *     override fun ScreenConfig() {
 *       LuaSettingsScreen(context, settingsSchema!!, luaScript)
 *     }
 *   }
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuaSettingsScreen(
    context: android.content.Context,
    schema: List<LuaSetting>,
    @Suppress("UNUSED_PARAMETER") luaScript: LuaValue
) {
    val prefs = remember {
        context.getSharedPreferences("lua_preferences", android.content.Context.MODE_PRIVATE)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        schema.forEach { setting ->
            when (setting) {
                is LuaSetting.Select -> {
                    // Читаем текущее значение из SharedPreferences (актуальнее чем schema.current)
                    val currentValue by remember {
                        mutableStateOf(
                            prefs.getString(setting.key, setting.current) ?: setting.current
                        )
                    }
                    var current by remember { mutableStateOf(currentValue) }
                    var expanded by remember { mutableStateOf(false) }

                    val currentLabel = setting.options.find { it.value == current }?.label ?: current

                    Column {
                        Text(
                            text = setting.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = colorAccent(),
                        )
                        Spacer(Modifier.height(10.dp))

                        // Если вариантов 2 — показываем как кнопки (как в WtrLabSource)
                        if (setting.options.size == 2) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max)
                            ) {
                                setting.options.forEach { opt ->
                                    val selected = current == opt.value
                                    Surface(
                                        onClick = {
                                            current = opt.value
                                            prefs.edit().putString(setting.key, opt.value).apply()
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) colorAccent().copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = if (selected) colorAccent()
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp, horizontal = 12.dp)
                                        ) {
                                            Text(
                                                text = opt.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (selected) colorAccent()
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Для большего числа вариантов — выпадающий список
                            Box {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.5.dp, colorAccent().copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = null,
                                        tint = colorAccent(),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = currentLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    setting.options.forEach { opt ->
                                        val selected = current == opt.value
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = opt.label,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (selected) colorAccent()
                                                    else MaterialTheme.colorScheme.onSurface,
                                                )
                                            },
                                            trailingIcon = if (selected) ({
                                                Icon(
                                                    Icons.Filled.Check, null,
                                                    tint = colorAccent()
                                                )
                                            }) else null,
                                            onClick = {
                                                current = opt.value
                                                prefs.edit().putString(setting.key, opt.value).apply()
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
