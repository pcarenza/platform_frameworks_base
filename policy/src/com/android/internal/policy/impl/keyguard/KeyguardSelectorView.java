/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.internal.policy.impl.keyguard;

import android.animation.ObjectAnimator;
import android.app.ActivityManagerNative;
import android.app.SearchManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.util.aokp.AokpRibbonHelper;
import com.android.internal.util.aokp.GlowPadTorchHelper;
import com.android.internal.util.aokp.LockScreenHelpers;
import com.android.internal.view.RotationPolicy;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;
import com.android.internal.widget.multiwaveview.TargetDrawable;
import com.android.internal.R;

import java.util.ArrayList;

public class KeyguardSelectorView extends LinearLayout implements KeyguardSecurityView {
    private static final boolean DEBUG = KeyguardHostView.DEBUG;
    private static final String TAG = "SecuritySelectorView";

    private KeyguardSecurityCallback mCallback;
    private GlowPadView mGlowPadView;
    private LinearLayout mRibbon;
    private LinearLayout ribbonView;
    private ObjectAnimator mAnim;
    private View mFadeView;
    private boolean mIsBouncing;
    private LockPatternUtils mLockPatternUtils;
    private SecurityMessageDisplay mSecurityMessageDisplay;
    private Drawable mBouncerFrame;
    private Resources res;

