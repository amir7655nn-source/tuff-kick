package com.tuff.kick

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder

/** One image loader for the whole app: animated GIF/WebP emotes and SVG badges. */
class TuffApp : Application(), ImageLoaderFactory {
    companion object {
        lateinit var instance: TuffApp
            private set
    }

    /** Plain loader without animated decoders: shows the first frame only. Used for the long emote picker (smooth scrolling). */
    val staticLoader: ImageLoader by lazy {
        ImageLoader.Builder(this).components { add(SvgDecoder.Factory()) }.build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                add(SvgDecoder.Factory())
            }
            .build()
    }
}
