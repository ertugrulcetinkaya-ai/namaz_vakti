package com.example.namazvakti

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val tag = "NamazWidget"
    private val allCities = PrayerLocationConfig.cityOptions
    private lateinit var store: PrayerTimesStore
    private lateinit var adapter: CityAdapter
    private lateinit var status: TextView
    private lateinit var searchField: EditText
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = (application as NamazVaktiApp).container.store

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32)
        }

        val title = TextView(this).apply {
            text = "Select city"
            textSize = 20f
        }

        status = TextView(this).apply {
            textSize = 16f
            text = "Current city: ANKARA"
            setPadding(0, 16, 0, 16)
        }

        searchField = EditText(this).apply {
            hint = "Şehir ara..."
            setSingleLine(true)
        }

        val refreshButton = Button(this).apply {
            text = "Vakitleri Yenile"
            setOnClickListener {
                Log.d(this@MainActivity.tag, "manual refresh tapped city=${status.text.toString()}")
                status.text = "Yenileme başlatıldı"
                lifecycleScope.launch {
                    PrayerWidgetScheduler.enqueueRefresh(this@MainActivity, force = true)
                    Log.d(this@MainActivity.tag, "manual refresh worker enqueued force=true")
                }
            }
        }

        listView = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            dividerHeight = 1
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            )
        }

        adapter = CityAdapter(this, allCities.toMutableList())
        listView.adapter = adapter

        lifecycleScope.launch {
            val currentSelection = withContext(Dispatchers.IO) {
                val location = store.readLocation()
                PrayerLocationConfig.optionForCityAndCountry(location.city, location.country)
            }
            val currentIndex = allCities.indexOfFirst {
                it.displayCity == currentSelection.displayCity
            }.coerceAtLeast(0)
            listView.setItemChecked(currentIndex, true)
            listView.setSelection(currentIndex)
            status.text = "Current city: ${currentSelection.displayCity}"
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val chosen = adapter.cityAt(position)
            Log.d(this@MainActivity.tag, "city row tapped displayName=${chosen.displayCity}")
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    Log.d(this@MainActivity.tag, "saving selected city=${chosen.city}")
                    store.saveLocation(PrayerLocation(chosen.city, chosen.country, chosen.displayCity))
                    store.clearCache()
                    Log.d(this@MainActivity.tag, "cache cleared for city change")
                }
                status.text = "Current city: ${chosen.displayCity}"
                Log.d(this@MainActivity.tag, "selected city after save=${chosen.displayCity}")
                Log.d(this@MainActivity.tag, "city selection refresh enqueued force=true")
                PrayerWidgetScheduler.enqueueRefresh(this@MainActivity, force = true)
            }
        }

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString().orEmpty())
            }
        })

        root.addView(title)
        root.addView(status)
        root.addView(searchField)
        root.addView(refreshButton)
        root.addView(
            listView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)
    }

    private class CityAdapter(
        private val activity: AppCompatActivity,
        private val allItems: List<PrayerLocationConfig.CityOption>
    ) : BaseAdapter() {
        private var filteredItems: MutableList<PrayerLocationConfig.CityOption> = allItems.toMutableList()

        fun filter(query: String) {
            val needle = query.trim()
            filteredItems = if (needle.isBlank()) {
                allItems.toMutableList()
            } else {
                allItems.filter { it.displayCity.contains(needle, ignoreCase = true) }.toMutableList()
            }
            notifyDataSetChanged()
        }

        fun cityAt(position: Int): PrayerLocationConfig.CityOption {
            return filteredItems.getOrNull(position) ?: PrayerLocationConfig.defaultCity
        }

        override fun getCount(): Int = filteredItems.size

        override fun getItem(position: Int): Any = cityAt(position)

        override fun getItemId(position: Int): Long = cityAt(position).displayCity.hashCode().toLong()

        override fun hasStableIds(): Boolean = false

        override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
            val view = (convertView as? TextView) ?: TextView(activity)
            val city = cityAt(position)
            view.text = city.displayCity
            view.textSize = 20f
            view.setPadding(32, 32, 32, 32)
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            return view
        }
    }
}
