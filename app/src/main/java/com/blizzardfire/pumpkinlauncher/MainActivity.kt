package com.blizzardfire.pumpkinlauncher

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.io.RandomAccessFile

class MainActivity : AppCompatActivity() {

    private var pumpkinService: PumpkinService? = null
    private var isBound = false
    private var isServerRunning = false
    private var playerCount = 0

    private lateinit var statusText: TextView
    private lateinit var startStopButton: Button
    private lateinit var consoleText: TextView
    private lateinit var consoleScroll: ScrollView
    private lateinit var commandInput: EditText
    private lateinit var sendCommandButton: Button
    private lateinit var uptimeText: TextView
    private lateinit var menuButton: Button
    private lateinit var playersCountText: TextView
    private lateinit var ramText: TextView
    private lateinit var cpuText: TextView
    private lateinit var netText: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerStatusDot: View
    private lateinit var drawerStatusText: TextView

    private var lastCpuTicks = -1L
    private var lastCpuSampleAt = 0L
    private var lastNetRx = -1L
    private var lastNetTx = -1L
    private var lastNetSampleAt = 0L
    private var autoScrollPaused = false
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = 0

    private val uptimeHandler = Handler(Looper.getMainLooper())
    private val uptimeRunnable = object : Runnable {
        override fun run() {
            updateUptime()
            updateStats()
            syncStatusToService()
            uptimeHandler.postDelayed(this, 1000)
        }
    }

