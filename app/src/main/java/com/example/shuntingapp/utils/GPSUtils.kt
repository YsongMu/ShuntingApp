package com.example.shuntingapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.*
import android.os.Bundle

class GPSUtils private constructor(private val mContext: Context) {
    companion object {
        // GPS定位
        private const val GPS_LOCATION = LocationManager.GPS_PROVIDER

        // 时间更新间隔，单位：ms
        private const val MIN_TIME: Long = 1000

        // 位置刷新距离，单位：m
        private const val MIN_DISTANCE = 1.toFloat()

        // singleton
        private var instance: GPSUtils? = null

        /**
         * 单例模式
         * @param mContext 上下文
         * @return
         */
        fun getInstance(mContext: Context): GPSUtils? {
            synchronized(this) {
                if (instance == null) {
                    instance = GPSUtils(mContext)
                }
            }
            return instance
        }
    }

    // 定位管理实例
    private var mLocationManager: LocationManager? = null

    // 定位回调
    private var mLocationCallBack: LocationCallBack? = null

    // 监听星数变化并更新
    private var beidouSatelliteCount = 0
    private var gnssCallback: GnssStatus.Callback? = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            beidouSatelliteCount = 0
            for (i in 0 until status.satelliteCount) {
                if (status.getConstellationType(i) == GnssStatus.CONSTELLATION_BEIDOU && status.usedInFix(i)) {
                    beidouSatelliteCount++
                }
            }
            mLocationCallBack?.setBeidouSatelliteCount(beidouSatelliteCount)
        }
    }

    // 获取定位
    @SuppressLint("MissingPermission")
    fun getLocation(mLocationCallBack: LocationCallBack?) {
        this.mLocationCallBack = mLocationCallBack
        if (mLocationCallBack == null) return
        // 定位管理初始化
        mLocationManager = mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (mLocationManager != null) {
            // 先注销旧的监听器，避免重复注册
            unregisterLocationUpdates()
            // 注册卫星回调
            mLocationManager!!.registerGnssStatusCallback(gnssCallback!!)
            // 通过GPS定位
            mLocationManager!!.requestLocationUpdates(
                GPS_LOCATION, MIN_TIME, MIN_DISTANCE, mLocationListener
            )
        } else {
            LogUtils.d("无法初始化定位管理")
        }
    }

    // 监听位置信息并更新
    private val mLocationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            mLocationCallBack?.setLocation(location)
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    // 注销监听
    fun unregisterLocationUpdates() {
        mLocationManager?.let {
            it.removeUpdates(mLocationListener)
            it.unregisterGnssStatusCallback(gnssCallback!!)
        }
    }

    /**
     * @className: LocationCallBack
     * @classDescription: 定位回调
     */
    interface LocationCallBack {
        fun setLocation(location: Location?)
        fun setBeidouSatelliteCount(count: Int)
    }
}