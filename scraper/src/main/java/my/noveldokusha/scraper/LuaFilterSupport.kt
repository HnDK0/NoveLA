package my.noveldokusha.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import timber.log.Timber

/**
 * Поддержка фильтров каталога для Lua плагинов через getFilterList() / getCatalogFiltered().
 *
 * Ключевой принцип: список фильтров всегда исходит из Lua, без хардкода в Kotlin.
 * Kotlin только:
 *   1. Проверяет наличие функций (isnil()) при создании адаптера
 *   2. Парсит результат fn.call() в типизированные модели для UI
 *   3. Конвертирует ActiveFilters обратно в LuaTable для getCatalogFiltered()
 *
 * Схема одного фильтра (Lua):
 * {
 *   type  = "select" | "checkbox" | "tristate" | "switch" | "text" | "sort"
 *   key   = "filter_key",
 *   label = "Display Label",
 *   -- для select/checkbox/tristate/sort:
 *   options = { { value = "v1", label = "Label 1" }, ... }
 *   -- для select/sort:
 *   defaultValue = "v1"
 *   -- для sort:
 *   defaultAscending = false
 *   -- для switch:
 *   defaultValue = false
 *   -- для text:
 *   defaultValue = ""
 * }
 *
 * Передача фильтров в getCatalogFiltered(index, filters):
 *   select    → filters["key"]              = "value"
 *   checkbox  → filters["key_included"]     = { "v1", "v2" }  (LuaTable)
 *   tristate  → filters["key_included"]     = { "v1" }        (LuaTable)
 *             → filters["key_excluded"]     = { "v2" }        (LuaTable)
 *   switch    → filters["key"]              = "true" / "false"
 *   text      → filters["key"]              = "string"
 *   sort      → filters["key"]              = "value"
 *             → filters["key_ascending"]    = "true" / "false"
 */

// ── Модели опций ──────────────────────────────────────────────────────────────

data class LuaFilterOption(val value: String, val label: String)

// ── Модели фильтров ───────────────────────────────────────────────────────────

sealed class LuaFilter {
    abstract val key: String
    abstract val label: String

    /** Одиночный выбор из списка (Picker в lnreader) */
    data class Select(
        override val key: String,
        override val label: String,
        val options: List<LuaFilterOption>,
        val defaultValue: String
    ) : LuaFilter()

    /**
     * Множественный выбор — только включить (CheckboxGroup в lnreader).
     * Передаётся как filters["key_included"] = LuaTable
     * Если multiselect = false — можно выбрать только один вариант.
     */
    data class CheckboxGroup(
        override val key: String,
        override val label: String,
        val options: List<LuaFilterOption>,
        val multiselect: Boolean = true
    ) : LuaFilter()

    /**
     * Тройное состояние — включить / исключить / игнорировать (ExcludableCheckboxGroup в lnreader).
     * Передаётся как filters["key_included"] и filters["key_excluded"] = LuaTable
     */
    data class TriState(
        override val key: String,
        override val label: String,
        val options: List<LuaFilterOption>
    ) : LuaFilter()

    /** Переключатель вкл/выкл (Switch в lnreader) */
    data class Switch(
        override val key: String,
        override val label: String,
        val defaultValue: Boolean
    ) : LuaFilter()

    /** Текстовый ввод (TextInput в lnreader) */
    data class TextInput(
        override val key: String,
        override val label: String,
        val defaultValue: String
    ) : LuaFilter()

    /**
     * Сортировка с направлением (кастомный тип, нет аналога в lnreader).
     * Передаётся как filters["key"] = "value" и filters["key_ascending"] = "true"/"false"
     */
    data class Sort(
        override val key: String,
        override val label: String,
        val options: List<LuaFilterOption>,
        val defaultValue: String,
        val defaultAscending: Boolean
    ) : LuaFilter()
}

// ── Состояние активных фильтров ───────────────────────────────────────────────

/**
 * Состояние выбранных фильтров — живёт только в ViewModel, сбрасывается при пересоздании.
 * НЕ сохраняется в SharedPreferences / SavedStateHandle.
 */
data class ActiveFilters(
    val sortValues: Map<String, String> = emptyMap(),            // key → option.value
    val sortAscending: Map<String, Boolean> = emptyMap(),        // key → ascending
    val selectValues: Map<String, String> = emptyMap(),          // key → option.value
    val checkboxIncluded: Map<String, List<String>> = emptyMap(),// key → [option.value, ...]
    val triIncluded: Map<String, List<String>> = emptyMap(),     // key → [option.value, ...]
    val triExcluded: Map<String, List<String>> = emptyMap(),     // key → [option.value, ...]
    val switchValues: Map<String, Boolean> = emptyMap(),         // key → true/false
    val textValues: Map<String, String> = emptyMap(),            // key → string
) {
    /**
     * true если все значения дефолтные.
     * Используется для: бейджа кнопки фильтров, выбора функции итератора.
     */
    val isEmpty: Boolean get() =
        sortValues.isEmpty() &&
                sortAscending.isEmpty() &&
                selectValues.isEmpty() &&
                checkboxIncluded.all { it.value.isEmpty() } &&
                triIncluded.all { it.value.isEmpty() } &&
                triExcluded.all { it.value.isEmpty() } &&
                switchValues.isEmpty() &&
                textValues.all { it.value.isEmpty() }
}

// ── Парсер Lua → Kotlin ───────────────────────────────────────────────────────

