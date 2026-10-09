package com.example.msdksample.asuka

import android.app.Application
import android.util.Log

class InspectionApplication : Application() {

    companion object {
        private const val TAG = "InspectionApplication"
        private lateinit var instance: InspectionApplication

        fun getInstance(): InspectionApplication = instance
        fun getContext() = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "Inspection Application initialized")
        Log.i(TAG, "DJI SDK: Using local flight record parsing mode")
    }
}
