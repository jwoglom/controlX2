package com.jwoglom.wearx2

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.widget.doOnTextChanged
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.MessageApi
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.material.textfield.TextInputEditText
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.PumpChallengeRequestBuilder
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionReleaseRequest
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusPermissionChangeReasonRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse
import com.jwoglom.pumpx2.pump.messages.util.MessageHelpers
import com.jwoglom.wearx2.shared.PumpMessageSerializer
import com.jwoglom.wearx2.shared.util.setupTimber
import timber.log.Timber
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors


class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, MessageApi.MessageListener {

    private lateinit var mApiClient: GoogleApiClient

    private lateinit var text: TextView
    private lateinit var requestMessageSpinner: Spinner
    private lateinit var requestSendButton: Button
    private lateinit var pairingCodeInput: TextInputEditText
    private lateinit var enableInsulinDelivery: Button

    private var requestedHistoryLogStartId = -1
    private var lastBolusId = -1
    private var historyLogStreamIdToLastEventId: MutableMap<Int, Int> = ConcurrentHashMap<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTimber("MA")

        Timber.d("mobile activity onCreate $savedInstanceState")
        setContentView(R.layout.activity_main)

        text = requireViewById<TextView>(R.id.text)
        requestMessageSpinner = findViewById(R.id.request_message_spinner)

