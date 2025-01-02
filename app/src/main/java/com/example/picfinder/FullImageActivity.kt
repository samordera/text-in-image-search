package com.example.picfinder

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class FullImageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image)

        val imageView: ImageView = findViewById(R.id.imageViewFull)
        val backButton: ImageButton = findViewById(R.id.buttonBack)
        val shareButton: ImageButton = findViewById(R.id.buttonShare)

        // Get the image path from the intent
        val imagePath = intent.getStringExtra("imagePath")

        // Load the image into the ImageView
        Glide.with(this).load(imagePath).into(imageView)

        // Handle the Back button click
        backButton.setOnClickListener {
            finish() // Close this activity and go back
        }

        // Handle the Share button click
        shareButton.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imagePath)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Image"))
        }
    }
}
