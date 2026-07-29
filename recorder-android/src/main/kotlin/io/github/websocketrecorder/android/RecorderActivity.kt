package io.github.websocketrecorder.android

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class RecorderActivity : Activity() {
    private lateinit var database: RecorderDatabase
    private var screen = Screen.LIST
    private var selectedExchange: StoredExchange? = null
    private var listView: ListView? = null
    private var filterButton: Button? = null
    private var listUpdateVersion = 0L
    private val availableTypes = linkedSetOf<String>()
    private val hiddenTypes = linkedSetOf<String>()
    private val reloadInProgress = AtomicBoolean()
    private val reloadPending = AtomicBoolean()
    private var backInvokedCallback: OnBackInvokedCallback? = null
    private val databaseListener: () -> Unit = {
        runOnUiThread { reloadRealtime() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = RecorderDatabase(applicationContext, Int.MAX_VALUE)
        registerPredictiveBackCallback()
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

    override fun onDestroy() {
        unregisterPredictiveBackCallback()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!navigateBack()) {
            super.onBackPressed()
        }
    }

    private fun navigateBack(): Boolean {
        when (screen) {
            Screen.PAYLOAD -> {
                selectedExchange?.let(::showExchange) ?: showList()
                return true
            }
            Screen.EXCHANGE -> {
                showList()
                return true
            }
            Screen.LIST -> return false
        }
    }

    private fun registerPredictiveBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = OnBackInvokedCallback {
            if (!navigateBack()) finish()
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback,
        )
        backInvokedCallback = callback
    }

    private fun unregisterPredictiveBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        backInvokedCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
        backInvokedCallback = null
    }

    private fun showList() {
        screen = Screen.LIST
        selectedExchange = null
        val root = verticalLayout()
        root.addView(header("WebSocket Recorder"))
        filterButton = Button(this).also { button ->
            button.setOnClickListener { showTypeFilter() }
            root.addView(button)
        }
        updateFilterButton()
        listView = ListView(this).also { list ->
            list.adapter = ExchangeAdapter(emptyList())
            list.setOnItemClickListener { _, _, position, _ ->
                val adapter = list.adapter as? ExchangeAdapter ?: return@setOnItemClickListener
                showExchange(adapter.getItem(position))
            }
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
        if (screen != Screen.LIST) return
        val scrollAnchor = target.captureScrollAnchor()
        if (!reloadInProgress.compareAndSet(false, true)) {
            reloadPending.set(true)
            return
        }
        thread(name = "websocket-recorder-query") {
            val exchanges = database.recent()
            runOnUiThread {
                try {
                    if (screen == Screen.LIST && target === listView) {
                        availableTypes.clear()
                        availableTypes += exchanges.map { it.type }
                        hiddenTypes.retainAll(availableTypes)
                        updateFilterButton()
                        val visibleExchanges = exchanges.filterNot { it.type in hiddenTypes }
                        val adapter = target.adapter as ExchangeAdapter
                        adapter.submitList(visibleExchanges)
                        val updateVersion = ++listUpdateVersion
                        scrollAnchor?.let { anchor ->
                            target.restoreScrollAnchor(adapter, anchor)
                            target.post {
                                if (
                                    screen == Screen.LIST &&
                                    target === listView &&
                                    updateVersion == listUpdateVersion
                                ) {
                                    target.restoreScrollAnchor(adapter, anchor)
                                }
                            }
                        }
                    }
                } finally {
                    reloadInProgress.set(false)
                    if (reloadPending.getAndSet(false)) reloadRealtime()
                }
            }
        }
    }

    private fun showTypeFilter() {
        val types = availableTypes.sorted()
        if (types.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Hide message types")
                .setMessage("No message types have been recorded yet.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val pendingHiddenTypes = hiddenTypes.toMutableSet()
        val checkedItems = BooleanArray(types.size) { index ->
            types[index] in pendingHiddenTypes
        }
        AlertDialog.Builder(this)
            .setTitle("Hide message types")
            .setMultiChoiceItems(
                types.toTypedArray(),
                checkedItems,
            ) { _, index, checked ->
                if (checked) {
                    pendingHiddenTypes += types[index]
                } else {
                    pendingHiddenTypes -= types[index]
                }
            }
            .setNeutralButton("Show all") { _, _ ->
                hiddenTypes.clear()
                updateFilterButton()
                reloadRealtime()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Apply") { _, _ ->
                hiddenTypes.clear()
                hiddenTypes += pendingHiddenTypes
                updateFilterButton()
                reloadRealtime()
            }
            .show()
    }

    private fun updateFilterButton() {
        filterButton?.text = if (hiddenTypes.isEmpty()) {
            "Filter message types"
        } else {
            "Filter · ${hiddenTypes.size} hidden"
        }
    }

    private fun ListView.captureScrollAnchor(): ScrollAnchor? {
        if (adapter == null || adapter.count == 0) return null
        val firstPosition = firstVisiblePosition
        val firstView = getChildAt(0) ?: return null
        val isAtTop = firstPosition == 0 && firstView.top >= paddingTop
        if (isAtTop) return null
        val exchange = adapter.getItem(firstPosition) as? StoredExchange ?: return null
        return ScrollAnchor(
            exchangeId = exchange.id,
            topOffset = firstView.top,
        )
    }

    private fun ListView.restoreScrollAnchor(
        adapter: ExchangeAdapter,
        anchor: ScrollAnchor,
    ) {
        val newPosition = adapter.positionOf(anchor.exchangeId)
        if (newPosition >= 0) {
            setSelectionFromTop(newPosition, anchor.topOffset)
        }
    }

    private fun showExchange(exchange: StoredExchange) {
        screen = Screen.EXCHANGE
        selectedExchange = exchange
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
                showPayload(exchange, "Request · ${exchange.type}", payload)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        exchange.response?.let { payload ->
            choices.addView(payloadButton("RESPONSE", INCOMING_COLOR) {
                showPayload(exchange, "Response · ${exchange.type}", payload)
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

    private fun showPayload(exchange: StoredExchange, title: String, payload: String) {
        screen = Screen.PAYLOAD
        selectedExchange = exchange
        val root = verticalLayout()
        root.addView(Button(this).apply {
            text = "← Exchange"
            setOnClickListener { showExchange(exchange) }
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
        initialItems: List<StoredExchange>,
    ) : BaseAdapter() {
        private val items = initialItems.toMutableList()
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): StoredExchange = items[position]

        override fun getItemId(position: Int): Long = items[position].id

        override fun hasStableIds(): Boolean = true

        fun submitList(newItems: List<StoredExchange>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun positionOf(exchangeId: Long): Int =
            items.indexOfFirst { it.id == exchangeId }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder: ExchangeViewHolder
            val view: View
            if (convertView == null) {
                view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.websocket_recorder_item_exchange, parent, false)
                holder = ExchangeViewHolder(view)
                view.tag = holder
            } else {
                holder = convertView.tag as ExchangeViewHolder
                view = convertView
            }

            val item = getItem(position)
            val appearance = when {
                item.hasRequest && item.hasResponse ->
                    ItemAppearance("REQ + RES", MATCHED_COLOR, MATCHED_TEXT_COLOR)
                item.hasRequest ->
                    ItemAppearance("OUT · WAITING", OUTGOING_COLOR, OUTGOING_TEXT_COLOR)
                else ->
                    ItemAppearance("IN", INCOMING_COLOR, INCOMING_TEXT_COLOR)
            }
            holder.type.text = item.type
            holder.uniqueId.text = item.uniqueId?.let { "ID  $it" } ?: "No uniqueId"
            holder.time.text = timeFormat.format(Date(item.updatedTimeMs))
            holder.state.text = appearance.label
            holder.state.setTextColor(appearance.textColor)
            holder.state.background = roundedBackground(appearance.backgroundColor)
            holder.indicator.setBackgroundColor(appearance.textColor)
            return view
        }
    }

    private class ExchangeViewHolder(view: View) {
        val indicator: View =
            view.findViewById(R.id.websocket_recorder_direction_indicator)
        val type: TextView =
            view.findViewById(R.id.websocket_recorder_type)
        val state: TextView =
            view.findViewById(R.id.websocket_recorder_state)
        val uniqueId: TextView =
            view.findViewById(R.id.websocket_recorder_unique_id)
        val time: TextView =
            view.findViewById(R.id.websocket_recorder_time)
    }

    private data class ItemAppearance(
        val label: String,
        val backgroundColor: Int,
        val textColor: Int,
    )

    private data class ScrollAnchor(
        val exchangeId: Long,
        val topOffset: Int,
    )

    private fun roundedBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 12.dp.toFloat()
        setColor(color)
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
        private const val OUTGOING_TEXT_COLOR = 0xFFE65100.toInt()
        private const val INCOMING_TEXT_COLOR = 0xFF1B5E20.toInt()
        private const val MATCHED_TEXT_COLOR = 0xFF6A1B9A.toInt()

        fun intent(context: Context): Intent =
            Intent(context, RecorderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun launch(context: Context) {
            context.startActivity(intent(context))
        }
    }

    private enum class Screen {
        LIST,
        EXCHANGE,
        PAYLOAD,
    }
}
