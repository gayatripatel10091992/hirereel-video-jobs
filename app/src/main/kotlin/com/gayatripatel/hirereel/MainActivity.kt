package com.gayatripatel.hirereel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gayatripatel.hirereel.data.SessionRepository
import com.gayatripatel.hirereel.ui.HireReelNav
import com.gayatripatel.hirereel.ui.theme.HireReelTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val session: SessionRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val signedIn = session.phone.value != null
        setContent {
            HireReelTheme { HireReelNav(signedIn) }
        }
    }
}
