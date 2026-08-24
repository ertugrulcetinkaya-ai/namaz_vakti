package com.example.namazvakti

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private val viewModel: PrayerViewModel by viewModels()
    private val allCities = PrayerLocationConfig.cityOptions
    private lateinit var adapter: CityAdapter
    private lateinit var status: TextView
    private lateinit var refreshButton: Button
    private lateinit var exactAlarmInfo: TextView
    private lateinit var exactAlarmButton: Button
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val padding = resources.getDimensionPixelSize(R.dimen.screen_padding)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                padding + systemBars.left,
                padding + systemBars.top,
                padding + systemBars.right,
                padding + systemBars.bottom
            )
            insets
        }
        val title = TextView(this).apply {
            text = getString(R.string.select_city)
            textSize = 20f
            setTextColor(getColor(R.color.screen_text))
        }
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, padding / 2, 0, padding / 2)
            setTextColor(getColor(R.color.screen_secondary_text))
        }
        val searchField = EditText(this).apply {
            hint = getString(R.string.search_city)
            contentDescription = getString(R.string.search_city)
            setSingleLine(true)
        }
        refreshButton = Button(this).apply {
            text = getString(R.string.refresh_prayer_times)
            setOnClickListener { viewModel.refresh() }
        }
        exactAlarmInfo = TextView(this).apply {
            text = getString(R.string.precise_widget_updates_disabled)
            setTextColor(getColor(R.color.screen_secondary_text))
        }
        exactAlarmButton = Button(this).apply {
            text = getString(R.string.enable_precise_widget_updates)
            setOnClickListener {
                startActivity(PrayerExactAlarmAccess.requestAccessIntent(this@MainActivity))
            }
        }
        listView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            dividerHeight = 1
        }
        adapter = CityAdapter(this, allCities)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectCity(adapter.cityAt(position))
        }
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { adapter.filter(s?.toString().orEmpty()) }
        })

        root.addView(title)
        root.addView(status)
        root.addView(searchField)
        root.addView(refreshButton)
        root.addView(exactAlarmInfo)
        root.addView(exactAlarmButton)
        root.addView(listView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { renderState(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val accessNeeded = PrayerExactAlarmAccess.requiresUserAccess() &&
            !PrayerExactAlarmAccess.canScheduleExactAlarms(this)
        exactAlarmInfo.visibility = if (accessNeeded) View.VISIBLE else View.GONE
        exactAlarmButton.visibility = if (accessNeeded) View.VISIBLE else View.GONE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(applicationContext)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "precise widget alarm setup failed", exception)
            }
        }
    }

    private fun renderState(state: PrayerUiState) {
        when (state) {
            PrayerUiState.Loading -> {
                status.text = getString(R.string.refreshing)
                refreshButton.isEnabled = false
            }
            is PrayerUiState.Ready -> {
                val base = getString(R.string.current_city, state.location.displayCity)
                val stateText = when (state.operation) {
                    OperationState.Refreshing -> "$base — ${getString(R.string.refreshing)}"
                    OperationState.Refreshed -> if (state.refreshOrigin == RefreshOrigin.LOCAL_CACHE) {
                        "$base — ${getString(R.string.offline_data)}"
                    } else {
                        "$base — ${getString(R.string.refresh_success)}"
                    }
                    OperationState.RefreshFailed -> "$base — ${getString(R.string.stale_data)}"
                    OperationState.Idle -> if (state.freshness == Freshness.Stale) {
                        "$base — ${getString(R.string.stale_data)}"
                    } else base
                }
                val metadataText = state.cache?.let { cache ->
                    val schoolName = if (
                        cache.settings.school == PrayerCalculationSettings.SCHOOL_HANAFI
                    ) getString(R.string.school_hanafi) else getString(R.string.school_standard)
                    getString(
                        R.string.cache_metadata,
                        cache.source.displayName,
                        cache.settings.method,
                        schoolName,
                        cache.fetchedAt.atZone(cache.timezone).format(METADATA_TIME_FORMATTER)
                    )
                }
                status.text = listOfNotNull(stateText, metadataText).joinToString("\n")
                refreshButton.isEnabled = state.operation != OperationState.Refreshing
                val index = adapter.positionOf(state.location)
                if (index >= 0) {
                    listView.setItemChecked(index, true)
                    listView.setSelection(index)
                }
            }
            is PrayerUiState.Error -> {
                status.text = getString(R.string.refresh_error)
                refreshButton.isEnabled = true
            }
        }
    }

    private class CityAdapter(
        private val activity: AppCompatActivity,
        private val allItems: List<PrayerLocationConfig.CityOption>
    ) : BaseAdapter() {
        private var filteredItems = allItems

        fun filter(query: String) {
            val needle = query.trim().citySearchKey()
            filteredItems = if (needle.isBlank()) allItems else allItems.filter {
                it.displayCity.citySearchKey().contains(needle)
            }
            notifyDataSetChanged()
        }

        fun cityAt(position: Int): PrayerLocationConfig.CityOption =
            filteredItems.getOrNull(position) ?: PrayerLocationConfig.defaultCity

        fun positionOf(location: PrayerLocation): Int = filteredItems.indexOfFirst {
            it.city == location.city && it.country == location.country
        }

        override fun getCount(): Int = filteredItems.size
        override fun getItem(position: Int): Any = cityAt(position)
        override fun getItemId(position: Int): Long = cityAt(position).displayCity.hashCode().toLong()
        override fun hasStableIds(): Boolean = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView as? TextView) ?: TextView(activity)
            val city = cityAt(position)
            view.text = city.displayCity
            view.textSize = 20f
            view.setTextColor(activity.getColor(R.color.screen_text))
            view.setPadding(activity.resources.getDimensionPixelSize(R.dimen.city_row_padding))
            view.contentDescription = city.displayCity
            return view
        }
    }

    private companion object {
        const val TAG = "NamazWidget"
        val METADATA_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }
}
