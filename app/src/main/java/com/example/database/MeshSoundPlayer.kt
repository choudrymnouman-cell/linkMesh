package com.example.database

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.PI

object MeshSoundPlayer {
    private var activeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var currentTrack: AudioTrack? = null

    fun playNotification() {
        scope.launch {
            try {
                playTone(800.0, 100)
                delay(50)
                playTone(1200.0, 150)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startRingtone() {
        stopSound()
        activeJob = scope.launch {
            try {
                while (true) {
                    playTone(440.0, 150)
                    delay(50)
                    playTone(554.37, 150)
                    delay(50)
                    playTone(659.25, 200)
                    delay(400)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startSiren() {
        stopSound()
        activeJob = scope.launch {
            try {
                while (true) {
                    // Sweeping siren up and down (disaster distress)
                    for (freq in 500..1200 step 20) {
                        if (activeJob?.isActive != true) break
                        playTone(freq.toDouble(), 12)
                    }
                    for (freq in 1200 downTo 500 step 20) {
                        if (activeJob?.isActive != true) break
                        playTone(freq.toDouble(), 12)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            }
        }
    }

    @Volatile
    private var mediaPlayer: android.media.MediaPlayer? = null

    fun startVoicePlayback(filePath: String, durationSec: Int, context: android.content.Context) {
        stopSound()

        val file = java.io.File(filePath)
        if (file.exists() && file.length() > 0) {
            try {
                val mp = android.media.MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                }
                mediaPlayer = mp
                mp.setOnCompletionListener {
                    try {
                        it.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (mediaPlayer == mp) {
                        mediaPlayer = null
                    }
                }
                return
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        activeJob = scope.launch {
            var audioTrack: AudioTrack? = null
            try {
                val sampleRate = 16000
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = Math.max(minBufferSize, 4096)

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                currentTrack = audioTrack
                audioTrack.play()

                val durationMs = durationSec * 1000L
                val startTime = System.currentTimeMillis()
                
                var n = 0L
                val pcmBuffer = ShortArray(512)
                val byteBuffer = ByteArray(1024)

                var phase0 = 0.0
                var phase1 = 0.0
                var phase2 = 0.0
                var phase3 = 0.0

                while (System.currentTimeMillis() - startTime < durationMs && activeJob?.isActive == true) {
                    val progress = n.toDouble() / sampleRate
                    
                    val baseFreq = 140.0 + 30.0 * sin(2.0 * PI * 0.35 * progress) + 10.0 * sin(2.0 * PI * 1.2 * progress)
                    val rawEnvelope = maxOf(0.0, sin(2.0 * PI * 0.55 * progress) * 0.65 + sin(2.0 * PI * 0.12 * progress) * 0.35)
                    val envelope = rawEnvelope * RawSineFadeFactor(progress, durationSec.toDouble())

                    for (i in 0 until pcmBuffer.size) {
                        val dt = 1.0 / sampleRate
                        
                        phase0 += 2.0 * PI * baseFreq * dt
                        phase1 += 2.0 * PI * (baseFreq * 2.15) * dt
                        phase2 += 2.0 * PI * (baseFreq * 3.4) * dt
                        phase3 += 2.0 * PI * (baseFreq * 4.8) * dt

                        phase0 %= (2.0 * PI)
                        phase1 %= (2.0 * PI)
                        phase2 %= (2.0 * PI)
                        phase3 %= (2.0 * PI)

                        val f0 = sin(phase0)
                        val f1 = 0.6 * sin(phase1)
                        val f2 = 0.3 * sin(phase2)
                        val f3 = 0.15 * sin(phase3)
                        
                        val noiseValue = 0.04 * ((Math.random() * 2.0) - 1.0)
                        val combinedSample = (f0 + f1 + f2 + f3 + noiseValue) * envelope
                        pcmBuffer[i] = (combinedSample * 14000.0).coerceIn(-32767.1, 32767.1).toInt().toShort()
                    }

                    n += pcmBuffer.size

                    for (i in 0 until pcmBuffer.size) {
                        val sample = pcmBuffer[i].toInt()
                        byteBuffer[2 * i] = (sample and 0x00FF).toByte()
                        byteBuffer[2 * i + 1] = ((sample and 0xFF00) ushr 8).toByte()
                    }

                    audioTrack.write(byteBuffer, 0, byteBuffer.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (currentTrack == audioTrack) {
                        currentTrack = null
                    }
                }
            }
        }
    }

    private fun RawSineFadeFactor(time: Double, duration: Double): Double {
        if (time < 0.15) return time / 0.15
        if (time > duration - 0.15) return maxOf(0.0, (duration - time) / 0.15)
        return 1.0
    }

    fun playLoudSiren(context: android.content.Context) {
        try {
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_NORMAL
            
            val maxMusicVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val maxAlarmVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxMusicVol, 0)
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxAlarmVol, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        startSiren()
    }

    fun stopSound() {
        activeJob?.cancel()
        activeJob = null

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }

        try {
            currentTrack?.let {
                it.stop()
                it.release()
            }
        } catch (e: Exception) {
            // ignore failure on immediate cancellation
        } finally {
            currentTrack = null
        }
    }

    private suspend fun playTone(frequency: Double, durationMs: Int) {
        val sampleRate = 8000
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val generatedSnd = ByteArray(2 * numSamples)

        val omega = 2.0 * PI * frequency / sampleRate
        val fadeSamples = minOf((sampleRate * 0.01).toInt(), numSamples / 2)
        for (i in 0 until numSamples) {
            var amp = 1.0
            if (i < fadeSamples && fadeSamples > 0) {
                amp = i.toDouble() / fadeSamples
            } else if (i > numSamples - fadeSamples && fadeSamples > 0) {
                amp = (numSamples - i).toDouble() / fadeSamples
            }
            val sample = (sin(omega * i) * 22000.0 * amp).toInt().toShort()
            generatedSnd[2 * i] = (sample.toInt() and 0x00FF).toByte()
            generatedSnd[2 * i + 1] = ((sample.toInt() and 0xFF00) ushr 8).toByte()
        }

        var audioTrack: AudioTrack? = null
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(generatedSnd.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            currentTrack = audioTrack
            audioTrack.write(generatedSnd, 0, generatedSnd.size)
            audioTrack.play()
            delay(durationMs.toLong())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // catch interruptedException or audio errors
        } finally {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (ex: Exception) {
                // Ignore any failure on quick double-stops
            } finally {
                if (currentTrack == audioTrack) {
                    currentTrack = null
                }
            }
        }
    }

    @Volatile
    private var callAudioTrack: AudioTrack? = null

    @Synchronized
    fun playCallAudioChunk(pcmBytes: ByteArray) {
        try {
            var track = callAudioTrack
            if (track == null) {
                val sampleRate = 8000
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = Math.max(minBufferSize, pcmBytes.size * 4)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                callAudioTrack = track
            }
            track.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun stopCallAudioStream() {
        try {
            callAudioTrack?.let {
                it.stop()
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            callAudioTrack = null
        }
    }
}
