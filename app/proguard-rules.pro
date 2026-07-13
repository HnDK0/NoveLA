# =============================================================================
# БАЗОВЫЕ ПРАВИЛА ANDROID & LIFECYCLE
# =============================================================================

-keep public class * extends androidx.lifecycle.ViewModel { *; }
-keepattributes SourceFile, LineNumberTable

# =============================================================================
# ИСПРАВЛЕНИЕ ОШИБОК СБОРКИ (R8 / MISSING CLASSES)
# =============================================================================

# 1. Глушим ошибки о стандартных Java-классах (Beans, Script)
-dontwarn java.beans.**
-dontwarn javax.script.**

# 2. Глушим ошибки BCEL (нужно для LuaJ, так как luajc не используется в Android)
-dontwarn org.apache.bcel.**

# 3. Глушим ошибки JSpecify (аннотации в новых версиях Jsoup и др.)
-dontwarn org.jspecify.**

# 4. Глушим ошибки SnakeYAML
-dontwarn org.yaml.snakeyaml.**

# =============================================================================
# LUA И СИСТЕМА ПЛАГИНОВ (LuaJ)
# =============================================================================

# Сохраняем ядро LuaJ
-keep class org.luaj.vm2.** { *; }

# Сохраняем функции API внутри LuaEngine
-keep class my.noveldokusha.scraper.LuaEngine$* { *; }

# Сохраняем адаптеры
-keep class my.noveldokusha.scraper.LuaSourceAdapter { *; }
-keep class my.noveldokusha.scraper.LuaSourceAdapterConfigurable { *; }
-keep interface my.noveldokusha.scraper.SourceInterface** { *; }

# =============================================================================
# МОДЕЛИ ДАННЫХ И СЕРИАЛИЗАЦИЯ
# =============================================================================

-keep class my.noveldokusha.scraper.domain.** { *; }
-keep class my.noveldokusha.scraper.configs.** { *; }
-keep class my.noveldokusha.core.** { *; }

-keepattributes RuntimeVisibleAnnotations, AnnotationDefault, Signature, EnclosingMethod

# =============================================================================
# СТОРОННИЕ БИБЛИОТЕКИ
# =============================================================================

# Jsoup
-keep class org.jsoup.** { *; }

# Gson
-keep class com.google.gson.** { *; }

# SnakeYAML
-keep class org.yaml.snakeyaml.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# =============================================================================
# KOTLIN SERIALIZATION
# =============================================================================

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# =============================================================================
# ЛОГИРОВАНИЕ
# =============================================================================

-dontwarn org.slf4j.impl.StaticLoggerBinder

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

-assumenosideeffects class timber.log.Timber* {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# =============================================================================
# TRANSLATION MANAGERS
# =============================================================================

-keep class my.noveldokusha.text_translator.TranslationManagerComposite { *; }
-keep class my.noveldokusha.text_translator.TranslationManagerGemini { *; }
-keep class my.noveldokusha.text_translator.TranslationManagerGoogleFree { *; }