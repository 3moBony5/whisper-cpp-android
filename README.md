# Whisper.cpp Android AAR Library (Kotlin Ready)

هذا المستودع يحتوي على مكتبة `whisper.cpp` مجمعة كملف `AAR` جاهز للاستخدام في تطبيقات أندرويد بلغة Kotlin. تم بناء المكتبة باستخدام الكود المصدري الرسمي لـ `whisper.cpp` ومثال الأندرويد المرفق به.

## 📦 محتويات المستودع

| الملف / المجلد | الوصف |
| :--- | :--- |
| `whisper-cpp-android.aar` | ملف مكتبة الأندرويد الرئيسي (Android Archive) الذي يحتوي على كود Kotlin/Java وجميع المكتبات الأصلية (`.so`). |
| `libs/arm64-v8a/libwhisper.so` | ملف المكتبة الأصلية المنفصل لبنية `arm64-v8a` (للاستخدام المتقدم أو استكشاف الأخطاء). |
| `README.md` | هذا الملف. |

## 🚀 دليل الاستخدام السريع (Kotlin)

لدمج المكتبة في مشروعك، اتبع الخطوات التالية:

### **الخطوة 1: إضافة ملف `AAR`**

1.  أنشئ مجلدًا باسم `libs` في المجلد الرئيسي لوحدة التطبيق الخاصة بك (`app/libs`).
2.  انسخ ملف `whisper-cpp-android.aar` إلى هذا المجلد.

### **الخطوة 2: تعديل ملف `build.gradle.kts` (الوحدة: `app`)**

أضف المستودع المحلي والاعتمادية:

```kotlin
// في بداية ملف build.gradle.kts (الوحدة: app)
repositories {
    flatDir {
        dirs("libs")
    }
}

// في قسم dependencies
dependencies {
    // ...
    implementation(name = "whisper-cpp-android", ext = "aar")
    // ...
}
```

### **الخطوة 3: إعداد فئات الواجهة (JNI)**

لحل مشكلة `UnsatisfiedLinkError`، يجب أن تتطابق أسماء الحزم والفئات مع ما تم استخدامه في بناء المكتبة الأصلية.

**اسم الحزمة المطلوب:** `com.whispercpp.whisper`

**أ. إنشاء ملف `WhisperLib.kt`**

أنشئ ملفًا باسم `WhisperLib.kt` في المسار `app/src/main/java/com/whispercpp/whisper/` وانسخ الكود التالي:

```kotlin
package com.whispercpp.whisper

import android.util.Log

// هذه الفئة تحتوي على استدعاءات JNI المباشرة
class WhisperLib {
    companion object {
        init {
            // يتم تحميل المكتبة الأصلية هنا
            System.loadLibrary("whisper")
        }

        // ---------------------------------------------------------------------
        // الدوال الأصلية (JNI Functions)
        // ---------------------------------------------------------------------

        // دالة اختبار بسيطة
        @JvmStatic
        external fun getSystemInfo(): String

        // تهيئة سياق Whisper
        @JvmStatic
        external fun initContext(modelPath: String): Long

        // تحرير الموارد
        @JvmStatic
        external fun freeContext(contextPtr: Long)

        // بدء عملية النسخ
        @JvmStatic
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray)

        // الحصول على عدد المقاطع النصية
        @JvmStatic
        external fun getTextSegmentCount(contextPtr: Long): Int

        // الحصول على مقطع نصي معين
        @JvmStatic
        external fun getTextSegment(contextPtr: Long, index: Int): String

        // ... يمكنك إضافة الدوال الأخرى من الكود المصدري إذا احتجت إليها
    }
}
```

**ب. إنشاء ملف `WhisperContext.kt` (الغلاف)**

أنشئ ملفًا باسم `WhisperContext.kt` في نفس المسار لتبسيط الاستخدام:

```kotlin
package com.whispercpp.whisper

// هذا هو الغلاف الذي يجب أن تستخدمه في تطبيقك
class WhisperContext private constructor(private var ptr: Long) {

    // ---------------------------------------------------------------------
    // الدوال العامة للاستخدام
    // ---------------------------------------------------------------------

    companion object {
        // دالة اختبار بسيطة
        fun getSystemInfo(): String {
            return WhisperLib.getSystemInfo()
        }

        // دالة تهيئة السياق من ملف النموذج
        fun createContext(filePath: String): WhisperContext {
            val ptr = WhisperLib.initContext(filePath)
            if (ptr == 0L) {
                throw RuntimeException("Couldn't create context with path $filePath")
            }
            return WhisperContext(ptr)
        }
    }

    // دالة النسخ الصوتي
    fun transcribeData(audioData: FloatArray, numThreads: Int = 4): String {
        require(ptr != 0L)
        WhisperLib.fullTranscribe(ptr, numThreads, audioData)
        
        val textCount = WhisperLib.getTextSegmentCount(ptr)
        return buildString {
            for (i in 0 until textCount) {
                append(WhisperLib.getTextSegment(ptr, i))
            }
        }
    }

    // دالة تحرير الموارد
    fun release() {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0
        }
    }
}
```

### **الخطوة 4: استخدام الكود في `MainActivity` (استخراج النص من الصوت)**

