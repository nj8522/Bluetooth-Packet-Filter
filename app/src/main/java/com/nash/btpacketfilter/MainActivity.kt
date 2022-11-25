package com.nash.btpacketfilter


import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var advertisingManager: AdvertisingManager

    private val scope = CoroutineScope(Dispatchers.IO)

    @SuppressLint("ServiceCast", "NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permission()

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        checkIfBluetoothIsPresent()
        advertisingManager = AdvertisingManager(bluetoothAdapter)

        startForegroundService(Intent(this, GattServer::class.java))
    }

    private fun checkIfBluetoothIsPresent() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not present", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth is present", Toast.LENGTH_SHORT).show()
            bluetoothSwitch(true)
        }
    }


    @SuppressLint("InlinedApi")
    private fun permission() {
        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(permissionReport: MultiplePermissionsReport?) {
                    if (permissionReport!!.areAllPermissionsGranted()) {
                        Toast.makeText(
                            this@MainActivity,
                            "All Permission Granted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    if (permissionReport.isAnyPermissionPermanentlyDenied) {
                        Log.i(TAG, "All permissions were not allowed")
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissionReport: MutableList<PermissionRequest>?,
                    permissionToken: PermissionToken?
                ) {
                    permissionToken!!.continuePermissionRequest()
                }
            }).withErrorListener {
                Toast.makeText(this@MainActivity, "Error Occurred", Toast.LENGTH_SHORT).show()
            }.onSameThread().check()
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothSwitch(turnOnBt : Boolean) {
        if (turnOnBt) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            bluetoothAdapter.disable()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, GattServer::class.java))
        bluetoothSwitch(false)
    }


}