/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.riotredesign.features.reactions.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import im.vector.riotredesign.R
import im.vector.riotredesign.core.utils.TextUtils

/**
 * An animated reaction button.
 * Displays a String reaction (emoji), with a count, and that can be selected or not (toggle)
 */
class ReactionButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                               defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), View.OnClickListener, View.OnLongClickListener {

    companion object {
        private val DECCELERATE_INTERPOLATOR = DecelerateInterpolator()
        private val ACCELERATE_DECELERATE_INTERPOLATOR = AccelerateDecelerateInterpolator()
        private val OVERSHOOT_INTERPOLATOR = OvershootInterpolator(4f)

    }

    private var emojiView: TextView? = null
    private var countTextView: TextView? = null

    private var reactionSelector: View? = null

    var emojiTypeFace: Typeface? = null
        set(value) {
            field = value
            emojiView?.typeface = value ?: Typeface.DEFAULT
        }

    private var dotsView: DotsView
    private var circleView: CircleView
    var reactedListener: ReactedListener? = null
    private var dotPrimaryColor: Int = 0
    private var dotSecondaryColor: Int = 0
    private var circleStartColor: Int = 0
    private var circleEndColor: Int = 0

    var reactionCount = 11
        set(value) {
            field = value
            countTextView?.text = TextUtils.formatCountToShortDecimal(value)
        }


    var reactionString = "😀"
        set(value) {
            field = value
            emojiView?.text = field
        }

    private var animationScaleFactor: Float = 0.toFloat()

    private var isChecked: Boolean = false

    private var animatorSet: AnimatorSet? = null

    private var onDrawable: Drawable? = null
    private var offDrawable: Drawable? = null

    init {
        LayoutInflater.from(getContext()).inflate(R.layout.reaction_button, this, true)
        emojiView = findViewById(R.id.reactionText)
        dotsView = findViewById(R.id.dots)
        circleView = findViewById(R.id.circle)
        reactionSelector = findViewById(R.id.reactionSelector)
        countTextView = findViewById(R.id.reactionCount)

        countTextView?.text = TextUtils.formatCountToShortDecimal(reactionCount)

        emojiView?.typeface = this.emojiTypeFace ?: Typeface.DEFAULT

        val array = context.obtainStyledAttributes(attrs, R.styleable.ReactionButton, defStyleAttr, 0)

        onDrawable = ContextCompat.getDrawable(context, R.drawable.rounded_rect_shape)
        offDrawable = ContextCompat.getDrawable(context, R.drawable.rounded_rect_shape_off)

        circleStartColor = array.getColor(R.styleable.ReactionButton_circle_start_color, 0)

        if (circleStartColor != 0)
            circleView.startColor = circleStartColor

        circleEndColor = array.getColor(R.styleable.ReactionButton_circle_end_color, 0)

        if (circleEndColor != 0)
            circleView.endColor = circleEndColor

        dotPrimaryColor = array.getColor(R.styleable.ReactionButton_dots_primary_color, 0)
        dotSecondaryColor = array.getColor(R.styleable.ReactionButton_dots_secondary_color, 0)

        if (dotPrimaryColor != 0 && dotSecondaryColor != 0) {
            dotsView.setColors(dotPrimaryColor, dotSecondaryColor)
        }

        array.getString(R.styleable.ReactionButton_emoji)?.let {
            reactionString = it
        }

        reactionCount = array.getInt(R.styleable.ReactionButton_reaction_count, 0)

        val status = array.getBoolean(R.styleable.ReactionButton_toggled, false)
        setChecked(status)
        setOnClickListener(this)
        setOnLongClickListener(this)
        array.recycle()
    }

    private fun getDrawableFromResource(array: TypedArray, styleableIndexId: Int): Drawable? {
        val id = array.getResourceId(styleableIndexId, -1)

        return if (-1 != id) ContextCompat.getDrawable(context, id) else null
    }

