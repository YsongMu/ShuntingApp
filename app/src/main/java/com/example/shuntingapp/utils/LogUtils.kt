package com.example.shuntingapp.utils

import android.util.Log

object LogUtils {
    private const val TAG = "AppLog"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun e(message: String) {
        Log.e(TAG, message)
    }

    fun v(message: String) {
        Log.v(TAG, message)
    }
}