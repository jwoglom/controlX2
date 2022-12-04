package com.jwoglom.wearx2

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.util.timber.DebugTree
import timber.log.Timber
import java.util.*


class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {

    private lateinit var mApiClient: GoogleApiClient

    private lateinit var text: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(DebugTree())
        setContentView(R.layout.activity_main)

        text = requireViewById<TextView>(R.id.text)
        button = requireViewById<Button>(R.id.button);
        button.setOnClickListener {
            sendMessage("/to-wear/send-data", "fromphone")
        }

        mApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build()

        mApiClient.connect()

        // Start WearCommService
        Intent(this, WearCommService::class.java).also { intent ->
            startService(intent)
        }
    }


    override fun onResume() {
        if (!mApiClient.isConnected && !mApiClient.isConnecting) {
            mApiClient.connect()
        }

        startBTPermissionsCheck()
        super.onResume()
    }

    private fun sendMessage(path: String, message: String) {
        Timber.i("mobile sendMessage: $path $message")
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
            Timber.i("mobile sendMessage nodes: $nodes")
            nodes.nodes.forEach { node ->
                Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message.toByteArray())
                    .setResultCallback { result ->
                        Timber.d("sendMessage callback: ${result}")
                        if (result.status.isSuccess) {
                            Timber.i("Message sent: ${path} ${message}")
                        }
                    }
            }
        }
    }

    override fun onConnected(bundle: Bundle?) {
        Timber.i("mobile onConnected $bundle")
        sendMessage("/to-wear/connected", "phone_launched")
        Wearable.MessageApi.addListener(mApiClient, this)
    }

    override fun onConnectionSuspended(id: Int) {
        Timber.i("mobile onConnectionSuspended: $id")
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        Timber.i("mobile onConnectionFailed: $result")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.i("phone messageReceived: ${messageEvent}: ${messageEvent.path}: ${messageEvent.data}")
        runOnUiThread {
            if (!messageEvent.path.startsWith("/to-wear")) {
                text.text = String(messageEvent.data)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Wearable.MessageApi.removeListener(mApiClient, this)
        if (mApiClient.isConnected) {
            mApiClient.disconnect()
        }
    }

    override fun onDestroy() {
        mApiClient.unregisterConnectionCallbacks(this)
        super.onDestroy()
    }

    // BT permissions
    private fun startBTPermissionsCheck() {
        if (getBluetoothManager().getAdapter() != null) {
            if (!isBluetoothEnabled()) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                val REQUEST_ENABLE_BT = 1
                startActivityForResult(
                    enableBtIntent,
                    REQUEST_ENABLE_BT
                )
            } else {
                checkPermissions()
            }
        } else {
            Timber.e("This device has no Bluetooth hardware")
        }
    }

    private fun getBluetoothManager(): BluetoothManager {
        return Objects.requireNonNull(
            getSystemService(BLUETOOTH_SERVICE) as BluetoothManager,
            "cannot get BluetoothManager"
        )
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter = getBluetoothManager().adapter ?: return false
        return bluetoothAdapter.isEnabled
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val missingPermissions = getMissingPermissions(getRequiredPermissions())
            if (missingPermissions.size > 0) {
                val ACCESS_LOCATION_REQUEST = 2
                requestPermissions(
                    missingPermissions,
                    ACCESS_LOCATION_REQUEST
                )
            } else {
                permissionsGranted()
            }
        }
    }

    private fun getMissingPermissions(requiredPermissions: Array<String>): Array<String> {
        val missingPermissions: MutableList<String> = ArrayList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (requiredPermission in requiredPermissions) {
                if (applicationContext.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(requiredPermission)
                }
            }
        }
        return missingPermissions.toTypedArray()
    }

    private fun getRequiredPermissions(): Array<String> {
        val targetSdkVersion = applicationInfo.targetSdkVersion
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work for SDK < 31
        val targetSdkVersion = applicationInfo.targetSdkVersion
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && targetSdkVersion < Build.VERSION_CODES.S) {
            if (checkLocationServices()) {
                // getBluetoothHandler().startScan()
            }
        } else {
            // getBluetoothHandler().startScan()
        }
    }

    private fun areLocationServicesEnabled(): Boolean {
        val locationManager =
            applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
        if (locationManager == null) {
            Timber.e("could not get location manager")
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled =
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            isGpsEnabled || isNetworkEnabled
        }
    }

    private fun checkLocationServices(): Boolean {
        return if (!areLocationServicesEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Location services are not enabled")
                .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                .setPositiveButton(
                    "Enable"
                ) { dialogInterface, i ->
                    dialogInterface.cancel()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(
                    "Cancel"
                ) { dialog, which -> // if this button is clicked, just close
                    // the dialog box and do nothing
                    dialog.cancel()
                }
                .create()
                .show()
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if all permission were granted
        var allGranted = true
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                break
            }
        }
        if (allGranted) {
            permissionsGranted()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Permission is required for scanning Bluetooth peripherals")
                .setMessage("Please grant permissions")
                .setPositiveButton(
                    "Retry"
                ) { dialogInterface, i ->
                    dialogInterface.cancel()
                    checkPermissions()
                }
                .create()
                .show()
        }
    }
}