    private fun syncStatusToService() {
        val actuallyRunning = pumpkinService?.isRunning == true
        if (actuallyRunning != isServerRunning) {
            isServerRunning = actuallyRunning
            if (isServerRunning) {
                startStopButton.text = "Stop"
                statusText.text = "Status: Running"
            } else {
                startStopButton.text = "Start"
                statusText.text = "Status: Stopped"
            }
        }
        drawerStatusDot.setBackgroundResource(
            if (isServerRunning) R.drawable.dot_square_green else R.drawable.dot_square_red
        )
        drawerStatusText.text = if (isServerRunning) "Running" else "Stopped"
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val line = intent?.getStringExtra("line") ?: return
            appendToConsole(line)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PumpkinService.LocalBinder
            pumpkinService = binder.getService()
            isBound = true

            isServerRunning = pumpkinService?.isRunning == true
            if (isServerRunning) {
                startStopButton.text = "Stop"
                statusText.text = "Status: Running"
            } else {
                startStopButton.text = "Start"
                statusText.text = "Status: Stopped"
            }

            uptimeHandler.post(uptimeRunnable)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            pumpkinService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        // Pad by system bars AND the keyboard so the command input is never covered.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(systemBars.bottom, ime.bottom)
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottom)
            insets
        }

        statusText = findViewById(R.id.statusText)
        startStopButton = findViewById(R.id.startStopButton)
        consoleText = findViewById(R.id.consoleText)
        consoleScroll = findViewById(R.id.consoleScroll)
        commandInput = findViewById(R.id.commandInput)
        sendCommandButton = findViewById(R.id.sendCommandButton)
        uptimeText = findViewById(R.id.uptimeText)
        menuButton = findViewById(R.id.menuButton)
        playersCountText = findViewById(R.id.playersCountText)
        ramText = findViewById(R.id.ramText)
        cpuText = findViewById(R.id.cpuText)
        netText = findViewById(R.id.netText)
        drawerLayout = findViewById(R.id.drawerLayout)
        drawerStatusDot = findViewById(R.id.drawerStatusDot)
        drawerStatusText = findViewById(R.id.drawerStatusText)

        startStopButton.setOnClickListener {
            if (isServerRunning) {
                stopServer()
            } else {
                startServer()
            }
        }

        sendCommandButton.setOnClickListener {
            sendCommand()
        }

        commandInput.setOnEditorActionListener { _, _, _ ->
            sendCommand()
            true
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            pumpkinService?.sendCommand("save-all")
        }
        findViewById<Button>(R.id.restartButton).setOnClickListener {
            pumpkinService?.sendCommand("stop")
            appendToConsole("Restarting server...")
            Handler(Looper.getMainLooper()).postDelayed({ startServer() }, 3000)
        }
        findViewById<Button>(R.id.backupButton).setOnClickListener {
            pumpkinService?.backupWorld()
        }

        menuButton.setOnClickListener { drawerLayout.openDrawer(androidx.core.view.GravityCompat.END) }

        val pauseButton = findViewById<Button>(R.id.pauseScrollButton)
        pauseButton.setOnClickListener {
            autoScrollPaused = !autoScrollPaused
            pauseButton.text = if (autoScrollPaused) "Resume" else "Pause"
            if (!autoScrollPaused) consoleScroll.post { consoleScroll.fullScroll(android.view.View.FOCUS_DOWN) }
        }
        findViewById<Button>(R.id.copyLogButton).setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("console", consoleText.text))
            appendToConsole("[launcher] Console copied to clipboard.")
        }
        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            consoleText.text = ""
        }

        // Long-press the input to pick a previous command.
        commandInput.setOnLongClickListener {
            if (commandHistory.isEmpty()) return@setOnLongClickListener false
            AlertDialog.Builder(this)
                .setTitle("Command history")
                .setItems(commandHistory.toTypedArray()) { _, which ->
                    commandInput.setText(commandHistory[which])
                    historyIndex = which
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
        commandInput.setOnKeyListener { _, keyCode, event ->
            // Up/Down arrows walk history when the caret is at the start/end.
            if (event.action != android.view.KeyEvent.ACTION_DOWN ||
                keyCode != android.view.KeyEvent.KEYCODE_DPAD_UP && keyCode != android.view.KeyEvent.KEYCODE_DPAD_DOWN
            ) return@setOnKeyListener false
            if (commandInput.selectionStart != 0 && keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) return@setOnKeyListener false
            if (commandHistory.isEmpty()) return@setOnKeyListener false
            historyIndex = if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                (historyIndex - 1).coerceAtLeast(0)
            } else {
                (historyIndex + 1).coerceAtMost(commandHistory.size)
            }
            commandInput.setText(commandHistory.getOrNull(historyIndex) ?: "")
            commandInput.setSelection(commandInput.text.length)
            true
        }

        findViewById<TextView>(R.id.navConsole).setOnClickListener { drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END) }
        findViewById<TextView>(R.id.navServerManager).setOnClickListener {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
            startActivity(Intent(this, ServerManagerActivity::class.java))
        }
        findViewById<TextView>(R.id.navEngine).setOnClickListener {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
            startActivity(Intent(this, EngineManagerActivity::class.java))
        }
        findViewById<TextView>(R.id.navPlugins).setOnClickListener {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
            startActivity(Intent(this, PluginsActivity::class.java))
        }
    }

    private fun startServer() {
        if (EngineModuleManager.activeLibrary(this) == null) {
            AlertDialog.Builder(this)
                .setTitle("Pumpkin engine required")
                .setMessage("Install a Pumpkin engine module before starting the server.")
                .setPositiveButton("Open Engine Manager") { _, _ ->
                    startActivity(Intent(this, EngineManagerActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val serviceIntent = Intent(this, PumpkinService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        isServerRunning = true
        startStopButton.text = "Stop"
        statusText.text = "Status: Running"
        playerCount = 0
        playersCountText.text = "0"
        appendToConsole("Server starting...")
    }

    private fun stopServer() {
        pumpkinService?.stopServer()

        isServerRunning = false
        startStopButton.text = "Start"
        statusText.text = "Status: Stopped"
        playerCount = 0
        playersCountText.text = "0"
        appendToConsole("Server stopping...")
    }

    private fun sendCommand() {
        val command = commandInput.text.toString().trim()
        if (command.isNotEmpty() && isBound) {
            pumpkinService?.sendCommand(command)
            if (commandHistory.lastOrNull() != command) commandHistory.add(command)
            historyIndex = commandHistory.size
            commandInput.text.clear()
        }
    }

    private fun appendToConsole(line: String) {
        val spannable = android.text.SpannableString(line)

        val infoColor = android.graphics.Color.parseColor("#65C362")
        val warnColor = android.graphics.Color.parseColor("#F4D95D")
        val errorColor = android.graphics.Color.parseColor("#E3585C")
        val grayColor = android.graphics.Color.parseColor("#888888")

        when {
            line.contains("ERROR") -> colorWord(spannable, "ERROR", errorColor)
            line.contains("WARN") -> colorWord(spannable, "WARN", warnColor)
            line.contains("INFO") -> colorWord(spannable, "INFO", infoColor)
        }

        val urlRegex = Regex("""https?://\S+""")
        urlRegex.findAll(line).forEach { match ->
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#556CD9")),
                match.range.first,
                match.range.last + 1,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (line.contains("Pumpkin")) {
            colorWord(spannable, "Pumpkin", android.graphics.Color.parseColor("#E57540"))
        }

        val timestampRegex = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")
        timestampRegex.find(line)?.let { match ->
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(grayColor),
                match.range.first,
                match.range.last + 1,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        when {
            line.contains("joined the game") -> {
                playerCount++
                playersCountText.text = playerCount.toString()
            }
            line.contains("left the game") -> {
                playerCount = (playerCount - 1).coerceAtLeast(0)
                playersCountText.text = playerCount.toString()
            }
        }

        consoleText.append(spannable)
        consoleText.append("\n")

        if (!autoScrollPaused) {
            consoleScroll.post {
                consoleScroll.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    private fun colorWord(spannable: android.text.SpannableString, word: String, color: Int) {
        val start = spannable.indexOf(word)
        if (start >= 0) {
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(color),
                start,
                start + word.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** RSS of this process — the engine runs in-process, so this is the server's memory. */
    private fun readRssKb(): Long {
        return try {
            RandomAccessFile("/proc/self/statm", "r").use { reader ->
                val fields = reader.readLine().trim().split(Regex("\\s+"))
                val pageSize = 4096L
                fields.getOrNull(1)?.toLongOrNull()?.times(pageSize)?.div(1024) ?: -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun readCpuTicks(): Long {
        return try {
            val stat = File("/proc/self/stat").readText()
            // Fields 14 and 15 (utime, stime) after the parenthesised comm field.
            val afterComm = stat.substringAfterLast(')')
                .trim()
                .split(Regex("\\s+"))
            (afterComm.getOrNull(11)?.toLongOrNull() ?: 0L) +
                (afterComm.getOrNull(12)?.toLongOrNull() ?: 0L)
        } catch (_: Exception) {
            -1L
        }
    }

    private fun updateStats() {
        if (!isServerRunning) {
            ramText.text = "—"
            cpuText.text = "—"
            netText.text = "—"
            lastCpuTicks = -1L
            lastNetRx = -1L
            lastNetTx = -1L
            lastNetSampleAt = 0L
            return
        }

        val rssKb = readRssKb()
        ramText.text = if (rssKb < 0) "—" else "${rssKb / 1024} MB"

        val now = android.os.SystemClock.elapsedRealtime()
        val ticks = readCpuTicks()
        if (ticks >= 0 && lastCpuTicks >= 0 && now > lastCpuSampleAt) {
            val deltaTicks = ticks - lastCpuTicks
            val deltaMs = now - lastCpuSampleAt
            // /proc stat ticks are 100 per second; spread across cores for a 0-100% figure.
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val percent = deltaTicks * 10000.0 / (100.0 * deltaMs) / cores
            cpuText.text = "%.0f%%".format(percent)
        } else if (ticks < 0) {
            cpuText.text = "—"
        }
        if (ticks >= 0) {
            lastCpuTicks = ticks
            lastCpuSampleAt = now
        }

        val rx = android.net.TrafficStats.getTotalRxBytes()
        val tx = android.net.TrafficStats.getTotalTxBytes()
        if (rx >= 0 && tx >= 0 && lastNetRx >= 0 && lastNetSampleAt > 0 && now > lastNetSampleAt) {
            val deltaMs = (now - lastNetSampleAt).coerceAtLeast(1L)
            val downPerSec = (rx - lastNetRx).coerceAtLeast(0L) * 1000 / deltaMs
            val upPerSec = (tx - lastNetTx).coerceAtLeast(0L) * 1000 / deltaMs
            netText.text = "↓${formatBytes(downPerSec)}/s ↑${formatBytes(upPerSec)}/s"
        } else if (rx >= 0 && tx >= 0) {
            netText.text = "…"
        } else {
            netText.text = "—"
        }
        if (rx >= 0 && tx >= 0) {
            lastNetRx = rx
            lastNetTx = tx
            lastNetSampleAt = now
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        do {
            value /= 1024.0
            unit++
        } while (value >= 1024.0 && unit < units.lastIndex)
        return "%.1f %s".format(value, units[unit])
    }

    private fun updateUptime() {
        val startTime = pumpkinService?.serverStartTime ?: 0
        if (startTime == 0L || pumpkinService?.isRunning != true) {
            uptimeText.text = "00:00:00"
            return
        }
        val elapsedMillis = System.currentTimeMillis() - startTime
        val days = elapsedMillis / 86400000
        val hours = (elapsedMillis / 3600000) % 24
        val minutes = (elapsedMillis / 60000) % 60
        val seconds = (elapsedMillis / 1000) % 60

        uptimeText.text = if (days > 0) {
            String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds)
        } else {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.blizzardfire.pumpkinlauncher.LOG_UPDATE")
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, filter)

        val serviceIntent = Intent(this, PumpkinService::class.java)
        bindService(serviceIntent, connection, 0)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
        uptimeHandler.removeCallbacks(uptimeRunnable)
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}