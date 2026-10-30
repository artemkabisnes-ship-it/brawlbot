package com.example.brawlbotsimple

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
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

    private val characters = listOf(
        "Shelly", "Colt", "Bull", "Jessie", "Nita", "Dynamike", "El Primo", "Brock", "Barley", "Poco",
        "Rosa", "Darryl", "Penny", "Carl", "Jacky", "Gus", "Bo", "Piper", "Pam", "Frank", "Bibi",
        "Bea", "Emz", "Mr. P", "Max", "Tick", "8-Bit", "Rico", "Spike", "Crow", "Leon", "Sandy",
        "Amber", "Mortis", "Tara", "Gene", "Sprout", "Byron", "Squeak", "Lou", "Ruffs", "Belle",
        "Buzz", "Griff", "Ash", "Lola", "Meg", "Surge", "Gale", "Colonel Ruffs", "Fang", "Eve",
        "Janet", "Otis", "Sam", "Buster", "Gray", "Mandy", "R-T", "Willow", "Maisie", "Hank",
        "Doug", "Pearl", "Chuck", "Charlie", "Mico", "Melodie", "Larry & Lawrie", "Angelo", "Draco",
        "Berry", "Clancy", "Kenji", "Moe", "Shade", "Juju", "Meeple", "Ollie", "Lumi", "Finx"
    )

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            BotState.targetCharacter = spinner.selectedItem.toString()
            BotState.targetTrophies = editTarget.text.toString().toIntOrNull() ?: 500
            BotState.allCharacters = characters
            BotState.currentIndex = characters.indexOf(BotState.targetCharacter)

            try {
                val i = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("code", result.resultCode)
                    putExtra("data", result.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(i)
                } else {
                    startService(i)
                }
                Toast.makeText(this, "Бот запущен", Toast.LENGTH_SHORT).show()
                // Сворачиваем приложение, чтобы бот начал работу
                moveTaskToBack(true)
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка запуска: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
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

        // Запрашиваем разрешение на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

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
