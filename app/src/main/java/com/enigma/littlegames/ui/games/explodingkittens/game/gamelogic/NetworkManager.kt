package com.enigma.littlegames.ui.games.explodingkittens.game.gamelogic

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

const val EK_SERVER_PORT = 8989

class NetworkManager(private val scope: CoroutineScope) {

    private var serverSocket: ServerSocket? = null
    private val clients = ConcurrentHashMap<Socket, PrintWriter>()
    private var clientSocket: Socket? = null
    private var clientWriter: PrintWriter? = null

    var onStateReceived: ((GameStateUpdate) -> Unit)? = null
    var onActionReceived: ((GameAction) -> Unit)? = null
    var onClientConnected: ((String) -> Unit)? = null
    var onClientDisconnected: ((String) -> Unit)? = null
    var onHostDisconnected: (() -> Unit)? = null

    fun startHost() {
        if (serverSocket != null) return
        scope.launch(Dispatchers.IO) {
            serverSocket = ServerSocket(EK_SERVER_PORT)
            while (isActive) {
                try {
                    val socket = serverSocket!!.accept()
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val ip = socket.inetAddress.hostAddress ?: "Unknown"
                    clients[socket] = writer
                    withContext(Dispatchers.Main) { onClientConnected?.invoke(ip) }
                    listenToClient(socket)
                } catch (_: Exception) { break }
            }
        }
    }

    private fun listenToClient(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            val ip = socket.inetAddress.hostAddress ?: "Unknown"
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive) {
                    val msg = reader.readLine() ?: break
                    val action = Json.decodeFromString<GameAction>(msg)
                    withContext(Dispatchers.Main) { onActionReceived?.invoke(action) }
                }
            } catch (_: Exception) {
            } finally {
                clients.remove(socket)
                withContext(Dispatchers.Main) { onClientDisconnected?.invoke(ip) }
                socket.close()
            }
        }
    }

    fun broadcastStateToClients(update: GameStateUpdate) {
        val json = Json.encodeToString(update)
        clients.values.forEach { writer ->
            scope.launch(Dispatchers.IO) { writer.println(json) }
        }
    }

    fun connectToHost(hostIp: String) {
        if (clientSocket != null) return
        scope.launch(Dispatchers.IO) {
            try {
                clientSocket = Socket(hostIp, EK_SERVER_PORT)
                clientWriter = PrintWriter(clientSocket!!.getOutputStream(), true)
                withContext(Dispatchers.Main) { onClientConnected?.invoke(hostIp) }
                listenForStateUpdates()
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onHostDisconnected?.invoke() }
            }
        }
    }

    private fun listenForStateUpdates() {
        scope.launch(Dispatchers.IO) {
            try {
                val socket = clientSocket ?: return@launch
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive) {
                    val msg = reader.readLine() ?: break
                    val state = Json.decodeFromString<GameStateUpdate>(msg)
                    withContext(Dispatchers.Main) { onStateReceived?.invoke(state) }
                }
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) { onHostDisconnected?.invoke() }
            }
        }
    }

    fun sendActionToHost(action: GameAction) {
        clientWriter?.let { writer ->
            scope.launch(Dispatchers.IO) { writer.println(Json.encodeToString(action)) }
        }
    }

    fun getLocalIPAddress(context: Context): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            InetAddress.getByAddress(byteArrayOf(
                (ip and 0xFF).toByte(), (ip shr 8 and 0xFF).toByte(),
                (ip shr 16 and 0xFF).toByte(), (ip shr 24 and 0xFF).toByte()
            )).hostAddress ?: "Unavailable"
        } catch (_: Exception) { "Unavailable" }
    }

    fun disconnect() {
        try {
            serverSocket?.close(); serverSocket = null
            clients.keys.forEach { it.close() }; clients.clear()
            clientSocket?.close(); clientSocket = null; clientWriter = null
        } catch (_: Exception) {}
    }
}
