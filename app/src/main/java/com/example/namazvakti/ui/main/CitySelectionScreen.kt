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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    var query by rememberSaveable(selectionStep, selectedProvinceCity) { mutableStateOf("") }
    val selectedProvince = remember(selectedProvinceCity) {
        selectedProvinceCity?.let { city ->
            PrayerLocationConfig.cityOptions.firstOrNull { it.city == city }
        }
    }
    val searchKey by remember(query) {
        derivedStateOf { query.trim().citySearchKey() }
    }
    val province = selectedProvince
    val centralLabel = stringResource(R.string.district_central)
    val collator = remember { Collator.getInstance(Locale.forLanguageTag("tr-TR")) }
    val provinceData = remember(province) {
        province?.let { DiyanetDistricts.forCity(it.city, it.country) }
    }
    val centralDistrictId = provinceData?.centralDistrictId
    val provinceDistricts by remember(provinceData, collator) {
        derivedStateOf {
            val districts = provinceData?.let { DiyanetDistricts.byProvinceId[it.id] }.orEmpty()
            val central = centralDistrictId?.let(DiyanetDistricts.byId::get)
            listOfNotNull(central) + districts
                .filterNot { it.id == central?.id }
                .sortedWith { left, right -> collator.compare(left.name, right.name) }
        }
    }
    val filteredCities by remember(searchKey) {
        derivedStateOf {
            PrayerLocationConfig.cityOptions.filter { city ->
                searchKey.isBlank() || city.displayCity.citySearchKey().contains(searchKey) ||
                    city.city.citySearchKey().contains(searchKey)
            }
        }
    }
    val filteredDistricts by remember(provinceDistricts, searchKey, centralLabel, centralDistrictId) {
        derivedStateOf {
            provinceDistricts.filter { district ->
                val name = if (district.id == centralDistrictId) centralLabel else district.name
                searchKey.isBlank() || name.citySearchKey().contains(searchKey) ||
                    district.name.citySearchKey().contains(searchKey)
            }
        }
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
                    IconButton(onClick = onBack) {
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
                                .clickable { onSelectProvince(city.city) },
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
                    val central = district.id == centralDistrictId
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
