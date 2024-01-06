package com.example.mymusic

import android.app.Application
import android.content.res.Configuration

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        applyDarkTheme()
    }

    private fun applyDarkTheme() {
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> {
                setTheme(R.style.Theme_MyMusic)
            }

            Configuration.UI_MODE_NIGHT_NO -> {
                setTheme(R.style.Theme_MyMusic_Dark)
            }
        }
    }
}