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
            val contentId = "d75a159c35b05c74f238d88010af8e9b0bccdbcc53d94a164c63d94af2fdac78"
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