        val requestMessages = MessageHelpers.getAllPumpRequestMessages()
            .stream().filter { m: String ->
                !m.startsWith("authentication.") && !m.startsWith(
                    "historyLog."
                )
            }.collect(Collectors.toList())
        Timber.i("requestMessages: %s", requestMessages)
        val adapter: ArrayAdapter<String?> = ArrayAdapter(this, android.R.layout.simple_spinner_item, requestMessages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        requestMessageSpinner.adapter = adapter
        requestSendButton = findViewById(R.id.request_message_send)
        requestSendButton.setOnClickListener {
            val itemName = requestMessageSpinner.selectedItem.toString()
            try {
                val className = MessageHelpers.REQUEST_PACKAGE + "." + itemName

                // Custom processing for arguments
                when (className) {
                    IDPSegmentRequest::class.java.name -> {
                        triggerIDPSegmentDialog()
                        return@setOnClickListener
                    }
                    IDPSettingsRequest::class.java.name -> {
                        triggerIDPSettingsDialog()
                        return@setOnClickListener
                    }
                    HistoryLogRequest::class.java.name -> {
                        triggerHistoryLogRequestDialog()
                        return@setOnClickListener
                    }
                    InitiateBolusRequest::class.java.name -> {
                        triggerInitiateBolusRequestDialog()
                        return@setOnClickListener
                    }
                    CancelBolusRequest::class.java.name -> {
                        triggerCancelBolusRequestDialog()
                        return@setOnClickListener
                    }
                    BolusPermissionChangeReasonRequest::class.java.name -> {
                        triggerMessageWithBolusIdParameter(
                            BolusPermissionChangeReasonRequest::class.java
                        )
                        return@setOnClickListener
                    }
                    BolusPermissionReleaseRequest::class.java.name -> {
                        triggerMessageWithBolusIdParameter(
                            BolusPermissionReleaseRequest::class.java
                        )
                        return@setOnClickListener
                    }
                    BolusPermissionRequest::class.java.name -> {
                        writePumpMessage(BolusPermissionRequest())
                        return@setOnClickListener
                    }
                    else -> {
                        val clazz = Class.forName(className)
                        Timber.i("Instantiated %s: %s", className, clazz)
                        writePumpMessage(
                            clazz.newInstance() as Message
                        )
                    }
                }
            } catch (e: ClassNotFoundException) {
                Timber.e(e)
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                Timber.e(e)
                e.printStackTrace()
            } catch (e: InstantiationException) {
                Timber.e(e)
                e.printStackTrace()
            }
        }
        requestSendButton.postInvalidate()

        pairingCodeInput = findViewById(R.id.pairing_code)
        pairingCodeInput.setText(PumpState.getPairingCode(applicationContext))
        pairingCodeInput.doOnTextChanged { text, start, before, count ->
            try {
                val code = PumpChallengeRequestBuilder.processPairingCode(text.toString())
                PumpChallengeRequestBuilder.create(0, code, ByteArray(0))
                PumpState.setPairingCode(applicationContext, code)
                Toast.makeText(applicationContext, "Set pairing code: $code", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.w("pairingCodeInput: $e")
            }
        }

        enableInsulinDelivery = findViewById(R.id.enable_insulin_delivery)
        enableInsulinDelivery.text = when (getInsulinDeliveryEnabled()) {
            true -> "Disable Insulin Delivery"
            false -> "Enable Insulin Delivery"
        }
        enableInsulinDelivery.setOnClickListener {
            enableInsulinDeliveryDialog()
        }

        mApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build()

        mApiClient.connect()

        startCommService()
    }


    override fun onResume() {
        Timber.i("activity onResume")
        if (!mApiClient.isConnected && !mApiClient.isConnecting) {
            mApiClient.connect()
        }

        startBTPermissionsCheck()
        super.onResume()
    }

    private fun startCommService() {
        // Start CommService
        val intent = Intent(applicationContext, CommService::class.java)

        if (Build.VERSION.SDK_INT >= 26) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
        applicationContext.bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                //retrieve an instance of the service here from the IBinder returned
                //from the onBind method to communicate with
                Timber.i("CommService onServiceConnected")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Timber.i("CommService onServiceDisconnected")
            }
        }, BIND_AUTO_CREATE)
    }

    private fun sendMessage(path: String, message: ByteArray) {
        Timber.i("mobile sendMessage: $path ${String(message)}")
        Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
            Timber.i("mobile sendMessage nodes: $nodes")
            nodes.nodes.forEach { node ->
                Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message)
                    .setResultCallback { result ->
                        if (result.status.isSuccess) {
                            Timber.i("Message sent: ${path} ${String(message)}")
                        } else {
                            Timber.e("mobile sendMessage callback: ${result}")
                        }
                    }
            }
        }
    }

    private fun writePumpMessage(msg: Message) {
        sendMessage("/to-pump/command", PumpMessageSerializer.toBytes(msg))
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

    // Message received from Wear
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.i("phone messageReceived: ${messageEvent.path}: ${String(messageEvent.data)}")
        when (messageEvent.path) {
            "/to-phone/connected" -> {
                text.text = "Watch connected"
            }
        }
    }

    private fun onPumpMessageReceived(message: Message) {
        when (message) {
            is HistoryLogResponse -> {
                if (requestedHistoryLogStartId > 0) {
                    historyLogStreamIdToLastEventId.put(
                        message.streamId,
                        requestedHistoryLogStartId
                    )
                    requestedHistoryLogStartId = -1
                }
            }
            is HistoryLogStreamResponse -> {
                var lastEventId =
                    historyLogStreamIdToLastEventId.getOrDefault(message.streamId, 0)
                for (log in message.historyLogs) {
                    Timber.i("HistoryLog event %d: type %d: %s", lastEventId, log.typeId(), log)
                    lastEventId++
                }
                historyLogStreamIdToLastEventId[message.streamId] = lastEventId
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

    /**
     * Messages
     */

    private fun triggerIDPSegmentDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter IDP ID")
        builder.setMessage("Enter the ID for the Insulin Delivery Profile")
        val input1 = EditText(this)
        val context: Context = this
        input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        builder.setView(input1)
        builder.setPositiveButton("OK") { dialog, which ->
            val idpId = input1.text.toString()
            Timber.i("idp id: %s", idpId)
            val builder2 = AlertDialog.Builder(context)
            builder2.setTitle("Enter segment index")
            builder2.setMessage("Enter the index for the Insulin Delivery Profile segment")
            val input2 = EditText(context)
            input2.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
            builder2.setView(input2)
            builder2.setPositiveButton(
                "OK"
            ) { dialog, which ->
                val idpSegment = input2.text.toString()
                Timber.i("idp segment: %s", idpSegment)
                writePumpMessage(IDPSegmentRequest(idpId.toInt(), idpSegment.toInt()))
            }
            builder2.setNegativeButton(
                "Cancel"
            ) { dialog, which -> dialog.cancel() }
            builder2.show()
        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder.show()
    }

    private fun triggerIDPSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter IDP ID")
        builder.setMessage("Enter the ID for the Insulin Delivery Profile")
        val input1 = EditText(this)
        val context: Context = this
        input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        builder.setView(input1)
        builder.setPositiveButton("OK") { dialog, which ->
            val idpId = input1.text.toString()
            Timber.i("idp id: %s", idpId)
            writePumpMessage(IDPSettingsRequest(idpId.toInt()))
        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder.show()
    }

    private fun triggerHistoryLogRequestDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter start log ID")
        builder.setMessage("Enter the ID of the first history log item to return from")
        val input1 = EditText(this)
        val context: Context = this
        input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        builder.setView(input1)
        builder.setPositiveButton("OK") { dialog, which ->
            val startLog = input1.text.toString()
            Timber.i("startLog id: %s", startLog)
            val builder2 = AlertDialog.Builder(context)
            builder2.setTitle("Enter number of logs ")
            builder2.setMessage("Enter the max number of logs to return")
            val input2 = EditText(context)
            input2.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
            builder2.setView(input2)
            builder2.setPositiveButton(
                "OK"
            ) { dialog, which ->
                val maxLogs = input2.text.toString()
                Timber.i("idp segment: %s", maxLogs)
                writePumpMessage(
                    HistoryLogRequest(startLog.toInt().toLong(), maxLogs.toInt())
                )
                requestedHistoryLogStartId = startLog.toInt()
            }
            builder2.setNegativeButton(
                "Cancel"
            ) { dialog, which -> dialog.cancel() }
            builder2.show()
        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder.show()
    }

    private fun triggerInitiateBolusRequestDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter units to deliver bolus")
        builder.setMessage("Enter the number of units in INTEGER FORM: 1000 = 1 unit, 100 = 0.1 unit, 10 = 0.01 unit. Minimum value is 50 (0.05 unit)")
        val input1 = EditText(this)
        val context: Context = this
        input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        builder.setView(input1)
        builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
            val numUnitsStr = input1.text.toString()
            Timber.i("numUnits: %s", numUnitsStr)
            if ("" == numUnitsStr) {
                Timber.e("Not delivering bolus because no units entered.")
                return@OnClickListener
            }
            val builder2 = AlertDialog.Builder(context)
            builder2.setTitle("CONFIRM BOLUS!!")
            builder2.setMessage("Enter the bolus ID from BolusPermissionRequest. THIS WILL ACTUALLY DELIVER THE BOLUS. Enter a blank value to cancel.")
            val input2 = EditText(context)
            input2.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
            builder2.setView(input2)
            builder2.setPositiveButton("OK",
                DialogInterface.OnClickListener { dialog, which ->
                    val bolusIdStr = input2.text.toString()
                    Timber.i("currentIob: %s", bolusIdStr)
                    if ("" == bolusIdStr) {
                        Timber.e("Not delivering bolus because no bolus ID entered.")
                        return@OnClickListener
                    }
                    val numUnits = numUnitsStr.toInt()
                    val bolusId = bolusIdStr.toInt()
                    lastBolusId = bolusId
                    // InitiateBolusRequest(long totalVolume, int bolusTypeBitmask, long foodVolume, long correctionVolume, int bolusCarbs, int bolusBG, long bolusIOB)
                    writePumpMessage(
                        InitiateBolusRequest(
                            numUnits.toLong(),
                            bolusId,
                            BolusDeliveryHistoryLog.BolusType.toBitmask(BolusDeliveryHistoryLog.BolusType.FOOD2),
                            0L,
                            0L,
                            0,
                            0,
                            0
                        )
                    )
                })
            builder2.setNegativeButton(
                "Cancel"
            ) { dialog, which -> dialog.cancel() }
            builder2.show()
        })
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder.show()
    }

    private fun triggerCancelBolusRequestDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("CancelBolusRequest")
        builder.setMessage("Enter the bolus ID (this can be received from currentStatus.LastBolusStatusV2)")
        val input1 = EditText(this)
        val context: Context = this
        input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        if (lastBolusId > 0) {
            input1.setText(java.lang.String.valueOf(lastBolusId))
        }
        builder.setView(input1)
        builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
            val bolusIdStr = input1.text.toString()
            Timber.i("bolusId: %s", bolusIdStr)
            if ("" == bolusIdStr) {
                Timber.e("Not cancelling bolus because no units entered.")
                return@OnClickListener
            }
            val bolusId = bolusIdStr.toInt()
            writePumpMessage(CancelBolusRequest(bolusId))
        })
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder.show()
    }

    private fun triggerMessageWithBolusIdParameter(
        messageClass: Class<out Message>
    ) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(messageClass.simpleName)
        builder.setMessage("Enter the bolus ID (this can be received from the in-progress bolus)")
        val input1 = EditText(this)
        val context: Context = this
        input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        if (lastBolusId > 0) {
            input1.setText("$lastBolusId")
        }
        builder.setView(input1)
        builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
            val bolusIdStr = input1.text.toString()
            Timber.i("bolusId: %s", bolusIdStr)
            if ("" == bolusIdStr) {
                Timber.e("Not sending message because no bolus ID entered.")
                return@OnClickListener
            }
            val bolusId = bolusIdStr.toInt()
            val constructorType = arrayOf<Class<*>?>(
                Long::class.javaPrimitiveType
            )
            val message: Message
            message = try {
                messageClass.getConstructor(*constructorType).newInstance(bolusId)
            } catch (e: IllegalAccessException) {
                Timber.e(e)
                return@OnClickListener
            } catch (e: InstantiationException) {
                Timber.e(e)
                return@OnClickListener
            } catch (e: InvocationTargetException) {
                Timber.e(e)
                return@OnClickListener
            } catch (e: NoSuchMethodException) {
                Timber.e(e)
                return@OnClickListener
            }
            writePumpMessage(message)
        })
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder.show()
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

    private fun prefs(context: Context): SharedPreferences? {
        return context.getSharedPreferences("WearX2", MODE_PRIVATE)
    }

    private fun getInsulinDeliveryEnabled(): Boolean {
        return prefs(applicationContext)?.getBoolean("insulinDeliveryEnabled", false) ?: false
    }

    private fun setInsulinDeliveryEnabled(b: Boolean) {
        prefs(applicationContext)?.edit()?.putBoolean("insulinDeliveryEnabled", b)?.apply()
    }

    private fun enableInsulinDeliveryDialog() {
        if (!getInsulinDeliveryEnabled()) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("HEALTH AND SAFETY WARNING")
                .setMessage(
                    """
                This application is for EXPERIMENTAL USE ONLY and can be used to MODIFY ACTIVE INSULIN DELIVERY ON YOUR INSULIN PUMP.
                
                There is NO WARRANTY IMPLIED OR EXPRESSED DUE TO USE OF THIS SOFTWARE. YOU ASSUME ALL RISK FOR ANY MALFUNCTIONS, BUGS, OR INSULIN DELIVERY ACTIONS.
                
                Are you sure you want to enable insulin delivery actions? The app will be restarted after enabling this setting.
                """.trimIndent()
                )
                .setPositiveButton(
                    "Enable Bolus Deliveries"
                ) { dialog, i ->
                    dialog.cancel()
                    setInsulinDeliveryEnabled(true)
                    Toast.makeText(applicationContext, "Bolus deliveries enabled", Toast.LENGTH_SHORT).show()
                    enableInsulinDelivery.text = when (getInsulinDeliveryEnabled()) {
                        true -> "Disable Insulin Delivery"
                        false -> "Enable Insulin Delivery"
                    }
                    Thread.sleep(500)
                    triggerAppReload(applicationContext)
                }
                .setNegativeButton(
                    "Cancel"
                ) { dialog, which -> dialog.cancel() }
                .setIcon(android.R.drawable.ic_dialog_info)
                .create()
                .show()
        } else {
            setInsulinDeliveryEnabled(false)
            Toast.makeText(applicationContext, "Bolus deliveries disabled", Toast.LENGTH_SHORT).show()
            enableInsulinDelivery.text = when (getInsulinDeliveryEnabled()) {
                true -> "Disable Insulin Delivery"
                false -> "Enable Insulin Delivery"
            }
        }
    }

    private fun triggerAppReload(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent!!.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }
}