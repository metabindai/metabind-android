/*
 * MainActivity.kt.
 *
 * © 2026 Yap Studios LLC
 */
package com.yapstudios.metabind.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.yapstudios.metabind.view.MetabindView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val contentId = "96923a208c7ef23da3677b4d696126aac957b5d0cc8d11312043a8c01c77b6d1"
            Column(
                Modifier
                    .fillMaxSize()
            ) {
                MetabindView(
                    contentId = contentId,
                    enableSubscription = true
                )
            }
        }
    }
}
