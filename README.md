EventsCollector
Tired of messy boilerplate when waiting for multiple asynchronous events to complete? EventsCollector simplifies this by collecting a set of values and assembling them into a single, type-safe Kotlin data class object.

It's a lightweight, reflection-based tool perfect for scenarios like waiting for multiple network and database responses before updating a UI.

✨ Features
✅ Type-Safe by Design: Uses Kotlin reflection and generics to provide a fully type-safe result object.

✅ Simple & Unambiguous API: Create a collector and emit events with clear, compile-time checked property references (UserData::name).

✅ Flexible Collection: Configure for a single, one-time collection or a continuous stream of event sets.

✅ Lifecycle Aware: Manages its own CoroutineScope and is easily cancelled to prevent resource leaks.

✅ (Optional) Compile-Time Validation: Includes a KSP processor to validate your data classes at build time, turning potential runtime errors into build errors.

🛠️ Setup
Step 1: Add JitPack to your project
In your root settings.gradle.kts file, add the JitPack repository:

```
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // <-- Add this
    }
}
```
Step 2: Add the Library Dependencies
In your app's build.gradle.kts file, add the dependencies for the library. Replace Tag with the latest release tag from your GitHub repository (e.g., v1.0.0).

```
dependencies {
    // The main collector library
    implementation("com.github.YourGitHubUsername:YourRepoName:Tag")

    // (Optional but Recommended) For compile-time checks
    implementation("com.github.YourGitHubUsername:YourRepoName:Tag") // For the annotation
    ksp("com.github.YourGitHubUsername:YourRepoName:Tag") // For the processor
}
```

🚀 Usage
Using the collector is a simple three-step process.

Step 1: Define Your Data Model
Create a Kotlin data class that represents the final object you want to receive. For compile-time safety, mark it with the @CollectableEventsData annotation.

```
import com.nodrex.eventscollector.annotations.CollectableEventsData

@CollectableEventsData
data class UserProfile(
    val name: String,
    val followerCount: Int,
    val avatarUrl: String
)
```
Step 2: Create the Collector
Use one of the factory functions to create and start a collector. For collecting just one object, startSingleCollector is the most convenient.

```
// In an Activity, ViewModel, or any CoroutineScope
val collector = EventsCollector.startSingleCollector<UserProfile>(
    onResult = { result, error ->
        if (result != null) {
            // Success! You have a fully populated, type-safe object.
            println("Profile received: ${result.name} has ${result.followerCount} followers.")
        } else {
            // An error occurred during collection.
            println("Failed to get profile: ${error?.message}")
        }
    }
)
```

Step 3: Emit Events
As your asynchronous data arrives, use the emit() function with a property reference to provide the values. The order does not matter.

```
// From a coroutine fetching user data
collector.emit(UserProfile::name, "Nodrex")
collector.emit(UserProfile::avatarUrl, "http://example.com/avatar.png")
```
```
// From another coroutine fetching stats
collector.emit(UserProfile::followerCount, 1024)
Once all three properties have been emitted, the onResult callback will be triggered with the complete UserProfile object.
```
⚠️ Important Note on Concurrency (Phase 1)
This version of the collector is designed for sequential workflows where you expect one event per property for each collection cycle.

If you emit multiple values for the same property concurrently before a full object is assembled, the internal SharedFlow will only use the latest value it received. This can lead to "mixed data" results. For advanced concurrent scenarios, a BatchingEventsCollector is planned for a future release.

🗺️ Roadmap (Phase 2)
Future versions of this library will include:

A BatchingEventsCollector for robust concurrent data collection.

Support for regular classes and full Java interoperability.

Automatic cancellation via parent CoroutineScopes (e.g., viewModelScope).
