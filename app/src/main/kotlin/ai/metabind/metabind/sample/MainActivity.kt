/*
 * MainActivity.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.metabind.sample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import ai.metabind.bindjs.JsRuntimeImpl
import ai.metabind.bindjs.McpHost
import ai.metabind.metabind.view.MetabindView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            JsRuntimeImpl.getInstance(applicationContext).setMcpHost(object : McpHost {
                override fun openLink(url: String) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            })
        }

        setContent {
            //val contentId = "4717fd788d9cb7dea89136d7b50bae12d94c7ac2780aac57c45b1592b4a3a3be" // tired lawn
            //val contentId = "0f45fa42e2c36351476e4fa5d64dc8f40a0a5c4df8f7f48276a51673d62d3dc5" // Green line sr50
            //val contentId = "16c5a3427a61d7d0e1ee44734723f41138bbfe018144df51fcbce4509d8b3a0e" // smart irrigation (component)
            //val contentId = "2b0f27d39916ccb6c204c4b4e5a577a12700ef31b45934b264cf741740e60ebe" // lounge chair (dev)
            val contentId = "d75a159c35b05c74f238d88010af8e9b0bccdbcc53d94a164c63d94af2fdac78"
            Box(Modifier.fillMaxSize()) {
                MetabindView(
                    contentId = contentId,
                    enableSubscription = true
                )
            }
        }
    }
}
