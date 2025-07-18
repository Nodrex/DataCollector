package com.nodrex.eventscollector

import com.nodrex.eventscollector.EventsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking


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
import com.nodrex.eventscollector.Util
import com.nodrex.eventscollector.ui.theme.EventsCollectorTheme

data class TaskData(val taskName: String, val status: String, val age: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val t = System.currentTimeMillis()
        Util.log("-------- " + System.currentTimeMillis())

        val collector = EventsCollector.start<TaskData>(
            onResult = { result, error ->


                Util.log("-------- collection took " + (t- System.currentTimeMillis()))

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
    }
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