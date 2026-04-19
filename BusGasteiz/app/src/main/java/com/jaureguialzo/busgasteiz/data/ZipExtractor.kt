package com.jaureguialzo.busgasteiz.data

import java.io.File
import java.util.zip.ZipInputStream

// MARK: - Extractor ZIP usando ZipInputStream de Java

object ZipExtractor {

    /// Extrae todos los ficheros del ZIP (en ByteArray) al directorio destino.
    fun extract(zipData: ByteArray, destDir: File) {
        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()

        ZipInputStream(zipData.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(destDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out ->
                        zis.copyTo(out)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
