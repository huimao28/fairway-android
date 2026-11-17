package com.huimao.fairway.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.huimao.fairway.domain.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 前台定位封装（FusedLocationProviderClient）。
 * 调用方必须先确认已授予定位权限；这里仍按 skill 要求包 try-catch SecurityException，
 * 权限被撤回时静默停更，UI 层按权限状态降级显示。
 */
class LocationTracker(context: Context) {

    private val client =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private val _location = MutableStateFlow<LatLng?>(null)
    val location: StateFlow<LatLng?> = _location.asStateFlow()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                _location.value = LatLng(it.latitude, it.longitude)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) _location.value = LatLng(loc.latitude, loc.longitude)
            }
        } catch (e: SecurityException) {
            // 权限未授予：不抛，UI 按无权限降级
        }
    }

    fun stop() {
        client.removeLocationUpdates(callback)
    }
}
