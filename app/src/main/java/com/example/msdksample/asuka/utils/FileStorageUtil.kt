package com.example.msdksample.asuka.utils

import android.content.Context
import java.io.File

object FileStorageUtil {

    fun getAppDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "TianshuInspection")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getPdfDirectory(context: Context): File {
        val dir = File(getAppDirectory(context), "reports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getPdfFilePath(context: Context, fileName: String): String {
        return File(getPdfDirectory(context), "$fileName.pdf").absolutePath
    }

    fun generateReportFileName(date: String, taskName: String): String {
        val sanitizedDate = date.replace("-", "")
        val sanitizedName = taskName.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")
        return "report_${sanitizedDate}_${sanitizedName}_${System.currentTimeMillis()}"
    }

    fun isFileExists(path: String): Boolean {
        return File(path).exists()
    }

    fun deleteFile(path: String): Boolean {
        return File(path).delete()
    }
}
