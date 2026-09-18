package com.grandhorizonrp.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grandhorizonrp.launcher.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupVideoBackground()
        setupButtons()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        runCatching { binding.videoBg.start() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { binding.videoBg.pause() }
    }

    private fun setupVideoBackground() {
        binding.videoBg.setOnErrorListener { _, _, _ -> true }
        runCatching {
            val uri = Uri.parse("android.resource://$packageName/${R.raw.launcher_video}")
            binding.videoBg.setVideoURI(uri)
            binding.videoBg.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                binding.imgPoster.isVisible = false
            }
            binding.videoBg.start()
        }
    }

    private fun setupButtons() {
        binding.btnPlay.setOnClickListener { onPlayClicked() }
        binding.btnAction.setOnClickListener {
            if (binding.btnAction.tag == TAG_CANCEL) {
                viewModel.cancelInstall()
            } else {
                viewModel.startInstall()
            }
        }
    }

    private fun onPlayClicked() {
        val ui = viewModel.ui.value
        val config = ui.config ?: return
        if (ui.state == MainViewModel.UiState.DOWNLOADING) return
        if (!ui.installed) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(
                    "Game data is not installed yet.\n\n" +
                        "Tap DOWNLOAD GAME DATA (~${formatBytes(ui.totalBytes)}) and keep the " +
                        "launcher open until it finishes. Connect to Wi-Fi and a charger " +
                        "for the best experience."
                )
                .setPositiveButton("Download now") { _, _ -> viewModel.startInstall() }
                .setNegativeButton("Later", null)
                .show()
            return
        }
        if (!GameLauncherHelper.launch(this, config)) {
            showGameClientDialog(config)
        }
    }

    private fun showGameClientDialog(config: LauncherConfig) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Game client not found")
            .setMessage(
                "The Grand Horizon game client is not installed on this device.\n\n" +
                    "Install the game client APK, then come back and press PLAY.\n\n" +
                    "Server: ${config.serverIp}:${config.serverPort}"
            )
            .setPositiveButton("Copy server address") { _, _ ->
                copyText("${config.serverIp}:${config.serverPort}")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("GHRP server", text))
        android.widget.Toast.makeText(this, "Copied: $text", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ui.collect { render(it) }
            }
        }
    }

    private fun render(m: MainViewModel.UiModel) {
        // ---- server card ----
        binding.serverNameText.text = m.config?.serverName ?: getString(R.string.app_name)
        binding.serverAddrText.text = m.config?.let { "${it.serverIp}:${it.serverPort}" } ?: "…"
        binding.playersText.text = when (m.serverOnline) {
            null -> getString(R.string.status_checking)
            true -> getString(R.string.status_online_fmt, m.players, m.maxPlayers)
            false -> getString(R.string.status_offline)
        }
        val dotColor = when (m.serverOnline) {
            true -> R.color.success
            false -> R.color.error
            null -> R.color.text_secondary
        }
        binding.statusDot.background?.setTint(ContextCompat.getColor(this, dotColor))

        // ---- data card ----
        binding.dataStatusText.text = when {
            m.state == MainViewModel.UiState.LOADING -> getString(R.string.data_checking)
            m.state == MainViewModel.UiState.ERROR ->
                getString(R.string.data_not_installed_fmt, formatBytes(m.totalBytes))
            m.installed && m.config != null -> getString(
                R.string.data_installed_fmt,
                m.config.gameData.version,
                formatBytes(m.installedSize)
            )
            else -> getString(
                R.string.data_not_installed_fmt,
                formatBytes(m.config?.gameData?.totalBytes ?: 0L)
            )
        }
        if (m.state == MainViewModel.UiState.DOWNLOADING && m.stage != null) {
            binding.dataStatusText.text = m.stage!!.message
        }
        binding.errorText.isVisible = m.error != null
        binding.errorText.text = m.error ?: ""

        // ---- progress ----
        val downloading = m.state == MainViewModel.UiState.DOWNLOADING
        binding.dataProgressBar.isVisible = downloading
        binding.progressRow.isVisible = downloading
        if (downloading) {
            val total = m.totalBytes.coerceAtLeast(1L)
            val progress = (m.downloadedBytes * 100.0 / total).roundToInt().coerceIn(0, 100)
            binding.dataProgressBar.progress = progress
            binding.progressStatsText.text = buildString {
                append(formatBytes(m.downloadedBytes))
                append(" / ")
                append(formatBytes(m.totalBytes))
                if (m.speedBps > 0) {
                    append("  •  ")
                    append(formatSpeed(m.speedBps))
                }
            }
            binding.progressPercentText.text = String.format(Locale.US, "%d%%", progress)
        }

        // ---- buttons ----
        binding.btnPlay.isEnabled = !downloading && m.config != null
        binding.btnAction.isEnabled = m.config != null
        binding.btnAction.apply {
            when {
                downloading -> {
                    tag = TAG_CANCEL
                    text = getString(R.string.btn_cancel)
                    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_close)
                }
                m.state == MainViewModel.UiState.ERROR -> {
                    tag = TAG_RETRY
                    text = getString(R.string.btn_retry)
                    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_retry)
                }
                m.installed -> {
                    tag = TAG_UPDATE
                    text = getString(R.string.btn_update)
                    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_update)
                }
                else -> {
                    tag = TAG_DOWNLOAD
                    text = getString(R.string.btn_download)
                    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_download)
                }
            }
        }

        // ---- news ----
        binding.newsText.text = m.config?.news?.takeIf { it.isNotBlank() }
            ?: getString(R.string.news_default)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "—"
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            mb >= 1024 -> String.format(Locale.US, "%.1f GB", mb / 1024.0)
            else -> String.format(Locale.US, "%.0f MB", mb)
        }
    }

    private fun formatSpeed(bps: Long): String {
        val kbs = bps / 1024.0
        return when {
            kbs >= 1024 -> String.format(Locale.US, "%.1f MB/s", kbs / 1024.0)
            else -> String.format(Locale.US, "%.0f KB/s", kbs)
        }
    }

    private companion object {
        const val TAG_CANCEL = "cancel"
        const val TAG_RETRY = "retry"
        const val TAG_UPDATE = "update"
        const val TAG_DOWNLOAD = "download"
    }
}
