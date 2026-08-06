package com.example.namazvakti

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: PrayerViewModel by viewModels()
    private val allCities = PrayerLocationConfig.cityOptions
    private lateinit var adapter: CityAdapter
    private lateinit var status: TextView
    private lateinit var refreshButton: Button
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = resources.getDimensionPixelSize(R.dimen.screen_padding)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding)
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

    private fun renderState(state: PrayerUiState) {
        when (state) {
            PrayerUiState.Loading -> {
                status.text = getString(R.string.refreshing)
                refreshButton.isEnabled = false
            }
            is PrayerUiState.Ready -> {
                val base = getString(R.string.current_city, state.location.displayCity)
                status.text = when (state.operation) {
                    OperationState.Refreshing -> "$base — ${getString(R.string.refreshing)}"
                    OperationState.Refreshed -> "$base — ${getString(R.string.refresh_success)}"
                    OperationState.RefreshFailed -> "$base — ${getString(R.string.stale_data)}"
                    OperationState.Idle -> if (state.freshness == Freshness.Stale) {
                        "$base — ${getString(R.string.stale_data)}"
                    } else base
                }
                refreshButton.isEnabled = true
                val index = allCities.indexOfFirst { it.displayCity == state.location.displayCity }
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

        override fun getCount(): Int = filteredItems.size
        override fun getItem(position: Int): Any = cityAt(position)
        override fun getItemId(position: Int): Long = cityAt(position).displayCity.hashCode().toLong()
        override fun hasStableIds(): Boolean = false

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
}
