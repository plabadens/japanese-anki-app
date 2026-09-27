package dev.pierre.japaneseanki

import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Uses a previously discovered, installed offline Japanese voice for file synthesis. */
class AndroidOfflineAudioSynthesizer(private val engine: TextToSpeech) {
    /** TextToSpeech keeps voice, rate, and listener as mutable engine-wide state. */
    private var engineUnsafe = false

    @Throws(IOException::class, InterruptedException::class)
    fun synthesizeToFile(text: String, voice: Voice, rate: Float, output: File, timeoutSeconds: Long = 30): File {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Audio synthesis must not run on the main thread" }
        return synchronized(engine) {
            if (engineUnsafe) throw IOException("Android TTS could not be stopped safely; refusing to reuse it")
            synthesizeToFileSerialized(text, voice, rate, output, timeoutSeconds)
        }
    }

    private fun synthesizeToFileSerialized(
        text: String,
        voice: Voice,
        rate: Float,
        output: File,
        timeoutSeconds: Long,
    ): File {
        require(text.isNotBlank())
        require(voice.locale.language == "ja" && !voice.isNetworkConnectionRequired)
        require(rate.isFinite() && rate in 0.5f..2f)
        require(timeoutSeconds > 0)
        if (engine.setVoice(voice) != TextToSpeech.SUCCESS || engine.setSpeechRate(rate) != TextToSpeech.SUCCESS) {
            throw IOException("The selected offline voice could not be configured")
        }
        val parent = output.absoluteFile.parentFile ?: throw IOException("Invalid audio output path")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create audio output directory")
        val temporary = File(parent, ".${output.name}.${UUID.randomUUID()}.tmp")
        val id = UUID.randomUUID().toString()
        val completion = CountDownLatch(1)
        val error = AtomicReference<Throwable?>(null)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                if (utteranceId == id) completion.countDown()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                if (utteranceId == id) {
                    error.set(IOException("Android TTS could not synthesize audio"))
                    completion.countDown()
                }
            }
            override fun onError(utteranceId: String, errorCode: Int) {
                if (utteranceId == id) {
                    error.set(IOException("Android TTS could not synthesize audio ($errorCode)"))
                    completion.countDown()
                }
            }
        })
        var requestStarted = false
        try {
            requestStarted = true
            val status = engine.synthesizeToFile(text, Bundle(), temporary, id)
            if (status != TextToSpeech.SUCCESS) throw IOException("Android TTS rejected the synthesis request")
            if (!completion.await(timeoutSeconds, TimeUnit.SECONDS)) throw IOException("Android TTS synthesis timed out")
            error.get()?.let { throw it }
            if (!temporary.isFile || temporary.length() == 0L) throw IOException("Android TTS produced no audio")
            if (!temporary.renameTo(output)) throw IOException("Could not commit synthesized audio")
            return output
        } catch (failure: Throwable) {
            if (requestStarted) {
                try {
                    if (engine.stop() != TextToSpeech.SUCCESS) {
                        throw IOException("Android TTS could not be stopped safely")
                    }
                } catch (stopFailure: Throwable) {
                    engineUnsafe = true
                    stopFailure.addSuppressed(failure)
                    throw stopFailure
                }
            }
            throw failure
        } finally {
            temporary.delete()
        }
    }
}
