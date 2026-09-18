package com.grandhorizonrp.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    enum class UiState { LOADING, READY, DOWNLOADING, INSTALLED, ERROR }

    data class UiModel(
        val state: UiState = UiState.LOADING,
        val config: LauncherConfig? = null,
        val installed: Boolean = false,
        val installedSize: Long = 0L,
        val stage: GameDataInstaller.Stage? = null,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val speedBps: Long = 0L,
        val error: String? = null,
        val serverOnline: Boolean? = null,
        val players: Int = 0,
        val maxPlayers: Int = 50
    )

    private val _ui = MutableStateFlow(UiModel())
    val ui: StateFlow<UiModel> = _ui.asStateFlow()

    private var installer: GameDataInstaller? = null
    private var installJob: Job? = null

    init {
        viewModelScope.launch {
            val config = RemoteConfig.fetch()
            val inst = GameDataInstaller(app)
            installer = inst
            _ui.update {
                it.copy(
                    config = config,
                    totalBytes = config.gameData.totalBytes,
                    installed = inst.isInstalled(config.gameData.version),
                    installedSize = inst.usedSpaceBytes()
                )
            }
            refreshState()
            val info = ServerQuery.query(config.serverIp, config.serverPort)
            _ui.update {
                it.copy(
                    serverOnline = info != null,
                    players = info?.players ?: 0,
                    maxPlayers = info?.maxPlayers ?: 50
                )
            }
        }
    }

    private fun refreshState() {
        val cfg = _ui.value.config ?: return
        val inst = installer ?: return
        _ui.update {
            it.copy(
                installed = inst.isInstalled(cfg.gameData.version),
                installedSize = inst.usedSpaceBytes(),
                state = when {
                    it.state == UiState.DOWNLOADING -> UiState.DOWNLOADING
                    it.state == UiState.ERROR -> UiState.ERROR
                    inst.isInstalled(cfg.gameData.version) -> UiState.INSTALLED
                    else -> UiState.READY
                }
            )
        }
    }

    fun startInstall() {
        val cfg = _ui.value.config ?: return
        val inst = installer ?: return
        if (installJob?.isActive == true) return

        _ui.update {
            it.copy(
                state = UiState.DOWNLOADING,
                error = null,
                downloadedBytes = 0L,
                totalBytes = cfg.gameData.totalBytes,
                stage = GameDataInstaller.Stage.PREPARING
            )
        }
        installJob = viewModelScope.launch {
            val listener = object : GameDataInstaller.Listener {
                override fun onStage(stage: GameDataInstaller.Stage) {
                    _ui.update { it.copy(stage = stage) }
                }

                override fun onProgress(downloadedBytes: Long, totalBytes: Long, speedBps: Long) {
                    _ui.update {
                        it.copy(
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            speedBps = speedBps
                        )
                    }
                }
            }
            inst.install(cfg, listener).fold(
                onSuccess = {
                    _ui.update {
                        it.copy(
                            state = UiState.INSTALLED,
                            installed = true,
                            installedSize = inst.usedSpaceBytes(),
                            error = null,
                            downloadedBytes = it.totalBytes,
                            speedBps = 0L,
                            stage = GameDataInstaller.Stage.DONE
                        )
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            state = if (e is GameDataInstaller.InstallCancelledException) {
                                UiState.READY
                            } else {
                                UiState.ERROR
                            },
                            error = if (e is GameDataInstaller.InstallCancelledException) {
                                null
                            } else {
                                e.message ?: "Installation failed"
                            },
                            speedBps = 0L
                        )
                    }
                }
            )
        }
    }

    fun cancelInstall() {
        installer?.cancel()
        installJob?.cancel()
        _ui.update { it.copy(state = UiState.READY, speedBps = 0L) }
    }
}
