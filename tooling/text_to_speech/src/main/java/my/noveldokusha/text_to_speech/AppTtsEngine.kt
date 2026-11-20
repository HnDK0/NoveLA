package my.noveldokusha.text_to_speech

import android.content.Context
import android.speech.tts.TextToSpeech

class AppTtsEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null
    private var ready = false
    private var generation = 0
    private val waitingCallbacks = mutableListOf<() -> Unit>()
    // null означает системный движок по умолчанию (service.defaultEngine его всегда и возвращает).
    private var boundEnginePackage: String? = null

    @Synchronized
    fun getOrCreate(onReady: (() -> Unit)? = null): TextToSpeech {
        onReady?.let {
            if (ready) it() else waitingCallbacks.add(it)
        }
        if (engine == null) {
            boundEnginePackage = null
            val requestGeneration = ++generation
            engine = TextToSpeech(appContext) { status ->
                completeInitialization(requestGeneration, status)
            }
        }
        return engine!!
    }

    @Synchronized
    fun whenReady(callback: () -> Unit) {
        if (ready) callback() else waitingCallbacks.add(callback)
    }

    private fun completeInitialization(requestGeneration: Int, status: Int) {
        val callbacks = synchronized(this) {
            if (generation != requestGeneration) return
            ready = status == TextToSpeech.SUCCESS
            val pending = waitingCallbacks.toList()
            waitingCallbacks.clear()
            if (ready) pending else emptyList()
        }
        callbacks.forEach { it() }
    }

    @Synchronized
    fun reinit(enginePackage: String?, onReady: () -> Unit) {
        engine?.stop()
        engine?.shutdown()
        ready = false
        waitingCallbacks.clear()
        waitingCallbacks.add(onReady)
        boundEnginePackage = enginePackage?.takeIf { it.isNotEmpty() }
        val requestGeneration = ++generation
        engine = if (enginePackage.isNullOrEmpty()) {
            TextToSpeech(appContext) { status -> completeInitialization(requestGeneration, status) }
        } else {
            TextToSpeech(appContext,
                { status -> completeInitialization(requestGeneration, status) }, enginePackage)
        }
    }

    fun getBoundEnginePackage(): String? = boundEnginePackage

    @Synchronized
    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        boundEnginePackage = null
        ready = false
        waitingCallbacks.clear()
        generation++
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
