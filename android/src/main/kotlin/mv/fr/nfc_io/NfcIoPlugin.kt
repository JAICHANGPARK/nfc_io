package mv.fr.nfc_io

import android.Manifest
import android.content.Context
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.EventChannel
import java.nio.charset.Charset
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

const val PERMISSION_NFC = 1007

class NfcIoPlugin :
        FlutterPlugin, MethodCallHandler,
        EventChannel.StreamHandler, NfcAdapter.ReaderCallback,
        ActivityAware {


    private var isReading = false
    private var nfcAdapter: NfcAdapter? = null
    private var nfcManager: NfcManager? = null

    private var eventSink: EventChannel.EventSink? = null

    private var kId = "nfcId"
    private var kContent = "nfcContent"
    private var kError = "nfcError"
    private var kStatus = "nfcStatus"

    private var READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V

    private lateinit var channel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var activity: FlutterActivity? = null

//  companion object {
//    @JvmStatic
//    fun registerWith(registrar: Registrar) {
//      val messenger = registrar.messenger();
//      val channel = MethodChannel(messenger, "nfc_io")
//      val eventChannel = EventChannel(messenger, "mv.fr.nfc_io.nfc_io")
//      val plugin = NfcIoPlugin(registrar)
//      channel.setMethodCallHandler(plugin)
//      eventChannel.setStreamHandler(plugin)
//    }
//  }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "nfc_io")
        channel.setMethodCallHandler(this)
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "mv.fr.nfc_io.nfc_io")
        eventChannel.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(p0: FlutterPlugin.FlutterPluginBinding) {
//        TODO("Not yet implemented")
        channel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }

    override fun onAttachedToActivity(p0: ActivityPluginBinding) {
        activity = p0.activity as FlutterActivity
        nfcManager = p0.activity.getSystemService(Context.NFC_SERVICE) as? NfcManager
        nfcAdapter = nfcManager?.defaultAdapter
    }

    override fun onDetachedFromActivityForConfigChanges() {

    }

    override fun onReattachedToActivityForConfigChanges(p0: ActivityPluginBinding) {

    }

    override fun onDetachedFromActivity() {

    }

    override fun onMethodCall(call: MethodCall, rawResult: Result) {
        val result = MethodResultWrapper(rawResult)

        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${Build.VERSION.RELEASE}")
            }
            "startReading" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    activity?.requestPermissions(
                            arrayOf(Manifest.permission.NFC),
                            PERMISSION_NFC
                    )
                }

                if (nfcAdapter == null) {
                    result.error("404", "NFC is not supported on this device", null)
                } else if (nfcAdapter?.isEnabled == false) {
                    result.error("501", "NFC is switched off. Consider switching it on", null)
                } else {
                    startNFC()
                    result.success(true)
                }
            }
            "stopReading" -> {
                stopNFC()
                val data = mapOf(kId to "", kContent to "", kError to "", kStatus to "stopped")
                result.success(data)
            }
            else -> {
                result.notImplemented()
            }
        }

        if (call.method == "getPlatformVersion") {
            result.success("Android ${android.os.Build.VERSION.RELEASE}")
        }
    }

    override fun onListen(arguements: Any?, rawEventSink: EventChannel.EventSink?) {
        this.eventSink = MainThreadEventSinkWrapper(rawEventSink!!)
    }

    override fun onCancel(arguements: Any?) {
        this.eventSink = null
        stopNFC()
    }

    private fun startNFC(): Boolean {
        isReading = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            nfcAdapter?.enableReaderMode(activity, this, READER_FLAGS, null)
        }

        return isReading
    }

    private fun stopNFC() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            nfcAdapter?.disableReaderMode(activity)
        }
        this.isReading = false
        this.eventSink = null
    }

    override fun onTagDiscovered(tag: Tag?) {
        // convert tag to NDEF tag
        val ndef = Ndef.get(tag)
        // ndef will be null if the discovered tag is not a NDEF tag
        // read NDEF message
        if (ndef == null) {
            eventSink?.success(mapOf(kId to bytesToHex(tag!!.id), kContent to tag.toString(), kError to "", kStatus to "read"))
        } else {
            ndef.connect()
            val message = ndef.ndefMessage
                    ?.toByteArray()
                    ?.toString(Charset.forName("UTF-8")) ?: ""
            val id = NDEFBytesToHexString(tag?.id) ?: ""
            ndef.close()
            val data = mapOf(kId to id, kContent to message, kError to "", kStatus to "read")
            eventSink?.success(data)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        var hex: String = ""
        for (b in bytes) {
            val st = String.format("%02X", b)
            hex = "$hex$st";
        }
        return "0x$hex".toLowerCase();
    }

    private fun NDEFBytesToHexString(src: ByteArray?): String? {
        val stringBuilder = StringBuilder("0x")
        if (src == null || src.isEmpty()) {
            return null
        }

        val buffer = CharArray(2)
        for (i in src.indices) {
            buffer[0] = Character.forDigit(src[i].toInt().ushr(4).and(0x0F), 16)
            buffer[1] = Character.forDigit(src[i].toInt().and(0x0F), 16)
            stringBuilder.append(buffer)
        }

        return stringBuilder.toString()
    }


}
