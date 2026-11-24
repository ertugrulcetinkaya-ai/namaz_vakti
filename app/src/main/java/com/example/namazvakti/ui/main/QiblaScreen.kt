package com.example.namazvakti.ui.main

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.namazvakti.R
import com.example.namazvakti.domain.model.DiyanetDistricts
import com.example.namazvakti.domain.model.PrayerLocation
import com.example.namazvakti.domain.model.PrayerLocationConfig
import com.example.namazvakti.domain.policy.AngularLowPassFilter
import com.example.namazvakti.domain.policy.QiblaCalculator
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val QiblaEmerald = Color(0xFF0F3B33)
private val QiblaGold = Color(0xFFE2C27A)
private val QiblaText = Color(0xFFEEF6F2)
private val QiblaMuted = Color(0xFF8EADA2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(
    location: PrayerLocation?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val hapticFeedback = LocalHapticFeedback.current
    val coordinates = remember(location) { qiblaCoordinates(location) }
    val qibla = remember(coordinates) {
        QiblaCalculator.calculate(coordinates.latitude, coordinates.longitude)
    }
    val qiblaDegrees = qibla.bearingDegrees.roundToInt().mod(360)
    val distanceLabel = remember(qibla.distanceKm) {
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(qibla.distanceKm)
    }
    val selectedLocationName = location?.displayName ?: PrayerLocationConfig.defaultCity.displayCity
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val rotationSensor = remember(sensorManager) {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }
    val accelerometer = remember(sensorManager) {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    val magneticFieldSensor = remember(sensorManager) {
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }
    val hasCompassSensors = rotationSensor != null || (accelerometer != null && magneticFieldSensor != null)
    val statusBarController = remember(activity, view) {
        activity?.window?.let { WindowCompat.getInsetsController(it, view) }
    }
    val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
    val magneticDeclination = remember(coordinates) {
        GeomagneticField(
            coordinates.latitude.toFloat(),
            coordinates.longitude.toFloat(),
            0f,
            System.currentTimeMillis()
        ).declination.toDouble()
    }
    val headingFilter = remember(sensorManager) { AngularLowPassFilter(smoothingFactor = 0.18) }
    var trueHeading by remember { mutableStateOf<Double?>(null) }
    var lowSensorAccuracy by remember { mutableStateOf(false) }
    var alignmentHapticPlayed by remember(location) { mutableStateOf(false) }

    DisposableEffect(statusBarController) {
        val previousLightStatusBars = statusBarController?.isAppearanceLightStatusBars
        statusBarController?.isAppearanceLightStatusBars = false
        onDispose {
            if (previousLightStatusBars != null) {
                statusBarController.isAppearanceLightStatusBars = previousLightStatusBars
            }
        }
    }

    DisposableEffect(
        sensorManager,
        lifecycleOwner,
        rotationSensor,
        accelerometer,
        magneticFieldSensor,
        displayRotation,
        magneticDeclination,
        hasCompassSensors
    ) {
        if (!hasCompassSensors) {
            onDispose { }
        } else {
            val accuracyBySensor = mutableMapOf<Int, Int>()
            var gravityValues: FloatArray? = null
            var magneticValues: FloatArray? = null
            var registered = false

            fun updateAccuracy(sensorType: Int, accuracy: Int) {
                accuracyBySensor[sensorType] = accuracy
                lowSensorAccuracy = accuracyBySensor.values.any(::isLowAccuracy)
            }

            fun updateHeading(rotationMatrix: FloatArray) {
                val remappedMatrix = FloatArray(9)
                val (axisX, axisY) = coordinateAxesFor(displayRotation)
                if (!SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)) {
                    return
                }
                val orientation = FloatArray(3)
                SensorManager.getOrientation(remappedMatrix, orientation)
                val magneticHeading = Math.toDegrees(orientation[0].toDouble())
                trueHeading = headingFilter.add(magneticHeading + magneticDeclination)
            }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.accuracy >= SensorManager.SENSOR_STATUS_UNRELIABLE) {
                        updateAccuracy(event.sensor.type, event.accuracy)
                    }

                    if (rotationSensor != null && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                        val rotationMatrix = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        updateHeading(rotationMatrix)
                        return
                    }

                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> gravityValues = event.values.clone()
                        Sensor.TYPE_MAGNETIC_FIELD -> magneticValues = event.values.clone()
                    }
                    val gravity = gravityValues ?: return
                    val magnetic = magneticValues ?: return
                    val rotationMatrix = FloatArray(9)
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, magnetic)) {
                        updateHeading(rotationMatrix)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    updateAccuracy(sensor.type, accuracy)
                }
            }

            fun registerSensors() {
                if (registered) return
                headingFilter.reset()
                val sensorsToRegister = if (rotationSensor != null) {
                    listOf(rotationSensor)
                } else {
                    listOfNotNull(accelerometer, magneticFieldSensor)
                }
                registered = sensorsToRegister
                    .map { sensor ->
                        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                    }
                    .any { it }
            }

            fun unregisterSensors() {
                if (!registered) return
                sensorManager.unregisterListener(listener)
                registered = false
            }

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> registerSensors()
                    Lifecycle.Event.ON_STOP -> unregisterSensors()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                registerSensors()
            }

            onDispose {
                unregisterSensors()
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    val alignmentError = trueHeading?.let {
        abs(AngularLowPassFilter.shortestDifference(it, qibla.bearingDegrees))
    }
    val isAligned = alignmentError != null && alignmentError <= ALIGNMENT_TOLERANCE_DEGREES
    LaunchedEffect(isAligned) {
        if (isAligned && !alignmentHapticPlayed) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            alignmentHapticPlayed = true
        }
    }

    Scaffold(
        containerColor = QiblaEmerald,
        contentColor = QiblaText,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qibla_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = QiblaText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = QiblaEmerald,
                    titleContentColor = QiblaText
                )
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(QiblaEmerald)
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (hasCompassSensors) {
                if (trueHeading == null) {
                    Box(
                        modifier = Modifier.size(COMPASS_SIZE),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = QiblaGold)
                        Text(
                            text = stringResource(R.string.qibla_compass_starting),
                            modifier = Modifier.padding(top = 92.dp),
                            color = QiblaMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    QiblaCompass(
                        headingDegrees = trueHeading!!.toFloat(),
                        qiblaRelativeDegrees = AngularLowPassFilter.shortestDifference(
                            trueHeading!!,
                            qibla.bearingDegrees
                        ).toFloat(),
                        qiblaDegrees = qiblaDegrees,
                        aligned = isAligned,
                        modifier = Modifier.size(COMPASS_SIZE)
                    )
                }
                Spacer(Modifier.height(24.dp))
            } else {
                Text(
                    text = stringResource(R.string.qibla_bearing_from_north, qiblaDegrees),
                    color = QiblaGold,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
            }

            Text(
                text = stringResource(R.string.qibla_bearing_distance, qiblaDegrees, distanceLabel),
                color = QiblaText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.qibla_location, selectedLocationName),
                color = QiblaMuted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            if (hasCompassSensors && lowSensorAccuracy) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.qibla_calibration_warning),
                    modifier = Modifier.widthIn(max = 320.dp),
                    color = QiblaGold,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QiblaCompass(
    headingDegrees: Float,
    qiblaRelativeDegrees: Float,
    qiblaDegrees: Int,
    aligned: Boolean,
    modifier: Modifier = Modifier
) {
    val north = stringResource(R.string.qibla_north_label)
    val east = stringResource(R.string.qibla_east_label)
    val south = stringResource(R.string.qibla_south_label)
    val west = stringResource(R.string.qibla_west_label)
    val description = stringResource(R.string.qibla_compass_accessibility, qiblaDegrees)
    val arrowColor = if (aligned) QiblaGold else QiblaMuted

    Canvas(
        modifier = modifier.semantics { contentDescription = description }
    ) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.46f
        val labelRadius = radius * 0.79f
        drawCircle(
            color = QiblaText.copy(alpha = 0.08f),
            radius = radius,
            center = center
        )
        drawCircle(
            color = QiblaMuted.copy(alpha = 0.8f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        for (index in 0 until 72) {
            val bearing = index * 5f
            val longTick = index % 3 == 0
            val cardinalTick = index % 18 == 0
            val tickLength = when {
                cardinalTick -> 18.dp.toPx()
                longTick -> 12.dp.toPx()
                else -> 6.dp.toPx()
            }
            val start = pointAt(center, radius - tickLength, bearing - headingDegrees)
            val end = pointAt(center, radius - 1.dp.toPx(), bearing - headingDegrees)
            drawLine(
                color = if (cardinalTick) QiblaGold else QiblaMuted,
                start = start,
                end = end,
                strokeWidth = if (longTick) 1.7.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = QiblaText.toArgb()
                textAlign = Paint.Align.CENTER
                textSize = 16.sp.toPx()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val labels = listOf(north, east, south, west)
            labels.forEachIndexed { index, label ->
                val bearing = index * 90f
                val position = pointAt(center, labelRadius, bearing - headingDegrees)
                canvas.nativeCanvas.drawText(label, position.x, position.y + paint.textSize / 3f, paint)
            }
        }

        rotate(degrees = qiblaRelativeDegrees, pivot = center) {
            val tipY = center.y - radius * 0.62f
            val tailY = center.y + radius * 0.2f
            drawLine(
                color = arrowColor,
                start = androidx.compose.ui.geometry.Offset(center.x, tailY),
                end = androidx.compose.ui.geometry.Offset(center.x, tipY),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
            val arrowHead = Path().apply {
                moveTo(center.x, tipY - 8.dp.toPx())
                lineTo(center.x - 11.dp.toPx(), tipY + 13.dp.toPx())
                lineTo(center.x, tipY + 8.dp.toPx())
                lineTo(center.x + 11.dp.toPx(), tipY + 13.dp.toPx())
                close()
            }
            drawPath(arrowHead, color = arrowColor)
        }
        drawCircle(color = QiblaGold, radius = 5.dp.toPx(), center = center)
    }
}

private fun pointAt(
    center: androidx.compose.ui.geometry.Offset,
    radius: Float,
    bearingDegrees: Float
): androidx.compose.ui.geometry.Offset {
    val angle = Math.toRadians((bearingDegrees - 90f).toDouble())
    return androidx.compose.ui.geometry.Offset(
        x = center.x + cos(angle).toFloat() * radius,
        y = center.y + sin(angle).toFloat() * radius
    )
}

private fun coordinateAxesFor(rotation: Int): Pair<Int, Int> = when (rotation) {
    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
}

private fun isLowAccuracy(accuracy: Int): Boolean =
    accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW

private fun qiblaCoordinates(location: PrayerLocation?): QiblaCoordinates {
    val defaultCity = PrayerLocationConfig.defaultCity
    if (location == null) {
        return QiblaCoordinates(defaultCity.latitude ?: 0.0, defaultCity.longitude ?: 0.0)
    }

    val cityOption = PrayerLocationConfig.optionForCityAndCountry(location.city, location.country)
    val province = DiyanetDistricts.forCity(location.city, location.country)
    val district = location.diyanetDistrictId
        ?.let(DiyanetDistricts.byId::get)
        ?.takeIf { it.provinceId == province?.id }
    if (district?.latitude != null && district.longitude != null) {
        return QiblaCoordinates(district.latitude, district.longitude)
    }

    if (location.latitude != null && location.longitude != null) {
        return QiblaCoordinates(location.latitude, location.longitude)
    }
    if (cityOption.latitude != null && cityOption.longitude != null) {
        return QiblaCoordinates(cityOption.latitude, cityOption.longitude)
    }
    return QiblaCoordinates(defaultCity.latitude ?: 0.0, defaultCity.longitude ?: 0.0)
}

private data class QiblaCoordinates(
    val latitude: Double,
    val longitude: Double
)

private const val ALIGNMENT_TOLERANCE_DEGREES = 5.0
private val COMPASS_SIZE = 292.dp
