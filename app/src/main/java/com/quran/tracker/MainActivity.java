package com.quran.tracker;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 2002;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 2003;

    // Known Quran apps, in order of preference. The first installed one is used.
    private static final String[] QURAN_PACKAGES = {
            "com.quran.labs.androidquran",
            "com.pakdata.QuranMajeed",
            "com.guidedways.iQuran",
            "com.andi.alquran",
            "com.andi.alquran.id",
            "com.quranreading.holyquran",
            "com.tos.quran",
            "com.equ.quran",
            "com.greentech.quran",
            "com.muslim.quran.alquran.qurankareem"
    };

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            webSettings.setAllowUniversalAccessFromFileURLs(true);
            webSettings.setAllowFileAccessFromFileURLs(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw();
        }

        // Never serve stale HTML/JS from cache after an app update.
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        webView.clearCache(true);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Once the user is back inside ختمتي, the floating return button is no
        // longer needed (covers returning via the button, Back, or Recents).
        stopService(new Intent(this, FloatingButtonService.class));
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void saveNotificationSettings(boolean enabled, int hour, int minute) {
            SharedPreferences prefs = mContext.getSharedPreferences("QuranTrackerPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("notification_enabled", enabled);
            editor.putInt("notification_hour", hour);
            editor.putInt("notification_minute", minute);
            editor.apply();

            if (enabled) {
                NotificationReceiver.scheduleAlarm(mContext, hour, minute);
            } else {
                NotificationReceiver.cancelAlarm(mContext);
            }
        }

        @JavascriptInterface
        public String getNotificationSettings() {
            SharedPreferences prefs = mContext.getSharedPreferences("QuranTrackerPrefs", Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("notification_enabled", false);
            int hour = prefs.getInt("notification_hour", 20);
            int minute = prefs.getInt("notification_minute", 0);

            try {
                JSONObject obj = new JSONObject();
                obj.put("enabled", enabled);
                obj.put("hour", hour);
                obj.put("minute", minute);
                return obj.toString();
            } catch (Exception e) {
                return "{\"enabled\":false,\"hour\":20,\"minute\":0}";
            }
        }

        @JavascriptInterface
        public boolean hasNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            }
            return true;
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                            MainActivity.this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            PERMISSION_REQUEST_CODE
                    );
                }
            }
        }

        @JavascriptInterface
        public void showToast(String message) {
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        }

        /** Store the latest progress and refresh the home-screen widget. */
        @JavascriptInterface
        public void updateWidget(int percent, int currentPage, int todayRead,
                                 int dailyGoal, int streak, String khatmahName) {
            SharedPreferences.Editor editor = mContext
                    .getSharedPreferences("QuranTrackerPrefs", Context.MODE_PRIVATE).edit();
            editor.putInt("widget_percent", percent);
            editor.putInt("widget_current_page", currentPage);
            editor.putInt("widget_today_read", todayRead);
            editor.putInt("widget_daily_goal", dailyGoal);
            editor.putInt("widget_streak", streak);
            editor.putString("widget_khatmah_name", khatmahName == null ? "" : khatmahName);
            editor.apply();

            QuranProgressWidget.refreshAll(mContext);
        }

        // ===== Floating button feature =====

        /** True if any known Quran app is installed on the device. */
        @JavascriptInterface
        public boolean isQuranAppInstalled() {
            return findInstalledQuranPackage() != null;
        }

        /** JSON array of every launchable installed app: [{"pkg":..,"label":..}, ...]. */
        @JavascriptInterface
        public String getInstalledApps() {
            PackageManager pm = getPackageManager();
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> resolved = pm.queryIntentActivities(launcherIntent, 0);
            List<JSONObject> items = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (ResolveInfo ri : resolved) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue; // skip ourselves
                if (!seen.add(pkg)) continue;                 // dedupe by package
                try {
                    JSONObject o = new JSONObject();
                    o.put("pkg", pkg);
                    o.put("label", ri.loadLabel(pm).toString());
                    items.add(o);
                } catch (Exception ignored) {
                }
            }

            Collections.sort(items, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    return a.optString("label").compareToIgnoreCase(b.optString("label"));
                }
            });

            JSONArray arr = new JSONArray();
            for (JSONObject o : items) arr.put(o);
            return arr.toString();
        }

        /** Persist the package the user chose to switch to. */
        @JavascriptInterface
        public void setSelectedQuranApp(String pkg) {
            mContext.getSharedPreferences("QuranTrackerPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("selected_quran_app", pkg == null ? "" : pkg)
                    .apply();
        }

        @JavascriptInterface
        public String getSelectedQuranApp() {
            return mContext.getSharedPreferences("QuranTrackerPrefs", Context.MODE_PRIVATE)
                    .getString("selected_quran_app", "");
        }

        /** Persisted user preference for showing the floating Quran button. */
        @JavascriptInterface
        public boolean isFloatingButtonEnabled() {
            return mContext.getSharedPreferences("QuranTrackerPrefs", Context.MODE_PRIVATE)
                    .getBoolean("floating_button_enabled", false);
        }

        @JavascriptInterface
        public void setFloatingButtonEnabled(boolean enabled) {
            mContext.getSharedPreferences("QuranTrackerPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("floating_button_enabled", enabled)
                    .apply();
        }

        /** True once the user has granted the "draw over other apps" permission. */
        @JavascriptInterface
        public boolean hasOverlayPermission() {
            return Settings.canDrawOverlays(mContext);
        }

        /** Opens the system "display over other apps" settings page for this app. */
        @JavascriptInterface
        public void requestOverlayPermission() {
            runOnUiThread(MainActivity.this::requestOverlayPermission);
        }

        /**
         * Opens the installed Quran app and shows the floating return button over
         * it. If the overlay permission is missing it is requested first.
         */
        @JavascriptInterface
        public void launchQuranWithFloatingButton(int page) {
            runOnUiThread(() -> MainActivity.this.launchQuranWithFloatingButton(page));
        }
    }

    private String findInstalledQuranPackage() {
        PackageManager pm = getPackageManager();
        for (String pkg : QURAN_PACKAGES) {
            if (pm.getLaunchIntentForPackage(pkg) != null) {
                return pkg;
            }
        }
        return null;
    }

    /**
     * Returns the package to open: the user's explicit choice if it is set and
     * still installed, otherwise an auto-detected known Quran app, else null.
     */
    private String resolveTargetPackage() {
        String selected = getSharedPreferences("QuranTrackerPrefs", Context.MODE_PRIVATE)
                .getString("selected_quran_app", "");
        if (selected != null && !selected.isEmpty()
                && getPackageManager().getLaunchIntentForPackage(selected) != null) {
            return selected;
        }
        return findInstalledQuranPackage();
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
        }
    }

    /** Whether the app has "Usage access" (needed to detect the foreground app). */
    private boolean hasUsageAccess() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), getPackageName());
            } else {
                mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), getPackageName());
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void launchQuranWithFloatingButton(int page) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                    "فعّل صلاحية «العرض فوق التطبيقات» لإظهار زر العودة.",
                    Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }

        String pkg = resolveTargetPackage();
        if (pkg == null) {
            Toast.makeText(this, "اختر التطبيق المراد فتحه من الإعدادات أولاً.", Toast.LENGTH_LONG).show();
            return;
        }

        // Needed so the button only shows while the Quran app is in the foreground
        if (!hasUsageAccess()) {
            Toast.makeText(this,
                    "فعّل «الوصول إلى بيانات الاستخدام» لتطبيق ختمتي، ليظهر الزر فوق تطبيق القرآن فقط.",
                    Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
            return;
        }

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launchIntent == null) {
            Toast.makeText(this, "تعذّر فتح تطبيق القرآن.", Toast.LENGTH_LONG).show();
            return;
        }

        // Show the floating return button (with the target page + package), then open the Quran app.
        Intent svc = new Intent(this, FloatingButtonService.class);
        svc.putExtra("page", page);
        svc.putExtra("pkg", pkg);
        ContextCompat.startForegroundService(this, svc);

        if (page > 0) {
            Toast.makeText(this, "افتح الصفحة " + page + " في تطبيق القرآن", Toast.LENGTH_LONG).show();
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launchIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.evaluateJavascript("if (typeof onPermissionGranted === 'function') { onPermissionGranted(); }", null);
                    }
                });
            } else {
                Toast.makeText(this, "لتلقي التنبيهات اليومية، يرجى تفعيل صلاحية الإشعارات.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            final boolean granted = Settings.canDrawOverlays(this);
            runOnUiThread(() -> webView.evaluateJavascript(
                    "if (typeof onOverlayPermissionResult === 'function') { onOverlayPermissionResult("
                            + granted + "); }", null));
        }
    }
}
