package com.example.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions to disk so they can be automatically attached
 * to feedback / crash reports without requiring the user to remember what happened.
 *
 * Install once at app startup via [install]. The last crash log is then readable
 * via [getLastCrashLog] from any composable or ViewModel.
 */
object CrashLogCapture {

    private const val TAG = "CrashLogCapture"
    private const val CRASH_FILE_NAME = "last_crash.txt"
    private const val MAX_LOG_BYTES = 64 * 1024 // 64 KB — enough for a full stack trace

    /**
     * Install the custom [Thread.UncaughtExceptionHandler].
     * Call this in [MainActivity.onCreate] (before super) so every thread is covered.
     *
     * The previous default handler is preserved and still called after we save the
     * log, so system crash dialogs / Play-console reporting continue to work normally.
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashLog(appContext, thread, throwable)
            } catch (e: Exception) {
                // Never let crash handling itself crash the handler chain.
                Log.e(TAG, "Failed to save crash log", e)
            }
            // Delegate to the original handler (system crash dialog / ANR reporting).
            previousHandler?.uncaughtException(thread, throwable)
        }

        Log.i(TAG, "Crash log capture installed")
    }

    /** Read the last crash log from disk. Returns null if no crash has occurred yet. */
    fun getLastCrashLog(context: Context): String? {
        return try {
            val file = crashFile(context)
            if (file.exists() && file.length() > 0) file.readText() else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read crash log", e)
            null
        }
    }

    /** Delete the saved crash log (e.g. after the user has submitted it). */
    fun clearCrashLog(context: Context) {
        try {
            crashFile(context).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear crash log", e)
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun saveCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        pw.println("=== Crash Report ===")
        pw.println("Time     : $timestamp")
        pw.println("Thread   : ${thread.name} (id=${thread.id})")
        pw.println("Android  : ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        pw.println("Device   : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        pw.println()
        pw.println("--- Stack Trace ---")
        throwable.printStackTrace(pw)

        // Include chained causes
        var cause = throwable.cause
        var depth = 0
        while (cause != null && depth < 5) {
            pw.println()
            pw.println("--- Caused by (depth ${++depth}) ---")
            cause.printStackTrace(pw)
            cause = cause.cause
        }

        pw.flush()

        // Truncate to avoid huge files from runaway recursion etc.
        val logText = sw.toString().take(MAX_LOG_BYTES)

        crashFile(context).writeText(logText, Charsets.UTF_8)
        Log.e(TAG, "Crash log saved (${ logText.length } bytes)")
    }

    private fun crashFile(context: Context): File =
        File(context.filesDir, CRASH_FILE_NAME)
}
