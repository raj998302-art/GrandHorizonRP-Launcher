package com.grandhorizonrp.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GameDataPart(
    val name: String,
    val url: String,
    val size: Long,
    val sha256: String? = null
)

data class GameDataConfig(
    val version: String,
    val parts: List<GameDataPart>
) {
    val totalBytes: Long get() = parts.sumOf { it.size }
}

data class LauncherConfig(
    val serverName: String,
    val serverIp: String,
    val serverPort: Int,
    val news: String,
    val gameData: GameDataConfig,
    val gamePackages: List<String>
)

object RemoteConfig {

    private const val REMOTE_URL =
        "https://raw.githubusercontent.com/raj998302-art/GrandHorizonRP-Launcher/main/launcher-config.json"

    val FALLBACK: LauncherConfig = requireNotNull(parse(FALLBACK_JSON))

    suspend fun fetch(timeoutMs: Long = 6000L): LauncherConfig = withContext(Dispatchers.IO) {
        val json: String? = withTimeoutOrNull(timeoutMs) {
            runCatching {
                val conn = URL(REMOTE_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.instanceFollowRedirects = true
                try {
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        null
                    }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }
        json?.let { parse(it) } ?: FALLBACK
    }

    fun parse(json: String): LauncherConfig? = runCatching {
        val root = JSONObject(json)
        val game = root.getJSONObject("gameData")
        val partsArray = game.getJSONArray("parts")
        val parts = (0 until partsArray.length()).map { i ->
            val p = partsArray.getJSONObject(i)
            GameDataPart(
                name = p.getString("name"),
                url = p.getString("url"),
                size = p.optLong("size", 0L),
                sha256 = p.optString("sha256").takeIf { it.isNotBlank() }
            )
        }
        LauncherConfig(
            serverName = root.optString("serverName", "Grand Horizon RP"),
            serverIp = root.optString("serverIp", "142.132.203.47"),
            serverPort = root.optInt("serverPort", 14448),
            news = root.optString("news", ""),
            gameData = GameDataConfig(
                version = game.optString("version", "1.0.0"),
                parts = parts
            ),
            gamePackages = root.optJSONArray("gamePackages")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: listOf("com.rockstargames.gtasa")
        )
    }.getOrNull()

    private val FALLBACK_JSON = """
        {
          "serverName": "Grand Horizon RP",
          "serverIp": "142.132.203.47",
          "serverPort": 14448,
          "news": "Grand Horizon RP official launcher — Hindi community roleplay server.",
          "gameData": {
            "version": "1.0.0",
            "parts": [
              {"name": "gamedata-core.zip", "size": 16000000, "url": "https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/gamedata-core.zip"},
              {"name": "gamedata-audio.zip", "size": 133000000, "url": "https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/gamedata-audio.zip"},
              {"name": "gamedata-mesh.zip", "size": 1690000000, "url": "https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/gamedata-mesh.zip"},
              {"name": "gamedata-textures.zip", "size": 1620000000, "url": "https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/gamedata-textures.zip"},
              {"name": "gamedata-resources.zip", "size": 549000000, "url": "https://github.com/raj998302-art/GrandHorizonRP-Launcher/releases/download/latest/gamedata-resources.zip"}
            ]
          },
          "gamePackages": ["com.rockstargames.gtasa"]
        }
    """.trimIndent()
}
