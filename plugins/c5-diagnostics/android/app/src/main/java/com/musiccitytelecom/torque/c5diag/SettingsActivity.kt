package com.musiccitytelecom.torque.c5diag

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val vin = findViewById<EditText>(R.id.vinEdit)
        val url = findViewById<EditText>(R.id.webhookEdit)
        val secret = findViewById<EditText>(R.id.secretEdit)
        val swap = findViewById<CheckBox>(R.id.swapC1C2Check)

        val current = SettingsStore.load(this)
        vin.setText(current.vin)
        url.setText(current.webhookUrl)
        secret.setText(current.webhookSecret)
        swap.isChecked = current.useP59C1C2Swap

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val cleanVin = vin.text.toString().trim().uppercase()
            val cleanUrl = url.text.toString().trim()
            val cleanSecret = secret.text.toString()

            if (cleanVin.isNotEmpty() && cleanVin.length != 17) {
                Toast.makeText(this, "VIN must be 17 characters or blank", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (cleanUrl.isNotEmpty() && !cleanUrl.startsWith("https://", ignoreCase = true)) {
                Toast.makeText(this, "Webhook must use HTTPS", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (cleanUrl.isNotEmpty() && cleanSecret.length < 24) {
                Toast.makeText(this, "Use a webhook secret of at least 24 characters", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            SettingsStore.save(
                this,
                PluginSettings(
                    vin = cleanVin,
                    webhookUrl = cleanUrl,
                    webhookSecret = cleanSecret,
                    useP59C1C2Swap = swap.isChecked
                )
            )
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