    private int mGlowTorch;
    private boolean mUserRotation;
    private boolean mGlowTorchOn;
    private boolean mGlowPadLock;
    private boolean mBoolLongPress;
    private int mTarget;
    private boolean mLongPress = false;
    private boolean mUnlockBroadcasted = false;
    private boolean mUsesCustomTargets;
    private int mUnlockPos;
    private String[] targetActivities = new String[8];
    private String[] longActivities = new String[8];
    private String[] customIcons = new String[8];
    private UnlockReceiver receiver;
    private IntentFilter filter;
    private boolean mReceiverRegistered = false;

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
            }
        }
    }
    private H mHandler = new H();

    private void launchAction(String action) {
        AwesomeConstant AwesomeEnum = fromString(action);
        switch (AwesomeEnum) {
        case ACTION_UNLOCK:
            mCallback.userActivity(0);
            mCallback.dismiss(false);
            break;
        case ACTION_ASSIST:
            mCallback.userActivity(0);
            mCallback.dismiss(false);
            Intent assistIntent =
                ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, UserHandle.USER_CURRENT);
                if (assistIntent != null) {
                    mActivityLauncher.launchActivity(assistIntent, false, true, null, null);
                } else {
                    Log.w(TAG, "Failed to get intent for assist activity");
                }
                break;
        case ACTION_CAMERA:
            mCallback.userActivity(0);
            mCallback.dismiss(false);
            mActivityLauncher.launchCamera(null, null);
            break;
        case ACTION_APP:
            mCallback.userActivity(0);
            mCallback.dismiss(false);
            Intent i = new Intent();
            i.setAction("com.android.systemui.aokp.LAUNCH_ACTION");
            i.putExtra("action", action);
            mContext.sendBroadcastAsUser(i, UserHandle.ALL);
            break;
        }
    }

        public void onTrigger(View v, int target) {
            final int resId = mGlowPadView.getResourceIdForTarget(target);
            switch (resId) {
                case com.android.internal.R.drawable.ic_action_assist_generic:
                    Intent assistIntent =
                            ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                            .getAssistIntent(mContext, true, UserHandle.USER_CURRENT);
                    if (assistIntent != null) {
                        mActivityLauncher.launchActivity(assistIntent, false, true, null, null);
                    } else {
                        Log.w(TAG, "Failed to get intent for assist activity");
                    }
                    mCallback.userActivity(0);
                    break;

       final Runnable SetLongPress = new Runnable () {
            public void run() {
                if (!mLongPress) {
                    GlowPadTorchHelper.vibrate(mContext);
                    mLongPress = true;
                }
            }
        };

        public void onTrigger(View v, int target) {
            if (mReceiverRegistered) {
                mContext.unregisterReceiver(receiver);
                mReceiverRegistered = false;
            }
            if ((!mUsesCustomTargets) || (mTargetCounter() == 0 && mUnlockCounter() < 2)) {
                mCallback.userActivity(0);
                mCallback.dismiss(false);
            } else {
                if (!mLongPress) {
                    mHandler.removeCallbacks(SetLongPress);
                    launchAction(targetActivities[target]);
                }
            }
        }

        public void onReleased(View v, int handle) {
            fireTorch();
            if (!mIsBouncing) {
                doTransition(mFadeView, 1.0f);
            }
            if (!mGlowPadLock && mLongPress) {
                mGlowPadLock = true;
                if (mReceiverRegistered) {
                    mContext.unregisterReceiver(receiver);
                    mReceiverRegistered = false;
                }
                launchAction(longActivities[mTarget]);
            }
        }

        public void onGrabbed(View v, int handle) {
            mCallback.userActivity(0);
            doTransition(mFadeView, 0.0f);
            if (mGlowTorch == 1) {
                mHandler.removeCallbacks(checkTorch);
                mHandler.postDelayed(startTorch, GlowPadTorchHelper.TORCH_TIMEOUT);
            }
        }

        public void onGrabbedStateChange(View v, int handle) {
            mHandler.removeCallbacks(SetLongPress);
            mLongPress = false;
        }

        public void onTargetChange(View v, int target) {
            if (target == -1) {
                mHandler.removeCallbacks(SetLongPress);
                mLongPress = false;
                if (mGlowTorchOn) {
                    mCallback.userActivity(0);
                }
            } else {
                fireTorch();
                if (mBoolLongPress && !TextUtils.isEmpty(longActivities[target]) && !longActivities[target].equals(AwesomeConstant.ACTION_NULL.value())) {
                    mTarget = target;
                    mHandler.postDelayed(SetLongPress, ViewConfiguration.getLongPressTimeout());
                }
            }
        }

        public void onFinishFinalAnimation() {

        }

    };

    KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onDevicePolicyManagerStateChanged() {
            updateTargets();
        }

        @Override
        public void onSimStateChanged(State simState) {
            updateTargets();
        }
    };

    private final KeyguardActivityLauncher mActivityLauncher = new KeyguardActivityLauncher() {

        @Override
        KeyguardSecurityCallback getCallback() {
            return mCallback;
        }

        @Override
        LockPatternUtils getLockPatternUtils() {
            return mLockPatternUtils;
        }

        @Override
        Context getContext() {
            return mContext;
        }};

    public KeyguardSelectorView(Context context) {
        this(context, null);
    }

    public KeyguardSelectorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLockPatternUtils = new LockPatternUtils(getContext());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        res = getResources();
        ContentResolver cr = mContext.getContentResolver();
        mGlowPadView = (GlowPadView) findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(mOnTriggerListener);
        ribbonView = (LinearLayout) findViewById(R.id.keyguard_ribbon_and_battery);
        ribbonView.bringToFront();
        mRibbon = (LinearLayout) ribbonView.findViewById(R.id.ribbon);
        mRibbon.removeAllViews();
        mRibbon.addView(AokpRibbonHelper.getRibbon(mContext,
            Settings.System.getArrayList(cr,
                Settings.System.RIBBON_TARGETS_SHORT[AokpRibbonHelper.LOCKSCREEN]),
            Settings.System.getArrayList(cr,
                Settings.System.RIBBON_TARGETS_LONG[AokpRibbonHelper.LOCKSCREEN]),
            Settings.System.getArrayList(cr,
                Settings.System.RIBBON_TARGETS_ICONS[AokpRibbonHelper.LOCKSCREEN]),
            Settings.System.getBoolean(cr,
                Settings.System.ENABLE_RIBBON_TEXT[AokpRibbonHelper.LOCKSCREEN], true),
            Settings.System.getInt(cr,
                Settings.System.RIBBON_TEXT_COLOR[AokpRibbonHelper.LOCKSCREEN], -1),
            Settings.System.getInt(cr,
                Settings.System.RIBBON_ICON_SIZE[AokpRibbonHelper.LOCKSCREEN], 0),
            Settings.System.getInt(cr,
                Settings.System.RIBBON_ICON_SPACE[AokpRibbonHelper.LOCKSCREEN], 5),
            Settings.System.getBoolean(cr,
                Settings.System.RIBBON_ICON_VIBRATE[AokpRibbonHelper.LOCKSCREEN], true),
            Settings.System.getBoolean(cr,
                Settings.System.RIBBON_ICON_COLORIZE[AokpRibbonHelper.LOCKSCREEN], true), 0));
        updateTargets();

        mGlowTorch = Settings.System.getInt(cr,
                Settings.System.LOCKSCREEN_GLOW_TORCH, 0);
        mGlowTorchOn = false;

        mSecurityMessageDisplay = new KeyguardMessageArea.Helper(this);
        View bouncerFrameView = findViewById(R.id.keyguard_selector_view_frame);
        mBouncerFrame = bouncerFrameView.getBackground();
        mUnlockBroadcasted = false;
        filter = new IntentFilter();
        filter.addAction(UnlockReceiver.ACTION_UNLOCK_RECEIVER);
        receiver = new UnlockReceiver();
        mContext.registerReceiver(receiver, filter);
        mReceiverRegistered = true;
    }

    public void setCarrierArea(View carrierArea) {
        mFadeView = carrierArea;
    }

    public boolean isTargetPresent(int resId) {
        return mGlowPadView.getTargetPosition(resId) != -1;
    }

    @Override
    public void showUsabilityHint() {
        mGlowPadView.ping();
    }

    public boolean isScreenPortrait() {
        return res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private void fireTorch() {
        mHandler.removeCallbacks(startTorch);
        if (mGlowTorch == 1 && mGlowTorchOn) {
            mGlowTorchOn = false;
            GlowPadTorchHelper.killTorch(mContext);
            RotationPolicy.setRotationLock(mContext, mUserRotation);
            mHandler.postDelayed(checkTorch, GlowPadTorchHelper.TORCH_CHECK);
        }
    }

    final Runnable startTorch = new Runnable () {
        public void run() {
            if (!mGlowTorchOn) {
                mUserRotation = RotationPolicy.isRotationLocked(mContext);
                RotationPolicy.setRotationLock(mContext, true);
                mGlowTorchOn = GlowPadTorchHelper.startTorch(mContext);
            }
        }
    };

    final Runnable checkTorch = new Runnable () {
        public void run() {
            if (GlowPadTorchHelper.torchActive(mContext)) {
                GlowPadTorchHelper.torchOff(mContext, true);
            }
        }
    };

    private void updateTargets() {
        mLongPress = false;
        mGlowPadLock = false;
        mUsesCustomTargets = mUnlockCounter() != 0;
        ArrayList<TargetDrawable> storedDraw = new ArrayList<TargetDrawable>();

        for (int i = 0; i < 8; i++) {
            targetActivities[i] = Settings.System.getString(
                    mContext.getContentResolver(), Settings.System.LOCKSCREEN_TARGETS_SHORT[i]);
            longActivities[i] = Settings.System.getString(
                    mContext.getContentResolver(), Settings.System.LOCKSCREEN_TARGETS_LONG[i]);
            customIcons[i] = Settings.System.getString(
                    mContext.getContentResolver(), Settings.System.LOCKSCREEN_TARGETS_ICON[i]);
        }
        boolean currentUserSetup = 0 != Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE,
                0 /*default */,
                currentUserHandle);
        boolean searchActionAvailable =
                ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                .getAssistIntent(mContext, false, UserHandle.USER_CURRENT) != null;
        mCameraDisabled = cameraDisabledByAdmin || disabledBySimState || !cameraTargetPresent
                || !currentUserSetup;
        mSearchDisabled = disabledBySimState || !searchActionAvailable || !searchTargetPresent
                || !currentUserSetup;
        updateResources();
    }

    private int mUnlockCounter() {
        int counter = 0;
        for (int i = 0; i < 8 ; i++) {
            if (!TextUtils.isEmpty(targetActivities[i])) {
                if (targetActivities[i].equals(AwesomeConstant.ACTION_UNLOCK.value())) {
                    mUnlockPos = i;
                    counter += 1;
                }
            }
        }
        return counter;
    }

    private int mTargetCounter() {
        int counter = 0;
        for (int i = 0; i < 8 ; i++) {
            if (!TextUtils.isEmpty(targetActivities[i])) {
                if (targetActivities[i].equals(AwesomeConstant.ACTION_UNLOCK.value()) ||
                    targetActivities[i].equals(AwesomeConstant.ACTION_NULL.value())) {
                // I just couldn't take the negative logic....
                } else {
                    counter += 1;
                }
            }
        }
        return counter;
    }

    public void updateResources() {
        // Update the search icon with drawable from the search .apk
        if (!mSearchDisabled) {
            Intent intent = ((SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE))
                    .getAssistIntent(mContext, false, UserHandle.USER_CURRENT);
            if (intent != null) {
                // XXX Hack. We need to substitute the icon here but haven't formalized
                // the public API. The "_google" metadata will be going away, so
                // DON'T USE IT!
                ComponentName component = intent.getComponent();
                boolean replaced = mGlowPadView.replaceTargetDrawablesIfPresent(component,
                        ASSIST_ICON_METADATA_NAME + "_google",
                        com.android.internal.R.drawable.ic_action_assist_generic);

                if (!replaced && !mGlowPadView.replaceTargetDrawablesIfPresent(component,
                            ASSIST_ICON_METADATA_NAME,
                            com.android.internal.R.drawable.ic_action_assist_generic)) {
                Slog.w(TAG, "Couldn't grab icon from package " + component);
            }
        }
    }

    void doTransition(View view, float to) {
        if (mAnim != null) {
            mAnim.cancel();
        }
        mAnim = ObjectAnimator.ofFloat(view, "alpha", to);
        mAnim.start();
    }

    public void setKeyguardCallback(KeyguardSecurityCallback callback) {
        mCallback = callback;
    }

    public void setLockPatternUtils(LockPatternUtils utils) {
        mLockPatternUtils = utils;
    }

    @Override
    public void reset() {
        mGlowPadView.reset(false);
    }

    @Override
    public boolean needsInput() {
        return false;
    }

    @Override
    public void onPause() {
        KeyguardUpdateMonitor.getInstance(getContext()).removeCallback(mInfoCallback);
    }

    @Override
    public void onResume(int reason) {
        KeyguardUpdateMonitor.getInstance(getContext()).registerCallback(mInfoCallback);
    }

    @Override
    public KeyguardSecurityCallback getCallback() {
        return mCallback;
    }

    @Override
    public void showBouncer(int duration) {
        mIsBouncing = true;
        KeyguardSecurityViewHelper.
                showBouncer(mSecurityMessageDisplay, mFadeView, mBouncerFrame, duration);
    }

    @Override
    public void hideBouncer(int duration) {
        mIsBouncing = false;
        KeyguardSecurityViewHelper.
                hideBouncer(mSecurityMessageDisplay, mFadeView, mBouncerFrame, duration);
    }

    public class UnlockReceiver extends BroadcastReceiver {
        public static final String ACTION_UNLOCK_RECEIVER = "com.android.lockscreen.ACTION_UNLOCK_RECEIVER";
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_UNLOCK_RECEIVER)) {
                if (!mUnlockBroadcasted) {
                    mUnlockBroadcasted = true;
                    mCallback.userActivity(0);
                    mCallback.dismiss(false);
                }
            }
            if (mReceiverRegistered) {
                mContext.unregisterReceiver(receiver);
                mReceiverRegistered = false;
            }
        }
    }
}
