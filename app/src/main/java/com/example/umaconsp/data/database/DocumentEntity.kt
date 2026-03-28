package com.example.umaconsp.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val content: String,
    val lastModified: Long = System.currentTimeMillis()
)