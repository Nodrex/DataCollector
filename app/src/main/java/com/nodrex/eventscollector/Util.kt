package com.nodrex.eventscollector

import android.util.Log

const val EVENTS_COLLECTOR_TAG = "[EventsCollector]"

object Util {

    fun log(info: String) {
        Log.d(EVENTS_COLLECTOR_TAG, info)
    }

}