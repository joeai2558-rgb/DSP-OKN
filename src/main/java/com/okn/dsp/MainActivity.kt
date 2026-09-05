package com.okn.dsp

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import java.util.UUID

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private val packets = PacketLog()
    private val permissionReq = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestBlePermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 28, 20, 20)
        }
        status = TextView(this).apply {
            text = "สถานะ: พร้อม"
            textSize = 18f
        }
        val scan = Button(this).apply { text = "1) สแกน DSP" }
        val disconnect = Button(this).apply { text = "2) ตัดการเชื่อมต่อ" }
        val clear = Button(this).apply { text = "ล้าง Packet Log" }
        val export = Button(this).apply { text = "ส่งออก Log" }
        logView = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(logView) }
        root.addView(status)
        root.addView(scan)
        root.addView(disconnect)
        root.addView(clear)
        root.addView(export)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        scan.setOnClickListener { startScan() }
        disconnect.setOnClickListener { disconnect() }
        clear.setOnClickListener { packets.clear(); refreshLog() }
        export.setOnClickListener { shareLog() }
    }

    private fun requestBlePermissions() {
        val needed = if (android.os.Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.BLUETOOTH)
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), permissionReq)
    }

    private fun startScan() {
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestBlePermissions(); return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            status.text = "สถานะ: เปิด Bluetooth ก่อน"; return
        }
        scanner = adapter.bluetoothLeScanner
        status.text = "สถานะ: กำลังสแกน..."
        scanner?.startScan(scanCallback)
        handler.postDelayed({ stopScan() }, 15000)
    }

    private fun stopScan() {
        if (scanner != null && (android.os.Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)) {
            scanner?.stopScan(scanCallback)
        }
        status.text = "สถานะ: หยุดสแกน"
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name ?: ""
            if (name == BleProfile.DEVICE_NAME || name.contains("DSP", true) || name.contains("Golo", true)) {
                appendLine("พบ DSP: $name ${device.address} RSSI=${result.rssi}")
                connect(device)
                stopScan()
            }
        }
        override fun onScanFailed(errorCode: Int) {
            appendLine("SCAN ERROR=$errorCode")
        }
    }

    private fun connect(device: BluetoothDevice) {
        status.text = "สถานะ: กำลังเชื่อมต่อ..."
        gatt?.close()
        gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    status.text = "สถานะ: เชื่อมต่อแล้ว"
                    appendLine("CONNECTED status=$statusCode")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    status.text = "สถานะ: ตัดการเชื่อมต่อ"
                    appendLine("DISCONNECTED status=$statusCode")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
            appendLine("SERVICES status=$statusCode")
            for (s in g.services) {
                appendLine("SERVICE ${s.uuid}")
                for (c in s.characteristics) {
                    appendLine("CHAR ${c.uuid} props=${c.properties}")
                    if (BleProfile.UUIDS.contains(c.uuid)) enableNotify(g, c)
                }
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val data = c.value ?: byteArrayOf()
            packets.add("RX", c.uuid.toString(), data)
            appendLine("RX ${c.uuid}: ${data.toHex()}")
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, statusCode: Int) {
            val data = c.value ?: byteArrayOf()
            packets.add("READ", c.uuid.toString(), data)
            appendLine("READ ${c.uuid} status=$statusCode: ${data.toHex()}")
        }
    }

    private fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(BleProfile.CCCD)
        if (d != null) {
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(d)
        }
        appendLine("NOTIFY enabled ${c.uuid}")
    }

    private fun disconnect() {
        gatt?.disconnect()
        status.text = "สถานะ: กำลังตัดการเชื่อมต่อ"
    }

    private fun shareLog() {
        val text = packets.text().ifBlank { "ไม่มี packet" }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "ส่ง Packet Log"))
    }

    private fun appendLine(s: String) {
        runOnUiThread {
            logView.append(s + "\n")
        }
    }

    private fun refreshLog() {
        logView.text = packets.text()
    }

    private fun ByteArray.toHex(): String =
        joinToString(" ") { "%02X".format(it) }

    override fun onDestroy() {
        gatt?.close()
        super.onDestroy()
    }
}
