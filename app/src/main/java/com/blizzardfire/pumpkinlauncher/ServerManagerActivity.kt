package com.blizzardfire.pumpkinlauncher

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class ServerManagerActivity : AppCompatActivity() {

    private lateinit var locationPage: View
    private lateinit var filesPage: View
    private lateinit var settingsPage: View
    private lateinit var serverPathText: TextView
    private lateinit var storageStatusText: TextView
    private lateinit var grantAccessButton: Button
    private lateinit var currentPathText: TextView
    private lateinit var fileRecyclerView: RecyclerView
    private lateinit var pasteButton: Button

    private lateinit var ramLimitInput: EditText
    private lateinit var prelaunchInput: EditText
    private lateinit var wrapperInput: EditText
    private lateinit var argsInput: EditText

    private var browseDir: File = File("/storage/emulated/0")
    private var clipboardSource: File? = null
    private var clipboardCut = false

    private val adapter = FileAdapter()

    private val textExtensions = setOf(
        "txt", "log", "json", "yml", "yaml", "toml", "properties", "conf", "cfg",
        "ini", "md", "cfg", "lang", "mcfunction", "snbt", "csv", "sh"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_manager)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        locationPage = findViewById(R.id.locationPage)
        filesPage = findViewById(R.id.filesPage)
        settingsPage = findViewById(R.id.settingsPage)
        serverPathText = findViewById(R.id.serverPathText)
        storageStatusText = findViewById(R.id.storageStatusText)
        grantAccessButton = findViewById(R.id.grantAccessButton)
        currentPathText = findViewById(R.id.currentPathText)
        pasteButton = findViewById(R.id.pasteButton)
        ramLimitInput = findViewById(R.id.ramLimitInput)
        prelaunchInput = findViewById(R.id.prelaunchInput)
        wrapperInput = findViewById(R.id.wrapperInput)
        argsInput = findViewById(R.id.argsInput)

        val locationTab = findViewById<Button>(R.id.locationTabButton)
        val filesTab = findViewById<Button>(R.id.filesTabButton)
        val settingsTab = findViewById<Button>(R.id.settingsTabButton)
        locationTab.setOnClickListener { showTab(locationTab, filesTab, settingsTab, 0) }
        filesTab.setOnClickListener { showTab(locationTab, filesTab, settingsTab, 1) }
        settingsTab.setOnClickListener { showTab(locationTab, filesTab, settingsTab, 2) }

        grantAccessButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 30) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
            } else {
                // "All files access" does not exist before API 30; ask the runtime permission instead.
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE)
            }
        }

        findViewById<Button>(R.id.pumpkinFolderButton).setOnClickListener {
            val dir = File("/storage/emulated/0/PumpkinLauncher")
            ServerLocation.setServerDir(this, dir)
            refreshLocation()
            Toast.makeText(this, "Server folder set", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.browseFolderButton).setOnClickListener { filesTab.performClick() }
        findViewById<Button>(R.id.resetFolderButton).setOnClickListener {
            ServerLocation.resetToDefault(this)
            refreshLocation()
            Toast.makeText(this, "Reset to default", Toast.LENGTH_SHORT).show()
        }

        fileRecyclerView = findViewById(R.id.fileRecyclerView)
        fileRecyclerView.layoutManager = LinearLayoutManager(this)
        fileRecyclerView.adapter = adapter

        findViewById<Button>(R.id.upButton).setOnClickListener {
            browseDir.parentFile?.let { navigate(it) }
        }
        findViewById<Button>(R.id.newFolderButton).setOnClickListener { promptCreate(isFolder = true) }
        findViewById<Button>(R.id.newFileButton).setOnClickListener { promptCreate(isFolder = false) }
        pasteButton.setOnClickListener { pasteInto(browseDir) }
        findViewById<Button>(R.id.setServerFolderButton).setOnClickListener {
            ServerLocation.setServerDir(this, browseDir)
            refreshLocation()
            Toast.makeText(this, "Server folder set:\n${browseDir.path}", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.saveSettingsButton).setOnClickListener {
            ServerSettings.save(this, LaunchSettings(
                ramLimitMb = ramLimitInput.text.toString().toIntOrNull() ?: 0,
                prelaunchCommand = prelaunchInput.text.toString(),
                wrapperCommand = wrapperInput.text.toString(),
                argsCommand = argsInput.text.toString()
            ))
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }

        browseDir = ServerLocation.getServerDir(this)
        refreshLocation()
        loadSettings()
        navigate(browseDir)
    }

    override fun onResume() {
        super.onResume()
        refreshLocation()
    }

    private fun showTab(location: Button, files: Button, settings: Button, index: Int) {
        locationPage.visibility = if (index == 0) View.VISIBLE else View.GONE
        filesPage.visibility = if (index == 1) View.VISIBLE else View.GONE
        settingsPage.visibility = if (index == 2) View.VISIBLE else View.GONE
        // Alpha marks the active tab; colors stay per-tab.
        location.alpha = if (index == 0) 1f else 0.55f
        files.alpha = if (index == 1) 1f else 0.55f
        settings.alpha = if (index == 2) 1f else 0.55f
    }

    /** API 30+ has "All files access"; older releases use WRITE_EXTERNAL_STORAGE. */
    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun refreshLocation() {
        val dir = ServerLocation.getServerDir(this)
        serverPathText.text = if (ServerLocation.isCustom(this)) dir.path else "${dir.path} (default)"
        val granted = hasStorageAccess()
        storageStatusText.text = if (granted) {
            "Storage access: granted"
        } else {
            "Storage access: missing — the server folder must be readable by the engine."
        }
        grantAccessButton.visibility = if (granted) View.GONE else View.VISIBLE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE) refreshLocation()
    }

    private fun loadSettings() {
        val settings = ServerSettings.load(this)
        ramLimitInput.setText(settings.ramLimitMb.takeIf { it > 0 }?.toString() ?: "")
        prelaunchInput.setText(settings.prelaunchCommand)
        wrapperInput.setText(settings.wrapperCommand)
        argsInput.setText(settings.argsCommand)
    }

    private fun navigate(dir: File) {
        if (!dir.isDirectory || !dir.canRead()) {
            Toast.makeText(this, "Cannot read ${dir.path}", Toast.LENGTH_SHORT).show()
            return
        }
        browseDir = dir
        currentPathText.text = dir.path
        adapter.refresh()
    }

    private fun promptCreate(isFolder: Boolean) {
        val input = EditText(this)
        input.hint = if (isFolder) "Folder name" else "File name"
        AlertDialog.Builder(this)
            .setTitle(if (isFolder) "New Folder" else "New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val target = File(browseDir, name)
                try {
                    if (target.exists()) {
                        Toast.makeText(this, "Already exists", Toast.LENGTH_SHORT).show()
                    } else if (isFolder) {
                        target.mkdirs()
                    } else {
                        target.createNewFile()
                    }
                    adapter.refresh()
                } catch (e: Exception) {
                    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pasteInto(target: File) {
        val source = clipboardSource ?: return
        try {
            if (clipboardCut) {
                if (!renameToWithFallback(source, File(target, source.name))) {
                    throw java.io.IOException("Move failed")
                }
                clipboardSource = null
                clipboardCut = false
            } else {
                copyRecursively(source, File(target, source.name))
            }
            pasteButton.visibility = View.INVISIBLE
            adapter.refresh()
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFileActions(file: File) {
        val items = arrayOf("Rename", "Copy", "Cut", "Delete")
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> promptRename(file)
                    1 -> { clipboardSource = file; clipboardCut = false; pasteButton.visibility = View.VISIBLE }
                    2 -> { clipboardSource = file; clipboardCut = true; pasteButton.visibility = View.VISIBLE }
                    3 -> confirmDelete(file)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptRename(file: File) {
        val input = EditText(this)
        input.setText(file.name)
        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                val dest = File(file.parentFile, newName)
                if (!renameToWithFallback(file, dest)) {
                    Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show()
                }
                adapter.refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Permanently delete \"${file.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    deleteRecursively(file)
                    adapter.refresh()
                } catch (e: Exception) {
                    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openFile(file: File) {
        val ext = file.extension.lowercase()
        if (file.length() > 2 * 1024 * 1024) {
            Toast.makeText(this, "File too large to edit", Toast.LENGTH_SHORT).show()
            return
        }
        if (ext in textExtensions || ext.isEmpty()) {
            startActivity(Intent(this, TextEditorActivity::class.java).apply {
                putExtra(TextEditorActivity.EXTRA_PATH, file.absolutePath)
            })
        } else {
            showFileActions(file)
        }
    }

    companion object {
        private const val REQUEST_STORAGE = 1001

        fun renameToWithFallback(source: File, dest: File): Boolean =
            source.renameTo(dest) || run {
                // renameTo fails across mount points (e.g. app-private to shared storage).
                copyRecursively(source, dest)
                deleteRecursively(source)
                true
            }

        fun copyRecursively(source: File, dest: File) {
            if (source.isDirectory) {
                dest.mkdirs()
                source.listFiles()?.forEach { copyRecursively(it, File(dest, it.name)) }
            } else {
                dest.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

        fun deleteRecursively(file: File) {
            if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
            if (!file.delete()) throw java.io.IOException("Could not delete ${file.name}")
        }
    }

    inner class FileAdapter : RecyclerView.Adapter<FileAdapter.ViewHolder>() {
        private var entries: List<File> = emptyList()

        fun refresh() {
            entries = browseDir.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconText: TextView = view.findViewById(R.id.fileIconText)
            val nameText: TextView = view.findViewById(R.id.fileNameText)
            val sizeText: TextView = view.findViewById(R.id.fileSizeText)
            val actionButton: Button = view.findViewById(R.id.fileActionButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = entries[position]
            holder.iconText.text = if (file.isDirectory) "📁" else "📄"
            holder.nameText.text = file.name
            holder.sizeText.text = if (file.isDirectory) {
                "${file.listFiles()?.size ?: 0} items"
            } else {
                formatSize(file.length())
            }

            holder.itemView.setOnClickListener {
                if (file.isDirectory) navigate(file) else openFile(file)
            }
            holder.actionButton.setOnClickListener { showFileActions(file) }
        }

        override fun getItemCount() = entries.size

        private fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024L * 1024 * 1024)} GB"
        }
    }
}
