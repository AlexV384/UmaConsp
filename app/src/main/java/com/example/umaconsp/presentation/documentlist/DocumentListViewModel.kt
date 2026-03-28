package com.example.umaconsp.presentation.documentlist

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.*
import java.util.*
import com.example.umaconsp.UmaconspApplication
import com.example.umaconsp.data.database.DocumentEntity

class DocumentListViewModel : ViewModel() {
    private val db = UmaconspApplication.database
    private val dao = db.documentDao()

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
        return newId
    }

    suspend fun deleteDocument(id: String) {
        dao.deleteDocumentById(id)
        _documentDeleted.emit(id)
    }

    suspend fun renameDocument(id: String, newTitle: String) {
        val doc = dao.getDocumentById(id) ?: return
        val updated = doc.copy(title = newTitle)
        dao.updateDocument(updated)
    }

    suspend fun getDocument(id: String): DocumentItem? {
        val doc = dao.getDocumentById(id)
        return doc?.let { DocumentItem(it.id, it.title, it.lastModified) }
    }

    suspend fun updateDocumentContent(id: String, newContent: String) {
        val doc = dao.getDocumentById(id) ?: return
        val updated = doc.copy(content = newContent, lastModified = System.currentTimeMillis())
        dao.updateDocument(updated)
    }

    suspend fun getDocumentContent(id: String): String {
        return dao.getDocumentById(id)?.content ?: ""
    }
}