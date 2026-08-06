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
 * Home-screen widget showing the current khatmah progress. Data is written to
 * SharedPreferences by the WebView (via WebAppInterface.updateWidget) and read
 * here to build the RemoteViews. Tapping the widget opens the app.
 */
public class QuranProgressWidget extends AppWidgetProvider {

    private static final String PREFS = "QuranTrackerPrefs";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, manager, id);
        }
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

        int percent = prefs.getInt("widget_percent", 0);
        int currentPage = prefs.getInt("widget_current_page", 1);
        int todayRead = prefs.getInt("widget_today_read", 0);
        int dailyGoal = prefs.getInt("widget_daily_goal", 10);
        int streak = prefs.getInt("widget_streak", 0);
        String khatmahName = prefs.getString("widget_khatmah_name", "");

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_progress);

        views.setTextViewText(R.id.widget_khatmah_name, khatmahName);
        views.setTextViewText(R.id.widget_percent, percent + "%");
        views.setTextViewText(R.id.widget_page, "الصفحة " + currentPage + " / 604");
        views.setProgressBar(R.id.widget_progress_bar, 100, percent, false);
        views.setTextViewText(R.id.widget_today, "وردك اليوم: " + todayRead + " / " + dailyGoal);
        views.setTextViewText(R.id.widget_streak, "🔥 " + streak);

        // Tap widget -> open the app
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(context, 0, intent, flags);
        views.setOnClickPendingIntent(R.id.widget_app_name, pending);
        views.setOnClickPendingIntent(R.id.widget_percent, pending);

        manager.updateAppWidget(widgetId, views);
    }
}
