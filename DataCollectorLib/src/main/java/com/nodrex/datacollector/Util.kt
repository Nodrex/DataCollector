package com.nodrex.datacollector

import android.util.Log

const val EVENTS_COLLECTOR_TAG = "[EventsCollector]"

object Util {

    fun log(info: String) {
        Log.d(EVENTS_COLLECTOR_TAG, info)
    }

    fun logw(info: String) {
        Log.w(EVENTS_COLLECTOR_TAG, info)
    }

    fun loge(info: String) {
        Log.e(EVENTS_COLLECTOR_TAG, info)
    }

}