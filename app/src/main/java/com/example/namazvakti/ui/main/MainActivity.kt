package com.example.namazvakti.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.PrayerCalculationSettings
import com.example.namazvakti.domain.model.PrayerError
import com.example.namazvakti.domain.model.PrayerLocationConfig
import com.example.namazvakti.domain.model.PrayerNotificationPrayer
import com.example.namazvakti.domain.model.PrayerNotificationSettings
import com.example.namazvakti.domain.model.RefreshOrigin
import com.example.namazvakti.widget.alarm.PrayerWidgetScheduler
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
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
import com.example.namazvakti.notification.PrayerNotificationScheduler

class MainActivity : AppCompatActivity() {
    private val viewModel: PrayerViewModel by viewModels()
    private val allCities = PrayerLocationConfig.cityOptions
    private lateinit var adapter: CityAdapter
    private lateinit var status: TextView
    private lateinit var refreshButton: Button
    private lateinit var exactAlarmInfo: TextView
    private lateinit var exactAlarmButton: Button
    private lateinit var notificationSettingsButton: Button
    private lateinit var listView: ListView
    private lateinit var exactAlarmController: PrayerExactAlarmController
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private var notificationDialog: AlertDialog? = null
    private var notificationDialogSwitch: SwitchCompat? = null
    private var notificationDialogControls: List<View> = emptyList()
    private var pendingNotificationSettings: PrayerNotificationSettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val pending = pendingNotificationSettings ?: return@registerForActivityResult
            pendingNotificationSettings = null
            if (granted) {
                viewModel.updateNotificationSettings(pending)
                notificationDialog?.dismiss()
            } else {
                viewModel.updateNotificationSettings(pending.copy(notificationsEnabled = false))
                notificationDialogSwitch?.isChecked = false
                setNotificationControlsEnabled(false)
                Toast.makeText(
                    this,
                    R.string.notification_prayer_permission_denied,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

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
            setOnClickListener { exactAlarmController.requestAccess() }
        }
        notificationSettingsButton = Button(this).apply {
            text = getString(R.string.notification_settings)
            isEnabled = false
            setOnClickListener { showNotificationSettings() }
        }
        exactAlarmController = PrayerExactAlarmController(this)
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
        root.addView(notificationSettingsButton)
        root.addView(listView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { renderState(it) } }
                launch {
                    viewModel.notificationSettingsError.collect { failed ->
                        if (failed) Toast.makeText(
                            this@MainActivity,
                            R.string.storage_error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        exactAlarmController.render(exactAlarmInfo, exactAlarmButton)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                PrayerWidgetScheduler.scheduleNextPrayerBoundaryRerender(applicationContext)
                PrayerNotificationScheduler.scheduleNext(applicationContext)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "precise widget alarm setup failed", exception)
            }
        }
    }

    private fun showNotificationSettings() {
        val current = viewModel.notificationSettings.value
        val padding = resources.getDimensionPixelSize(R.dimen.screen_padding)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding)
        }
        val enabledSwitch = SwitchCompat(this).apply {
            text = getString(R.string.notifications_enabled)
            isChecked = current.notificationsEnabled
        }
        notificationDialogSwitch = enabledSwitch
        content.addView(enabledSwitch)

        val checkboxes = PrayerNotificationPrayer.entries.associateWith { prayer ->
            CheckBox(this).apply {
                text = prayerLabel(prayer)
                isChecked = prayer in current.enabledPrayers
            }.also(content::addView)
        }
        val leadLabel = TextView(this).apply {
            text = getString(R.string.notification_lead_time)
            setPadding(0, padding / 2, 0, 0)
        }
        content.addView(leadLabel)
        val leadValues = listOf(0, 5, 10, 15, 30, 45)
        val leadSpinner = Spinner(this).apply {
            adapter = ArrayAdapter.createFromResource(
                this@MainActivity,
                R.array.notification_lead_choices,
                android.R.layout.simple_spinner_item
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(leadValues.indexOf(current.leadMinutes).coerceAtLeast(0))
        }
        content.addView(leadSpinner)

        notificationDialogControls = checkboxes.values.toList() + listOf(leadLabel, leadSpinner)
        setNotificationControlsEnabled(current.notificationsEnabled)
        enabledSwitch.setOnCheckedChangeListener { _, enabled ->
            setNotificationControlsEnabled(enabled)
        }

        val scroll = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.notification_settings_title)
            .setView(scroll)
            .setNegativeButton(R.string.notification_cancel, null)
            .setPositiveButton(R.string.notification_save, null)
            .create()
        notificationDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updated = PrayerNotificationSettings(
                    notificationsEnabled = enabledSwitch.isChecked,
                    enabledPrayers = checkboxes.filterValues { it.isChecked }.keys,
                    leadMinutes = leadValues[leadSpinner.selectedItemPosition]
                )
                saveNotificationSettings(updated)
            }
        }
        dialog.setOnDismissListener {
            if (notificationDialog === dialog) {
                notificationDialog = null
                notificationDialogSwitch = null
                notificationDialogControls = emptyList()
            }
        }
        dialog.show()
    }

    private fun saveNotificationSettings(settings: PrayerNotificationSettings) {
        if (settings.notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotificationSettings = settings
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        viewModel.updateNotificationSettings(settings)
        notificationDialog?.dismiss()
    }

    private fun setNotificationControlsEnabled(enabled: Boolean) {
        notificationDialogControls.forEach { it.isEnabled = enabled }
    }

    private fun prayerLabel(prayer: PrayerNotificationPrayer): String = getString(
        when (prayer) {
            PrayerNotificationPrayer.IMSAK -> R.string.prayer_imsak
            PrayerNotificationPrayer.SUNRISE -> R.string.prayer_sunrise
            PrayerNotificationPrayer.DHUHR -> R.string.prayer_dhuhr
            PrayerNotificationPrayer.ASR -> R.string.prayer_asr
            PrayerNotificationPrayer.MAGHRIB -> R.string.prayer_maghrib
            PrayerNotificationPrayer.ISHA -> R.string.prayer_isha
        }
    )

    private fun renderState(state: PrayerUiState) {
        when (state) {
            PrayerUiState.Loading -> {
                status.text = getString(R.string.refreshing)
                refreshButton.isEnabled = false
            }
            is PrayerUiState.Ready -> {
                notificationSettingsButton.isEnabled = true
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
                notificationSettingsButton.isEnabled = false
                status.text = when (state.error) {
                    PrayerError.Network -> getString(R.string.network_error)
                    is PrayerError.Service -> getString(R.string.service_error)
                    PrayerError.InvalidData -> getString(R.string.invalid_data_error)
                    PrayerError.Storage,
                    PrayerError.TransientStorage,
                    PrayerError.PermanentStorage -> getString(R.string.storage_error)
                    PrayerError.Unknown -> getString(R.string.refresh_error)
                }
                refreshButton.isEnabled = true
            }
        }
    }

    private companion object {
        const val TAG = "NamazWidget"
        val METADATA_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }
}
