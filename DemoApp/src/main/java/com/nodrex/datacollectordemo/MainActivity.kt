package com.nodrex.datacollectordemo


import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.nodrex.datacollector.DataCollector
import com.nodrex.datacollector.annotations.CollectableData
import com.nodrex.datacollectordemo.ui.theme.EventsCollectorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EventsCollectorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Text(
                        text = "Hello Android!",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        testLib()

    }
}


@CollectableData
data class TaskData(val taskName: String, val status: String, val age: Int)

private fun testLib() {
    val collector = DataCollector.collectSingle<TaskData>(
        onResult = { result, error ->
            if (result != null) {
                Log.d("DEMO_APP_TAG", "Success! Result: $result")
            } else {
                Log.d("DEMO_APP_TAG", "Collector failed: $error")
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


    //To test type checking with lint
    /*collector.emit(TaskData::age, "30")
    collector.emit(TaskData::status, 125)
    collector.emit(TaskData::age, "stringAge")*/

}
