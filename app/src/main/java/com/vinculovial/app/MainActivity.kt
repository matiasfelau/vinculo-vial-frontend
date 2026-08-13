package com.vinculovial.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vinculovial.app.presentation.navigation.VinculoVialNavHost
import com.vinculovial.app.presentation.theme.VinculoVialTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VinculoVialTheme {
                VinculoVialNavHost()
            }
        }
    }
}
