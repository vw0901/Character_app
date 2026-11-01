package com.ifitwereyou.character

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ifitwereyou.character.ui.AppViewModel
import com.ifitwereyou.character.ui.CharacterApp
import com.ifitwereyou.character.ui.theme.CharacterTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            CharacterTheme {
                CharacterApp(viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 离开应用前尽量把 600ms 防抖里的改动落盘。
        viewModel.flushSave()
    }
}
