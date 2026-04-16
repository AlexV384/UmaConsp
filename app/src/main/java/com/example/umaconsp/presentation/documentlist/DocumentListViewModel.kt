package com.example.umaconsp.presentation.documentlist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.umaconsp.UmaconspApplication
import com.example.umaconsp.data.database.DocumentEntity
import com.example.umaconsp.data.export.DocumentExporter
import com.example.umaconsp.presentation.settings.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.*

class DocumentListViewModel(
    private val settingsManager: SettingsManager = SettingsManager(UmaconspApplication.instance)
) : ViewModel() {
    private val db = UmaconspApplication.database
    private val dao = db.documentDao()
    private val exporter = DocumentExporter(UmaconspApplication.instance)

    private val _documentDeleted = MutableSharedFlow<String>()
    val documentDeleted: SharedFlow<String> = _documentDeleted.asSharedFlow()

    val documents: Flow<List<DocumentItem>> = dao.getAllDocuments().map { list ->
        list.map { doc ->
            DocumentItem(
                id = doc.id,
                title = doc.title,
                lastModified = doc.lastModified
            )
        }
    }

    private val exportScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val exportChannel = Channel<ExportTask>(Channel.UNLIMITED)
    private val pendingExports = mutableMapOf<String, Job>()

    init {
        exportScope.launch {
            for (task in exportChannel) {
                delay(task.debounceMs)
                val latestJob = pendingExports[task.documentId]
                if (latestJob !== task.job) {
                    continue
                }
                performExport(task.documentId, task.title, task.content)
                pendingExports.remove(task.documentId)
            }
        }
    }

    private suspend fun scheduleExport(documentId: String, title: String, content: String) {
        pendingExports[documentId]?.cancel()
        val job = exportScope.launch {
            val currentJob = coroutineContext[Job]!!
            val task = ExportTask(documentId, title, content, debounceMs = 500L, job = currentJob)
            exportChannel.send(task)
        }
        pendingExports[documentId] = job
    }

    private suspend fun performExport(documentId: String, title: String, content: String) {
        val exportUriString = settingsManager.exportFolderUriFlow.firstOrNull()
        if (exportUriString.isNullOrEmpty()) return
        val exportUri = Uri.parse(exportUriString)
        try {
            exporter.exportDocument(exportUri, documentId, title, content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun forceExportDocument(documentId: String) {
        val doc = dao.getDocumentById(documentId) ?: return
        pendingExports[documentId]?.cancel()
        pendingExports.remove(documentId)
        performExport(doc.id, doc.title, doc.content)
    }

    suspend fun createNewDocument(): String {
        val newId = UUID.randomUUID().toString()
        val count = dao.getAllDocuments().firstOrNull()?.size ?: 0
        val newDoc = DocumentEntity(
            id = newId,
            title = "Конспект ${count + 1}",
            content = "",
            lastModified = System.currentTimeMillis()
        )
        dao.insertDocument(newDoc)
        scheduleExport(newDoc.id, newDoc.title, newDoc.content)
        return newId
    }

    suspend fun deleteDocument(id: String) {
        dao.deleteDocumentById(id)
        _documentDeleted.emit(id)
        pendingExports[id]?.cancel()
        pendingExports.remove(id)
    }

    suspend fun renameDocument(id: String, newTitle: String) {
        val doc = dao.getDocumentById(id) ?: return
        val updated = doc.copy(title = newTitle)
        dao.updateDocument(updated)
        scheduleExport(updated.id, updated.title, updated.content)
    }

    suspend fun getDocument(id: String): DocumentItem? {
        val doc = dao.getDocumentById(id)
        return doc?.let { DocumentItem(it.id, it.title, it.lastModified) }
    }

    suspend fun updateDocumentContent(id: String, newContent: String) {
        val doc = dao.getDocumentById(id) ?: return
        val updated = doc.copy(content = newContent, lastModified = System.currentTimeMillis())
        dao.updateDocument(updated)
        scheduleExport(updated.id, updated.title, updated.content)
    }

    suspend fun getDocumentContent(id: String): String {
        return dao.getDocumentById(id)?.content ?: ""
    }

    override fun onCleared() {
        exportScope.cancel()
        super.onCleared()
    }

    private data class ExportTask(
        val documentId: String,
        val title: String,
        val content: String,
        val debounceMs: Long,
        val job: Job
    )
}