    /**
     * This triggers the entire functionality of the button such as icon changes,
     * animations, listeners etc.
     *
     * @param v
     */
    override fun onClick(v: View) {

        if (!isEnabled)
            return

        isChecked = !isChecked

        //icon!!.setImageDrawable(if (isChecked) likeDrawable else unLikeDrawable)
        reactionSelector?.background = if (isChecked) onDrawable else offDrawable

        if (isChecked) {
            reactedListener?.onReacted(this)
        } else {
            reactedListener?.onUnReacted(this)
        }


        if (animatorSet != null) {
            animatorSet!!.cancel()
        }

        if (isChecked) {
            emojiView!!.animate().cancel()
            emojiView!!.scaleX = 0f
            emojiView!!.scaleY = 0f

            circleView.innerCircleRadiusProgress = 0f
            circleView.outerCircleRadiusProgress = 0f
            dotsView.currentProgress = 0f

            animatorSet = AnimatorSet()

            val outerCircleAnimator = ObjectAnimator.ofFloat(circleView, CircleView.OUTER_CIRCLE_RADIUS_PROGRESS, 0.1f, 1f)
            outerCircleAnimator.duration = 250
            outerCircleAnimator.interpolator = DECCELERATE_INTERPOLATOR

            val innerCircleAnimator = ObjectAnimator.ofFloat(circleView, CircleView.INNER_CIRCLE_RADIUS_PROGRESS, 0.1f, 1f)
            innerCircleAnimator.duration = 200
            innerCircleAnimator.startDelay = 200
            innerCircleAnimator.interpolator = DECCELERATE_INTERPOLATOR

            val starScaleYAnimator = ObjectAnimator.ofFloat(emojiView, ImageView.SCALE_Y, 0.2f, 1f)
            starScaleYAnimator.duration = 350
            starScaleYAnimator.startDelay = 250
            starScaleYAnimator.interpolator = OVERSHOOT_INTERPOLATOR

            val starScaleXAnimator = ObjectAnimator.ofFloat(emojiView, ImageView.SCALE_X, 0.2f, 1f)
            starScaleXAnimator.duration = 350
            starScaleXAnimator.startDelay = 250
            starScaleXAnimator.interpolator = OVERSHOOT_INTERPOLATOR

            val dotsAnimator = ObjectAnimator.ofFloat(dotsView, DotsView.DOTS_PROGRESS, 0f, 1f)//.ofFloat<DotsView>(dotsView, DotsView.DOTS_PROGRESS, 0, 1f)
            dotsAnimator.duration = 900
            dotsAnimator.startDelay = 50
            dotsAnimator.interpolator = ACCELERATE_DECELERATE_INTERPOLATOR

            animatorSet!!.playTogether(
                    outerCircleAnimator,
                    innerCircleAnimator,
                    starScaleYAnimator,
                    starScaleXAnimator,
                    dotsAnimator
            )

            animatorSet!!.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    circleView.innerCircleRadiusProgress = 0f
                    circleView.outerCircleRadiusProgress = 0f
                    dotsView.currentProgress = 0f
                    emojiView!!.scaleX = 1f
                    emojiView!!.scaleY = 1f
                }

                override fun onAnimationEnd(animation: Animator) {
//                    if (animationEndListener != null) {
//                        //  animationEndListener!!.onAnimationEnd(this@ReactionButton)
//                    }
                }
            })

            animatorSet!!.start()
        }
    }

    /**
     * Used to trigger the scale animation that takes places on the
     * icon when the button is touched.
     *
     * @param event
     * @return
     */
//    override fun onTouchEvent(event: MotionEvent): Boolean {
//        if (!isEnabled)
//            return true
//
//        when (event.action) {
//            MotionEvent.ACTION_DOWN   ->
//                /*
//                Commented out this line and moved the animation effect to the action up event due to
//                conflicts that were occurring when library is used in sliding type views.
//
//                icon.animate().scaleX(0.7f).scaleY(0.7f).setDuration(150).setInterpolator(DECCELERATE_INTERPOLATOR);
//                */
//                isPressed = true
//
//            MotionEvent.ACTION_MOVE   -> {
//                val x = event.x
//                val y = event.y
//                val isInside = x > 0 && x < width && y > 0 && y < height
//                if (isPressed != isInside) {
//                    isPressed = isInside
//                }
//            }
//
//            MotionEvent.ACTION_UP     -> {
//                emojiView!!.animate().scaleX(0.7f).scaleY(0.7f).setDuration(150).interpolator = DECCELERATE_INTERPOLATOR
//                emojiView!!.animate().scaleX(1f).scaleY(1f).interpolator = DECCELERATE_INTERPOLATOR
//                if (isPressed) {
//                    performClick()
//                    isPressed = false
//                }
//            }
//            MotionEvent.ACTION_CANCEL -> isPressed = false
//        }
//        return true
//    }

    override fun onLongClick(v: View?): Boolean {
        reactedListener?.onLongClick(this)
        return reactedListener != null
    }

    /**
     * This set sets the colours that are used for the little dots
     * that will be exploding once the like button is clicked.
     *
     * @param primaryColor
     * @param secondaryColor
     */
    fun setExplodingDotColorsRes(@ColorRes primaryColor: Int, @ColorRes secondaryColor: Int) {
        dotsView.setColors(ContextCompat.getColor(context, primaryColor), ContextCompat.getColor(context, secondaryColor))
    }

    fun setExplodingDotColorsInt(@ColorInt primaryColor: Int, @ColorInt secondaryColor: Int) {
        dotsView.setColors(primaryColor, secondaryColor)
    }

    fun setCircleStartColorRes(@ColorRes circleStartColor: Int) {
        this.circleStartColor = ContextCompat.getColor(context, circleStartColor)
        circleView.startColor = this.circleStartColor
    }

    fun setCircleStartColorInt(@ColorInt circleStartColor: Int) {
        this.circleStartColor = circleStartColor
        circleView.startColor = circleStartColor
    }

    fun setCircleEndColorRes(@ColorRes circleEndColor: Int) {
        this.circleEndColor = ContextCompat.getColor(context, circleEndColor)
        circleView.endColor = this.circleEndColor
    }

    /**
     * Sets the initial state of the button to liked
     * or unliked.
     *
     * @param status
     */
    fun setChecked(status: Boolean?) {
        if (status!!) {
            isChecked = true
            reactionSelector?.background = onDrawable
        } else {
            isChecked = false
            reactionSelector?.background = offDrawable
        }
    }

    /**
     * Sets the factor by which the dots should be sized.
     */
    fun setAnimationScaleFactor(animationScaleFactor: Float) {
        this.animationScaleFactor = animationScaleFactor
    }


    interface ReactedListener {
        fun onReacted(reactionButton: ReactionButton)
        fun onUnReacted(reactionButton: ReactionButton)
        fun onLongClick(reactionButton: ReactionButton)
    }
}