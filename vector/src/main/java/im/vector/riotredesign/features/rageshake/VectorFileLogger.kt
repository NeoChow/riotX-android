/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.riotredesign.features.rageshake

import android.content.Context
import android.text.TextUtils
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.*
import java.util.logging.Formatter
import kotlin.collections.ArrayList

object VectorFileLogger : Timber.DebugTree() {

    private const val LOG_SIZE_BYTES = 50 * 1024 * 1024 // 50MB

    // relatively large rotation count because closing > opening the app rotates the log (!)
    private const val LOG_ROTATION_COUNT = 15

    private val sLogger = Logger.getLogger("im.vector.riotredesign")
    private lateinit var sFileHandler: FileHandler
    private lateinit var sCacheDirectory: File
    private var sFileName = "riotx"

    fun init(context: Context) {
        val logsDirectoryFile = context.cacheDir.absolutePath + "/logs"

        setLogDirectory(File(logsDirectoryFile))
        init("RiotXLog")
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (t != null) {
            logToFile(t)
        }

        logToFile("$priority ", tag ?: "Tag", message)
    }

    /**
     * Set the directory to put log files.
     *
     * @param cacheDir The directory, usually [android.content.ContextWrapper.getCacheDir]
     */
    private fun setLogDirectory(cacheDir: File) {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        sCacheDirectory = cacheDir
    }

    /**
     * Initialises the logger. Should be called AFTER [Log.setLogDirectory].
     *
     * @param fileName the base file name
     */
    private fun init(fileName: String) {
        try {
            if (!TextUtils.isEmpty(fileName)) {
                sFileName = fileName
            }
            sFileHandler = FileHandler(sCacheDirectory.absolutePath + "/" + sFileName + ".%g.txt", LOG_SIZE_BYTES, LOG_ROTATION_COUNT)
            sFileHandler.formatter = LogFormatter()
            sLogger.useParentHandlers = false
            sLogger.level = Level.ALL
            sLogger.addHandler(sFileHandler)
        } catch (e: IOException) {
        }
    }

    /**
     * Adds our own log files to the provided list of files.
     *
     * @param files The list of files to add to.
     * @return The same list with more files added.
     */
    fun getLogFiles(): List<File> {
        val files = ArrayList<File>()

        try {
            // reported by GA
            if (null != sFileHandler) {
                sFileHandler.flush()
                val absPath = sCacheDirectory.absolutePath

                for (i in 0..LOG_ROTATION_COUNT) {
                    val filepath = "$absPath/$sFileName.$i.txt"
                    val file = File(filepath)
                    if (file.exists()) {
                        files.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "## addLogFiles() failed : " + e.message)
        }

        return files
    }

    class LogFormatter : Formatter() {

        override fun format(r: LogRecord): String {
            if (!mIsTimeZoneSet) {
                DATE_FORMAT.timeZone = TimeZone.getTimeZone("UTC")
                mIsTimeZoneSet = true
            }

            val thrown = r.thrown
            if (thrown != null) {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                sw.write(r.message)
                sw.write(LINE_SEPARATOR)
                thrown.printStackTrace(pw)
                pw.flush()
                return sw.toString()
            } else {
                val b = StringBuilder()
                val date = DATE_FORMAT.format(Date(r.millis))
                b.append(date)
                b.append("Z ")
                b.append(r.message)
                b.append(LINE_SEPARATOR)
                return b.toString()
            }
        }

        companion object {
            private val LINE_SEPARATOR = System.getProperty("line.separator") ?: "\n"
            private val DATE_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            private var mIsTimeZoneSet = false
        }
    }

    /**
     * Log an Throwable
     *
     * @param throwable the throwable to log
     */
    private fun logToFile(throwable: Throwable?) {
        if (null == sCacheDirectory || throwable == null) {
            return
        }

        val errors = StringWriter()
        throwable.printStackTrace(PrintWriter(errors))

        sLogger.info(errors.toString())
    }

    private fun logToFile(level: String, tag: String, content: String) {
        if (null == sCacheDirectory) {
            return
        }

        val b = StringBuilder()
        b.append(Thread.currentThread().id)
        b.append(" ")
        b.append(level)
        b.append("/")
        b.append(tag)
        b.append(": ")
        b.append(content)
        sLogger.info(b.toString())
    }
}