package com.flowforge.android.triggers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.flowforge.android.FlowForgeApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

data class NfcWriteResult(
    val written: Boolean,
    val tagId: String = "",
    val bytes: Int = 0,
    val error: String? = null,
)

/** Coordinates the "arm, then tap a tag" flow used by the NFC write module. */
object NfcWriteGate {

    @Volatile
    internal var pending: CompletableDeferred<NfcWriteResult>? = null

    @Volatile
    internal var payload: String = ""

    @Volatile
    internal var asUrl: Boolean = false

    suspend fun arm(context: Context, text: String, url: Boolean, timeoutMs: Long): NfcWriteResult {
        val adapter = NfcAdapter.getDefaultAdapter(context)
            ?: return NfcWriteResult(false, error = "This device has no NFC")
        if (!adapter.isEnabled) {
            return NfcWriteResult(false, error = "NFC is switched off — turn it on in Android settings")
        }

        val deferred = CompletableDeferred<NfcWriteResult>()
        pending = deferred
        payload = text
        asUrl = url

        runCatching {
            context.startActivity(
                Intent(context, NfcActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }.onFailure { return NfcWriteResult(false, error = "Could not open the NFC screen") }

        return withTimeoutOrNull(timeoutMs) { deferred.await() }
            ?: NfcWriteResult(false, error = "No tag was tapped in time").also { pending = null }
    }

    internal fun finish(result: NfcWriteResult) {
        pending?.complete(result)
        pending = null
    }
}

/**
 * Foreground screen used for both halves of NFC: writing a tag when a scenario arms a write, and
 * feeding the "NFC tag scanned" trigger when a tag is tapped while it is open.
 */
class NfcActivity : ComponentActivity() {

    private var adapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        setContentView(
            TextView(this).apply {
                text = if (NfcWriteGate.pending != null) {
                    "Hold an NFC tag against the back of the phone to write it."
                } else {
                    "Hold an NFC tag against the back of the phone."
                }
                textSize = 18f
                setPadding(48, 96, 48, 48)
            }
        )
        handleTag(intent)
    }

    override fun onResume() {
        super.onResume()
        val pending = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_MUTABLE,
        )
        runCatching { adapter?.enableForegroundDispatch(this, pending, null, null) }
    }

    override fun onPause() {
        runCatching { adapter?.disableForegroundDispatch(this) }
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTag(intent)
    }

    override fun onDestroy() {
        // A write that never happened should not leave a scenario hanging.
        NfcWriteGate.pending?.let { NfcWriteGate.finish(NfcWriteResult(false, error = "The NFC screen was closed")) }
        super.onDestroy()
    }

    private fun handleTag(intent: Intent?) {
        val tag: Tag = (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            ) ?: return

        val tagId = tag.id?.joinToString("") { "%02x".format(it) }.orEmpty()

        if (NfcWriteGate.pending != null) {
            val result = writeTag(tag, tagId)
            NfcWriteGate.finish(result)
            setResult(Activity.RESULT_OK)
            finish()
            return
        }

        // Otherwise this is a read — feed the trigger.
        val payload = readTag(tag)
        FlowForgeApp.instance.dispatchTrigger(
            "trigger.nfc",
            mapOf("id" to tagId, "payload" to payload, "techs" to tag.techList.toList()),
            "NFC tag $tagId",
        ) { trigger ->
            val wanted = trigger.param("contains").trim()
            wanted.isBlank() || payload.contains(wanted, ignoreCase = true)
        }
        finish()
    }

    private fun writeTag(tag: Tag, tagId: String): NfcWriteResult {
        val record = if (NfcWriteGate.asUrl) {
            NdefRecord.createUri(NfcWriteGate.payload)
        } else {
            NdefRecord.createTextRecord("en", NfcWriteGate.payload)
        }
        val message = NdefMessage(arrayOf(record))
        val size = message.toByteArray().size

        Ndef.get(tag)?.let { ndef ->
            return runCatching {
                ndef.connect()
                if (!ndef.isWritable) error("That tag is read-only")
                if (ndef.maxSize < size) error("That tag only holds ${ndef.maxSize} bytes, needs $size")
                ndef.writeNdefMessage(message)
                ndef.close()
                NfcWriteResult(true, tagId, size)
            }.getOrElse { NfcWriteResult(false, tagId, 0, it.message) }
        }

        NdefFormatable.get(tag)?.let { formatable ->
            return runCatching {
                formatable.connect()
                formatable.format(message)
                formatable.close()
                NfcWriteResult(true, tagId, size)
            }.getOrElse { NfcWriteResult(false, tagId, 0, it.message) }
        }

        return NfcWriteResult(false, tagId, 0, "That tag cannot hold an NDEF message")
    }

    private fun readTag(tag: Tag): String {
        val ndef = Ndef.get(tag) ?: return ""
        return runCatching {
            ndef.connect()
            val message = ndef.cachedNdefMessage ?: ndef.ndefMessage
            ndef.close()
            message?.records?.joinToString("\n") { record ->
                runCatching { String(record.payload).trimStart { it.code < 32 } }.getOrDefault("")
            }.orEmpty()
        }.getOrDefault("")
    }
}
