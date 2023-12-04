package com.example.beercounter.adapter

import android.annotation.SuppressLint
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.example.beercounter.BeerButtonData
import com.example.beercounter.R

class ButtonAdapter(private val buttonDataList: List<BeerButtonData>, private val onButtonClick: (BeerButtonData) -> Unit) : RecyclerView.Adapter<ButtonAdapter.ButtonViewHolder>() {

    private var maxButtonWidth = 0
    private var maxButtonHeight = 0

    inner class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: Button = itemView.findViewById(R.id.beerButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.beer_button_item, parent, false)
        return ButtonViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        val buttonData = buttonDataList[position]
        val button = holder.button

        val nameWords = buttonData.name.split(" ")
        val formattedName = buildString {
            for (word in nameWords) {
                val truncatedWord = if (word.length > 9) {
                    word.substring(0, 9)
                } else {
                    word
                }
                if (isNotEmpty()) {
                    append("<br>")
                }
                append(truncatedWord)
            }
        }

        val formattedValue = (buttonData.count.value ?: 0.0)

        // Используем HTML-разметку для переноса строк и для высоты
        val htmlText = "$formattedName:<br><br><b>${formattedValue} <small>л</small></b>"

        button.text = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)

        button.setOnClickListener {
            onButtonClick(buttonData)
        }

        // Измеряем высоту кнопки
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        button.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), heightMeasureSpec)
        val buttonHeight = button.measuredHeight

        // Сохраняем максимальную высоту
        if (buttonHeight > maxButtonHeight) {
            maxButtonHeight = buttonHeight
        }

        // Измеряем ширину кнопки
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        button.measure(widthMeasureSpec, heightMeasureSpec)
        val buttonWidth = button.measuredWidth

        // Сравниваем с текущей максимальной шириной и обновляем, если больше
        if (buttonWidth > maxButtonWidth) {
            maxButtonWidth = buttonWidth
        }
    }


    override fun getItemCount(): Int {
        return buttonDataList.size
    }

    override fun onViewAttachedToWindow(holder: ButtonViewHolder) {
        // Устанавливаем максимальную ширину и высоту для каждой кнопки в RecyclerView
        val layoutParams = holder.button.layoutParams
        layoutParams.width = maxButtonWidth
        layoutParams.height = maxButtonHeight
        holder.button.layoutParams = layoutParams
    }

    fun updateButtonValue(buttonData: BeerButtonData, newValue: Double) {
        val position = buttonDataList.indexOf(buttonData)
        if (position != -1) {
            buttonDataList[position].updateValue(newValue)
            notifyItemChanged(position)
        }
    }
}
