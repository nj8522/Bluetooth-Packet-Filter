package com.nash.btpacketfilter

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nash.btpacketfilter.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.BleManager
import java.util.*

class GattServer  : Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private lateinit var btObserver : BroadcastReceiver
    private var myDeviceCharacteristicsChangedChannel : SendChannel<String>? = null
    private val clientManagers = mutableMapOf<String, ClientManager>()

    companion object {
        const val DATA_PLANE_ACTION = "data-plane"
        private const val TAG = "GATT Server"
    }

    override fun onCreate() {
        super.onCreate()
        buildNotification()
        bluetoothOsChangeObserver()
    }

    private fun bluetoothOsChangeObserver() {
        btObserver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                when(intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                        when(btState) {
                            BluetoothAdapter.STATE_ON -> enableBleServices()
                            BluetoothAdapter.STATE_OFF -> disableBleServices()
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_NAME)
                        Log.i(TAG, "Device Status changed, Bt Address: ${device?.address}, " +
                                "State: ${device?.bondState}, Class: ${device?.bluetoothClass}")
                        when(device?.bondState) {
                            BluetoothDevice.BOND_BONDED -> addBluetoothDevice(device)
                            BluetoothDevice.BOND_NONE -> removeBluetoothDevice(device)
                        }
                    }
                }
            }
        }

        registerReceiver(btObserver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        registerReceiver(btObserver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    inner class DataPlane : Binder() {
        fun setDeviceCharacteristicsChangedChannel(sendChannel: SendChannel<String>) {
            myDeviceCharacteristicsChangedChannel = sendChannel
        }
    }

    override fun onBind(intent: Intent?): IBinder? =
        when(intent?.action) {
            DATA_PLANE_ACTION -> DataPlane()
            else -> null
        }

    override fun onUnbind(intent: Intent?): Boolean =
        when(intent?.action) {
            DATA_PLANE_ACTION -> {
                myDeviceCharacteristicsChangedChannel = null
                true
            }
            else -> false
        }


    @SuppressLint("MissingPermission")
    private fun enableBleServices() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager.adapter?.isEnabled == true) {
           Log.i(TAG, "Enabling BLE services")
           bluetoothManager.adapter.bondedDevices.forEach{ devices -> addBluetoothDevice(devices) }
        } else {
            Log.i(TAG, "Cannot Enable BLE services as there is not bluetooth adapter or is disabled")
        }
    }

    private fun disableBleServices() {
        clientManagers.values.forEach { clientManagers -> clientManagers.close()}
        clientManagers.clear()
    }

    private fun addBluetoothDevice(device: BluetoothDevice) {
          if (!clientManagers.containsKey(device.address)) {
               val clientManager = ClientManager()
               clientManager.connect(device).useAutoConnect(true).enqueue()
               clientManagers[device.address] = clientManager
          }
    }

    private fun removeBluetoothDevice(device : BluetoothDevice) {
        clientManagers.remove(device.address)?.close()
    }

    @SuppressLint("NewApi")
    private fun buildNotification() {
        val notificationChannel = NotificationChannel(
            GattServer::class.java.simpleName,
            "Bluetooth Pair Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        val notificationService = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationService.createNotificationChannel(notificationChannel)

        val notification = NotificationCompat.Builder(this, GattServer::class.java.simpleName)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("BLE Gatt Service")
            .setContentText("Running Gatt Service in the foreground")
            .setAutoCancel(true)

        startForeground(1, notification.build())
    }

    private fun clearExistingNotification() {
        val notificationManager : NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }

    private inner class ClientManager : BleManager(this@GattServer) {

        override fun getGattCallback(): BleManagerGattCallback = GattCallBack()

//        override fun log(priority: Int, message: String) {
//            if (BuildConfig.DEBUG || priority == Log.ERROR) {
//                Log.println(priority, TAG, message)
//            }
//        }

        private inner class GattCallBack : BleManagerGattCallback() {

            private var deviceCharacteristics: BluetoothGattCharacteristic? = null

            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                val service = gatt.getService(MyServiceProfile.MY_SERVICE_UUID)
                deviceCharacteristics = service.getCharacteristic(MyServiceProfile.MY_CHARACTERISTIC_UUID)
                val deviceCharacteristicsProperty = deviceCharacteristics?.properties ?: 0
                return (deviceCharacteristicsProperty and BluetoothGattCharacteristic.PROPERTY_READ != 0) &&
                        (deviceCharacteristicsProperty and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
            }

            override fun initialize() {
                super.initialize()
                setNotificationCallback(deviceCharacteristics).with{_,data ->
                       if (data.value != null) {
                           val value = String(data.value!!, Charsets.UTF_8)
                           coroutineScope.launch {
                             myDeviceCharacteristicsChangedChannel?.send(value)
                           }
                       }
                }

                beginAtomicRequestQueue()
                    .add(enableNotifications(deviceCharacteristics)
                        .fail { _ : BluetoothDevice, status : Int ->
                            log(Log.ERROR, "Could not subscribe: $status")
                            disconnect().enqueue()
                        }
                    ) .done{
                        log(Log.INFO, "Target Initialized")
                    }.enqueue()
            }

            override fun onServicesInvalidated() {
                deviceCharacteristics = null
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(btObserver)
        disableBleServices()
        clearExistingNotification()
    }
}


object MyServiceProfile {
    val MY_SERVICE_UUID: UUID = UUID.fromString("80323644-3537-4F0B-A53B-CF494ECEAAB3")
    val MY_CHARACTERISTIC_UUID: UUID = UUID.fromString("80323644-3537-4F0B-A53B-CF494ECEAAB3")
}
