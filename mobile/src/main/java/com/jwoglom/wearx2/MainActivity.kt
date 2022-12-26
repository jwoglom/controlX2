package com.jwoglom.wearx2

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.compositionLocalOf
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.wearx2.presentation.DataStore
import com.jwoglom.wearx2.presentation.MobileApp
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.screens.PumpSetupStage
import com.jwoglom.wearx2.shared.util.setupTimber
import timber.log.Timber
import java.util.*

var dataStore = DataStore()
val LocalDataStore = compositionLocalOf { dataStore }

class MainActivity : ComponentActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {
    private lateinit var mApiClient: GoogleApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("mobile UIActivity onCreate $savedInstanceState")
        super.onCreate(savedInstanceState)
        setupTimber("MUA")
        setContent {
            MobileApp(
                startDestination = determineStartDestination(),
                sendMessage = {path, message -> sendMessage(path, message) },
            )
        }

        mApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build()

        mApiClient.connect()

        if (Prefs(applicationContext).tosAccepted()) {
            startCommService()
        } else {
            Timber.i("commService not started because first TOS not accepted")
        }
    }

    override fun onResume() {
        Timber.i("activity onResume")
        if (!mApiClient.isConnected && !mApiClient.isConnecting) {
            mApiClient.connect()
        }

        if (Prefs(applicationContext).tosAccepted()) {
            startBTPermissionsCheck()
        } else {
            Timber.i("BTPermissionsCheck not started because TOS not accepted")
        }
        super.onResume()
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

    private fun startCommService() {
        Timber.i("starting CommService")
        // Start CommService
        val intent = Intent(applicationContext, CommService::class.java)

        if (Build.VERSION.SDK_INT >= 26) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
        applicationContext.bindService(intent, commServiceConnection, BIND_AUTO_CREATE)
    }

    private val commServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            //retrieve an instance of the service here from the IBinder returned
            //from the onBind method to communicate with
            Timber.i("CommService onServiceConnected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.i("CommService onServiceDisconnected")
        }
    }

    override fun onConnected(bundle: Bundle?) {
        Timber.i("mobile onConnected $bundle")
        sendMessage("/to-wear/connected", "phone_launched".toByteArray())
        Wearable.MessageApi.addListener(mApiClient, this)
    }

    override fun onConnectionSuspended(id: Int) {
        Timber.i("mobile onConnectionSuspended: $id")
        mApiClient.reconnect()
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        Timber.i("mobile onConnectionFailed: $result")
        mApiClient.reconnect()
    }

    private fun sendMessage(path: String, message: ByteArray) {
        Timber.i("mobile sendMessage: $path ${String(message)}")
        fun inner(node: Node) {
            Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message)
                .setResultCallback { result ->
                    if (result.status.isSuccess) {
                        Timber.i("Message sent: ${path} ${String(message)}")
                    } else {
                        Timber.e("mobile sendMessage callback: ${result}")
                    }
                }
        }
        if (!path.startsWith("/to-wear")) {
            Wearable.NodeApi.getLocalNode(mApiClient).setResultCallback { nodes ->
                Timber.i("mobile sendMessage local: ${nodes.node}")
                inner(nodes.node)
            }
        }
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
            Timber.i("mobile sendMessage nodes: $nodes")
            nodes.nodes.forEach { node ->
                inner(node)
            }
        }
    }


    // Message received from Wear
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.i("phone messageReceived: ${messageEvent.path}: ${String(messageEvent.data)}")
        when (messageEvent.path) {
            "/to-phone/start-comm" -> {
                when (String(messageEvent.data)) {
                    "skip_notif_permission" -> {
                        startBTPermissionsCheck()
                        startCommService()
                        dataStore.pumpSetupStage.value =
                            dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMPX2_INIT)
                    }
                    else -> {
                        requestNotificationCallback = { isGranted ->
                            if (isGranted) {
                                startBTPermissionsCheck()
                                startCommService()
                                dataStore.pumpSetupStage.value =
                                    dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMPX2_INIT)
                            } else {
                                dataStore.pumpSetupStage.value =
                                    dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PERMISSIONS_NOT_GRANTED)
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }

            "/to-phone/comm-started" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_SEARCHING_FOR_PUMP)
            }

            "/to-phone/set-pairing-code" -> {
                val pairingCodeText = String(messageEvent.data)
                PumpState.setPairingCode(applicationContext, pairingCodeText)
                Toast.makeText(applicationContext, "Set pairing code: $pairingCodeText", Toast.LENGTH_SHORT).show()
                if (dataStore.pumpSetupStage.value == PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE) {
                    sendMessage("/to-pump/pair", "".toByteArray())
                }
            }

            "/to-phone/connected" -> {
                dataStore.watchConnected.value = true
            }

            "/from-pump/pump-discovered" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCOVERED)
                dataStore.setupDeviceName.value = String(messageEvent.data)
            }

            "/from-pump/pump-model" -> {
                dataStore.setupDeviceModel.value = String(messageEvent.data)
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_MODEL_METADATA)
            }

            "/from-pump/initial-pump-connection" -> {
                dataStore.setupDeviceName.value = String(messageEvent.data)
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_INITIAL_PUMP_CONNECTION)
            }

            "/from-pump/missing-pairing-code" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE)
            }

            "/from-pump/invalid-pairing-code" -> {
                Timber.w("invalid-pairing-code with code: ${PumpState.getPairingCode(applicationContext)}")
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE)
            }

            "/from-pump/pump-connected" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_CONNECTED)
                dataStore.setupDeviceName.value = String(messageEvent.data)
            }

            "/from-pump/pump-disconnected" -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCONNECTED)
                dataStore.setupDeviceModel.value = String(messageEvent.data)
            }
        }
    }


    /**
     * BT permissions
     */

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

    /**
     * Notification permission
     */
    private var requestNotificationCallback: (Boolean) -> Unit = {}
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        requestNotificationCallback(isGranted)
    }

    private fun determineStartDestination(): String {
        return when {
            !Prefs(applicationContext).tosAccepted() -> Screen.FirstLaunch.route
            !Prefs(applicationContext).pumpSetupComplete() -> Screen.PumpSetup.route
            !Prefs(applicationContext).appSetupComplete() -> Screen.AppSetup.route
            else -> Screen.Landing.route
        }
    }
}
