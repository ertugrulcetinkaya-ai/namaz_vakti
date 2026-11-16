package com.example.namazvakti.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.DiyanetDistricts
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerLocationConfig
import java.text.Collator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitySelectionScreen(
    selectedLocation: PrayerLocation?,
    selectionStep: Int,
    selectedProvinceCity: String?,
    onBack: () -> Unit,
    onSelectProvince: (String) -> Unit,
    onSelectDistrict: (PrayerLocationConfig.CityOption, String?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val selectedProvince = selectedProvinceCity?.let { city ->
        PrayerLocationConfig.cityOptions.firstOrNull { it.city == city }
    }
    val searchKey = query.trim().citySearchKey()
    val filteredCities = PrayerLocationConfig.cityOptions.filter { city ->
        searchKey.isBlank() || city.displayCity.citySearchKey().contains(searchKey) ||
            city.city.citySearchKey().contains(searchKey)
    }
    val province = selectedProvince
    val centralLabel = stringResource(R.string.district_central)
    val provinceDistricts = province?.let { city ->
        val provinceData = DiyanetDistricts.forCity(city.city, city.country)
        val districts = DiyanetDistricts.districts.filter { it.provinceId == provinceData?.id }
        val central = districts.firstOrNull { it.id == provinceData?.centralDistrictId }
        val collator = Collator.getInstance(Locale.forLanguageTag("tr-TR"))
        listOfNotNull(central) + districts
            .filterNot { it.id == central?.id }
            .sortedWith { left, right -> collator.compare(left.name, right.name) }
    }.orEmpty()
    val filteredDistricts = provinceDistricts.filter { district ->
        val name = if (district.id == province?.let {
                DiyanetDistricts.forCity(it.city, it.country)?.centralDistrictId
            }) {
            centralLabel
        } else {
            district.name
        }
        searchKey.isBlank() || name.citySearchKey().contains(searchKey) ||
            district.name.citySearchKey().contains(searchKey)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionStep == 2 && province != null) {
                            province.displayCity
                        } else {
                            stringResource(R.string.city_selection_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionStep == 2) query = ""
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            stringResource(
                                if (selectionStep == 2) R.string.district_selection_hint else R.string.search_city
                            )
                        )
                    },
                    singleLine = true
                )
            }
            if (selectionStep == 1 || province == null) {
                if (filteredCities.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.city_search_no_results),
                            modifier = Modifier.padding(vertical = 20.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    items(filteredCities, key = { it.country + it.city }) { city ->
                        val selected = selectedLocation?.let {
                            it.city == city.city && it.country == city.country
                        } == true
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    query = ""
                                    onSelectProvince(city.city)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = city.displayCity,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.city_selected),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (filteredDistricts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.city_search_no_results),
                        modifier = Modifier.padding(vertical = 20.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                items(filteredDistricts, key = { it.id }) { district ->
                    val provinceData = DiyanetDistricts.forCity(province.city, province.country)
                    val central = district.id == provinceData?.centralDistrictId
                    val districtName = if (central) {
                        stringResource(R.string.district_central)
                    } else {
                        district.name
                    }
                    val selected = selectedLocation?.let { location ->
                        location.city == province.city && location.country == province.country &&
                            if (central) location.isCentralDistrict else location.diyanetDistrictId == district.id
                    } == true
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDistrict(province, district.id.takeUnless { central }) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = districtName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.district_selected),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
