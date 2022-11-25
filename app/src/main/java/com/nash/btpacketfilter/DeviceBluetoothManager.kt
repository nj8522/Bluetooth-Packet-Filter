package com.nash.btpacketfilter

import android.app.Application
import android.bluetooth.BluetoothManager

object DeviceBluetoothManager : Application() {

    fun getBluetoothManager() : BluetoothManager {
        return getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
    }
}