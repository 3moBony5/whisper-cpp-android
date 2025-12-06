# مكتبة Whisper.cpp للأندرويد (AAR)

هذا المستودع يوفر مكتبة `whisper.cpp` مجمعة كملف `AAR`، جاهزة للدمج المباشر في تطبيقات أندرويد المكتوبة بلغة Kotlin. تم بناء هذه المكتبة استنادًا إلى الكود المصدري الرسمي لـ `whisper.cpp` ومثال الأندرويد المرفق به.

## 📦 محتويات المستودع

| الملف / المجلد | الوصف |
| :--- | :--- |
| `whisper-cpp-android.aar` | ملف مكتبة الأندرويد المجمعة (Android Archive) الذي يحتوي على واجهة Kotlin/Java وجميع المكتبات الأصلية (`.so`). |
| `libs/arm64-v8a/libwhisper.so` | ملف المكتبة الأصلية المنفصل لبنية `arm64-v8a` (للتصحيح المتقدم). |
| `lib/` | الكود المصدري الكامل لوحدة مكتبة الأندرويد لمن يرغب في البناء من المصدر. |
| `README.md` | هذا الملف. |

## 🚀 دليل الاستخدام السريع (Kotlin)

لدمج المكتبة في مشروعك، اتبع الخطوات التالية:

### **الخطوة 1: إضافة ملف `AAR`**

1.  أنشئ مجلدًا باسم `libs` في المجلد الرئيسي لوحدة التطبيق الخاصة بك (`app/libs`).
2.  انسخ ملف `whisper-cpp-android.aar` إلى هذا المجلد.

### **الخطوة 2: إعداد Gradle**

في ملف `build.gradle.kts` الخاص بوحدة التطبيق (`app/build.gradle.kts`):

```kotlin
// في بداية الملف، أضف المستودع المحلي
repositories {
    flatDir {
        dirs("libs")
    }
}

// في قسم dependencies، أضف الاعتمادية
dependencies {
    // ...
    implementation(name = "whisper-cpp-android", ext = "aar")
    // ...
}
```

### **الخطوة 3: إعداد فئات الواجهة (JNI)**

لضمان عمل استدعاءات JNI بشكل صحيح وتجنب خطأ `UnsatisfiedLinkError`، يجب أن تتطابق أسماء الحزم والفئات مع ما تم استخدامه في بناء المكتبة الأصلية.

**اسم الحزمة المطلوب:** `com.whispercpp.whisper`

**أ. إنشاء ملف `WhisperLib.kt`**

أنشئ ملفًا باسم `WhisperLib.kt` في المسار `app/src/main/java/com/whispercpp/whisper/` وانسخ الكود التالي. هذه الفئة هي نقطة الاتصال المباشرة مع كود C++:

```kotlin
package com.whispercpp.whisper

import android.util.Log

class WhisperLib {
    companion object {
        init {
            // تحميل المكتبة الأصلية عند أول استخدام للفئة
            System.loadLibrary("whisper")
        }

        // ---------------------------------------------------------------------
        // دوال JNI الخارجية (External JNI Functions)
        // ---------------------------------------------------------------------

        @JvmStatic
        external fun getSystemInfo(): String

        @JvmStatic
        external fun initContext(modelPath: String): Long

        @JvmStatic
        external fun freeContext(contextPtr: Long)

        @JvmStatic
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray)

        @JvmStatic
        external fun getTextSegmentCount(contextPtr: Long): Int

        @JvmStatic
        external fun getTextSegment(contextPtr: Long, index: Int): String
    }
}
```

**ب. إنشاء ملف `WhisperContext.kt` (الغلاف)**

أنشئ ملفًا باسم `WhisperContext.kt` في نفس المسار. هذه الفئة توفر واجهة نظيفة وموجهة للكائنات (Object-Oriented) للاستخدام في تطبيقك:

```kotlin
package com.whispercpp.whisper

class WhisperContext private constructor(private var ptr: Long) {

    companion object {
        fun getSystemInfo(): String {
            return WhisperLib.getSystemInfo()
        }

        fun createContext(filePath: String): WhisperContext {
            val ptr = WhisperLib.initContext(filePath)
            if (ptr == 0L) {
                throw RuntimeException("فشل في إنشاء سياق Whisper. تأكد من صحة مسار النموذج.")
            }
            return WhisperContext(ptr)
        }
    }

    fun transcribeData(audioData: FloatArray, numThreads: Int = 4): String {
        require(ptr != 0L) { "سياق Whisper غير مهيأ أو تم تحريره." }
        WhisperLib.fullTranscribe(ptr, numThreads, audioData)
        
        val textCount = WhisperLib.getTextSegmentCount(ptr)
        return buildString {
            for (i in 0 until textCount) {
                append(WhisperLib.getTextSegment(ptr, i))
            }
        }
    }

    fun release() {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0
        }
    }
}
```

### **الخطوة 4: استخدام المكتبة لاستخراج النص من الصوت**

يوضح هذا القسم كيفية استخدام المكتبة لنسخ ملف صوتي يختاره المستخدم.

#### **أ. إعداد ملف النموذج (`.bin`)**

