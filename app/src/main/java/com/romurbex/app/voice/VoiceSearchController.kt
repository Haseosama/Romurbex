package com.romurbex.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Reconnaissance et synthèse vocale pour la recherche vocale — inspiré de SpeechInput/SpeechOutput
 * du projet Eve, simplifié pour un usage ponctuel (une écoute, une réponse) plutôt qu'une écoute
 * continue avec mot de réveil.
 */
class VoiceSearchController(context: Context) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    private var ttsReady = false
    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts.language = Locale.getDefault()
    }

    fun listenOnce(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Reconnaissance vocale indisponible sur cet appareil")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onResult(text) else onError("Rien entendu")
                }

                override fun onError(error: Int) {
                    onError("Erreur reconnaissance vocale ($error)")
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                },
            )
        }
    }

    fun speak(text: String, onDone: () -> Unit) {
        if (!ttsReady || text.isBlank()) {
            onDone()
            return
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onDone()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onDone()
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts.stop()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        tts.stop()
        tts.shutdown()
    }
}
