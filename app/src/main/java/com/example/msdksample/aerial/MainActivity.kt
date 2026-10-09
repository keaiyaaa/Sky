package com.example.msdksample.aerial

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.msdksample.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val button = findViewById<Button>(R.id.btnStartAerial)
        button.setOnClickListener {
            try {
                val intent = Intent(this, Aerial_photography::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
}