1.  **تنزيل النموذج:** قم بتنزيل نموذج Whisper الذي تفضله (مثل `ggml-tiny.bin`) من [صفحة نماذج Whisper.cpp الرسمية](https://github.com/ggerganov/whisper.cpp#models).
2.  **إضافة إلى المشروع:** ضع ملف النموذج الذي قمت بتنزيله داخل مجلد `app/src/main/assets`.

#### **ب. كود `MainActivity.kt`**

استخدم `CoroutineScope` لتشغيل عملية النسخ الصوتي في خلفية التطبيق لتجنب تجميد واجهة المستخدم.

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
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private lateinit var modelPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // اختبار سريع لـ JNI
        Log.d("Whisper", "System Info: ${WhisperContext.getSystemInfo()}")

        // 1. نسخ ملف النموذج من Assets إلى مسار يمكن الوصول إليه
        modelPath = copyAssetToCache("ggml-tiny.bin") 

        // 2. بدء عملية النسخ الصوتي (يجب استبدال هذا بآلية اختيار ملف صوتي)
        val audioFilePath = "path/to/user/selected/audio.wav" 
        
        coroutineScope.launch {
            transcribeAudio(audioFilePath)
        }
    }

    // ---------------------------------------------------------------------
    // الدوال المساعدة
    // ---------------------------------------------------------------------

    /**
     * ينسخ ملف النموذج من مجلد assets إلى مجلد cache الخاص بالتطبيق.
     */
    private fun copyAssetToCache(assetName: String): String {
        val cacheFile = File(cacheDir, assetName)
        if (!cacheFile.exists()) {
            try {
                assets.open(assetName).use { inputStream: InputStream ->
                    FileOutputStream(cacheFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                Log.e("Whisper", "فشل في نسخ النموذج من Assets.", e)
                throw e
            }
        }
        return cacheFile.absolutePath
    }

    /**
     * يحمل ملف صوتي ويحوله إلى FloatArray.
     * ⚠️ يجب أن يكون الصوت بصيغة 16kHz Mono FloatArray.
     */
    private fun loadAudioFile(filePath: String): FloatArray {
        // يجب على المطور تنفيذ هذا الجزء باستخدام مكتبة خارجية لقراءة ملف WAV/MP3
        // وتحويله إلى الصيغة المطلوبة (16kHz, Mono, FloatArray).
        Log.w("Whisper", "⚠️ يجب تنفيذ دالة loadAudioFile لتحويل ملف الصوت إلى 16kHz Mono FloatArray.")
        return FloatArray(0) 
    }

    /**
     * دالة النسخ الصوتي الرئيسية التي تعمل في خلفية التطبيق.
     */
    private suspend fun transcribeAudio(audioFilePath: String) {
        var context: WhisperContext? = null
        try {
            // 1. تحميل النموذج وتهيئة السياق
            context = WhisperContext.createContext(modelPath)
            Log.i("Whisper", "تم تهيئة سياق Whisper بنجاح.")

            // 2. تحميل بيانات الصوت
            val audioData = loadAudioFile(audioFilePath)
            if (audioData.isEmpty()) {
                Log.e("Whisper", "بيانات الصوت فارغة. لا يمكن إجراء النسخ.")
                return
            }

            // 3. بدء عملية النسخ
            val transcription = context.transcribeData(audioData)
            
            // 4. عرض النتيجة
            Log.i("Whisper", "نتيجة النسخ: $transcription")

        } catch (e: Exception) {
            Log.e("Whisper", "حدث خطأ أثناء عملية النسخ.", e)
        } finally {
            // 5. تحرير الموارد
            context?.release()
        }
    }
}
```

## 🛠️ البناء من المصدر (Build from Source)

إذا كنت مطورًا وترغب في بناء ملف `AAR` بنفسك أو تعديل الكود المصدري لـ `whisper.cpp`، يمكنك استخدام الكود المصدري الموجود في مجلد `lib`.

### **المتطلبات**

1.  Android Studio مع تثبيت **Android NDK (الإصدار 25.2.9519653)**.
2.  تنزيل شفرة مصدر `whisper.cpp` ووضعها في مجلد بجوار مجلد `lib` (لأن ملف `CMakeLists.txt` يعتمد على المسار النسبي).

### **خطوات البناء**

1.  افتح مشروع الأندرويد الخاص بك في Android Studio.
2.  استورد المجلد `lib` كوحدة (Module) جديدة (File -> New -> Import Module).
3.  قم بتعديل ملف `settings.gradle` لإضافة الوحدة:
    ```gradle
    include ':app', ':lib'
    ```
4.  قم بمزامنة المشروع (Sync Project with Gradle Files).
5.  يمكنك الآن بناء الوحدة `lib` مباشرة من Android Studio أو باستخدام سطر الأوامر:
    ```bash
    ./gradlew :lib:assembleRelease
    ```
    سيتم إنشاء ملف `AAR` في المسار `lib/build/outputs/aar/lib-release.aar`.

---

## ⚠️ ملاحظات هامة حول ملف الصوت

*   **الصيغة المطلوبة:** تتوقع مكتبة `whisper.cpp` بيانات صوتية خام (Raw Audio Data) في صيغة **FloatArray**، بمعدل أخذ عينات (Sample Rate) يبلغ **16000 هرتز (16kHz)**، و **أحادية القناة (Mono)**.
*   **تنفيذ `loadAudioFile`:** يجب عليك تنفيذ الدالة `loadAudioFile` بنفسك باستخدام مكتبة خارجية أو كود مخصص لقراءة ملف WAV أو MP3 الذي يختاره المستخدم وتحويله إلى الصيغة المطلوبة (16kHz Mono FloatArray).
