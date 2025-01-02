package com.example.picfinder

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView

class ImageAdapter(private val context: Context, private var images: List<String>) :
    RecyclerView.Adapter<ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        // Calculate the size of each item
        val layoutParams = ViewGroup.LayoutParams(
            parent.width / 3,
            parent.width / 3
        )
        val imageView = AppCompatImageView(context).apply {
            this.layoutParams = layoutParams
            scaleType = ImageView.ScaleType.CENTER_CROP // Crop and center the image
        }
        return ImageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imagePath = images[position]
        val bitmap = BitmapFactory.decodeFile(imagePath)
        holder.imageView.setImageBitmap(bitmap)

        holder.imageView.setOnClickListener {
            val intent = Intent(context, FullImageActivity::class.java).apply {
                putExtra("imagePath", imagePath)
            }
            context.startActivity(intent)
        }
    }


    override fun getItemCount(): Int {
        return images.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newImages: List<String>) {
        images = newImages
        notifyDataSetChanged()
    } }

class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacing: Int,
    private val includeEdge: Boolean
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val column = position % spanCount

        if (includeEdge) {
            outRect.left = spacing - column * spacing / spanCount
            outRect.right = (column + 1) * spacing / spanCount

            if (position < spanCount) {
                outRect.top = spacing
            }
            outRect.bottom = spacing
        } else {
            outRect.left = column * spacing / spanCount
            outRect.right =
                spacing - (column + 1) * spacing / spanCount

            if (position >= spanCount) {
                outRect.top = spacing
            }
        }
    }
}