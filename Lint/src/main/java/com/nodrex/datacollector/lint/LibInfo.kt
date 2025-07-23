package com.nodrex.datacollector.lint

object LibInfo {
    const val DATA_COLLECTOR_PACKAGE_PATH = "com.nodrex.datacollector"
    const val DATA_COLLECTOR_CLASS_PATH = "$DATA_COLLECTOR_PACKAGE_PATH.DataCollector"
    const val DATA_COLLECTOR_COMPANION_PATH =
        "$DATA_COLLECTOR_PACKAGE_PATH.DataCollector.Companion"
    const val ANNOTATION_CLASS_PATH =
        "$DATA_COLLECTOR_PACKAGE_PATH.annotations.CollectableData"

    const val EMIT_FUNCTION_NAME = "emit"
    const val COLLECT_FUNCTION_NAME = "collect"
    const val COLLECT_SINGLE_FUNCTION_NAME = "collectSingle"
}