هذا القسم يوضح كيفية استخدام المكتبة لاستخراج النص من ملف صوتي يختاره المستخدم.

#### **أ. إعداد ملف النموذج (`.bin`)**

1.  **تنزيل النموذج:** قم بتنزيل نموذج Whisper الذي تفضله (مثل `ggml-tiny.bin`) من [صفحة نماذج Whisper.cpp الرسمية](https://github.com/ggerganov/whisper.cpp#models).
2.  **إضافة إلى المشروع:** ضع ملف النموذج الذي قمت بتنزيله داخل مجلد `app/src/main/assets`.

#### **ب. كود `MainActivity.kt`**

سنستخدم `CoroutineScope` لتشغيل عملية النسخ الصوتي في خلفية التطبيق لتجنب تجميد واجهة المستخدم.

```kotlin
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private lateinit var modelPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // تأكد من وجود ملف تخطيط

        // 1. اختبار JNI (يجب أن يعمل الآن)
        val info = WhisperContext.getSystemInfo()
        Log.d("Whisper", "System Info: $info")

        // 2. نسخ ملف النموذج من Assets إلى مسار يمكن الوصول إليه
        modelPath = copyAssetToCache("ggml-tiny.bin") // اسم ملف النموذج في مجلد assets

        // 3. بدء عملية النسخ الصوتي (كمثال)
        // يجب استبدال هذا بآلية اختيار ملف صوتي من قبل المستخدم
        val audioFilePath = "path/to/your/audio.wav" // يجب أن يكون ملف صوتي بصيغة WAV
        
        coroutineScope.launch {
            transcribeAudio(audioFilePath)
        }
    }

    // ---------------------------------------------------------------------
    // الدوال المساعدة
    // ---------------------------------------------------------------------

    // دالة لنسخ ملف النموذج من assets إلى مجلد cache
    private fun copyAssetToCache(assetName: String): String {
        val cacheFile = File(cacheDir, assetName)
        if (!cacheFile.exists()) {
            try {
                assets.open(assetName).use { inputStream ->
                    FileOutputStream(cacheFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                Log.e("Whisper", "Failed to copy asset $assetName", e)
                throw e
            }
        }
        return cacheFile.absolutePath
    }

    // دالة لتحميل ملف صوتي وتحويله إلى FloatArray
    // **ملاحظة:** تتطلب هذه الدالة أن يكون ملف الصوت بصيغة WAV أحادي القناة (Mono) بمعدل 16kHz
    private fun loadAudioFile(filePath: String): FloatArray {
        val file = File(filePath)
        // يجب عليك استخدام مكتبة متخصصة (مثل MediaCodec أو WavFile) لقراءة ملف WAV
        // هذا مجرد مثال مبسط (قد لا يعمل مع جميع ملفات WAV)
        // يجب أن تقوم بقراءة بيانات PCM وتحويلها إلى FloatArray
        
        // مثال على تحويل بيانات PCM (16-bit) إلى FloatArray
        // (هذا الجزء يتطلب تنفيذاً دقيقاً لقراءة ملف WAV)
        
        // لغرض الاختبار، سنفترض أن لديك دالة تقوم بذلك
        // return convertWavToFloatArray(file)
        
        // لغرض التوثيق، نتركها فارغة مع تنبيه
        Log.w("Whisper", "loadAudioFile needs proper WAV file parsing and conversion to 16kHz mono FloatArray.")
        return FloatArray(0) // يجب استبدالها بالبيانات الصوتية الفعلية
    }

    // دالة النسخ الصوتي الرئيسية
    private suspend fun transcribeAudio(audioFilePath: String) {
        var context: WhisperContext? = null
        try {
            // 1. تحميل النموذج وتهيئة السياق
            context = WhisperContext.createContext(modelPath)
            Log.i("Whisper", "Context created successfully.")

            // 2. تحميل بيانات الصوت
            val audioData = loadAudioFile(audioFilePath)
            if (audioData.isEmpty()) {
                Log.e("Whisper", "Audio data is empty. Cannot transcribe.")
                return
            }

            // 3. بدء عملية النسخ
            val transcription = context.transcribeData(audioData)
            
            // 4. عرض النتيجة
            Log.i("Whisper", "Transcription Result: $transcription")

        } catch (e: Exception) {
            Log.e("Whisper", "Error during transcription", e)
        } finally {
            // 5. تحرير الموارد
            context?.release()
        }
    }
}
```

## ⚠️ ملاحظات هامة حول ملف الصوت

*   **الصيغة المطلوبة:** تتوقع مكتبة `whisper.cpp` بيانات صوتية خام (Raw Audio Data) في صيغة **FloatArray**، بمعدل أخذ عينات (Sample Rate) يبلغ **16000 هرتز (16kHz)**، و **أحادية القناة (Mono)**.
*   **تنفيذ `loadAudioFile`:** يجب عليك تنفيذ الدالة `loadAudioFile` بنفسك باستخدام مكتبة خارجية أو كود مخصص لقراءة ملف WAV أو MP3 الذي يختاره المستخدم وتحويله إلى الصيغة المطلوبة (16kHz Mono FloatArray).

---
**تم تحديث ملف `README.md` بهذه التفاصيل.**
