package com.example.brawlbotsimple

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {

    private lateinit var spinner: Spinner
    private lateinit var editTarget: EditText
    private lateinit var btnStart: Button
    private var projectionManager: MediaProjectionManager? = null

    private val characters = listOf("Shelly", "Colt", "Bull", "Poco", "El Primo", "Nita")

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            BotState.targetCharacter = spinner.selectedItem.toString()
            BotState.targetTrophies = editTarget.text.toString().toIntOrNull() ?: 500
            BotState.allCharacters = characters
            BotState.currentIndex = characters.indexOf(BotState.targetCharacter)

            val i = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("code", result.resultCode)
                putExtra("data", result.data)
            }
            startForegroundService(i)
            Toast.makeText(this, "Бот запущен", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinner = findViewById(R.id.spinnerChar)
        editTarget = findViewById(R.id.editTarget)
        btnStart = findViewById(R.id.btnStart)

        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, characters)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

        btnStart.setOnClickListener {
            projectionLauncher.launch(projectionManager!!.createScreenCaptureIntent())
        }
    }
}

object BotState {
    var targetCharacter: String = "Shelly"
    var targetTrophies: Int = 500
    var allCharacters: List<String> = emptyList()
    var currentIndex: Int = 0
    var running: Boolean = false
}
