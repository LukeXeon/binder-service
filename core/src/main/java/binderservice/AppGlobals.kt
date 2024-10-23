package binderservice

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.Log

@SuppressLint("PrivateApi")
object AppGlobals {

    private const val TAG = "AppGlobals"

    lateinit var application: Application
        internal set

    init {
        try {
            val activityThreadClazz = Class.forName("android.app.ActivityThread")
            application = activityThreadClazz.getDeclaredMethod("currentApplication").apply {
                isAccessible = true
            }.invoke(null) as Application
        } catch (e: Exception) {
            Log.w(TAG, "application", e)
        }
    }

    internal val workLooper: Looper by lazy {
        try {
            val queuedWork = Class.forName("android.app.QueuedWork")
                .getDeclaredMethod("getHandler")
                .apply {
                    isAccessible = true
                }.invoke(null) as Handler
            queuedWork.looper
        } catch (e: Exception) {
            Log.w(TAG, "workLooper", e)
            HandlerThread("workLooper", Process.THREAD_PRIORITY_FOREGROUND).apply {
                start()
            }.looper
        }
    }

    val processName: String = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            try {
                val activityThreadClazz = Class.forName("android.app.ActivityThread")
                activityThreadClazz.getDeclaredMethod("currentProcessName")
                    .apply {
                        isAccessible = true
                    }.invoke(null) as String
            } catch (e: Exception) {
                // If fallback logic is ever needed, refer to:
                // https://chromium-review.googlesource.com/c/chromium/src/+/905563/1
                throw RuntimeException(e)
            }
        }
    }

    val isDebuggable: Boolean
        get() {
            return application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        }
}