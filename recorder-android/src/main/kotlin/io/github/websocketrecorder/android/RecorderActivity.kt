package io.github.websocketrecorder.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread

class RecorderActivity : Activity() {
    private lateinit var database: RecorderDatabase
    private var showingDetail = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = RecorderDatabase(applicationContext, maxStoredEvents = Int.MAX_VALUE)
        showEventList()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (showingDetail) {
            showEventList()
        } else {
            super.onBackPressed()
        }
    }

    private fun showEventList() {
        showingDetail = false
        val root = verticalLayout()
        root.addView(header("WebSocket Recorder"))

        val list = ListView(this).apply {
            emptyView = TextView(this@RecorderActivity).apply {
                text = "No WebSocket events captured"
                gravity = Gravity.CENTER
                setPadding(24.dp, 48.dp, 24.dp, 48.dp)
            }
        }
        val empty = list.emptyView
        root.addView(
            list,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        root.addView(
            empty,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val clear = Button(this).apply {
            text = "Clear history"
            setOnClickListener {
                thread(name = "websocket-recorder-clear") {
                    database.clear()
                    runOnUiThread { showEventList() }
                }
            }
        }
        root.addView(clear)
        setContentView(root)

        thread(name = "websocket-recorder-query") {
            val events = database.recent()
            runOnUiThread {
                list.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    events.map { it.title },
                )
                list.onItemClickListener =
                    AdapterView.OnItemClickListener { _, _, position, _ ->
                        showEventDetail(events[position])
                    }
            }
        }
    }

    private fun showEventDetail(event: StoredEvent) {
        showingDetail = true
        val root = verticalLayout()
        val back = Button(this).apply {
            text = "← Messages"
            setOnClickListener { showEventList() }
        }
        root.addView(back)
        root.addView(header(event.title))

        val displayedPayload = event.payload.orEmpty().let { payload ->
            if (event.isJsonCandidate) JsonPrettyPrinter.format(payload) else payload
        }
        val content = TextView(this).apply {
            text = displayedPayload.ifEmpty { "No payload" }
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(16.dp, 16.dp, 16.dp, 32.dp)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        setContentView(root)
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

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        fun intent(context: Context): Intent = Intent(context, RecorderActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun launch(context: Context) {
            context.startActivity(intent(context))
        }
    }
}
