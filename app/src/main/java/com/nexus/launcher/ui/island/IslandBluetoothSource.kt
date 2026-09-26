package com.nexus.launcher.ui.island

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.nexus.launcher.R

class IslandBluetoothSource(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            emit(intent)
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        handler.removeCallbacksAndMessages(null)
        IslandTriggerBus.publish(IslandKind.BLUETOOTH, null)
    }

    private fun emit(intent: Intent?) {
        if (intent == null) return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val name = runCatching { device?.name }.getOrNull()
            ?: context.getString(R.string.island_bluetooth_device)
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_BOND_STATE_CHANGED,
            -> {
                val bonding = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE,
                ) == BluetoothDevice.BOND_BONDING
                if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED || bonding) {
                    IslandTriggerBus.publish(
                        IslandKind.BLUETOOTH,
                        IslandPayload(
                            kind = IslandKind.BLUETOOTH,
                            title = context.getString(R.string.island_bluetooth, name),
                            identity = "bt:$name",
                        ),
                    )
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({
                        IslandTriggerBus.publish(IslandKind.BLUETOOTH, null)
                    }, 5_000L)
                }
            }
            else -> IslandTriggerBus.publish(IslandKind.BLUETOOTH, null)
        }
    }
}
