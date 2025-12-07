package com.whispercpp.whisper

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

private const val LOG_TAG = "AudioUtils"

/**
 * يقرأ ملف WAV ويحوله إلى FloatArray بصيغة 16kHz Mono.
 *
 * @param filePath المسار المطلق لملف WAV.
 * @return FloatArray يحتوي على بيانات الصوت بصيغة 16kHz Mono.
 * @throws IllegalArgumentException إذا كان الملف ليس بصيغة WAV أو كانت صيغته غير مدعومة.
 */
fun readWavFile(filePath: String): FloatArray {
    val file = File(filePath)
    if (!file.exists()) {
        throw IllegalArgumentException("الملف غير موجود: $filePath")
    }

    FileInputStream(file).use { stream ->
        // 1. قراءة رأس ملف WAV (WAV Header)
        val header = ByteArray(44)
        if (stream.read(header) != 44) {
            throw IllegalArgumentException("ملف WAV غير صالح: حجم الرأس غير كافٍ.")
        }

        // التحقق من توقيع RIFF و WAVE
        if (String(header, 0, 4) != "RIFF" || String(header, 8, 4) != "WAVE") {
            throw IllegalArgumentException("الملف ليس بصيغة WAV.")
        }

        // قراءة معدل العينات (Sample Rate)
        val sampleRateBuffer = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = sampleRateBuffer.getInt()

        // قراءة عدد القنوات (Number of Channels)
        val numChannelsBuffer = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN)
        val numChannels = numChannelsBuffer.getShort().toInt()

        // قراءة عمق البت (Bits Per Sample)
        val bitsPerSampleBuffer = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN)
        val bitsPerSample = bitsPerSampleBuffer.getShort().toInt()

        Log.d(LOG_TAG, "WAV Info: Rate=$sampleRate, Channels=$numChannels, Bits=$bitsPerSample")

        if (bitsPerSample != 16) {
            throw IllegalArgumentException("عمق البت غير مدعوم. يجب أن يكون 16 بت.")
        }

        // 2. قراءة بيانات الصوت
        val dataChunkHeader = ByteArray(8)
        var dataSize = 0
        while (stream.read(dataChunkHeader) == 8) {
            val chunkId = String(dataChunkHeader, 0, 4)
            val chunkSize = ByteBuffer.wrap(dataChunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
            if (chunkId == "data") {
                dataSize = chunkSize
                break
            } else {
                // تخطي الأجزاء الأخرى (مثل LIST أو fact)
                stream.skip(chunkSize.toLong())
            }
        }

        if (dataSize == 0) {
            throw IllegalArgumentException("لم يتم العثور على جزء البيانات (data chunk) في ملف WAV.")
        }

        val numSamples = dataSize / (numChannels * (bitsPerSample / 8))
        val pcmData = ByteArray(dataSize)
        if (stream.read(pcmData) != dataSize) {
            throw IllegalArgumentException("فشل في قراءة بيانات الصوت بالكامل.")
        }

        // 3. التحويل إلى FloatArray (16kHz Mono)
        val floatData = FloatArray(numSamples)
        val byteBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)

        // التحويل من 16-bit PCM إلى Float (-1.0 to 1.0)
        for (i in 0 until numSamples) {
            val sample = byteBuffer.getShort().toFloat() / 32768.0f
            floatData[i] = sample
        }

        // 4. دمج القنوات (إذا كان ستيريو)
        val monoData: FloatArray
        if (numChannels == 2) {
            val monoSamples = numSamples / 2
            monoData = FloatArray(monoSamples)
            for (i in 0 until monoSamples) {
                // متوسط القناتين
                monoData[i] = (floatData[i * 2] + floatData[i * 2 + 1]) / 2.0f
            }
        } else {
            monoData = floatData
        }

        // 5. إعادة أخذ العينات (Resampling) إلى 16kHz (إذا لزم الأمر)
        if (sampleRate == 16000) {
            return monoData
        }

        Log.d(LOG_TAG, "إعادة أخذ العينات من ${sampleRate}Hz إلى 16000Hz.")
        val targetRate = 16000
        val ratio = sampleRate.toDouble() / targetRate.toDouble()
        val resampledSize = (monoData.size / ratio).roundToInt()
        val resampledData = FloatArray(resampledSize)

        for (i in 0 until resampledSize) {
            val srcIndex = (i * ratio).toInt()
            if (srcIndex < monoData.size) {
                resampledData[i] = monoData[srcIndex]
            }
        }

        return resampledData
    }
}
