package com.richi_mc.kipisafe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.richi_mc.kipisafe.ui.navigation.KipiSafeNavigation
import com.richi_mc.kipisafe.ui.theme.KipiSafeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KipiSafeTheme {
                KipiSafeNavigation()
            }
        }
    }
}