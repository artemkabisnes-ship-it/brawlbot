package com.example.brawlbotsimple

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*

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

            val finalData = result.data
            val finalCode = result.resultCode

            Toast.makeText(this, "Подготовка...", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)

            // Задержка 2 секунды, чтобы MIUI не убил службу
            CoroutineScope(Dispatchers.Main).launch {
                delay(2000)
                try {
                    val i = Intent(this@MainActivity, ScreenCaptureService::class.java).apply {
                        putExtra("code", finalCode)
                        putExtra("data", finalData)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(i)
                    } else {
                        startService(i)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

        // Открываем спец. возможности (один раз при старте)
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Настраиваем кнопку Запуск/Остановка
        btnStart.setOnClickListener {
            if (BotState.running) {
                // Останавливаем бота
                BotState.running = false
                stopService(Intent(this, ScreenCaptureService::class.java))
                btnStart.text = "Запустить бота"
                Toast.makeText(this, "Бот остановлен", Toast.LENGTH_SHORT).show()
            } else {
                // Запускаем бота
                try {
                    projectionLauncher.launch(projectionManager!!.createScreenCaptureIntent())
                    btnStart.text = "Остановить бота"
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Обновляем текст кнопки, если вернулись в приложение
        if (BotState.running) {
            btnStart.text = "Остановить бота"
        } else {
            btnStart.text = "Запустить бота"
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
