package com.example.msdksample.asuka

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.msdksample.R

class VipActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vip_asuka)

        findViewById<android.widget.ImageView>(R.id.iv_back).setOnClickListener {
            finish()
        }

        findViewById<android.widget.Button>(R.id.btn_buy_basic).setOnClickListener {
            showPurchaseSuccess(18, 1000)
        }

        findViewById<android.widget.Button>(R.id.btn_buy_pro).setOnClickListener {
            showPurchaseSuccess(88, 5000)
        }

        findViewById<android.widget.Button>(R.id.btn_buy_enterprise).setOnClickListener {
            showPurchaseSuccess(188, 12000)
        }
    }

    private fun showPurchaseSuccess(price: Int, tokens: Int) {
        val fragment = SuccessModalFragment.newInstance("购买成功！\n已获得 $tokens Token")
        fragment.show(supportFragmentManager, "SuccessModal")
    }
}