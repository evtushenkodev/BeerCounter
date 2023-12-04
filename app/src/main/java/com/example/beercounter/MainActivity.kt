package com.example.beercounter

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
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


data class BeerButtonData(val name: String, val count: MutableLiveData<Double>, var received: Double = 0.0, var sold: Double = 0.0) {
    fun updateValue(newValue: Double, isReceived: Boolean) {
        if (isReceived) {
            received += newValue - (count.value ?: 0.0)
        } else {
            sold += (count.value ?: 0.0) - newValue
        }
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
    private lateinit var sharedPreferences: SharedPreferences

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

        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        isShiftOpen = sharedPreferences.getBoolean("isShiftOpen", false)

        updateRecyclerViewVisibility()
        updateButtonsVisibility()

        if (isDatabaseAvailable()) {
            loadButtonDataFromDatabase()
        } else {
            Toast.makeText(this, "База данных не существует", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openShift() {
        if (!isShiftOpen) {
            isShiftOpen = true
            saveShiftState()
            updateButtonsVisibility()
            updateRecyclerViewVisibility()
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
            // Создание диалога подтверждения
            AlertDialog.Builder(this).apply {
                setTitle("Подтверждение")
                setMessage("Вы уверены, что хотите закрыть смену?")
                setPositiveButton("Да") { _, _ ->
                    // Пользователь подтвердил закрытие смены
                    actuallyCloseShift()
                }
                setNegativeButton("Нет", null) // Ничего не делаем, если пользователь отменил
                show()
            }
        }
    }

    private fun actuallyCloseShift() {
        isShiftOpen = false
        saveShiftState()
        updateButtonsVisibility()
        updateRecyclerViewVisibility()
        Toast.makeText(this, "Смена закрыта", Toast.LENGTH_SHORT).show()

        val beerDifferences = calculateBeerDifferences()
        val currentDate = SimpleDateFormat("ddMMyyyy", Locale.getDefault()).format(Date())
        val fileName = "shift_data_$currentDate.xlsx"
        saveExcelDocument.launch(fileName)
        closeShiftButton.visibility = View.GONE
        openShiftButton.visibility = View.VISIBLE
        chooseFileButton.visibility = View.VISIBLE
    }

    private fun saveShiftState() {
        sharedPreferences.edit()
            .putBoolean("isShiftOpen", isShiftOpen)
            .apply()
    }

    private fun updateButtonsVisibility() {
        openShiftButton.visibility = if (isShiftOpen) View.GONE else View.VISIBLE
        closeShiftButton.visibility = if (isShiftOpen) View.VISIBLE else View.GONE
    }

    private fun updateRecyclerViewVisibility() {
        recyclerView.visibility = if (isShiftOpen) View.VISIBLE else View.GONE
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

    private fun calculateBeerDifferences(): List<BeerData> {
        return buttonDataList.map { beerButtonData ->
            val initialCount = initialBeerCounts[beerButtonData.name] ?: 0.0
            val currentCount = beerButtonData.count.value ?: 0.0
            val difference = currentCount - initialCount
            BeerData(beerButtonData.name, currentCount, beerButtonData.received, beerButtonData.sold)
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

    private fun saveDataToExcelFile(uri: Uri, beerData: List<BeerData>) {
        try {
            val workbook: Workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Shift Data")

            // Создаем заголовки для колонок
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("Название пива")
            headerRow.createCell(1).setCellValue("Остаток на конец смены")
            headerRow.createCell(2).setCellValue("Принято")
            headerRow.createCell(3).setCellValue("Продано")

            var totalEnd = 0.0
            var totalReceived = 0.0
            var totalSold = 0.0

            for ((index, data) in beerData.withIndex()) {
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(data.name)
                row.createCell(1).setCellValue(data.endAmount)
                row.createCell(2).setCellValue(data.received)
                row.createCell(3).setCellValue(data.sold)

                totalEnd += data.endAmount
                totalReceived += data.received
                totalSold += data.sold
            }

            // Добавляем строку с общими суммами
            val totalRow = sheet.createRow(beerData.size + 1)
            totalRow.createCell(0).setCellValue("Итого")
            totalRow.createCell(1).setCellValue(totalEnd)
            totalRow.createCell(2).setCellValue(totalReceived)
            totalRow.createCell(3).setCellValue(totalSold)

            // Сохраняем файл
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
            buttonAdapter.updateButtonDisplay(buttonData, currentCount)
        }

        // Установить начальное значение в counterTextView
        updateButtonCountText()

        addButton.setOnClickListener {
            val currentCount = buttonData.count.value ?: 0.0
            val customValue = customValueEditText.text.toString().toDoubleOrNull() ?: 0.0
            val newCount = currentCount + customValue
            buttonData.updateValue(newCount, true) // Передаем true, так как это прием пива
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
                buttonData.updateValue(newCount, false) // Передаем false, так как это продажа пива
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

data class BeerData(val name: String, val endAmount: Double, val received: Double, val sold: Double)
