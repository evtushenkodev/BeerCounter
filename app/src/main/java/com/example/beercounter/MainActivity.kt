package com.example.beercounter

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

import android.widget.TextView
import android.app.AlertDialog
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Spinner


class MainActivity : AppCompatActivity() {
    private val buttonCountMap = mutableMapOf<String, Double>()
    private val buttonTextViewMap = mutableMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gridLayout = findViewById<GridLayout>(R.id.buttonsLayout)
        val buttonList = listOf("Балтика 1", "Живое 2", "Арпа 3", "Пиво 4")

        buttonList.forEach { buttonName ->
            val themedContext = ContextThemeWrapper(this, R.style.ButtonStyle)
            val button = Button(themedContext)
            button.text = buttonName
            button.setOnClickListener { onButtonClick(buttonName) }

            val layoutParams = GridLayout.LayoutParams()
            layoutParams.width = 0 // Ширина кнопки
            layoutParams.height = GridLayout.LayoutParams.WRAP_CONTENT
            layoutParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            button.layoutParams = layoutParams

            gridLayout.addView(button)
            buttonCountMap[buttonName] = 0.0
            buttonTextViewMap[buttonName] = button
        }
    }


    // Добавляем функцию onButtonClick, чтобы обработать нажатие кнопки
    private fun onButtonClick(buttonName: String) {
        showCounterDialog(buttonName)
    }

    private fun showCounterDialog(buttonName: String) {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_counter, null)
        builder.setView(dialogView)
        val beerNameTextView = dialogView.findViewById<TextView>(R.id.beerNameTextView)
        beerNameTextView.text = buttonName

        val counterTextView = dialogView.findViewById<TextView>(R.id.counterTextView)
        val addButton = dialogView.findViewById<Button>(R.id.addButton)
        val subtractButton = dialogView.findViewById<Button>(R.id.subtractButton)

        val predefinedValueButton1 = dialogView.findViewById<Button>(R.id.predefinedValueButton1)
        val predefinedValueButton2 = dialogView.findViewById<Button>(R.id.predefinedValueButton2)
        val predefinedValueButton3 = dialogView.findViewById<Button>(R.id.predefinedValueButton3)
        val customValueEditText = dialogView.findViewById<EditText>(R.id.customValueEditText)

        fun updateButtonCountText() {
            val currentCount = buttonCountMap[buttonName] ?: 0.0
            buttonTextViewMap[buttonName]?.text = "$buttonName: $currentCount"
        }

        addButton.setOnClickListener {
            val currentCount = buttonCountMap[buttonName] ?: 0.0
            val customValue = customValueEditText.text.toString().toDoubleOrNull() ?: 0.0
            buttonCountMap[buttonName] = currentCount + customValue
            counterTextView.text = (currentCount + customValue).toString()
            updateButtonCountText()
        }

        subtractButton.setOnClickListener {
            val currentCount = buttonCountMap[buttonName] ?: 0.0
            val customValue = customValueEditText.text.toString().toDoubleOrNull() ?: 0.0
            if (currentCount >= customValue) {
                buttonCountMap[buttonName] = currentCount - customValue
                counterTextView.text = (currentCount - customValue).toString()
                updateButtonCountText()
            }
        }

        predefinedValueButton1.setOnClickListener {
            val currentCount = buttonCountMap[buttonName] ?: 0.0
            val predefinedValue = 1.0

            if (currentCount >= predefinedValue) {
                buttonCountMap[buttonName] = currentCount - predefinedValue
                counterTextView.text = (currentCount - predefinedValue).toString()
                updateButtonCountText()
            }
        }

        predefinedValueButton2.setOnClickListener {
            val currentCount = buttonCountMap[buttonName] ?: 0.0
            val predefinedValue = 1.5

            if (currentCount >= predefinedValue) {
                buttonCountMap[buttonName] = currentCount - predefinedValue
                counterTextView.text = (currentCount - predefinedValue).toString()
                updateButtonCountText()
            }
        }

        predefinedValueButton3.setOnClickListener {
            val currentCount = buttonCountMap[buttonName] ?: 0.0
            val predefinedValue = 2.0

            if (currentCount >= predefinedValue) {
                buttonCountMap[buttonName] = currentCount - predefinedValue
                counterTextView.text = (currentCount - predefinedValue).toString()
                updateButtonCountText()
            }
        }

        builder.setPositiveButton("Закрыть") { _, _ ->
            updateButtonCountText()
        }
        builder.create().show()
    }
}
