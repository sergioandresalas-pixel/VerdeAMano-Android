package com.verdeamano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.verdeamano.core.design.VerdeTheme
import com.verdeamano.navigation.VerdeApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_VerdeAMano)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VerdeTheme { DevelopmentNetworkAccess { VerdeApp() } } }
    }
}
