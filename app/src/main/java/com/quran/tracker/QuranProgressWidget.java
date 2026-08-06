package com.quran.tracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/**
 * Home-screen widget showing the current khatmah progress, with in-place
 * controls for the surah, the page and the ayah.
 *
 * <h3>Who owns the data</h3>
 * The reading data lives in the WebView's localStorage — daily counts, streaks,
 * makeup days and khatmah completion are all derived there by
 * {@code recordProgress()} in app.js. The widget must therefore never compute
 * progress itself, or the two would drift apart.
 *
 * So a tap follows two paths at once:
 * <ol>
 *   <li><b>Now:</b> the new page is written to SharedPreferences and the widget
 *       redraws immediately, so the arrows feel instant.</li>
 *   <li><b>Shortly after:</b> {@link WidgetCommitService} runs the real
 *       {@code recordProgress()} in a headless WebView, which saves to
 *       localStorage and pushes the authoritative numbers back into these same
 *       preferences. Rapid taps are debounced so only the final page commits.</li>
 * </ol>
 */
public class QuranProgressWidget extends AppWidgetProvider {

    static final String PREFS = "QuranTrackerPrefs";

    static final String ACTION_SURAH_PREV = "com.quran.tracker.widget.SURAH_PREV";
    static final String ACTION_SURAH_NEXT = "com.quran.tracker.widget.SURAH_NEXT";
    static final String ACTION_PAGE_DEC = "com.quran.tracker.widget.PAGE_DEC";
    static final String ACTION_PAGE_INC = "com.quran.tracker.widget.PAGE_INC";
    static final String ACTION_AYAH_DEC = "com.quran.tracker.widget.AYAH_DEC";
    static final String ACTION_AYAH_INC = "com.quran.tracker.widget.AYAH_INC";

    /** Page the widget is showing; committed to the app by the service. */
    static final String KEY_PAGE = "widget_current_page";
    /** Ayah the widget is showing, within the page. */
    static final String KEY_AYAH = "widget_current_ayah";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, manager, id);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action != null && action.startsWith("com.quran.tracker.widget.")) {
            handleStep(context, action);
            return;
        }
        super.onReceive(context, intent);
    }

    /** Apply an arrow/stepper press to the locally displayed position. */
    private void handleStep(Context context, String action) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        QuranData data = QuranData.get(context);

        int page = prefs.getInt(KEY_PAGE, 1);
        int ayah = prefs.getInt(KEY_AYAH, data.firstAyahOfPage(page));
        int newPage = page;

        switch (action) {
            case ACTION_SURAH_PREV:
                newPage = data.pageOfPreviousSurah(page);
                break;
            case ACTION_SURAH_NEXT:
                newPage = data.pageOfNextSurah(page);
                break;
            case ACTION_PAGE_DEC:
                newPage = Math.max(1, page - 1);
                break;
            case ACTION_PAGE_INC:
                newPage = Math.min(QuranData.TOTAL_PAGES, page + 1);
                break;
            case ACTION_AYAH_DEC:
                if (ayah > data.firstAyahOfPage(page)) {
                    setAyahLocally(context, page, ayah - 1);
                    return;                       // same page, nothing to commit
                }
                // At the top of the page: step back a page, landing on its last ayah.
                newPage = Math.max(1, page - 1);
                setPageLocally(context, newPage, data.lastAyahOfPage(newPage));
                WidgetCommitService.scheduleCommit(context, newPage);
                return;
            case ACTION_AYAH_INC:
                if (ayah < data.lastAyahOfPage(page)) {
                    setAyahLocally(context, page, ayah + 1);
                    return;
                }
                newPage = Math.min(QuranData.TOTAL_PAGES, page + 1);
                setPageLocally(context, newPage, data.firstAyahOfPage(newPage));
                WidgetCommitService.scheduleCommit(context, newPage);
                return;
            default:
                return;
        }

        setPageLocally(context, newPage, data.firstAyahOfPage(newPage));
        WidgetCommitService.scheduleCommit(context, newPage);
    }

    /**
     * Move within the page. The app records progress per page, so an ayah step
     * that stays on the same page is a display-only change — committing it
     * would log a reading of zero pages.
     */
    private static void setAyahLocally(Context context, int page, int ayah) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_AYAH, ayah).apply();
        refreshAll(context);
    }

    /** Move the widget's displayed position without touching the app's data. */
    private static void setPageLocally(Context context, int page, int ayah) {
        int p = Math.max(1, Math.min(QuranData.TOTAL_PAGES, page));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_PAGE, p)
                .putInt(KEY_AYAH, ayah)
                .apply();
        refreshAll(context);
    }

    /** Called by the app to refresh every placed widget instance. */
    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, QuranProgressWidget.class);
        int[] ids = manager.getAppWidgetIds(cn);
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        QuranData data = QuranData.get(context);

        int percent = prefs.getInt("widget_percent", 0);
        int currentPage = prefs.getInt(KEY_PAGE, 1);
        int todayRead = prefs.getInt("widget_today_read", 0);
        int dailyGoal = prefs.getInt("widget_daily_goal", 10);
        int streak = prefs.getInt("widget_streak", 0);
        String khatmahName = prefs.getString("widget_khatmah_name", "");
        int ayah = prefs.getInt(KEY_AYAH, data.firstAyahOfPage(currentPage));

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_progress);

        views.setTextViewText(R.id.widget_khatmah_name, khatmahName);
        views.setTextViewText(R.id.widget_percent, percent + "%");
        views.setProgressBar(R.id.widget_progress_bar, 100, percent, false);
        views.setTextViewText(R.id.widget_streak, "🔥 " + streak);
        views.setTextViewText(R.id.widget_today, "وردك اليوم: " + todayRead + " / " + dailyGoal);

        views.setTextViewText(R.id.widget_surah_label, data.surahNameOfPage(currentPage));
        views.setTextViewText(R.id.widget_page_val, String.valueOf(currentPage));
        views.setTextViewText(R.id.widget_ayah_val, String.valueOf(ayah));

        // Tapping anywhere that is not a stepper opens the quick-adjust sheet.
        Intent sheet = new Intent(context, QuickAdjustActivity.class);
        sheet.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
                context, 0, sheet, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        views.setOnClickPendingIntent(R.id.widget_surah_prev, stepIntent(context, ACTION_SURAH_PREV, 1));
        views.setOnClickPendingIntent(R.id.widget_surah_next, stepIntent(context, ACTION_SURAH_NEXT, 2));
        views.setOnClickPendingIntent(R.id.widget_page_dec, stepIntent(context, ACTION_PAGE_DEC, 3));
        views.setOnClickPendingIntent(R.id.widget_page_inc, stepIntent(context, ACTION_PAGE_INC, 4));
        views.setOnClickPendingIntent(R.id.widget_ayah_dec, stepIntent(context, ACTION_AYAH_DEC, 5));
        views.setOnClickPendingIntent(R.id.widget_ayah_inc, stepIntent(context, ACTION_AYAH_INC, 6));

        manager.updateAppWidget(widgetId, views);
    }

    /** Distinct request codes keep the six PendingIntents from overwriting each other. */
    private static PendingIntent stepIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, QuranProgressWidget.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
