package com.quran.tracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Commits a position picked on the widget into the app's real reading data.
 *
 * <p>The numbers a reader cares about — today's count, the streak, makeup days,
 * khatmah completion — are all computed by {@code recordProgress()} in app.js.
 * Reimplementing that in Java would guarantee the two drift apart, so instead we
 * run the very same function and let it save.
 *
 * <p>Two routes, picked by whether the app is already running:
 * <ul>
 *   <li><b>App open:</b> the change goes to {@link MainActivity}'s live WebView.
 *       Loading a second WebView over the same localStorage would let the
 *       foreground copy overwrite the commit on its next save.</li>
 *   <li><b>App closed:</b> a headless WebView loads the same
 *       {@code file:///android_asset/index.html}, which shares that localStorage,
 *       applies the change and is then torn down.</li>
 * </ul>
 *
 * <p>Taps are debounced: holding "+" through ten pages commits page ten once,
 * not ten times. If the process dies before the commit lands, the page is still
 * recorded as pending and {@link MainActivity} applies it on next launch.
 */
final class WidgetCommitService {

    private static final long DEBOUNCE_MS = 700;
    private static final long RETRY_MS = 200;
    private static final int MAX_TRIES = 40;   // ~8s for a cold WebView start

    /** Page waiting to be written into localStorage; 0 when nothing is pending. */
    static final String KEY_PENDING_PAGE = "widget_pending_page";

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable scheduled;
    private static WebView headless;
    private static boolean committing;

    private WidgetCommitService() {
    }

    /** Debounce a commit of {@code page}; the last call within the window wins. */
    static void scheduleCommit(final Context context, final int page) {
        final Context app = context.getApplicationContext();
        app.getSharedPreferences(QuranProgressWidget.PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_PENDING_PAGE, page).apply();

        if (scheduled != null) handler.removeCallbacks(scheduled);
        scheduled = new Runnable() {
            @Override
            public void run() {
                scheduled = null;
                commitNow(app, page);
            }
        };
        handler.postDelayed(scheduled, DEBOUNCE_MS);
    }

    /** Apply immediately, skipping the debounce (used by the quick-adjust sheet). */
    static void commitNow(Context context, int page) {
        final Context app = context.getApplicationContext();

        MainActivity live = MainActivity.getLiveInstance();
        if (live != null) {
            live.applyWidgetPosition(page);
            clearPending(app, page);
            return;
        }
        if (committing) return;
        committing = true;
        runHeadless(app, page, 0);
    }

    private static void runHeadless(final Context app, final int page, final int attempt) {
        if (attempt == 0) {
            try {
                headless = new WebView(app);
                headless.getSettings().setJavaScriptEnabled(true);
                headless.getSettings().setDomStorageEnabled(true);
                headless.getSettings().setDatabaseEnabled(true);
                headless.addJavascriptInterface(
                        new MainActivity.WidgetBridge(app), "Android");
                headless.setWebViewClient(new WebViewClient());
                headless.loadUrl("file:///android_asset/index.html");
            } catch (Throwable t) {
                // No WebView available (rare, e.g. mid-update): leave the page
                // pending so the app applies it on next launch.
                committing = false;
                return;
            }
        }

        if (attempt >= MAX_TRIES) {
            teardown();
            return;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (headless == null) {
                    committing = false;
                    return;
                }
                headless.evaluateJavascript(
                        "(function(){try{"
                                + "if(typeof applyWidgetPosition!=='function')return 'wait';"
                                + "return applyWidgetPosition(" + page + ");"
                                + "}catch(e){return 'err';}})()",
                        value -> {
                            boolean done = value != null && value.contains("ok");
                            if (done) {
                                clearPending(app, page);
                                QuranProgressWidget.refreshAll(app);
                                // Give app.js a moment to finish its save before
                                // the WebView goes away.
                                handler.postDelayed(WidgetCommitService::teardown, 400);
                            } else {
                                runHeadless(app, page, attempt + 1);
                            }
                        });
            }
        }, RETRY_MS);
    }

    private static void teardown() {
        committing = false;
        if (headless != null) {
            try {
                headless.destroy();
            } catch (Throwable ignored) {
            }
            headless = null;
        }
    }

    private static void clearPending(Context app, int page) {
        SharedPreferences prefs =
                app.getSharedPreferences(QuranProgressWidget.PREFS, Context.MODE_PRIVATE);
        if (prefs.getInt(KEY_PENDING_PAGE, 0) == page) {
            prefs.edit().remove(KEY_PENDING_PAGE).apply();
        }
    }
}
