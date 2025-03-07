package net.pantasystem.milktea.common_android.ui.text


import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.penfeizhou.animation.apng.APNGDrawable
import java.lang.ref.WeakReference

class DrawableEmojiSpan(
    var adapter: EmojiAdapter?,
    k: Any?,
    aspectRatio: Float? = null,
    emojiScale: Float = 1f,
) : EmojiSpan<Any?>(k, aspectRatio = aspectRatio, emojiScale = emojiScale) {
    //val weakReference: WeakReference<View> = WeakReference(view)


//    /**
//     * invalidateSelfによって呼び出されるコールバックを実装することによって
//     * invalidateSelfが呼び出されたときに自信のview.invalidateを呼び出し再描画をする
//     * (GifDrawableはdrawを呼び出すと自動的にcurrentのGifが読み込まれる)
//     */
//    inner class Animated : Drawable.Callback{
//        override fun invalidateDrawable(p0: Drawable) {
//            //weakReference.get()?.invalidate()
//            adapter?.update()
//        }
//
//        override fun scheduleDrawable(p0: Drawable, p1: Runnable, p2: Long) {
//        }
//
//        override fun unscheduleDrawable(p0: Drawable, p1: Runnable) {
//        }
//    }


    val target: CustomTarget<Drawable> = generateTarget()
//    override val target = object : CustomTarget<Drawable>(){
//        override fun onResourceReady(
//            resource: Drawable,
//            transition: Transition<in Drawable>?
//        ) {
//            imageDrawable = resource
//            imageDrawable?.callback = Animated()
//            when (resource) {
//                is GifDrawable -> {
//                    resource.start()
//                }
//                is APNGDrawable -> {
//                    resource.start()
//                }
//                else -> {
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                        if (resource is AnimatedImageDrawable) {
//                            resource.start()
//                        } else {
//                            adapter?.update()
//                        }
//                    } else {
//                        adapter?.update()
//                    }
//                }
//            }
//        }
//        override fun onLoadCleared(placeholder: Drawable?) {
//            imageDrawable = placeholder
//        }
//    }


    private fun generateTarget(): DrawableEmojiTarget {
        return DrawableEmojiTarget(this)
    }
}

private class DrawableEmojiTarget(
    span: DrawableEmojiSpan,
) : CustomTarget<Drawable>() {

    private val span = WeakReference(span)
    override fun onResourceReady(
        resource: Drawable,
        transition: Transition<in Drawable>?,
    ) {
        span.get()?.imageDrawable = resource

        val callback = span.get()?.imageDrawable?.callback
        resource.callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) {
                callback?.invalidateDrawable(who)
                span.get()?.adapter?.update(false)
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                callback?.scheduleDrawable(who, what, `when`)
            }

            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                callback?.unscheduleDrawable(who, what)
            }
        }

        when (resource) {
            is GifDrawable -> {
                resource.start()
            }
            is APNGDrawable -> {
                resource.start()
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (resource is AnimatedImageDrawable) {
                        resource.start()
                    } else {
                        span.get()?.adapter?.update()
                    }
                } else {
                    span.get()?.adapter?.update()
                }
            }
        }
    }

    override fun onLoadCleared(placeholder: Drawable?) {

    }
}

