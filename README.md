# DataCollector  

[![](https://jitpack.io/v/Nodrex/DataCollector.svg)](https://jitpack.io/#Nodrex/DataCollector)

DataCollector simplifies the orchestration of multiple asynchronous data sources by collecting their values and assembling them into a single, type-safe Kotlin data class object.

It's a lightweight, reflection-based tool perfect for scenarios where you need to wait for responses from multiple asynchronous sources—such as network calls, database queries, or file reads—before taking a final action.

✨ Features

✅ Type-Safe by Design: Uses Kotlin reflection and generics to provide a fully type-safe result object.

✅ Simple & Unambiguous API: Create a collector and emit Data with clear, lint-checked property references.

✅ Flexible Collection: Configure for a single, one-time collection or a continuous stream of data sets.

✅ Lifecycle Aware: Manages its own CoroutineScope and is easily cancelled to prevent resource leaks.

✅ Compile-Time Validation: Includes a KSP processor to validate your data classes at build time, turning potential runtime errors into build errors.

---

🛠️ Setup
Step 1: Add JitPack to your project
In your root settings.gradle.kts file, add the JitPack repository:

```Kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // <-- Add this
    }
}
```
Step 2: Add the Library Dependencies
In your app's build.gradle.kts file, add the dependencies for the library.

```Kotlin
dependencies {
    // The main collector library
    implementation("com.github.Nodrex.DataCollector:DataCollectorLib:2.0.0")

    // Required peer dependencies for the collector
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.0")
}
```
⚙️ Optional: Build-Time Validation (Recommended)

To enable build-time checks that ensure you are using the collector correctly, you must apply the KSP plugin and add the processor dependency.

If you choose not to add these, the library will still function, but the build-time validation for your data classes will be disabled.

```Kotlin
plugins {
    // ... your other plugins
    id("org.jetbrains.kotlin.android") version "2.0.0"
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}

dependencies {
    ksp("com.github.Nodrex.DataCollector:Compiler:2.0.0") // For data class build-time validation
}
```

---

🚀 Usage
Using the collector is a simple three-step process.

Step 1: Define Your Data Model
Create a Kotlin data class that represents the final object you want to receive. For compile-time safety, mark it with the @CollectableData annotation.

```Kotlin
import com.nodrex.datacollector.annotations.CollectableData

@CollectableData
data class UserProfile(
    val name: String,
    val followerCount: Int,
    val avatarUrl: String
)
```
Step 2: Create the Collector
Use one of the factory functions to create and start a collector. For collecting just one object, collectSingle is the most convenient.

```Kotlin
// In an Activity, ViewModel, or any CoroutineScope
val collector = DataCollector.collectSingle<UserProfile>(
    onResult = { result, error ->
        if (result != null) {
            // Success! You have a fully populated, type-safe object.
            Log.d("TAG" ,"Profile received: ${result.name} has ${result.followerCount} followers.")
        } else {
            // An error occurred during collection.
            Log.d("TAG" ,"Failed to get profile: ${error?.message}")
        }
    }
)
```

Step 3: Emit Data
As your asynchronous data arrives, use the emit() function with a property reference to provide the values. The order does not matter.

```Kotlin
// From a coroutine fetching user data
collector.emit(UserProfile::name, "Nodrex")
collector.emit(UserProfile::avatarUrl, "http://example.com/avatar.png")
```
```Kotlin
// From another coroutine fetching stats
collector.emit(UserProfile::followerCount, 1024)
```
Once all three properties have been emitted, the onResult callback will be triggered with the complete UserProfile object.

---

⚠️ Important Note on Concurrency (Phase 1)
This version of the collector is designed for sequential workflows where you expect one data per property for each collection cycle.

If you emit multiple values for the same property concurrently before a full object is assembled, the internal SharedFlow will only use the latest value it received. This can lead to "mixed data" results. For advanced concurrent scenarios, a GroupedDataCollector is planned for a future release.

---

🧹 Cleanup

Automatic Cleanup: After the collector has finished its work (e.g., after collectorCount is met), it will automatically cancel() itself to release all resources.

Manual Cleanup: If you need to stop the collection process early, you can manually call collector.cancel() at any time.

---

✅ Lint Checks: Advanced Build-Time Safety
This library includes a custom Lint module that provides advanced type checks for your emit calls, turning potential runtime errors into build errors.
What It Checks
The main rule validates that the type of the value you pass to emit matches the type of the property reference.

Example of code that will now fail the build:
```Kotlin
data class MyData(val name: String, val age: Int)

val collector = DataCollector.collectSingle<MyData> { /* ... */ }

// ❌ BUILD ERROR: The lint check will flag this line.
collector.emit(MyData::age, "25") // Expected Int, but got a String
```
The build will fail with a clear error: Type mismatch. Property expects type Int but received String.

---

🚀More detailed Example
```Kotlin
import com.nodrex.datacollector.DataCollector
import com.nodrex.datacollector.annotations.CollectableData
import kotlinx.coroutines.*

// --- 1. Define Your Data Models ---

// A mock Android Bitmap class for this example
class Bitmap(val source: String) {
    override fun toString() = "Bitmap(from='$source')"
}

// Data from the local database
data class UserSettings(val theme: String)
data class UserAccount(val id: String, val email: String)

// Data from a network API (like Retrofit)
data class UserData(val fullName: String, val followerCount: Int)

// The final, assembled object that the collector will provide
@CollectableData
data class MyData(
    val settings: UserSettings,
    val account: UserAccount,
    val data: UserData,
    val image: Bitmap
)

// --- 2. Simulate Your Asynchronous Data Sources ---

// Simulates fetching from a local database (fast)
suspend fun fetchAccountFromDb(): UserAccount {
    delay(200)
    println("✅ Account fetched from DB")
    return UserAccount("user-123", "test@example.com")
}
suspend fun fetchSettingsFromDb(): UserSettings {
    delay(300)
    println("✅ Settings fetched from DB")
    return UserSettings("Dark Mode")
}

// Simulates a network call with Retrofit (slower)
suspend fun fetchUserDataFromNetwork(): UserData {
    delay(800)
    println("✅ UserData fetched from Network")
    return UserData("Nodrex", 1024)
}

// Simulates loading an image from a file (medium speed)
suspend fun loadImageFromFile(): Bitmap {
    delay(500)
    println("✅ Image loaded from File")
    return Bitmap("local_storage")
}


// --- 3. Use the Collector to Wait for All Data ---

suspend fun main() = coroutineScope {
    println("🚀 Starting to collect all user Data...")

    val collector = DataCollector.collectSingle<MyData>(
        onResult = { result, error ->
            if (result != null) {
                // This block is only called when ALL data is ready
                println("\n🎉 All Data collected! Assembled object:")
                println(result)
            } else {
                println("Failed to collect Data: ${error?.message}")
            }
        }
    )

    // Launch concurrent jobs to fetch all data sources
    launch {
        collector.emit(MyData::account, fetchAccountFromDb())
    }
    launch {
        collector.emit(MyData::settings, fetchSettingsFromDb())
    }
    launch {
        collector.emit(MyData::data, fetchUserDataFromNetwork())
    }
    launch {
        collector.emit(MyData::image, loadImageFromFile())
    }
}
```

---

🗺️ Roadmap (Phase 2)
Kotlin-multiplatform support

Future versions of this library will include:

A GroupedDataCollector for robust concurrent data collection.

Support for regular classes and full Java interoperability.

Automatic cancellation via parent CoroutineScopes (e.g., viewModelScope, lifeCycleScope and so on).

Optional timeout parameter to prevent the collector from waiting forever if one of the expected events never arrives.

---
## 📜 License

This project is licensed under the [MIT License](https://opensource.org/license/MIT). Feel free to use, modify, and distribute the library under the terms of the license.

---
## 👥 Contributions

Contributions are welcome! If you want to improve this library, please feel free to submit a pull request or open an issue.

---

## 📣 Demo Application

This repository includes a DemoApp module that contains a working example of how to use the DataCollector library.

You can find a complete code sample in the [`MainActivity.kt`](https://github.com/Nodrex/DataCollector/blob/master/DemoApp/src/main/java/com/nodrex/datacollectordemo/MainActivity.kt) file to see it in action.
