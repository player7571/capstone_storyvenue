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

        val prefs = getSharedPreferences("storyvenue", MODE_PRIVATE)
        val hasToken = prefs.getString("access_token", null) != null

        setContent {
            StoryVenueAppTheme {
                StoryVenueNavGraph(hasToken = hasToken)
            }
        }
    }
}