package com.example.myapplication;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

public class GesturePlaygroundActivity extends AppCompatActivity {

    private View gestureBox;
    private TextView tvGestureStatus;
    private TextView tvGestureDetail;

    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

    private float scaleFactor = 1.0f;
    private float rotationAngle = 0f;
    private float boxTranslationX = 0f;
    private float boxTranslationY = 0f;

    // For rotation tracking
    private float previousAngle = 0f;
    private boolean isRotating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gesture_playground);

        // Setup toolbar with back navigation
        MaterialToolbar toolbar = findViewById(R.id.toolbar_gestures);
        toolbar.setNavigationOnClickListener(v -> finish());

        gestureBox = findViewById(R.id.gesture_box);
        tvGestureStatus = findViewById(R.id.tv_gesture_status);
        tvGestureDetail = findViewById(R.id.tv_gesture_detail);

        View touchArea = findViewById(R.id.touch_area);

        // Initialize gesture detectors
        gestureDetector = new GestureDetector(this, new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

        touchArea.setOnTouchListener((v, event) -> {
            // Handle rotation with two fingers
            handleRotation(event);

            // Pass to scale detector first
            scaleGestureDetector.onTouchEvent(event);

            // Pass to gesture detector
            gestureDetector.onTouchEvent(event);

            return true;
        });
    }

    // ==================== GESTURE LISTENER ====================
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        // --- SINGLE TAP ---
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            updateStatus("👆 Single Tap", String.format("Position: (%.0f, %.0f)", e.getX(), e.getY()));
            animateTapFlash();
            return true;
        }

        // --- DOUBLE TAP ---
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            updateStatus("👆👆 Double Tap", "Resetting box to original state");
            resetBox();
            return true;
        }

        // --- LONG PRESS ---
        @Override
        public void onLongPress(MotionEvent e) {
            updateStatus("✋ Long Press", "Hold detected — vibrating!");
            performHapticFeedback();
            animateLongPress();
        }

        // --- FLING / SWIPE ---
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null)
                return false;

            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();

            String direction;
            float moveX = 0, moveY = 0;

            if (Math.abs(diffX) > Math.abs(diffY)) {
                // Horizontal swipe
                if (diffX > 0) {
                    direction = "➡️ Swipe Right";
                    moveX = 150;
                } else {
                    direction = "⬅️ Swipe Left";
                    moveX = -150;
                }
            } else {
                // Vertical swipe
                if (diffY > 0) {
                    direction = "⬇️ Swipe Down";
                    moveY = 150;
                } else {
                    direction = "⬆️ Swipe Up";
                    moveY = -150;
                }
            }

            float speed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
            updateStatus(direction, String.format("Speed: %.0f px/s", speed));
            animateSwipe(moveX, moveY);
            return true;
        }
    }

    // ==================== SCALE (PINCH) LISTENER ====================
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.3f, Math.min(scaleFactor, 3.0f)); // Clamp

            gestureBox.setScaleX(scaleFactor);
            gestureBox.setScaleY(scaleFactor);

            updateStatus("🤏 Pinch Zoom", String.format("Scale: %.2fx", scaleFactor));
            return true;
        }
    }

    // ==================== ROTATION HANDLING ====================
    private void handleRotation(MotionEvent event) {
        if (event.getPointerCount() == 2) {
            float angle = calculateAngle(event);

            if (isRotating) {
                float delta = angle - previousAngle;
                rotationAngle += delta;
                gestureBox.setRotation(rotationAngle);
                updateStatus("🔄 Rotation", String.format("Angle: %.1f°", rotationAngle));
            }

            previousAngle = angle;
            isRotating = true;
        } else {
            isRotating = false;
        }
    }

    private float calculateAngle(MotionEvent event) {
        double dx = event.getX(1) - event.getX(0);
        double dy = event.getY(1) - event.getY(0);
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    // ==================== ANIMATIONS ====================
    private void animateTapFlash() {
        GradientDrawable bg = (GradientDrawable) gestureBox.getBackground();
        int originalColor = Color.parseColor("#6C63FF");
        int flashColor = Color.parseColor("#FF6584");

        bg.setColor(flashColor);
        gestureBox.postDelayed(() -> bg.setColor(originalColor), 200);

        // Pulse animation
        ObjectAnimator scaleXUp = ObjectAnimator.ofFloat(gestureBox, "scaleX", scaleFactor, scaleFactor * 1.15f);
        ObjectAnimator scaleYUp = ObjectAnimator.ofFloat(gestureBox, "scaleY", scaleFactor, scaleFactor * 1.15f);
        ObjectAnimator scaleXDown = ObjectAnimator.ofFloat(gestureBox, "scaleX", scaleFactor * 1.15f, scaleFactor);
        ObjectAnimator scaleYDown = ObjectAnimator.ofFloat(gestureBox, "scaleY", scaleFactor * 1.15f, scaleFactor);

        scaleXUp.setDuration(100);
        scaleYUp.setDuration(100);
        scaleXDown.setDuration(100);
        scaleYDown.setDuration(100);

        AnimatorSet set = new AnimatorSet();
        set.play(scaleXUp).with(scaleYUp);
        set.play(scaleXDown).with(scaleYDown).after(scaleXUp);
        set.start();
    }

    private void animateLongPress() {
        ObjectAnimator shake1 = ObjectAnimator.ofFloat(gestureBox, "translationX", boxTranslationX,
                boxTranslationX - 10);
        ObjectAnimator shake2 = ObjectAnimator.ofFloat(gestureBox, "translationX", boxTranslationX - 10,
                boxTranslationX + 10);
        ObjectAnimator shake3 = ObjectAnimator.ofFloat(gestureBox, "translationX", boxTranslationX + 10,
                boxTranslationX);

        shake1.setDuration(50);
        shake2.setDuration(100);
        shake3.setDuration(50);

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(shake1, shake2, shake3);
        set.start();
    }

    private void animateSwipe(float moveX, float moveY) {
        boxTranslationX += moveX;
        boxTranslationY += moveY;

        ObjectAnimator animX = ObjectAnimator.ofFloat(gestureBox, "translationX", boxTranslationX);
        ObjectAnimator animY = ObjectAnimator.ofFloat(gestureBox, "translationY", boxTranslationY);

        animX.setDuration(400);
        animY.setDuration(400);
        animX.setInterpolator(new OvershootInterpolator());
        animY.setInterpolator(new OvershootInterpolator());

        AnimatorSet set = new AnimatorSet();
        set.playTogether(animX, animY);
        set.start();
    }

    private void resetBox() {
        scaleFactor = 1.0f;
        rotationAngle = 0f;
        boxTranslationX = 0f;
        boxTranslationY = 0f;

        ObjectAnimator animScaleX = ObjectAnimator.ofFloat(gestureBox, "scaleX", 1f);
        ObjectAnimator animScaleY = ObjectAnimator.ofFloat(gestureBox, "scaleY", 1f);
        ObjectAnimator animRotation = ObjectAnimator.ofFloat(gestureBox, "rotation", 0f);
        ObjectAnimator animTransX = ObjectAnimator.ofFloat(gestureBox, "translationX", 0f);
        ObjectAnimator animTransY = ObjectAnimator.ofFloat(gestureBox, "translationY", 0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(animScaleX, animScaleY, animRotation, animTransX, animTransY);
        set.setDuration(500);
        set.setInterpolator(new OvershootInterpolator());
        set.start();

        GradientDrawable bg = (GradientDrawable) gestureBox.getBackground();
        bg.setColor(Color.parseColor("#6C63FF"));
    }

    // ==================== HELPERS ====================
    private void updateStatus(String gesture, String detail) {
        tvGestureStatus.setText(gesture);
        tvGestureDetail.setText(detail);
    }

    private void performHapticFeedback() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(100);
        }
    }
}
