package com.quran.tracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Shows a small draggable floating "ختمتي" button on top of every other app
 * (using the SYSTEM_ALERT_WINDOW / "draw over other apps" permission).
 *
 * Tapping the button brings {@link MainActivity} back to the foreground and
 * removes the overlay. The service runs as a foreground service so the system
 * keeps the overlay alive while the user is inside the external Quran app.
 */
public class FloatingButtonService extends Service {

    private static final String CHANNEL_ID = "floating_button_channel";
    private static final int NOTIFICATION_ID = 4101;

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams layoutParams;

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
        addFloatingButton();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If killed by the system, do not recreate automatically.
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "زر العودة العائم",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("يبقي زر العودة إلى ختمتي ظاهراً فوق تطبيق القرآن.");
            channel.setShowBadge(false);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("ختمتي")
                .setContentText("اضغط زر ختمتي العائم للعودة إلى التطبيق.")
                .setSmallIcon(R.drawable.logo)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void addFloatingButton() {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        TextView button = new TextView(this);
        button.setText("ختمتي");
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);

        int padH = dp(20);
        int padV = dp(12);
        button.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0E8C6A"));
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(2), Color.parseColor("#FFFFFF"));
        button.setBackground(bg);
        button.setElevation(dp(6));

        floatingView = button;

        int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = dp(16);
        layoutParams.y = dp(120);

        button.setOnTouchListener(new FloatingTouchListener());

        try {
            windowManager.addView(floatingView, layoutParams);
        } catch (Exception e) {
            // Permission revoked or window manager refused — nothing we can show.
            stopSelf();
        }
    }

    /** Handles dragging the button around, and treats a small movement as a tap. */
    private class FloatingTouchListener implements View.OnTouchListener {
        private int initialX, initialY;
        private float touchX, touchY;
        private boolean moved;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = layoutParams.x;
                    initialY = layoutParams.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    moved = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - touchX);
                    int dy = (int) (event.getRawY() - touchY);
                    if (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8)) {
                        moved = true;
                    }
                    layoutParams.x = initialX + dx;
                    layoutParams.y = initialY + dy;
                    try {
                        windowManager.updateViewLayout(floatingView, layoutParams);
                    } catch (Exception ignored) {
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        returnToApp();
                    }
                    return true;
            }
            return false;
        }
    }

    private void returnToApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        stopSelf();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception ignored) {
            }
            floatingView = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
