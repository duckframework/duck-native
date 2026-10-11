package com.duckframework.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Reports changes in internet connectivity.
 *
 * Only changes are reported, not the initial state, and repeated callbacks for
 * the same state are ignored. Start it in `onStart` and stop it in `onStop`.
 *
 * @param context Used to reach the connectivity service.
 * @param onChanged Called with true when online and false when offline.
 */
class NetworkMonitor(
    context: Context,
    private val onChanged: (online: Boolean) -> Unit,
) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private var started = false
    private var lastOnline: Boolean? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()

        override fun onLost(network: Network) = publish()

        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities,
        ) = publish()
    }

    /**
     * Starts listening. Does nothing when already started.
     */
    @Synchronized
    fun start() {
        if (started) return
        started = true

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, callback)

        // Catch anything that changed while stopped
        publish()
    }

    /**
     * Stops listening. Does nothing when not started.
     */
    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        manager.unregisterNetworkCallback(callback)
    }

    @Synchronized
    private fun publish() {
        val online = isOnline()
        val previous = lastOnline
        lastOnline = online

        // The first reading is only a baseline
        if (previous != null && previous != online) {
            onChanged(online)
        }
    }

    /**
     * Returns true when the device currently has validated internet access.
     */
    fun isOnline(): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        val hasInternet =
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated =
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return hasInternet && validated
    }
}
