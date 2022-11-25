package com.nash.btpacketfilter

import android.bluetooth.BluetoothGatt
import android.content.Context
import no.nordicsemi.android.ble.BleManager

class PacketFilterManager(context: Context) : BleManager(context) {

    //private var fluxCapacitorControlPoint : BluetoothGattCharacteristic = BluetoothGattCharacteristic()


    override fun getMinLogPriority(): Int {
        return super.getMinLogPriority()
    }

    override fun log(priority: Int, message: String) {
        super.log(priority, message)
    }



    override fun getGattCallback(): BleManagerGattCallback {
        return GattCallBack()
    }

    private class GattCallBack() : BleManagerGattCallback() {



        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            //val fluxCapacitorService : BluetoothGattService
            return false
        }

        override fun initialize() {
            super.initialize()
        }


        override fun onServicesInvalidated() {}

    }


}