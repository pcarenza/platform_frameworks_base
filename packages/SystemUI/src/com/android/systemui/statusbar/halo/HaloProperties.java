/*
 * Copyright (C) 2013 ParanoidAndroid.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.halo;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.ColorFilterMaker;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.util.TypedValue;
import android.provider.Settings;

import com.android.systemui.R;

public class HaloProperties extends FrameLayout {

    public enum Overlay {
        NONE,
        BLACK_X,
        BACK_LEFT,
        BACK_RIGHT,
        DISMISS,
        SILENCE_LEFT,
        SILENCE_RIGHT,
        CLEAR_ALL,
        MESSAGE
    }

    private LayoutInflater mInflater;

    protected int mHaloX = 0, mHaloY = 0;
    protected float mHaloContentAlpha = 0;

    private Drawable mHaloDismiss;
    private Drawable mHaloBackL;
    private Drawable mHaloBackR;
    private Drawable mHaloBlackX;
    private Drawable mHaloClearAll;
    private Drawable mHaloSilenceL;
    private Drawable mHaloSilenceR;
    private Drawable mHaloMessage;
    private Drawable mHaloCurrentOverlay;

    protected View mHaloBubble;
    protected ImageView mHaloBg, mHaloIcon, mHaloOverlay;

    protected View mHaloContentView, mHaloTickerContent;
    protected TextView mHaloTextViewR, mHaloTextViewL;
    protected RelativeLayout mHaloTickerContainer;

    protected View mHaloNumberView;
    protected TextView mHaloNumber;

    private static int mStyle;
    private static final int BLUE = 0;
    private static final int GREEN = 1;
    private static final int WHITE = 2;
    private static final int PURPLE = 3;
    private static final int RED = 4;
    private static final int YELLOW = 5;
    private static final int PINK = 6;
    private static final int BLACK = 7;

    private boolean mAttached = false;

    private SettingsObserver mSettingsObserver;
    private Handler mHandler;
    private float mFraction = 1.0f;
    private int mHaloMessageNumber = 0;

    CustomObjectAnimator mHaloOverlayAnimator;

    public HaloProperties(Context context) {
        super(context);

        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mHaloDismiss = mContext.getResources().getDrawable(R.drawable.halo_dismiss);
        mHaloBackL = mContext.getResources().getDrawable(R.drawable.halo_back_left);
        mHaloBackR = mContext.getResources().getDrawable(R.drawable.halo_back_right);
        mHaloBlackX = mContext.getResources().getDrawable(R.drawable.halo_black_x);
        mHaloClearAll = mContext.getResources().getDrawable(R.drawable.halo_clear_all);
        mHaloSilenceL = mContext.getResources().getDrawable(R.drawable.halo_silence_left);
        mHaloSilenceR = mContext.getResources().getDrawable(R.drawable.halo_silence_right);
        mHaloMessage = mContext.getResources().getDrawable(R.drawable.halo_message);

        mHaloBubble = mInflater.inflate(R.layout.halo_bubble, null);
        mHaloBg = (ImageView) mHaloBubble.findViewById(R.id.halo_bg);
        mHaloIcon = (ImageView) mHaloBubble.findViewById(R.id.app_icon);
        mHaloOverlay = (ImageView) mHaloBubble.findViewById(R.id.halo_overlay);

        mHaloContentView = mInflater.inflate(R.layout.halo_speech, null);
        mHaloTickerContainer = (RelativeLayout)mHaloContentView.findViewById(R.id.container);
        mHaloTickerContent = mHaloContentView.findViewById(R.id.ticker);
        mHaloTextViewR = (TextView) mHaloTickerContent.findViewById(R.id.bubble_r);
        mHaloTextViewR.setAlpha(0f);
        mHaloTextViewL = (TextView) mHaloTickerContent.findViewById(R.id.bubble_l);
        mHaloTextViewL.setAlpha(0f);

        updateColorView();

        mHaloNumberView = mInflater.inflate(R.layout.halo_number, null);
        mHaloNumber = (TextView) mHaloNumberView.findViewById(R.id.number);
        mHaloNumber.setAlpha(0f);

        mFraction = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.HALO_SIZE, 1.0f);
        setHaloSize(mFraction);

        mHaloOverlayAnimator = new CustomObjectAnimator(this);
        mHandler = new Handler();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            mSettingsObserver = new SettingsObserver(new Handler());
            mSettingsObserver.observe();
            updateColorView();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAttached) {
            mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
            mAttached = false;
        }
    } 

    public void setHaloSize(float fraction) {

        final int newBubbleSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_bubble_size) * fraction);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(newBubbleSize, newBubbleSize);
        mHaloBg.setLayoutParams(layoutParams);
        mHaloIcon.setLayoutParams(layoutParams);
        mHaloOverlay.setLayoutParams(layoutParams);

        final int newNumberSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_number_size) * fraction);
        final int newNumberTextSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_number_text_size) * fraction);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(newNumberSize, newNumberSize);
        mHaloNumber.setLayoutParams(layoutParams2);
        mHaloNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, newNumberTextSize);

        final int newSpeechTextSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_speech_text_size) * fraction);
        mHaloTextViewR.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSpeechTextSize);
        mHaloTextViewL.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSpeechTextSize);

        updateResources();
    }

    public void setHaloX(int value) {
        mHaloX = value;
    }

    public void setHaloY(int value) {
        mHaloY = value;
    }

    public int getHaloX() {
        return mHaloX; 
    }

    public int getHaloY() {
        return mHaloY;
    }

    protected CustomObjectAnimator msgNumberFlipAnimator = new CustomObjectAnimator(this);
    protected CustomObjectAnimator msgNumberAlphaAnimator = new CustomObjectAnimator(this);
    public void setHaloMessageNumber(int value, boolean alwaysFlip) {

        // Allow transitions only if no overlay is set
        if (mHaloCurrentOverlay == null) {
            msgNumberAlphaAnimator.cancel(true);
            float oldAlpha = mHaloNumber.getAlpha();
            mHaloNumber.setAlpha(1f);                
            mHaloNumber.setText((value < 100) ? String.valueOf(value == 0 ? 1 : value) : "+");
            
            if (value == 0) {
                msgNumberAlphaAnimator.animate(ObjectAnimator.ofFloat(mHaloNumber, "alpha", 0f).setDuration(1000),
                        new DecelerateInterpolator(), null, 1500, null);
            }

            if (!alwaysFlip && oldAlpha == 1f && (value == mHaloMessageNumber || (value > 99 && mHaloMessageNumber > 99))) return;
            msgNumberFlipAnimator.animate(ObjectAnimator.ofFloat(mHaloNumber, "rotationY", -180, 0).setDuration(500),
                        new DecelerateInterpolator(), null);
        }
        mHaloMessageNumber = value;
    }

    public void setHaloContentAlpha(float value) {
        mHaloContentAlpha = value;
        mHaloTextViewL.setAlpha(value);
        mHaloTextViewR.setAlpha(value);
    }

    public float getHaloContentAlpha() {
        return mHaloContentAlpha;
    }

    public void setHaloOverlay(Overlay overlay) {
        setHaloOverlay(overlay, mHaloOverlay.getAlpha());
    }

    public void setHaloOverlay(Overlay overlay, float overlayAlpha) {

        Drawable d = null;

        switch(overlay) {
            case BLACK_X:
                d = mHaloBlackX;
                break;
            case BACK_LEFT:
                d = mHaloBackL;
                break;
            case BACK_RIGHT:
                d = mHaloBackR;
                break;
            case DISMISS:
                d = mHaloDismiss;
                break;
            case SILENCE_LEFT:
                d = mHaloSilenceL;
                break;
            case SILENCE_RIGHT:
                d = mHaloSilenceR;
                break;
            case CLEAR_ALL:
                d = mHaloClearAll;
                break;
            case MESSAGE:
                d = mHaloMessage;
                break;
        }

        if (d != mHaloCurrentOverlay) {
            mHaloOverlay.setImageDrawable(d);
            mHaloCurrentOverlay = d;

            // Fade out number batch
            if (overlay != Overlay.NONE) {
                msgNumberAlphaAnimator.animate(ObjectAnimator.ofFloat(mHaloNumber, "alpha", 0f).setDuration(250),
                        new DecelerateInterpolator(), null);
            }
        }

        mHaloOverlay.setAlpha(overlayAlpha);
        updateResources();
    }

    public void updateResources() {

        final int iconSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_bubble_size) * mFraction);
        final int newSize = (int)(getWidth() * 0.9f) - iconSize;
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(newSize, LinearLayout.LayoutParams.WRAP_CONTENT);
        mHaloTickerContainer.setLayoutParams(layoutParams);

        mHaloContentView.measure(MeasureSpec.getSize(mHaloContentView.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloContentView.getMeasuredHeight()));
        mHaloContentView.layout(0, 0, 0, 0);

        mHaloBubble.measure(MeasureSpec.getSize(mHaloBubble.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloBubble.getMeasuredHeight()));
        mHaloBubble.layout(0, 0, 0, 0);

        mHaloNumberView.measure(MeasureSpec.getSize(mHaloNumberView.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloNumberView.getMeasuredHeight()));
        mHaloNumberView.layout(0, 0, 0, 0);
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_COLORS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_CIRCLE_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_BUBBLE_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.HALO_BUBBLE_TEXT_COLOR), false, this);
            updateColorView();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateColorView();
        }
    }

    private void updateColorView() {
        ContentResolver cr = mContext.getContentResolver();
        boolean mEnableColor = Settings.System.getInt(cr,
               Settings.System.HALO_COLORS, 0) == 1;
        int mCircleColor = Settings.System.getInt(cr,
               Settings.System.HALO_CIRCLE_COLOR, 0xFF33B5E5);
        int mBubbleColor = Settings.System.getInt(cr,
               Settings.System.HALO_BUBBLE_COLOR, 0xFF33B5E5);
        int mTextColor = Settings.System.getInt(cr, 
               Settings.System.HALO_BUBBLE_TEXT_COLOR, 0xFFFFFFFF);

        if (mEnableColor) {
           // Ring
           mHaloBg.setBackgroundResource(R.drawable.halo_bg);
           mHaloBg.getBackground().setColorFilter(ColorFilterMaker.
                   changeColorAlpha(mCircleColor, .32f, 0f));

           // Speech bubbles
           mHaloTextViewL.setBackgroundResource(R.drawable.bubble_l);
           mHaloTextViewL.getBackground().setColorFilter(ColorFilterMaker.
                    changeColorAlpha(mBubbleColor, .32f, 0f));
           mHaloTextViewL.setTextColor(mTextColor);
           mHaloTextViewR.setBackgroundResource(R.drawable.bubble_r);
           mHaloTextViewR.getBackground().setColorFilter(ColorFilterMaker.
                    changeColorAlpha(mBubbleColor, .32f, 0f));
           mHaloTextViewR.setTextColor(mTextColor);
        }
    }
}
