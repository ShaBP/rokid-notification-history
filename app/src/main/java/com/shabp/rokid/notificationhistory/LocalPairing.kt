package com.shabp.rokid.notificationhistory

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** One-time loopback pairing. Pairing code is never stored or logged. */
internal class LocalPairing(private val service: AccessibilityService) {
    private val prefs = service.getSharedPreferences("local_pairing", Context.MODE_PRIVATE)
    private val endpoint = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}:(\\d{2,5})")
    private val code = Regex("(?<!\\d)\\d{6}(?!\\d)")

    fun observe(event: AccessibilityEvent?) {
        if (!prefs.getBoolean("active", false) || event?.packageName?.toString() != "com.android.settings") return
        if (SystemClock.elapsedRealtime() - prefs.getLong("started", 0L) > 180_000L) {
            finish("Pairing timed out")
            return
        }
        val roots = ArrayList<AccessibilityNodeInfo>()
        event.source?.let(roots::add)
        service.rootInActiveWindow?.let(roots::add)
        service.windows?.forEach { it.root?.let(roots::add) }
        val nodes = ArrayList<Pair<String, String>>()
        roots.forEach { collect(it, nodes, 0) }
        val all = nodes.joinToString(" ") { it.second }.lowercase()
        if (all.contains("wireless debugging") && !nodes.any { code.containsMatchIn(it.second) }) {
            val port = nodes.asSequence().mapNotNull { endpoint.find(it.second)?.groupValues?.get(1)?.toIntOrNull() }
                .firstOrNull { it in 1..65535 }
            if (port != null) prefs.edit().putInt("connect_port", port).apply()
        }
        val dialog = all.contains("pairing code") || all.contains("pair with device") ||
            nodes.any { it.first.endsWith(":id/pairing_code") }
        if (!dialog) return
        val pairCode = nodes.asSequence().filter { it.first.endsWith(":id/pairing_code") }
            .mapNotNull { code.find(it.second)?.value }.firstOrNull()
            ?: nodes.asSequence().mapNotNull { code.find(it.second)?.value }.firstOrNull()
            ?: return
        val pairPort = nodes.asSequence().filter { it.first.endsWith(":id/ip_addr") }
            .mapNotNull { endpoint.find(it.second)?.groupValues?.get(1)?.toIntOrNull() }.firstOrNull()
            ?: nodes.asSequence().mapNotNull { endpoint.find(it.second)?.groupValues?.get(1)?.toIntOrNull() }.firstOrNull()
            ?: return
        val connectPort = readWirelessPort().takeIf { it > 0 } ?: prefs.getInt("connect_port", 0)
        if (connectPort <= 0 || connectPort == pairPort || !running.compareAndSet(false, true)) return
        prefs.edit().putString("status", "Pairing locally…").apply()
        Thread({
            try { bootstrap(pairPort, pairCode, connectPort) }
            catch (t: Throwable) {
                Log.w("NotificationPairing", "Local pairing failed", t)
                finish("Pairing failed: " + (t.message ?: t.javaClass.simpleName).take(100))
            } finally { running.set(false) }
        }, "notification-local-pair").start()
    }

    private fun bootstrap(pairPort: Int, pairingCode: String, connectPort: Int) {
        synchronized(certLock) {
            if (!certReady) {
                val key = File(File(service.filesDir, "kadb"), "private.key")
                key.parentFile?.mkdirs()
                KadbCert.configure(
                    OkioFilePrivateKeyStore(Path::class.java.getMethod("get", String::class.java)
                        .invoke(null, key.absolutePath) as Path, FileSystem.SYSTEM),
                    KadbCertPolicy(), emptyList())
                certReady = true
            }
        }
        runBlocking { Kadb.pair("127.0.0.1", pairPort, pairingCode, "Notification History") }
        val adb = Kadb("127.0.0.1", connectPort, 5_000, 15_000)
        try {
            val result = adb.shell("pm grant com.shabp.rokid.notificationhistory android.permission.WRITE_SECURE_SETTINGS")
            if (result.exitCode != 0) throw IllegalStateException(result.allOutput.trim())
            if (!RecoveryController.hasGrant(service)) throw IllegalStateException("grant not visible to app")
            RecoveryController.setOptedIn(service, true)
            finish("Recovery ready; grant applied")
        } finally { adb.close() }
    }

    private fun finish(message: String) {
        prefs.edit().putBoolean("active", false).putString("status", message).apply()
    }

    private fun collect(node: AccessibilityNodeInfo, result: MutableList<Pair<String, String>>, depth: Int) {
        if (depth > 10 || result.size >= 180) return
        val id = node.viewIdResourceName.orEmpty()
        node.text?.toString()?.let { result.add(id to it) }
        node.contentDescription?.toString()?.let { result.add(id to it) }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, result, depth + 1) }
    }

    private fun readWirelessPort(): Int {
        val propertyPort = try {
        val cls = Class.forName("android.os.SystemProperties")
        (cls.getMethod("get", String::class.java, String::class.java)
            .invoke(null, "service.adb.tls.port", "") as String).toIntOrNull() ?: 0
        } catch (_: Exception) { 0 }
        if (propertyPort > 0) return propertyPort
        val request = Parcel.obtain()
        val response = Parcel.obtain()
        return try {
            val manager = Class.forName("android.os.ServiceManager")
            val binder = manager.getMethod("getService", String::class.java)
                .invoke(null, "adb") as? IBinder ?: return 0
            request.writeInterfaceToken("android.debug.IAdbManager")
            if (!binder.transact(10, request, response, 0)) return 0
            response.readException()
            response.readInt().takeIf { it > 0 } ?: 0
        } catch (_: Exception) { 0 }
        finally { response.recycle(); request.recycle() }
    }

    companion object {
        private val running = AtomicBoolean(false)
        private val certLock = Any()
        private var certReady = false

        @JvmStatic fun begin(context: Context) {
            context.getSharedPreferences("local_pairing", Context.MODE_PRIVATE).edit()
                .putBoolean("active", true)
                .putLong("started", SystemClock.elapsedRealtime())
                .putInt("connect_port", 0)
                .putString("status", "Open the pairing-code dialog in Wireless debugging")
                .apply()
        }

        @JvmStatic fun status(context: Context): String =
            context.getSharedPreferences("local_pairing", Context.MODE_PRIVATE)
                .getString("status", "Not started").orEmpty()
    }
}
