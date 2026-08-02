package my.noveldokusha.text_to_speech

import android.content.Context
import android.speech.tts.TextToSpeech

class AppTtsEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null
    // null означает системный движок по умолчанию (service.defaultEngine его всегда и возвращает).
    private var boundEnginePackage: String? = null

    fun getOrCreate(onReady: (() -> Unit)? = null): TextToSpeech {
        if (engine == null) {
            boundEnginePackage = null
            engine = TextToSpeech(appContext) { if (it == TextToSpeech.SUCCESS) onReady?.invoke() }
        }
        return engine!!
    }

    fun reinit(enginePackage: String?, onReady: () -> Unit) {
        engine?.stop()
        engine?.shutdown()
        boundEnginePackage = enginePackage?.takeIf { it.isNotEmpty() }
        engine = if (enginePackage.isNullOrEmpty()) {
            TextToSpeech(appContext) { if (it == TextToSpeech.SUCCESS) onReady() }
        } else {
            TextToSpeech(appContext, { if (it == TextToSpeech.SUCCESS) onReady() }, enginePackage)
        }
    }

    fun getBoundEnginePackage(): String? = boundEnginePackage

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        boundEnginePackage = null
    }

    companion object {
        @Volatile
        private var instance: AppTtsEngine? = null

        fun getInstance(context: Context): AppTtsEngine {
            return instance ?: synchronized(this) {
                instance ?: AppTtsEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
