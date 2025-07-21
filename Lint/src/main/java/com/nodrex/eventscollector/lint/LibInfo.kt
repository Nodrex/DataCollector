package com.nodrex.eventscollector.lint

object LibInfo {
    const val EVENTS_COLLECTOR_PACKAGE_PATH = "com.nodrex.eventscollector"
    const val EVENTS_COLLECTOR_CLASS_PATH = "$EVENTS_COLLECTOR_PACKAGE_PATH.EventsCollector"
    const val EVENTS_COLLECTOR_COMPANION_PATH =
        "$EVENTS_COLLECTOR_PACKAGE_PATH.EventsCollector.Companion"
    const val ANNOTATION_CLASS_PATH =
        "$EVENTS_COLLECTOR_PACKAGE_PATH.annotations.CollectableEventsData"

    const val EMIT_FUNCTION_NAME = "emit"
    const val START_FUNCTION_NAME = "start"
    const val START_SINGLE_COLLECTOR_FUNCTION_NAME = "startSingleCollector"
}