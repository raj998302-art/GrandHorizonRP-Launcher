package com.grandhorizonrp.launcher

import android.content.Context
import android.content.Intent

/**
 * Finds and launches the installed game client, passing the Grand Horizon RP
 * server address via intent extras (with a copyable fallback in the UI).
 */
object GameLauncherHelper {

    fun findInstalledGame(context: Context, packages: List<String>): String? {
        val pm = context.packageManager
        return packages.firstOrNull { pkg ->
            runCatching { pm.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
        }
    }

    fun launch(context: Context, config: LauncherConfig): Boolean {
        val pm = context.packageManager
        val pkg = findInstalledGame(context, config.gamePackages) ?: return false
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.putExtra("server_ip", config.serverIp)
        intent.putExtra("server_port", config.serverPort)
        intent.putExtra("samp_ip", config.serverIp)
        intent.putExtra("samp_port", config.serverPort)
        intent.putExtra("hostname", config.serverName)
        intent.putExtra("connect", "${config.serverIp}:${config.serverPort}")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
