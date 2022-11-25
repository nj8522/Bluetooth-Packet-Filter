package com.nash.btpacketfilter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

@SuppressLint("MissingPermission", "HardwareIds")
class AdvertisingManager(private val btAdapter: BluetoothAdapter) {


  private val TAG = "AdvertisingManager"
  private val btAddress : String? = btAdapter.address
  private val btAdvertiser : BluetoothLeAdvertiser by lazy {
      btAdapter.bluetoothLeAdvertiser ?: throw NullPointerException("Bluetooth not initialized")
  }
  var btAdvertisingCallback : AdvertiseCallback? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun startBtAdvertising() = suspendCancellableCoroutine { continuation ->
          Log.i(TAG, "Started Advertising")
          btAdvertisingCallback = object : AdvertiseCallback() {

              override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    continuation.resume(Unit) {}
              }

              override fun onStartFailure(errorCode: Int) {
                  continuation.resumeWithException(AdvertisingException(errorCode))
              }
          }

      continuation.invokeOnCancellation {
          btAdvertiser.stopAdvertising(btAdvertisingCallback)
      }

      val btAdvertisingSettings = AdvertiseSettings.Builder()
          .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
          .setConnectable(true)
          .build()

      val advertisingData =  AdvertiseData.Builder()
          .addServiceUuid(ParcelUuid(MyServiceProfile.MY_SERVICE_UUID))
          .build()

      val scanResponse = AdvertiseData.Builder()
          .setIncludeDeviceName(true)
          .build()

      btAdvertiser.startAdvertising(
          btAdvertisingSettings,
          advertisingData,
          scanResponse,
          btAdvertisingCallback
      )

      Log.i(TAG, "Settings: $btAdvertisingSettings, Data: $advertisingData, Response $scanResponse")
  }

    fun stopAdvertising() {
           Log.i(TAG, "Stopped Advertising")
           btAdvertiser.stopAdvertising(btAdvertisingCallback)
    }


    data class AdvertisingException(val errorCode: Int) : Exception() {}
}