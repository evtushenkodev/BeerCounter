package com.example.beercounter

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var dbHelper: MyDatabaseHelper
    private val buttonDataList = mutableListOf<BeerButtonData>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var buttonAdapter: ButtonAdapter
    private val initialBeerCounts = mutableMapOf<String, Double>()
    private var isShiftOpen = false
    private lateinit var chooseFileButton: Button
    private lateinit var openShiftButton: Button
    private lateinit var closeShiftButton: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chooseFileButton = findViewById(R.id.chooseFileButton)
        chooseFileButton.visibility = View.VISIBLE

        openShiftButton = findViewById(R.id.openShiftButton)
        closeShiftButton = findViewById(R.id.closeShiftButton)

        openShiftButton.setOnClickListener { openShift() }
        closeShiftButton.setOnClickListener { closeShift() }

        openShiftButton.visibility = View.VISIBLE
        closeShiftButton.visibility = View.GONE

        dbHelper = MyDatabaseHelper(this)
        dbHelper.writableDatabase

        recyclerView = findViewById(R.id.buttonsRecyclerView)
        val numberOfColumns = resources.getInteger(R.integer.buttons_per_row)
        recyclerView.layoutManager = GridLayoutManager(this, numberOfColumns)
        buttonAdapter = ButtonAdapter(buttonDataList) { buttonData ->
            showCounterDialog(buttonData)
        }
        recyclerView.adapter = buttonAdapter

        if (isDatabaseAvailable()) {
            loadButtonDataFromDatabase()
        } else {
            Toast.makeText(this, "База данных не существует", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openShift() {
        if (!isShiftOpen) {
            isShiftOpen = true
            Toast.makeText(this, "Смена открыта", Toast.LENGTH_SHORT).show()

            // Записываем начальные значения количества пива
            buttonDataList.forEach { beerButtonData ->
                initialBeerCounts[beerButtonData.name] = beerButtonData.count.value ?: 0.0
                openShiftButton.visibility = View.GONE
                closeShiftButton.visibility = View.VISIBLE
                chooseFileButton.visibility = View.GONE
            }
        }
    }

    private fun closeShift() {
        if (isShiftOpen) {
            isShiftOpen = false
            Toast.makeText(this, "Смена закрыта", Toast.LENGTH_SHORT).show()

            val beerDifferences = calculateBeerDifferences()
            val currentDate = SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(Date())
            val fileName = "shift_data_$currentDate.xlsx"
            saveExcelDocument.launch(fileName) // Запускаем сохранение файла с выбранным именем
            closeShiftButton.visibility = View.GONE
            openShiftButton.visibility = View.VISIBLE
            chooseFileButton.visibility = View.VISIBLE
        }
    }

    private fun isDatabaseAvailable(): Boolean {
        val databaseFile = getDatabasePath("beer_data.db")
        return databaseFile.exists()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun insertDataToDatabase(buttonDataList: List<BeerButtonData>) {
        // Очистим существующие данные в базе данных
        val database = dbHelper.writableDatabase
        database.delete("beer_table", null, null)

        // Вставляем новые данные из списка
        for (beerButtonData in buttonDataList) {
            val values = ContentValues()
            values.put("name", beerButtonData.name)
            values.put("count", beerButtonData.count.value)

            // Вставляем данные в базу данных
            database.insert("beer_table", null, values)
        }
    }

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
                val beerDifferences = calculateBeerDifferences()
                saveDataToExcelFile(uri, beerDifferences) // Сохраняем данные в файл
            }
        }

    private fun calculateBeerDifferences(): List<Triple<String, Double, Double>> {
        // Вычисляем разницу в количестве пива
        return buttonDataList.map { beerButtonData ->
            val initialCount = initialBeerCounts[beerButtonData.name] ?: 0.0
            val currentCount = beerButtonData.count.value ?: 0.0
            val difference = currentCount - initialCount
            Triple(beerButtonData.name, initialCount, difference)
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


    @SuppressLint("Range", "NotifyDataSetChanged")
    private fun loadButtonDataFromDatabase() {
        // Загрузка данных из базы данных и заполнение buttonDataList
        val database = dbHelper.readableDatabase
        val projection = arrayOf("name", "count")
        val cursor = database.query("beer_table", projection, null, null, null, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                Log.d("loadButtonDataFromDatabase", "Cursor moveToFirst() successful")
                do {
                    val name = cursor.getString(cursor.getColumnIndex("name"))
                    val count = cursor.getDouble(cursor.getColumnIndex("count"))
                    val liveData = MutableLiveData(count)
                    buttonDataList.add(BeerButtonData(name, liveData))

                    Log.d("loadButtonDataFromDatabase", "Name: $name, Count: $count")
                } while (cursor.moveToNext())

                cursor.close()

                // Уведомить адаптер об изменении данных
                buttonAdapter.notifyDataSetChanged()
            }
        }
    }


    private fun updateButtonDataInDatabase() {
        // Обновление данных в базе данных
        val database = dbHelper.writableDatabase

        for (buttonData in buttonDataList) {
            val values = ContentValues().apply {
                put("name", buttonData.name)
                put("count", buttonData.count.value ?: 0.0)
            }

            val selection = "name = ?"
            val selectionArgs = arrayOf(buttonData.name)
            database.update("beer_table", values, selection, selectionArgs)

        }
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

            insertDataToDatabase(buttonDataList)

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

    private fun saveDataToExcelFile(uri: Uri, beerDifferences: List<Triple<String, Double, Double>>) {
        try {
            val workbook: Workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Shift Data")

            for ((index, data) in beerDifferences.withIndex()) {
                val (name, initialCount, difference) = data
                val row = sheet.createRow(index)
                val cellName = row.createCell(0)
                val cellInitialCount = row.createCell(1)
                val cellDifference = row.createCell(2)

                cellName.setCellValue(name)
                cellInitialCount.setCellValue(initialCount)
                cellDifference.setCellValue(difference)
            }

            val outputStream = contentResolver.openOutputStream(uri)
            outputStream?.let {
                workbook.write(it)
                it.close()
            }
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    @SuppressLint("SetTextI18n")
    private fun showCounterDialog(buttonData: BeerButtonData) {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_counter, null)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.show()

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

        @SuppressLint("SetTextI18n")
        fun updateButtonCountText() {
            val currentCount = buttonData.count.value ?: 0.0
            val text = "%.1f л".format(currentCount)
            counterTextView.text = text

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
            updateButtonCountText()

            val toastMessage = "Принято $customValue л  $buttonName"
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()

            updateButtonDataInDatabase() // Вызываем метод для обновления данных в базе

            dialog.dismiss()
        }


        saleButton.setOnClickListener {
            val currentCount = buttonData.count.value ?: 0.0
            val customValue = customValueEditText.text.toString().toDoubleOrNull() ?: 0.0

            if (currentCount >= customValue) {
                val newCount = currentCount - customValue
                buttonData.updateValue(newCount)
                updateButtonCountText()

                val toastMessage = "Продано $customValue л  $buttonName"
                Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()

                updateButtonDataInDatabase() // Вызываем метод для обновления данных в базе

                dialog.dismiss()
            } else {
                Toast.makeText(this, "Недостаточно пива для продажи", Toast.LENGTH_SHORT).show()
            }
        }


        predefinedValueButton1.setOnClickListener {
            val predefinedValue = 1.0
            val currentValue = customValueEditText.text.toString().toDoubleOrNull() ?: 0.0
            customValueEditText.setText((currentValue + predefinedValue).toString())
        }

        predefinedValueButton2.setOnClickListener {
            val predefinedValue = 1.5
            val currentValue = customValueEditText.text.toString().toDoubleOrNull() ?: 0.0
            customValueEditText.setText((currentValue + predefinedValue).toString())
        }

        predefinedValueButton3.setOnClickListener {
            val predefinedValue = 2.0
            val currentValue = customValueEditText.text.toString().toDoubleOrNull() ?: 0.0
            customValueEditText.setText((currentValue + predefinedValue).toString())
        }

    }

}
