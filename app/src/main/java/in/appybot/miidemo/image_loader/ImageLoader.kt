package `in`.appybot.miidemo.image_loader

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.util.LruCache
import android.widget.ImageView
import java.util.*
import java.util.Collections.synchronizedMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageLoader (context: Context)  {

    private val maxCacheSize: Int = 5 * 1024 * 1024
    private val memoryCache: LruCache<String, Bitmap>

    private val executorService: ExecutorService

    private val imageViewMap = synchronizedMap(WeakHashMap<ImageView, String>())
    private val handler: Handler

    init {
        memoryCache = LruCache(maxCacheSize)
        executorService = Executors.newFixedThreadPool(5, Utils.ImageThreadFactory())
        handler = Handler()

        val metrics = context.getResources().getDisplayMetrics()
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    companion object {

        private var INSTANCE: ImageLoader? = null

        internal var screenWidth = 0
        internal var screenHeight = 0

        @Synchronized
        fun with(context: Context): ImageLoader {

            require(context != null) {
                "ImageLoader:with - Context should not be null."
            }

            return INSTANCE ?: ImageLoader(context).also {
                INSTANCE = it
            }

        }
    }

    fun load(imageView: ImageView, imageUrl: String) {

        require(imageView != null) {
            "ImageLoader:load - ImageView should not be null."
        }

        require(imageUrl != null && imageUrl.length > 0) {
            "ImageLoader:load - Image Url should not be empty"
        }

        imageView.setImageResource(0);
        imageViewMap.put(imageView, imageUrl)

        var bitmap = checkImageInCache(imageUrl)
        bitmap?.let {
            loadImageIntoImageView(imageView, it, imageUrl)
        } ?: run {
            executorService.submit(PhotosLoader(ImageRequest(imageUrl, imageView)))
        }
    }

    @Synchronized
    private  fun loadImageIntoImageView(imageView: ImageView, bitmap: Bitmap?, imageUrl: String) {

        require(bitmap != null) {
            "ImageLoader:loadImageIntoImageView - Bitmap should not be null"
        }

        val scaledBitmap = Utils.scaleBitmapForLoad(bitmap, imageView.width, imageView.height)

        scaledBitmap?.let {
            if(!isImageViewReused(ImageRequest(imageUrl, imageView))) imageView?.setImageBitmap(scaledBitmap)
        }
    }

    private fun isImageViewReused(imageRequest: ImageRequest): Boolean {
        val tag = imageViewMap.get(imageRequest.imageView)
        return tag == null || tag != imageRequest.imgUrl
    }

    @Synchronized
    private fun checkImageInCache(imageUrl: String): Bitmap? = memoryCache.get(imageUrl)

    inner class DisplayBitmap(var imageRequest: ImageRequest) : Runnable {
        override fun run() {
            if(!isImageViewReused(imageRequest)) loadImageIntoImageView(imageRequest.imageView, checkImageInCache(imageRequest.imgUrl), imageRequest.imgUrl)
        }
    }

    inner class ImageRequest(var imgUrl: String, var imageView: ImageView)

    inner class PhotosLoader(var imageRequest: ImageRequest) : Runnable {

        override fun run() {

            if(isImageViewReused(imageRequest)) return

            val bitmap = Utils.downloadBitmapFromURL(imageRequest.imgUrl)
            memoryCache.put(imageRequest.imgUrl, bitmap)

            if(isImageViewReused(imageRequest)) return

            val displayBitmap = DisplayBitmap(imageRequest)
            handler.post(displayBitmap)
        }
    }
}


