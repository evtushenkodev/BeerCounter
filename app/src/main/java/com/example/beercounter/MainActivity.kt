package com.example.beercounter

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beercounter.adapter.ButtonAdapter
import org.apache.logging.log4j.LogManager
import org.apache.poi.openxml4j.exceptions.InvalidFormatException
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.IOException
import java.util.Date
import java.util.Locale


data class BeerButtonData(val name: String, val count: MutableLiveData<Double>) {
    // Метод для обновления значения в объекте BeerButtonData
    fun updateValue(newValue: Double) {
        count.value = newValue
    }
}


class MainActivity : AppCompatActivity() {
    private val buttonCountMap = mutableMapOf<String, MutableLiveData<Double>>()
    private val buttonDataList = mutableListOf<BeerButtonData>()

    private lateinit var recyclerView: RecyclerView
    private lateinit var buttonAdapter: ButtonAdapter


    private val openExcelDocument: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
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

        recyclerView = findViewById(R.id.buttonsRecyclerView)
        val numberOfColumns = resources.getInteger(R.integer.buttons_per_row)
        recyclerView.layoutManager = GridLayoutManager(this, numberOfColumns)
        buttonAdapter = ButtonAdapter(buttonDataList) { buttonData ->
            showCounterDialog(buttonData)
        }
        recyclerView.adapter = buttonAdapter
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun readExcelAndCreateButtons(excelFileUri: Uri) {
        buttonDataList.clear() // Очищаем существующий список кнопок

        try {
            val inputStream = contentResolver.openInputStream(excelFileUri)
            val workbook = XSSFWorkbook(inputStream)

            for (sheet in workbook) {
                for (row in sheet) {
                    val buttonName = row.getCell(0)?.stringCellValue
                    val buttonCount = row.getCell(1)?.numericCellValue

                    if (buttonName != null && buttonCount != null) {
                        val liveData = MutableLiveData(buttonCount)
                        val beerButtonData = BeerButtonData(buttonName, liveData)
                        buttonDataList.add(beerButtonData) // Добавляем данные о кнопке в список
                    }
                }
            }

            workbook.close()

            buttonAdapter.notifyDataSetChanged() // Обновляем адаптер после изменения списка кнопок
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

            for ((index, buttonData) in buttonDataList.withIndex()) {
                val row = sheet.createRow(index)
                val cellName = row.createCell(0)
                val cellValue = row.createCell(1)

                cellName.setCellValue(buttonData.name)
                cellValue.setCellValue(buttonData.count.value ?: 0.0)
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


    private fun showCounterDialog(buttonData: BeerButtonData) {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_counter, null)
        builder.setView(dialogView)
        val beerNameTextView = dialogView.findViewById<TextView>(R.id.beerNameTextView)
        val buttonName = buttonData.name
        beerNameTextView.text = buttonName

        val counterTextView = dialogView.findViewById<TextView>(R.id.counterTextView)
        val addButton = dialogView.findViewById<Button>(R.id.addButton)
        val saleButton = dialogView.findViewById<Button>(R.id.saleButton)
        val predefinedValueButton1 = dialogView.findViewById<Button>(R.id.predefinedValueButton1)
        val predefinedValueButton2 = dialogView.findViewById<Button>(R.id.predefinedValueButton2)
        val predefinedValueButton3 = dialogView.findViewById<Button>(R.id.predefinedValueButton3)
        val customValueEditText = dialogView.findViewById<EditText>(R.id.customValueEditText)

        // Получить начальное значение из buttonData
        val initialValue = buttonData.count.value ?: 0.0

        fun updateButtonCountText() {
            val currentCount = buttonData.count.value ?: 0.0
            val text: String
            val delta: String
            val colorSpan: ForegroundColorSpan
            val greenColor = Color.parseColor("#008000")

            if (currentCount > initialValue) {
                delta = "+ %.1f".format(currentCount - initialValue)
                colorSpan =
                    ForegroundColorSpan(greenColor) // Зеленый цвет для прибавленного значения
            } else if (currentCount < initialValue) {
                delta = "- %.1f".format(initialValue - currentCount)
                colorSpan = ForegroundColorSpan(Color.RED) // Красный цвет для отнятого значения
            } else {
                delta = ""
                colorSpan = ForegroundColorSpan(Color.BLACK) // Черный цвет, если изменений нет
            }

            text = "%.1f л %s".format(currentCount, delta)
            val spannable = SpannableString(text)
            val deltaStart = text.indexOf(delta)
            val deltaEnd = deltaStart + delta.length
            spannable.setSpan(colorSpan, deltaStart, deltaEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            counterTextView.text = spannable

            // Обновляем текст на кнопке в адаптере
            buttonAdapter.updateButtonValue(buttonData, currentCount)
        }

        // Установить начальное значение в counterTextView
        updateButtonCountText()

        addButton.setOnClickListener {
            val currentCount = buttonData.count.value ?: 0.0
            val customValue = customValueEditText.text.toString().toDoubleOrNull() ?: 0.0
            val newCount = currentCount + customValue
            buttonData.updateValue(newCount)
            customValueEditText.text.clear()
            val newText = "%.1f л + %.1f".format(newCount, customValue)
            val spannable = SpannableString(newText)
            // Устанавливаем цвет для второй части текста (отнятой суммы)
            spannable.setSpan(
                ForegroundColorSpan(Color.GREEN),
                newText.indexOf('+') + 1,
                newText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            counterTextView.text = spannable
            updateButtonCountText()

        }


        saleButton.setOnClickListener {
            val currentCount = buttonData.count.value ?: 0.0
            val customValue = customValueEditText.text.toString().toDoubleOrNull() ?: 0.0
            if (currentCount >= customValue) {
                val newCount = currentCount - customValue
                buttonData.updateValue(newCount)
                customValueEditText.text.clear()
                val newText = "%.1f л - %.1f".format(newCount, customValue)
                val spannable = SpannableString(newText)
                spannable.setSpan(
                    ForegroundColorSpan(Color.RED),
                    newText.indexOf('-') + 1,
                    newText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                counterTextView.text = spannable
                updateButtonCountText()

            }
        }

        predefinedValueButton1.setOnClickListener {
            val currentCount = buttonData.count.value ?: 0.0
            val predefinedValue = 1.0

            if (currentCount >= predefinedValue) {
                val newCount = currentCount - predefinedValue
                buttonData.updateValue(newCount)
                val newText = "%.1f л - %.1f".format(newCount, predefinedValue)
                val spannable = SpannableString(newText)
                spannable.setSpan(
                    ForegroundColorSpan(Color.RED),
                    newText.indexOf('-') + 1,
                    newText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                counterTextView.text = spannable
                updateButtonCountText()

            }
        }

        predefinedValueButton2.setOnClickListener {
            val currentCount = buttonData.count.value ?: 0.0
            val predefinedValue = 1.5

            if (currentCount >= predefinedValue) {
                val newCount = currentCount - predefinedValue
                buttonData.updateValue(newCount)
                val newText = "%.1f л - %.1f".format(newCount, predefinedValue)
                val spannable = SpannableString(newText)
                spannable.setSpan(
                    ForegroundColorSpan(Color.RED),
                    newText.indexOf('-') + 1,
                    newText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                counterTextView.text = spannable
                updateButtonCountText()

            }
        }

        predefinedValueButton3.setOnClickListener {
            val currentCount = buttonData.count.value ?: 0.0
            val predefinedValue = 2.0

            if (currentCount >= predefinedValue) {
                val newCount = currentCount - predefinedValue
                buttonData.updateValue(newCount)
                val newText = "%.1f л - %.1f".format(newCount, predefinedValue)
                val spannable = SpannableString(newText)
                spannable.setSpan(
                    ForegroundColorSpan(Color.RED),
                    newText.indexOf('-') + 1,
                    newText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                counterTextView.text = spannable
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
