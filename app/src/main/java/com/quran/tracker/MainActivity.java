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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 2002;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 2003;
    private static final int EXPORT_REQUEST_CODE = 3001;
    private static final int IMPORT_REQUEST_CODE = 3002;
    private static final int INSTALL_PERMISSION_REQUEST_CODE = 3003;

    private String pendingExportJson = null;

    private UpdateManager updateManager;
    /** Details of the update the user was offered, kept until the download starts. */
    private JSONObject pendingUpdate = null;
    /** Downloaded APK waiting for the install permission to be granted. */
    private File pendingApk = null;
    /** Guards against re-checking when the WebView reloads. */
    private boolean updateCheckedThisLaunch = false;
    private long lastUpdateCheckMs = 0L;

    /** Shortest gap between two update checks when returning to the app. */
    private static final long RESUME_CHECK_INTERVAL_MS = 60_000L;

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

    /**
     * The running activity, or null when the app is closed. The widget commit
     * path checks this: while the app is open its WebView must be the only one
     * writing localStorage, or a foreground save would clobber the commit.
     */
    private static MainActivity liveInstance;

    static MainActivity getLiveInstance() {
        return liveInstance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        liveInstance = this;

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

        updateManager = new UpdateManager(this);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // A page picked on the widget while the app was closed may not
                // have reached localStorage yet — apply it before anything else.
                applyPendingWidgetPage();
                // Silent update check once the UI is ready (throttled, see below).
                maybeCheckForUpdateSilently();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        webView.clearCache(true);
        webView.loadUrl("file:///android_asset/index.html");
    }

    /**
     * Run the app's own {@code recordProgress()} for a page chosen on the
     * widget, so today's count, the streak and the goals all move exactly as
     * they would had the user tapped inside the app.
     */
    void applyWidgetPosition(final int page) {
        if (webView == null) return;
        webView.post(() -> {
            try {
                webView.evaluateJavascript(
                        "(function(){try{"
                                + "if(typeof applyWidgetPosition!=='function')return 'wait';"
                                + "return applyWidgetPosition(" + page + ");"
                                + "}catch(e){return 'err';}})()", null);
            } catch (Throwable ignored) {
            }
        });
    }

    /** Apply a widget change that never made it into localStorage. */
    private void applyPendingWidgetPage() {
        SharedPreferences prefs =
                getSharedPreferences("QuranTrackerPrefs", Context.MODE_PRIVATE);
        int pending = prefs.getInt(WidgetCommitService.KEY_PENDING_PAGE, 0);
        if (pending > 0) {
            prefs.edit().remove(WidgetCommitService.KEY_PENDING_PAGE).apply();
            applyWidgetPosition(pending);
        }
    }

    @Override
    protected void onDestroy() {
        if (liveInstance == this) liveInstance = null;
        super.onDestroy();
    }

    /**
     * The slice of the {@code Android} bridge a headless commit WebView needs.
     * app.js guards every other bridge call with {@code Android.method &&}, so
     * the absent members simply switch those features off for that instance.
     */
    static class WidgetBridge {
        private final Context ctx;

        WidgetBridge(Context context) {
            this.ctx = context.getApplicationContext();
        }

        @JavascriptInterface
        public void updateWidget(int percent, int currentPage, int todayRead,
                                 int dailyGoal, int streak, String khatmahName) {
            writeWidgetPrefs(ctx, percent, currentPage, todayRead, dailyGoal, streak, khatmahName);
        }

        /** No UI to toast onto during a headless commit. */
        @JavascriptInterface
        public void showToast(String message) {
        }
    }

    /** Single place that mirrors progress into the widget's preferences. */
    static void writeWidgetPrefs(Context context, int percent, int currentPage, int todayRead,
                                 int dailyGoal, int streak, String khatmahName) {
        SharedPreferences.Editor editor = context
                .getSharedPreferences("QuranTrackerPrefs", Context.MODE_PRIVATE).edit();
        editor.putInt("widget_percent", percent);
        editor.putInt(QuranProgressWidget.KEY_PAGE, currentPage);
        editor.putInt(QuranProgressWidget.KEY_AYAH,
                QuranData.get(context).firstAyahOfPage(currentPage));
        editor.putInt("widget_today_read", todayRead);
        editor.putInt("widget_daily_goal", dailyGoal);
        editor.putInt("widget_streak", streak);
        editor.putString("widget_khatmah_name", khatmahName == null ? "" : khatmahName);
        editor.apply();

        QuranProgressWidget.refreshAll(context);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Once the user is back inside ختمتي, the floating return button is no
        // longer needed (covers returning via the button, Back, or Recents).
        stopService(new Intent(this, FloatingButtonService.class));

        // Opening the app usually resumes the existing activity instead of
        // creating it, so onPageFinished never fires again — check here too.
        checkForUpdateOnResume();
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

        /** Export: let the user pick where to save the backup JSON file. */
        @JavascriptInterface
        public void exportData(String filename, String json) {
            pendingExportJson = json;
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE,
                        (filename == null || filename.isEmpty()) ? "khatmati_backup.json" : filename);
                try {
                    startActivityForResult(intent, EXPORT_REQUEST_CODE);
                } catch (Exception e) {
                    Toast.makeText(mContext, "تعذّر فتح نافذة الحفظ.", Toast.LENGTH_LONG).show();
                }
            });
        }

        /** Import: let the user pick a backup JSON file to restore. */
        @JavascriptInterface
        public void importData() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(intent, IMPORT_REQUEST_CODE);
                } catch (Exception e) {
                    Toast.makeText(mContext, "تعذّر فتح منتقي الملفات.", Toast.LENGTH_LONG).show();
                }
            });
        }

        /** Store the latest progress and refresh the home-screen widget. */
        @JavascriptInterface
        public void updateWidget(int percent, int currentPage, int todayRead,
                                 int dailyGoal, int streak, String khatmahName) {
            writeWidgetPrefs(mContext, percent, currentPage, todayRead,
                    dailyGoal, streak, khatmahName);
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

        // ===== In-app update =====

        /** Installed version, e.g. "4.7". */
        @JavascriptInterface
        public String getAppVersionName() {
            return updateManager.getInstalledVersionName();
        }

        @JavascriptInterface
        public int getAppVersionCode() {
            return updateManager.getInstalledVersionCode();
        }

        /**
         * Asks the update server whether a newer build exists.
         * The result comes back as onUpdateAvailable / onUpToDate / onUpdateError in JS.
         *
         * @param manual true when the user pressed "check for updates" — only then do
         *               we report "you are up to date" or connection errors.
         */
        @JavascriptInterface
        public void checkForUpdate(boolean manual) {
            runOnUiThread(() -> MainActivity.this.runUpdateCheck(manual));
        }

        /** Downloads the offered update and launches the installer when finished. */
        @JavascriptInterface
        public void startUpdateDownload() {
            runOnUiThread(MainActivity.this::startUpdateDownload);
        }

        /** True once the user allowed this app to install APKs (Android 8+). */
        @JavascriptInterface
        public boolean canInstallPackages() {
            return updateManager.canInstallPackages();
        }
    }

    // ===== In-app update =====

    /**
     * Runs every time the app is opened: checks quietly and, if a newer build
     * exists, the web layer pops the update dialog right away. onPageFinished can
     * fire again (reloads, back navigation) so the check is done once per launch.
     */
    private void maybeCheckForUpdateSilently() {
        if (updateCheckedThisLaunch) return;
        updateCheckedThisLaunch = true;
        lastUpdateCheckMs = System.currentTimeMillis();
        runUpdateCheck(false);
    }

    /**
     * Re-checks when the user comes back to the app. Throttled by a minute so
     * short trips out (permission screens, the Quran app, the installer) do not
     * fire a request each time.
     */
    private void checkForUpdateOnResume() {
        if (!updateCheckedThisLaunch) return;          // the page-load check will run
        if (updateManager.isDownloading() || pendingApk != null) return;
        if (System.currentTimeMillis() - lastUpdateCheckMs < RESUME_CHECK_INTERVAL_MS) return;

        lastUpdateCheckMs = System.currentTimeMillis();
        runUpdateCheck(false);
    }

    private void runUpdateCheck(final boolean manual) {
        updateManager.checkForUpdate(new UpdateManager.CheckCallback() {
            @Override
            public void onUpdateAvailable(JSONObject info) {
                pendingUpdate = info;
                callJs("onUpdateAvailable", info.toString());
            }

            @Override
            public void onUpToDate(int installedCode, int remoteCode) {
                callJs("onUpdateNotAvailable",
                        "المثبَّت: " + installedCode + " • على الخادم: " + remoteCode);
            }

            @Override
            public void onError(String message) {
                // Reported for silent checks too: it is written to the settings
                // status line only, never as a popup.
                callJs("onUpdateError", message);
            }
        });
    }

    private void startUpdateDownload() {
        if (pendingUpdate == null) {
            callJs("onUpdateError", "لا يوجد تحديث محدَّد.");
            return;
        }
        if (updateManager.isDownloading()) return;

        final String apkUrl = pendingUpdate.optString("apkUrl", "");
        final String versionName = pendingUpdate.optString("versionName", "");
        if (apkUrl.isEmpty()) {
            callJs("onUpdateError", "رابط التحديث غير متوفر.");
            return;
        }

        updateManager.downloadAndInstall(apkUrl, versionName, new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                webView.evaluateJavascript(
                        "if (typeof onUpdateProgress === 'function') { onUpdateProgress("
                                + percent + "); }", null);
            }

            @Override
            public void onReady(File apk) {
                pendingApk = apk;
                callJs("onUpdateDownloaded", null);
                installOrRequestPermission();
            }

            @Override
            public void onError(String message) {
                callJs("onUpdateError", message);
            }
        });
    }

    /** Installs the downloaded APK, first asking for the "unknown sources" permission. */
    private void installOrRequestPermission() {
        if (pendingApk == null || !pendingApk.exists()) return;

        if (!updateManager.canInstallPackages()) {
            Toast.makeText(this,
                    "اسمح لتطبيق ختمتي بتثبيت التحديثات، ثم عد للتطبيق.",
                    Toast.LENGTH_LONG).show();
            try {
                updateManager.requestInstallPermission(this, INSTALL_PERMISSION_REQUEST_CODE);
            } catch (Exception e) {
                callJs("onUpdateError", "تعذّر فتح شاشة صلاحية التثبيت.");
            }
            return;
        }

        try {
            updateManager.install(this, pendingApk);
        } catch (Exception e) {
            callJs("onUpdateError", "تعذّر بدء التثبيت.");
        }
    }

    /** Calls a JS function with a single optional string argument, if it exists. */
    private void callJs(String fn, String arg) {
        final String call = arg == null
                ? fn + "();"
                : fn + "(" + JSONObject.quote(arg) + ");";
        runOnUiThread(() -> webView.evaluateJavascript(
                "if (typeof " + fn + " === 'function') { " + call + " }", null));
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

        } else if (requestCode == EXPORT_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                writeExport(data.getData());
            }
            pendingExportJson = null;

        } else if (requestCode == IMPORT_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                readImport(data.getData());
            }

        } else if (requestCode == INSTALL_PERMISSION_REQUEST_CODE) {
            if (updateManager.canInstallPackages()) {
                installOrRequestPermission();
            } else {
                callJs("onUpdateError", "لم يتم منح صلاحية التثبيت، فتعذّر تطبيق التحديث.");
            }
        }
    }

    private void writeExport(Uri uri) {
        if (pendingExportJson == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os != null) {
                os.write(pendingExportJson.getBytes(StandardCharsets.UTF_8));
                os.flush();
                Toast.makeText(this, "تم حفظ ملف النسخة الاحتياطية بنجاح ✅", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "تعذّر حفظ الملف.", Toast.LENGTH_LONG).show();
        }
    }

    private void readImport(Uri uri) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            Toast.makeText(this, "تعذّر قراءة الملف.", Toast.LENGTH_LONG).show();
            return;
        }

        final String content = sb.toString();
        runOnUiThread(() -> webView.evaluateJavascript(
                "if (typeof onDataImported === 'function') { onDataImported("
                        + JSONObject.quote(content) + "); }", null));
    }
}
