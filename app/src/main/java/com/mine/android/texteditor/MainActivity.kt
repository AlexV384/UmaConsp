package com.mine.android.texteditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.IOException

const val FILE_CREATED = 1

class MainActivity : ComponentActivity() {
    private lateinit var saveButton: Button
    private lateinit var textEditor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        saveButton = findViewById(R.id.save_button)
        textEditor = findViewById(R.id.plain_text_input)

        saveButton.setOnClickListener {
            saveFile()
        }
    }

    private fun saveFile() {
        var intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "text_file.txt")
        }

        startActivityForResult(intent, FILE_CREATED)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_CREATED) {
            if (resultCode == RESULT_OK) {
                val uri: Uri? = data?.data

                if (uri != null) {
                    try {
                        val outputStream = contentResolver.openOutputStream(uri)

                        outputStream?.write(textEditor.getText().toString().encodeToByteArray())

                        outputStream?.close()
                    } catch (e: IOException) {
                        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Невозможно получить ссылку на файл", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}