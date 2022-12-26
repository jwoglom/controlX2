package com.jwoglom.wearx2.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import com.jwoglom.wearx2.Prefs
import com.jwoglom.wearx2.presentation.components.DialogScreen
import com.jwoglom.wearx2.presentation.navigation.Screen
import com.jwoglom.wearx2.presentation.theme.WearX2Theme
import kotlin.system.exitProcess

@Composable
fun FirstLaunch(
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
) {
    val context = LocalContext.current

    DialogScreen(
        "Health and Safety Warning",
        buttonContent = {
            Button(
                onClick = {
                    Prefs(context).setTosAccepted(false)
                    exitProcess(0)
                }
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    navController?.navigate(Screen.PumpSetup.route)
                    Prefs(context).setTosAccepted(true)
                }
            ) {
                Text("Agree")
            }
        }
    ) {
        item {
            Text(
                text = """
                            This application is for EXPERIMENTAL USE ONLY and can be used to MODIFY ACTIVE INSULIN DELIVERY ON YOUR INSULIN PUMP.

                            There is NO WARRANTY IMPLIED OR EXPRESSED DUE TO USE OF THIS SOFTWARE. YOU ASSUME ALL RISK FOR ANY MALFUNCTIONS, BUGS, OR INSULIN DELIVERY ACTIONS.
                        """.trimIndent(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    WearX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            FirstLaunch(
                sendMessage = {_, _ -> },
            )
        }
    }
}