package my.noveldokusha.text_to_speech

import android.content.Context
import android.speech.tts.TextToSpeech

class AppTtsEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null

    fun getOrCreate(onReady: (() -> Unit)? = null): TextToSpeech {
        if (engine == null) {
            engine = TextToSpeech(appContext) { if (it == TextToSpeech.SUCCESS) onReady?.invoke() }
        }
        return engine!!
    }

    fun reinit(enginePackage: String?, onReady: () -> Unit) {
        engine?.stop()
        engine?.shutdown()
        engine = if (enginePackage.isNullOrEmpty()) {
            TextToSpeech(appContext) { if (it == TextToSpeech.SUCCESS) onReady() }
        } else {
            TextToSpeech(appContext, { if (it == TextToSpeech.SUCCESS) onReady() }, enginePackage)
        }
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
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
