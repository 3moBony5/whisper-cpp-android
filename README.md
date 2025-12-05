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

### **الخطوة 4: استخدام الكود في `MainActivity`**

```kotlin
import com.whispercpp.whisper.WhisperContext
import android.util.Log

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. اختبار JNI (يجب أن يعمل الآن)
        val info = WhisperContext.getSystemInfo()
        Log.d("Whisper", "System Info: $info")

        // 2. مثال على استخدام النسخ الصوتي
        // ملاحظة: يجب عليك توفير ملف النموذج (ggml-tiny.bin مثلاً) في مجلد assets
        val modelPath = "path/to/your/ggml-model.bin" 
        
        try {
            val context = WhisperContext.createContext(modelPath)
            
            // 3. قم بتحميل بيانات الصوت الخاصة بك هنا (يجب أن تكون FloatArray)
            val audioData: FloatArray = loadYourAudioData() 
            
            val transcription = context.transcribeData(audioData)
            Log.i("Whisper", "Transcription Result: $transcription")
            
            context.release()
        } catch (e: Exception) {
            Log.e("Whisper", "Error during transcription", e)
        }
    }
    
    // دالة مساعدة (يجب عليك تنفيذها)
    private fun loadYourAudioData(): FloatArray {
        // يجب أن تقوم بتحميل ملف صوتي وتحويله إلى FloatArray (16kHz, mono)
        return FloatArray(0) 
    }
}
```

## ⚠️ ملف النموذج (BIN)

**المكتبة لا تتضمن نموذج الذكاء الاصطناعي.** يجب عليك تنزيل نموذج `ggml-*.bin` (مثل `ggml-tiny.bin`) ووضعه في مجلد `assets` في مشروعك وتوفير مساره في `modelPath`.
