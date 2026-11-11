package com.f7.downloadmanager.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager

/**
 * Reads the few device facts the queue needs to behave politely: is there a network, is it
 * metered, and is the phone charging. Also notifies the engine when connectivity changes so
 * transfers can pause and pick up again instead of failing.
 */
class Constraints(private val ctx: Context) {

    private val connectivity = ctx.getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean {
        val manager = connectivity ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isMetered(): Boolean {
        val manager = connectivity ?: return false
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun isCharging(): Boolean {
        val intent = runCatching {
            ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    /** Registers a default-network callback. Safe to call once per process. */
    fun watch(onChange: (online: Boolean, metered: Boolean) -> Unit) {
        val manager = connectivity ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onChange(isOnline(), isMetered())
                override fun onLost(network: Network) = onChange(isOnline(), isMetered())
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    onChange(isOnline(), isMetered())
            })
        }
    }
}
