package com.example.image_filter_intention

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Spacer(modifier = Modifier.padding(vertical = 250.dp))
                    Text(
                        text = stringFromJNI(),
                        color = Color.Black
                    )
                }
            }
        }
    }

    /**
     * A native method that is implemented by the 'image_filter_intention' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'image_filter_intention' library on application startup.
        init {
            System.loadLibrary("image_filter_intention")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GreetingPreview() {
    MaterialTheme {
        Text(text = "Hello from native!")
    }
}