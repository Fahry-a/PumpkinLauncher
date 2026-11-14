package com.blizzardfire.pumpkinlauncher

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class TextEditorActivity : AppCompatActivity() {

    private lateinit var editorInput: EditText
    private var targetFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_editor)

        editorInput = findViewById(R.id.editorInput)
        val pathText = findViewById<TextView>(R.id.editorPathText)

        val file = intent.getStringExtra(EXTRA_PATH)?.let { File(it) }
        if (file == null || !file.isFile) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        targetFile = file
        pathText.text = file.path

        val content = try {
            file.readText()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot read: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (content.any { it.code == 0 }) {
            // NUL bytes mean the file is binary; decoding it would corrupt it on save.
            Toast.makeText(this, "Binary file — not editable", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        editorInput.setText(content)

        findViewById<Button>(R.id.editorBackButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.editorSaveButton).setOnClickListener {
            val target = targetFile ?: return@setOnClickListener
            try {
                target.writeText(editorInput.text.toString())
                Toast.makeText(this, "Saved ${target.name}", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}
