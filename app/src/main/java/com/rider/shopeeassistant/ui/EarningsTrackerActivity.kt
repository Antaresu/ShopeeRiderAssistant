package com.rider.shopeeassistant.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rider.shopeeassistant.R

class EarningsTrackerActivity : AppCompatActivity() {

    private lateinit var tvNetProfit: TextView
    private lateinit var tvGrossTotal: TextView
    private lateinit var tvGasDeductionText: TextView
    private lateinit var etKmRun: EditText
    private lateinit var etGasPrice: EditText

    private val shippingFee = 980.0f
    private val incentiveBonus = 120.0f
    private val tips = 40.0f
    private val grossEarnings = shippingFee + incentiveBonus + tips
    private val click125KmPerLiter = 48.0f // Honda Click 125 V4 average consumption

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_earnings)

        tvNetProfit = findViewById(R.id.tvNetProfit)
        tvGrossTotal = findViewById(R.id.tvGrossTotal)
        tvGasDeductionText = findViewById(R.id.tvGasDeductionText)
        etKmRun = findViewById(R.id.etKmRun)
        etGasPrice = findViewById(R.id.etGasPricePerLiter)

        tvGrossTotal.text = String.format("₱%.2f", grossEarnings)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                calculateNetTakeHome()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etKmRun.addTextChangedListener(watcher)
        etGasPrice.addTextChangedListener(watcher)

        calculateNetTakeHome()
    }

    private fun calculateNetTakeHome() {
        val km = etKmRun.text.toString().toFloatOrNull() ?: 0f
        val pricePerLiter = etGasPrice.text.toString().toFloatOrNull() ?: 0f

        val litersConsumed = km / click125KmPerLiter
        val gasExpense = litersConsumed * pricePerLiter
        val netProfit = grossEarnings - gasExpense

        tvGasDeductionText.text = String.format(
            "Click 125 V4 (%.1f L naubos) | Kaltas sa Gasolina: -₱%.2f",
            litersConsumed, gasExpense
        )
        tvNetProfit.text = String.format("₱%.2f", netProfit)
    }
}
