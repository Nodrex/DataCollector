package com.nodrex.eventscollector.annotations

/**
 * An annotation to mark a data class that is intended for use with
 * the EventsCollector. The KSP processor will validate any class
 * marked with this annotation at compile time.
 */
@Target(AnnotationTarget.CLASS) // This annotation can only be applied to classes.
@Retention(AnnotationRetention.SOURCE) // The annotation is only needed at compile time and can be discarded afterward.
annotation class CollectableEventsData