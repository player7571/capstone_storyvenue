package com.capstone.storyvenue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.capstone.storyvenue.ui.navigation.StoryVenueNavGraph
import com.capstone.storyvenue.ui.theme.StoryVenueAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StoryVenueAppTheme {
                StoryVenueNavGraph()
            }
        }
    }
}
