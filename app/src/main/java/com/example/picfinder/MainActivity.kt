package com.example.picfinder

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.result.ActivityResultLauncher
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.*

class MainActivity : AppCompatActivity() {
    private lateinit var imageRecyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    private var imageList: List<String> = listOf()

    private lateinit var loadingIndicatorLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var annotationText: TextView

    private val annotationFileName = "annotations.dat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageRecyclerView = findViewById(R.id.recyclerViewImages)
        imageRecyclerView.layoutManager = GridLayoutManager(this, 3)

        val spacing = 16
        val spanCount = 3
        val includeEdge = true
        imageRecyclerView.addItemDecoration(GridSpacingItemDecoration(spanCount, spacing, includeEdge))

        imageAdapter = ImageAdapter(this, imageList)
        imageRecyclerView.adapter = imageAdapter

        // Initialize UI elements for loading indicator
        loadingIndicatorLayout = findViewById(R.id.loadingIndicatorLayout)
        progressBar = findViewById(R.id.progressBar)
        annotationText = findViewById(R.id.annotationText)

        val debugButton: Button = findViewById(R.id.debugButton)
        debugButton.setOnClickListener {
            printAnnotationFileContents()
        }

        val searchBar: EditText = findViewById(R.id.searchBar)
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                filterImages(query)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        if (areMediaPermissionsGranted()) {
            loadImagesFromStorage()
            printAnnotationFileContents()
        } else {
            requestMediaPermission()
        }
    }

    private fun filterImages(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val annotations = readAnnotations()

            val filteredImages = imageList.filter { imagePath ->
                annotations[imagePath]?.contains(query, ignoreCase = true) == true
            }

            withContext(Dispatchers.Main) {
                imageAdapter.updateData(filteredImages)
            }
        }
    }

    private fun readAnnotations(): HashMap<String, String> {
        val file = File(filesDir, annotationFileName)
        if (!file.exists()) return HashMap()

        ObjectInputStream(FileInputStream(file)).use { input ->
            @Suppress("UNCHECKED_CAST")
            return input.readObject() as HashMap<String, String>
        }
    }

    private fun writeAnnotations(annotations: HashMap<String, String>) {
        val file = File(filesDir, annotationFileName)
        ObjectOutputStream(FileOutputStream(file)).use { output ->
            output.writeObject(annotations)
        }
    }

    private fun areMediaPermissionsGranted(): Boolean {
        val isImagePermissionGranted = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }

        return isImagePermissionGranted
    }

    private fun requestMediaPermission() {
        val permissions: Array<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        requestPermissionLauncher.launch(permissions)
    }

    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission ->
            val imagePermissionGranted: Boolean = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    permission[Manifest.permission.READ_MEDIA_IMAGES] == true
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    permission[Manifest.permission.READ_EXTERNAL_STORAGE] == true
                }
                else -> {
                    permission[Manifest.permission.READ_EXTERNAL_STORAGE] == true &&
                            permission[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
                }
            }
            when {
                imagePermissionGranted -> {
                    loadImagesFromStorage()
                }
                !imagePermissionGranted -> {
                    Toast.makeText(this, "Image access permission denied", Toast.LENGTH_LONG).show()
                }
            }
        }

    private fun loadImagesFromStorage() {
        val contentResolver: ContentResolver = contentResolver
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)

        val images = mutableListOf<String>()
        cursor?.use {
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (cursor.moveToNext()) {
                val imagePath = cursor.getString(columnIndex)
                images.add(imagePath)
            }
        }

        imageList = images
        imageAdapter.updateData(imageList)

        CoroutineScope(Dispatchers.IO).launch {
            val annotations = readAnnotations()

            val unannotatedImages = images.filterNot { annotations.containsKey(it) }

            withContext(Dispatchers.Main) {
                if (unannotatedImages.isEmpty()) {
                    println("All images are already annotated.")
                    return@withContext
                }

                showLoadingIndicator(true)
            }

            for (imagePath in unannotatedImages) {
                val file = File(imagePath)
                val requestBody: RequestBody = file
                    .asRequestBody("image/*".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", file.name, requestBody)

                try {
                    val response = RetrofitInstance.apiService.uploadImage(part)
                    if (response.isSuccessful) {
                        val annotationText = response.body()?.texts ?: "No text found"
                        annotations[imagePath] = annotationText
                        println("Annotated Image: $imagePath -> $annotationText")
                    } else {
                        println("Failed to annotate image: $imagePath")
                    }
                } catch (e: Exception) {
                    println("Error uploading image: $imagePath, ${e.message}")
                }
            }

            writeAnnotations(annotations)

            withContext(Dispatchers.Main) {
                showLoadingIndicator(false)
                println("Annotation workflow completed.")
            }
        }
    }

    private fun printAnnotationFileContents() {
        try {
            val file = File(filesDir, annotationFileName)
            if (file.exists()) {
                ObjectInputStream(FileInputStream(file)).use { input ->
                    @Suppress("UNCHECKED_CAST")
                    val annotations = input.readObject() as HashMap<String, String>
                    annotations.forEach { (imagePath, annotation) ->
                        Log.d("AnnotationData", "Image Path: $imagePath, Annotated Text: $annotation")
                    }
                    Toast.makeText(this, "Annotation data logged to Logcat", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("AnnotationData", "No annotations found; file does not exist.")
                Toast.makeText(this, "No annotation file found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("AnnotationData", "Error reading annotations.dat: ${e.message}", e)
            Toast.makeText(this, "Error reading annotations", Toast.LENGTH_LONG).show()
        }
    }

    private fun showLoadingIndicator(show: Boolean) {
        if (show) {
            loadingIndicatorLayout.visibility = LinearLayout.VISIBLE
        } else {
            loadingIndicatorLayout.visibility = LinearLayout.GONE
        }
    }
}
