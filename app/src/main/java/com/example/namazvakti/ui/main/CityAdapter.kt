package com.example.namazvakti.ui.main

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerLocationConfig
import androidx.core.view.setPadding

class CityAdapter(
    private val context: Context,
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

    fun cityAt(position: Int): PrayerLocationConfig.CityOption {
        require(position in filteredItems.indices) {
            "City position $position is outside the filtered list"
        }
        return filteredItems[position]
    }

    fun positionOf(location: PrayerLocation): Int = filteredItems.indexOfFirst {
        it.city == location.city && it.country == location.country
    }

    override fun getCount(): Int = filteredItems.size
    override fun getItem(position: Int): Any = cityAt(position)
    override fun getItemId(position: Int): Long = cityAt(position).displayCity.hashCode().toLong()
    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = (convertView as? TextView) ?: TextView(context)
        val city = cityAt(position)
        view.text = city.displayCity
        view.textSize = 20f
        view.setTextColor(context.getColor(R.color.screen_text))
        view.setPadding(context.resources.getDimensionPixelSize(R.dimen.city_row_padding))
        view.contentDescription = city.displayCity
        return view
    }
}
