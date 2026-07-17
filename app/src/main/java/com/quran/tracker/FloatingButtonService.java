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
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private TextView pageText;
    private WindowManager.LayoutParams layoutParams;
    private int targetPage = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();
        addFloatingButton();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            targetPage = intent.getIntExtra("page", 0);
        }
        updateButtonLabel();
        // If killed by the system, do not recreate automatically.
        return START_NOT_STICKY;
    }

    private void updateButtonLabel() {
        if (pageText == null) return;
        pageText.setText(targetPage > 0 ? ("ص " + targetPage) : "ختمتي");
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

        // Horizontal pill: [logo circle] ص 400
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(6), dp(6), dp(14), dp(6));

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Color.parseColor("#14181B"));
        pillBg.setCornerRadius(dp(30));
        pillBg.setStroke(dp(1), Color.parseColor("#66ABE83F"));
        pill.setBackground(pillBg);
        pill.setElevation(dp(10));

        // Logo inside a white circle
        int circle = dp(34);
        ImageView logo = new ImageView(this);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(Color.WHITE);
        logo.setBackground(circleBg);
        logo.setImageResource(R.drawable.logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int lp = dp(4);
        logo.setPadding(lp, lp, lp, lp);
        logo.setClipToOutline(true);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(circle, circle);
        pill.addView(logo, logoParams);

        // Page text "ص 400"
        pageText = new TextView(this);
        pageText.setTextColor(Color.parseColor("#ABE83F"));
        pageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        pageText.setTypeface(pageText.getTypeface(), android.graphics.Typeface.BOLD);
        pageText.setSingleLine(true);
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = dp(8);
        textParams.rightMargin = dp(8);
        pill.addView(pageText, textParams);

        updateButtonLabel();

        floatingView = pill;

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

        pill.setOnTouchListener(new FloatingTouchListener());

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
