package com.example.beercounter

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

import android.widget.TextView
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.MutableLiveData
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.poi.openxml4j.exceptions.InvalidFormatException
import org.apache.poi.ss.usermodel.Workbook
import java.io.IOException
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {
    private val buttonCountMap = mutableMapOf<String, MutableLiveData<Double>>()
    private val buttonTextViewMap = mutableMapOf<String, TextView>()

    private val openExcelDocument: ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            readExcelAndCreateButtons(uri)
        }
    }

    private val saveExcelDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
            if (uri != null) {
                // Сохраняем данные в файл при выборе места сохранения
                saveDataToExcelFile(uri)
            }
        }

    fun onChooseFileButtonClick(view: View) {
        openExcelDocument.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    }

    fun onSaveButtonClick(view: View) {
        val currentDate = SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(Date())
        val fileName = "data_$currentDate.xlsx"
        saveExcelDocument.launch(fileName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    private fun readExcelAndCreateButtons(excelFileUri: Uri) {
        val gridLayout = findViewById<GridLayout>(R.id.buttonsLayout)
        gridLayout.removeAllViews() // Удаляем существующие кнопки

        try {
            val inputStream = contentResolver.openInputStream(excelFileUri)
            val workbook = XSSFWorkbook(inputStream)

            for (sheet in workbook) {
                for (row in sheet) {
                    val buttonName = row.getCell(0)?.stringCellValue
                    val buttonCount = row.getCell(1)?.numericCellValue

                    if (buttonName != null && buttonCount != null) {
                        val liveData = MutableLiveData(buttonCount)
                        createButtonWithCounter(buttonName, liveData)
                    }
                }
            }

            workbook.close()
        } catch (e: InvalidFormatException) {
            val logger = LogManager.getLogger("MyLogger")
            logger.error("InvalidFormatException while opening Excel file", e)
        } catch (e: IOException) {
            val logger = LogManager.getLogger("MyLogger")
            logger.error("IOException while opening Excel file", e)
        } catch (e: Exception) {
            val logger = LogManager.getLogger("MyLogger")
            logger.error("Error opening Excel file", e)
            e.printStackTrace()
        }
    }

    private fun saveDataToExcelFile(fileUri: Uri) {
        try {
            val workbook: Workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Data")

            for ((buttonName, liveData) in buttonCountMap) {
                val row = sheet.createRow(sheet.physicalNumberOfRows)
                val cellName = row.createCell(0)
                val cellValue = row.createCell(1)

                cellName.setCellValue(buttonName)
                cellValue.setCellValue(liveData.value ?: 0.0)
            }

            val outputStream = contentResolver.openOutputStream(fileUri)

            if (outputStream != null) {
                workbook.write(outputStream)
                outputStream.close()
                workbook.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createButtonWithCounter(buttonName: String, liveData: MutableLiveData<Double>) {
        val button = Button(this)

        // Создаем строку, которая включает текст из второй колонки с добавленной буквой "л" в конце
        val buttonText = "$buttonName:\n${liveData.value ?: 0.0} л"

        button.text = buttonText

        // Создаем параметры для кнопки
        val layoutParams = GridLayout.LayoutParams()
        layoutParams.width = GridLayout.LayoutParams.WRAP_CONTENT
        layoutParams.height = GridLayout.LayoutParams.WRAP_CONTENT
        layoutParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        layoutParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 2f)

        // Устанавливаем отступы между кнопками
        layoutParams.setMargins(20, resources.getDimensionPixelSize(R.dimen.button_margin), 10, 10)

        // Применяем стиль к кнопке
        button.setBackgroundResource(R.drawable.button_background)
        button.setTextColor(ContextCompat.getColor(this, R.color.black))
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        button.setPadding(0, 100, 0, 100)
        button.layoutParams = layoutParams

        // Устанавливаем обработчик нажатия кнопки
        button.setOnClickListener { onButtonClick(buttonName) }

        val gridLayout = findViewById<GridLayout>(R.id.buttonsLayout)
        gridLayout.addView(button)

        // Сохраняем кнопку и соответствующий LiveData в мапы
        buttonCountMap[buttonName] = liveData
        buttonTextViewMap[buttonName] = button
    }

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

        // Получить начальное значение из buttonCountMap
        val initialValue = buttonCountMap[buttonName]?.value ?: 0.0

        // Установить начальное значение в counterTextView
        counterTextView.text = initialValue.toString()

        fun updateButtonCountText() {
            val currentCount = buttonCountMap[buttonName]?.value ?: 0.0
            buttonTextViewMap[buttonName]?.text = "$buttonName:\n$currentCount л"
        }

        addButton.setOnClickListener {
            val currentCount = buttonCountMap[buttonName]?.value ?: 0.0
            val customValue = customValueEditText.text.toString().toDoubleOrNull() ?: 0.0
            val newCount = currentCount + customValue
            buttonCountMap[buttonName]?.value = newCount
            counterTextView.text = newCount.toString()
            updateButtonCountText()
        }

        subtractButton.setOnClickListener {
            val currentCount = buttonCountMap[buttonName]?.value ?: 0.0
            val customValue = customValueEditText.text.toString().toDoubleOrNull() ?: 0.0
            if (currentCount >= customValue) {
                val newCount = currentCount - customValue
                buttonCountMap[buttonName]?.value = newCount
                counterTextView.text = newCount.toString()
                updateButtonCountText()
            }
        }

        predefinedValueButton1.setOnClickListener {
            val currentCount = buttonCountMap[buttonName]?.value ?: 0.0
            val predefinedValue = 1.0

            if (currentCount >= predefinedValue) {
                val newCount = currentCount - predefinedValue
                buttonCountMap[buttonName]?.value = newCount
                counterTextView.text = newCount.toString()
                updateButtonCountText()
            }
        }

        predefinedValueButton2.setOnClickListener {
            val currentCount = buttonCountMap[buttonName]?.value ?: 0.0
            val predefinedValue = 1.5

            if (currentCount >= predefinedValue) {
                val newCount = currentCount - predefinedValue
                buttonCountMap[buttonName]?.value = newCount
                counterTextView.text = newCount.toString()
                updateButtonCountText()
            }
        }

        predefinedValueButton3.setOnClickListener {
            val currentCount = buttonCountMap[buttonName]?.value ?: 0.0
            val predefinedValue = 2.0

            if (currentCount >= predefinedValue) {
                val newCount = currentCount - predefinedValue
                buttonCountMap[buttonName]?.value = newCount
                counterTextView.text = newCount.toString()
                updateButtonCountText()
            }
        }

        builder.setPositiveButton("Закрыть") { dialog, _ ->
            updateButtonCountText()
            dialog.dismiss()
        }

        builder.create().show()
    }
}
