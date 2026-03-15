package com.v2ray.ang.ui

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast

class CustomSettingsActivity : HelperBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_settings)

        val switchDark = findViewById<SwitchCompat>(R.id.switch_dark_theme)
        val etLimit = findViewById<EditText>(R.id.et_server_limit)
        val btnSave = findViewById<Button>(R.id.btn_save_settings)

        // 1. Загружаем текущие настройки
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        val serverLimit = prefs.getInt("server_limit", 100)

        switchDark.isChecked = isDarkMode
        etLimit.setText(serverLimit.toString())

        // 2. Логика сохранения
        btnSave.setOnClickListener {
            val newLimit = etLimit.text.toString().toIntOrNull() ?: 100
            val newDarkValue = switchDark.isChecked

            prefs.edit().apply {
                putBoolean("dark_mode", newDarkValue)
                putInt("server_limit", newLimit)
                apply()
            }

            // Применяем тему мгновенно
            if (newDarkValue) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }

            toast("Settings saved!")
            finish() // Возвращаемся на главный экран
        }
    }
}