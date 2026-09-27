package com.musiccitytelecom.torque.c5diag

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import org.prowl.torque.remote.ITorqueService

class TorqueClient(private val context: Context) {
    @Volatile
    private var service: ITorqueService? = null
    private var bound = false
    private var stateCallback: ((Boolean, String) -> Unit)? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ITorqueService.Stub.asInterface(binder)
            val version = try {
                service?.version ?: 0
            } catch (_: Exception) {
                0
            }
            stateCallback?.invoke(true, "Connected to Torque API " + version)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            stateCallback?.invoke(false, "Torque service disconnected")
        }
    }

    fun bind(callback: (Boolean, String) -> Unit) {
        stateCallback = callback
        if (bound && service != null) {
            callback(true, "Connected to Torque")
            return
        }

        val intent = Intent().apply {
            component = ComponentName(
                "org.prowl.torque",
                "org.prowl.torque.remote.TorqueService"
            )
        }

        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) callback(false, "Unable to bind Torque Pro service")
        } catch (e: Exception) {
            bound = false
            callback(false, "Torque bind failed: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    fun unbind() {
        if (!bound) return
        try {
            context.unbindService(connection)
        } catch (_: Exception) {
        } finally {
            bound = false
            service = null
        }
    }

    fun hasFullPermissions(): Boolean = try {
        service?.hasFullPermissions() == true
    } catch (_: Exception) {
        false
    }

    fun query(header: String, command: String): List<String> {
        val svc = service ?: return emptyList()
        return try {
            svc.sendCommandGetResponse(header, command)?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
