package com.quran.tracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Half-screen sheet opened by tapping the widget.
 *
 * <p>Three wheels — page, surah, ayah — over one position. They are not
 * independent: the page determines which surah and which ayahs are on screen,
 * so moving any wheel re-derives the others. A guard flag stops that
 * cross-updating from feeding back on itself.
 *
 * <p>Nothing is saved while scrolling. Only "تطبيق التغييرات" commits, and it
 * commits through {@link WidgetCommitService} so the change runs the app's own
 * recordProgress() rather than a second implementation of it.
 */
public class QuickAdjustActivity extends AppCompatActivity {

    /** Fraction of the screen the sheet occupies. */
    private static final double SHEET_HEIGHT_RATIO = 0.55;

    private QuranData data;
    private NumberPicker pickerPage;
    private NumberPicker pickerSurah;
    private NumberPicker pickerAyah;
    private TextView subtitle;

    /** True while one wheel is programmatically moving the others. */
    private boolean syncing = false;

    private int page = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_adjust);

        data = QuranData.get(this);
        SharedPreferences prefs = getSharedPreferences(QuranProgressWidget.PREFS, Context.MODE_PRIVATE);
        page = clampPage(prefs.getInt(QuranProgressWidget.KEY_PAGE, 1));

        pickerPage = findViewById(R.id.picker_page);
        pickerSurah = findViewById(R.id.picker_surah);
        pickerAyah = findViewById(R.id.picker_ayah);
        subtitle = findViewById(R.id.sheet_subtitle);

        sizeSheet();
        setUpPagePicker();
        setUpSurahPicker();
        setUpAyahPicker();
        syncFromPage(page);

        // Tapping the dimmed area outside the panel dismisses without saving.
        findViewById(R.id.sheet_scrim).setOnClickListener(v -> finish());
        findViewById(R.id.sheet_apply).setOnClickListener(v -> applyAndClose());
    }

    /** Give the panel roughly half the phone, as a sheet rather than a dialog. */
    private void sizeSheet() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        View panel = findViewById(R.id.sheet_panel);
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        lp.height = (int) (metrics.heightPixels * SHEET_HEIGHT_RATIO);
        panel.setLayoutParams(lp);
    }

    // ===== Wheels =====

    private void setUpPagePicker() {
        pickerPage.setMinValue(1);
        pickerPage.setMaxValue(QuranData.TOTAL_PAGES);
        pickerPage.setWrapSelectorWheel(false);
        pickerPage.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        pickerPage.setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (syncing) return;
            syncFromPage(newVal);
        });
    }

    private void setUpSurahPicker() {
        String[] names = new String[QuranData.TOTAL_SURAHS];
        for (int s = 1; s <= QuranData.TOTAL_SURAHS; s++) {
            names[s - 1] = s + ". " + data.nameOfSurah(s);
        }
        pickerSurah.setMinValue(1);
        pickerSurah.setMaxValue(QuranData.TOTAL_SURAHS);
        pickerSurah.setDisplayedValues(names);
        pickerSurah.setWrapSelectorWheel(false);
        pickerSurah.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        pickerSurah.setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (syncing) return;
            // Choosing a surah jumps to where that surah opens.
            syncFromPage(data.firstPageOfSurah(newVal));
        });
    }

    private void setUpAyahPicker() {
        pickerAyah.setMinValue(1);
        pickerAyah.setMaxValue(1);
        pickerAyah.setWrapSelectorWheel(false);
        pickerAyah.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        // The wheel only ever holds the ayahs that fall on the current page, so
        // moving it changes the ayah alone — the page wheel handles the rest.
        pickerAyah.setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (syncing) return;
            updateSubtitle(page, data.firstAyahOfPage(page) + (newVal - 1));
        });
    }

    /**
     * Re-derive every wheel from a page. Called on load and whenever any one of
     * the three moves, so the sheet always shows one coherent position.
     */
    private void syncFromPage(int newPage) {
        syncing = true;
        try {
            page = clampPage(newPage);

            pickerPage.setValue(page);
            pickerSurah.setValue(data.surahOfPage(page));

            int first = data.firstAyahOfPage(page);
            int last = Math.max(first, data.lastAyahOfPage(page));
            int count = last - first + 1;
            String[] labels = new String[count];
            for (int i = 0; i < count; i++) labels[i] = String.valueOf(first + i);

            // Shrink the range before swapping labels, or an index past the new
            // end of the array throws while the old maximum is still in force.
            pickerAyah.setMinValue(1);
            pickerAyah.setMaxValue(1);
            pickerAyah.setDisplayedValues(null);
            pickerAyah.setMaxValue(count);
            pickerAyah.setDisplayedValues(labels);
            pickerAyah.setValue(1);

            updateSubtitle(page, first);
        } finally {
            syncing = false;
        }
    }

    private void updateSubtitle(int p, int ayah) {
        if (subtitle == null) return;
        subtitle.setText("سورة " + data.surahNameOfPage(p) + " • آية " + ayah + " • صفحة " + p);
    }

    private static int clampPage(int p) {
        if (p < 1) return 1;
        if (p > QuranData.TOTAL_PAGES) return QuranData.TOTAL_PAGES;
        return p;
    }

    // ===== Commit =====

    private void applyAndClose() {
        int selectedAyah = data.firstAyahOfPage(page) + (pickerAyah.getValue() - 1);

        getSharedPreferences(QuranProgressWidget.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(QuranProgressWidget.KEY_PAGE, page)
                .putInt(QuranProgressWidget.KEY_AYAH, selectedAyah)
                .apply();

        QuranProgressWidget.refreshAll(this);
        WidgetCommitService.commitNow(this, page);
        finish();
    }
}
