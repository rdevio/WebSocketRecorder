package io.github.websocketrecorder.android

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class RecorderActivity : Activity() {
    private lateinit var database: RecorderDatabase
    private var showingDetail = false
    private var listView: ListView? = null
    private val reloadInProgress = AtomicBoolean()
    private val reloadPending = AtomicBoolean()
    private val databaseListener: () -> Unit = { reloadRealtime() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = RecorderDatabase(applicationContext, Int.MAX_VALUE)
        showList()
    }

    override fun onStart() {
        super.onStart()
        database.addChangeListener(databaseListener)
        reloadRealtime()
    }

    override fun onStop() {
        database.removeChangeListener(databaseListener)
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (showingDetail) showList() else super.onBackPressed()
    }

    private fun showList() {
        showingDetail = false
        val root = verticalLayout()
        root.addView(header("WebSocket Recorder"))
        listView = ListView(this).also { list ->
            root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        root.addView(Button(this).apply {
            text = "Clear history"
            setOnClickListener {
                thread(name = "websocket-recorder-clear") { database.clear() }
            }
        })
        setContentView(root)
        reloadRealtime()
    }

    private fun reloadRealtime() {
        val target = listView ?: return
        if (showingDetail) return
        if (!reloadInProgress.compareAndSet(false, true)) {
            reloadPending.set(true)
            return
        }
        thread(name = "websocket-recorder-query") {
            val exchanges = database.recent()
            runOnUiThread {
                try {
                    if (!showingDetail && target === listView) {
                        target.adapter = ExchangeAdapter(exchanges)
                        target.setOnItemClickListener { _, _, position, _ ->
                            showExchange(exchanges[position])
                        }
                    }
                } finally {
                    reloadInProgress.set(false)
                    if (reloadPending.getAndSet(false)) reloadRealtime()
                }
            }
        }
    }

    private fun showExchange(exchange: StoredExchange) {
        showingDetail = true
        listView = null
        val root = verticalLayout()
        root.addView(Button(this).apply {
            text = "← Messages"
            setOnClickListener { showList() }
        })
        root.addView(header(exchange.type))
        root.addView(TextView(this).apply {
            text = exchange.uniqueId?.let { "uniqueId: $it" } ?: "No uniqueId"
            setTextIsSelectable(true)
            setPadding(8.dp, 4.dp, 8.dp, 12.dp)
        })

        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        exchange.request?.let { payload ->
            choices.addView(payloadButton("REQUEST", OUTGOING_COLOR) {
                showPayload("Request · ${exchange.type}", payload)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        exchange.response?.let { payload ->
            choices.addView(payloadButton("RESPONSE", INCOMING_COLOR) {
                showPayload("Response · ${exchange.type}", payload)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(choices)
        root.addView(TextView(this).apply {
            text = when {
                exchange.hasRequest && exchange.hasResponse -> "Request matched with response"
                exchange.hasRequest -> "Waiting for response"
                else -> "Incoming message"
            }
            gravity = Gravity.CENTER
            setPadding(12.dp, 24.dp, 12.dp, 12.dp)
        })
        setContentView(root)
    }

    private fun showPayload(title: String, payload: String) {
        showingDetail = true
        val root = verticalLayout()
        root.addView(Button(this).apply {
            text = "← Exchange"
            setOnClickListener { showList() }
        })
        root.addView(header(title))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "Copy"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(title, payload))
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply {
            text = "Share"
            setOnClickListener {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, title)
                            putExtra(Intent.EXTRA_TEXT, payload)
                        },
                        "Share WebSocket message",
                    ),
                )
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)
        root.addView(ScrollView(this).apply {
            addView(TextView(this@RecorderActivity).apply {
                text = JsonPrettyPrinter.format(payload)
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                setPadding(12.dp, 12.dp, 12.dp, 32.dp)
            })
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private inner class ExchangeAdapter(
        private val items: List<StoredExchange>,
    ) : ArrayAdapter<StoredExchange>(
        this,
        android.R.layout.simple_list_item_2,
        android.R.id.text1,
        items,
    ) {
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val item = items[position]
            view.findViewById<TextView>(android.R.id.text1).text = item.type
            val state = when {
                item.hasRequest && item.hasResponse -> "REQ + RES"
                item.hasRequest -> "OUT · waiting"
                else -> "IN"
            }
            view.findViewById<TextView>(android.R.id.text2).text =
                "$state   ${timeFormat.format(Date(item.updatedTimeMs))}"
            view.setBackgroundColor(
                when {
                    item.hasRequest && item.hasResponse -> MATCHED_COLOR
                    item.hasRequest -> OUTGOING_COLOR
                    else -> INCOMING_COLOR
                },
            )
            return view
        }
    }

    private fun payloadButton(label: String, color: Int, action: () -> Unit) =
        Button(this).apply {
            text = label
            setBackgroundColor(color)
            setOnClickListener { action() }
        }

    private fun verticalLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(12.dp, 12.dp, 12.dp, 12.dp)
    }

    private fun header(value: String) = TextView(this).apply {
        text = value
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(8.dp, 12.dp, 8.dp, 12.dp)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val OUTGOING_COLOR = 0xFFFFE0B2.toInt()
        private const val INCOMING_COLOR = 0xFFC8E6C9.toInt()
        private const val MATCHED_COLOR = 0xFFE1BEE7.toInt()

        fun intent(context: Context): Intent =
            Intent(context, RecorderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun launch(context: Context) {
            context.startActivity(intent(context))
        }
    }
}
