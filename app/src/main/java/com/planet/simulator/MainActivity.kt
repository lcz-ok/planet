package com.planet.simulator

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.planet.simulator.engine.GameSurface

class MainActivity : AppCompatActivity() {
    private lateinit var gameSurface: GameSurface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        gameSurface = GameSurface(this)
        setContentView(gameSurface)
    }
    override fun onResume() { super.onResume(); gameSurface.resume() }
    override fun onPause() { super.onPause(); gameSurface.pause() }
    override fun onDestroy() { super.onDestroy(); gameSurface.destroy() }
}
