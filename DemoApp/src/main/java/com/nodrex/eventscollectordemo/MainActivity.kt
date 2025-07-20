package com.nodrex.eventscollectordemo


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nodrex.eventscollector.EventsCollector
import com.nodrex.eventscollector.annotations.CollectableEventsData
import com.nodrex.eventscollectordemo.ui.theme.EventsCollectorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

//@CollectableEventsData
class TaskData(val taskName: String, val status: String, val age: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EventsCollectorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        testLib()

    }
}

private fun testLib() {
    val collector = EventsCollector.startSingleCollector<TaskData>(
        onResult = { result, error ->
            if (result != null) {
                println("Success! Result: $result")
            } else {
                println("Collector failed: $error")
            }
        }
    )

    runBlocking {
        delay(200)
    }

    collector.emit(TaskData::taskName, "Finalize Report")

    runBlocking {
        delay(300)
    }

    collector.emit(TaskData::status, "Done")

    runBlocking {
        delay(500)
    }
    collector.emit(TaskData::age, 30)

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    EventsCollectorTheme {
        Greeting("Android")
    }
}