/**
 * Парсит результат вызова getFilterList() в список Kotlin-моделей.
 *
 * Принимает LuaValue — результат fn.call(), уже вычисленную таблицу.
 * НЕ вызывает функцию сам — это делает LuaSourceAdapterFilterable.getFilterList().
 *
 * Вызов из адаптера:
 *   val fn = luaScript.get("getFilterList")
 *   val result = fn.call()
 *   val filters = parseLuaFilterList(result)
 */
fun parseLuaFilterList(luaTable: LuaValue): List<LuaFilter> {
    if (!luaTable.istable()) return emptyList()

    val filters = mutableListOf<LuaFilter>()
    val table = luaTable.checktable()

    for (i in 1..table.length()) {
        val item = table.get(LuaValue.valueOf(i))
        if (!item.istable()) continue
        val t = item.checktable()

        val key = t.get("key").optjstring("")
        if (key.isEmpty()) continue
        val type = t.get("type").optjstring("select")
        val label = t.get("label").optjstring(key)

        val filter: LuaFilter? = when (type) {
            "sort" -> {
                val optsTable = t.get("options").opttable(null)
                if (optsTable == null) null
                else {
                    val opts = parseOptions(optsTable)
                    if (opts.isEmpty()) null
                    else LuaFilter.Sort(
                        key              = key,
                        label            = label,
                        options          = opts,
                        defaultValue     = t.get("defaultValue").optjstring(opts.first().value),
                        defaultAscending = t.get("defaultAscending").optboolean(false)
                    )
                }
            }
            "select" -> {
                val optsTable = t.get("options").opttable(null)
                if (optsTable == null) null
                else {
                    val opts = parseOptions(optsTable)
                    if (opts.isEmpty()) null
                    else LuaFilter.Select(
                        key          = key,
                        label        = label,
                        options      = opts,
                        defaultValue = t.get("defaultValue").optjstring("")
                    )
                }
            }
            "checkbox" -> {
                val optsTable = t.get("options").opttable(null)
                if (optsTable == null) null
                else {
                    val opts = parseOptions(optsTable)
                    if (opts.isEmpty()) null
                    else LuaFilter.CheckboxGroup(
                        key         = key,
                        label       = label,
                        options     = opts,
                        multiselect = t.get("multiselect").optboolean(true)
                    )
                }
            }
            "tristate" -> {
                val optsTable = t.get("options").opttable(null)
                if (optsTable == null) null
                else {
                    val opts = parseOptions(optsTable)
                    if (opts.isEmpty()) null
                    else LuaFilter.TriState(key = key, label = label, options = opts)
                }
            }
            "switch" -> LuaFilter.Switch(
                key          = key,
                label        = label,
                defaultValue = t.get("defaultValue").optboolean(false)
            )
            "text" -> LuaFilter.TextInput(
                key          = key,
                label        = label,
                defaultValue = t.get("defaultValue").optjstring("")
            )
            else -> {
                Timber.w("parseLuaFilterList: unknown filter type '$type' for key '$key'")
                null
            }
        }

        if (filter != null) filters.add(filter)
    }

    return filters
}

private fun parseOptions(table: LuaTable): List<LuaFilterOption> {
    val options = mutableListOf<LuaFilterOption>()
    for (j in 1..table.length()) {
        val opt = table.get(LuaValue.valueOf(j))
        if (!opt.istable()) continue
        val ot = opt.checktable()
        val v = ot.get("value").optjstring("")
        if (v.isEmpty()) continue
        val l = ot.get("label").optjstring(v)
        options.add(LuaFilterOption(value = v, label = l))
    }
    return options
}

// ── Конвертер ActiveFilters → LuaTable ───────────────────────────────────────

/**
 * Преобразует ActiveFilters в LuaTable для передачи в getCatalogFiltered(index, filters).
 *
 * Использует luaEngine.convertToLua() для конвертации List<String> → LuaTable-массив.
 * Пустые списки и пустые строки НЕ добавляются в таблицу —
 * плагин сам обрабатывает отсутствие ключа как "не задан".
 */
fun ActiveFilters.toLuaTable(luaEngine: LuaEngine): LuaTable {
    val t = LuaTable()

    // sort: значение и направление
    sortValues.forEach { (key, value) ->
        if (value.isNotEmpty()) t.set(key, LuaValue.valueOf(value))
    }
    sortAscending.forEach { (key, asc) ->
        t.set("${key}_ascending", LuaValue.valueOf(asc.toString()))
    }

    // select
    selectValues.forEach { (key, value) ->
        if (value.isNotEmpty()) t.set(key, LuaValue.valueOf(value))
    }

    // checkbox: только _included
    checkboxIncluded.forEach { (key, values) ->
        if (values.isNotEmpty()) t.set("${key}_included", luaEngine.convertToLua(values))
    }

    // tristate: _included и _excluded как LuaTable-массивы
    triIncluded.forEach { (key, values) ->
        if (values.isNotEmpty()) t.set("${key}_included", luaEngine.convertToLua(values))
    }
    triExcluded.forEach { (key, values) ->
        if (values.isNotEmpty()) t.set("${key}_excluded", luaEngine.convertToLua(values))
    }

    // switch: строка "true"/"false"
    switchValues.forEach { (key, value) ->
        t.set(key, LuaValue.valueOf(value.toString()))
    }

    // text
    textValues.forEach { (key, value) ->
        if (value.isNotEmpty()) t.set(key, LuaValue.valueOf(value))
    }

    return t
}