package com.example.umaconsp.data.localstorage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

const val TAG = "DirectoryManager"

class PrivateFolder(val context: Context, var isLoading: Boolean = false) {
    fun getAllPrivateDirectories(): List<File> {
        val privateDir = context.filesDir
        return privateDir.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()
    }
    fun getImportedModels() : List<String> {
        return getAllPrivateDirectories().map { x -> x.name }
    }

    suspend fun importModel(uri: Uri) {
//        Log.i("ModelImporter", "Picked directory successful: ${uri.path}")
        val dest = ensureModelFolder(uri, context)
        isLoading = true
        copyModelFolder(uri, dest, context)
        isLoading = false
        Log.i(TAG, "Existing private directories: ")
        for (a in getAllPrivateDirectories()) {
            Log.i(TAG, "${a.path}")
            Log.i(TAG, "Files: ")
            for (b in a.listFiles()) {
                Log.i(TAG, "\t ${b.path}")
            }
        }
    }

    companion object {
        fun copyModelFolderSync(source: Uri, dest: File, context: Context) {
            val pickedDir: DocumentFile = DocumentFile.fromTreeUri(context, source)!!
            for (file: DocumentFile in pickedDir.listFiles()) {
                try {
                    context.contentResolver.openInputStream(file.uri).use { `in` ->
                        FileOutputStream(File(dest, file.name!!)).use { out ->
                            val buffer = ByteArray(4096)
                            var read: Int
                            while ((`in`!!.read(buffer).also { read = it }) != -1) {
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        suspend fun copyModelFolder(source: Uri, dest: File, context: Context) {
            withContext(Dispatchers.IO) {
                val pickedDir: DocumentFile = DocumentFile.fromTreeUri(context, source)
                    ?: throw IllegalArgumentException("Invalid directory URI")

                for (file: DocumentFile in pickedDir.listFiles()) {
                    try {
                        context.contentResolver.openInputStream(file.uri).use { input ->
                            FileOutputStream(File(dest, file.name!!)).use { output ->
                                val buffer = ByteArray(4096)
                                var read: Int
                                while ((input!!.read(buffer).also { read = it }) != -1) {
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        // Optionally rethrow or handle the error
                        throw e
                    }
                }
            }
        }

        fun ensureModelFolder(treeUri: Uri, context: Context): File {
            val pickedDir: DocumentFile = DocumentFile.fromTreeUri(context, treeUri)!!
            if (pickedDir.isDirectory) {
                Log.d(TAG, "PickedDir: ${pickedDir.name}")
                val destDir = File(context.filesDir, pickedDir.name!!)
                if (!destDir.exists()) destDir.mkdirs()
                return destDir
            }
            Log.e("DirectoryManager", "Directory doesn't exist")
            return File("/data/local/tmp/tmp.bin")
        }
    }
}