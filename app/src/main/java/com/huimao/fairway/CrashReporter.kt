package com.huimao.fairway

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 闪退日志：抓到未捕获异常就写文件，下次启动弹窗显示堆栈，
 * 不用接电脑抓 logcat 也能定位（debug 测试阶段用）。
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                appContext.openFileOutput(FILE, Context.MODE_PRIVATE).use {
                    it.write(sw.toString().toByteArray())
                }
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrash(context: Context): String? = runCatching {
        context.openFileInput(FILE).bufferedReader().readText()
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { context.deleteFile(FILE) }
    }
}
