package com.quran.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * The full mushaf, rendered natively.
 *
 * This screen exists because the WebView tab could not be made to show the
 * real Uthmani face reliably: every font-loading route tried in that page —
 * a linked file, a data: URI, a FontFace built from an ArrayBuffer, the same
 * bytes behind a blob: URL — either failed outright or registered the face
 * without ever applying it to drawn text, and there was no way from inside
 * that page to tell which had happened until the reader's own diagnostic was
 * added. A Typeface loaded here through AssetManager has no such ambiguity:
 * {@link Typeface#createFromAsset} either returns a usable typeface or the
 * process throws, so there is no state where the font silently falls back.
 *
 * The reading position is not this screen's data to own — it belongs to the
 * WebView's localStorage, exactly like the widget's position is not the
 * widget's to own. So "حفظ التقدم" does not write anything itself; it
 * returns the chosen page (and ayah, if one was tapped) as an activity
 * result, and {@link MainActivity} runs the app's own recordProgress() in
 * its live WebView — the same path a tap inside the app takes.
 */
public class MushafReaderActivity extends Activity {

    static final String EXTRA_START_PAGE = "start_page";
    static final String EXTRA_RESULT_PAGE = "result_page";
    static final String EXTRA_RESULT_HAS_AYAH = "result_has_ayah";
    static final String EXTRA_RESULT_SURAH = "result_surah";
    static final String EXTRA_RESULT_AYAH = "result_ayah";

    private static final float[] FONT_SIZES_SP = {17f, 19f, 21f, 24f, 27f, 31f, 36f};
    private static final int DEFAULT_FONT_STEP = 3; // index into FONT_SIZES_SP, 1-based

    // ---- Day palette ----
    private static final int DAY_BG = 0xFFFFFFFF;
    private static final int DAY_INK = 0xFF14181C;
    private static final int DAY_MUTED = 0xFF8A9299;
    private static final int DAY_CHIP = 0xFFF4F6FA;
    private static final int DAY_LINE = 0x14000000;
    private static final int DAY_ACCENT = 0xFF139D62;

    // ---- Night palette ----
    private static final int NIGHT_BG = 0xFF000000;
    private static final int NIGHT_INK = 0xFFF2F4F1;
    private static final int NIGHT_MUTED = 0xFF7C857E;
    private static final int NIGHT_CHIP = 0xFF12171A;
    private static final int NIGHT_LINE = 0x1AFFFFFF;
    private static final int NIGHT_ACCENT = 0xFF7BD8A6;

    private QuranNativeData data;
    private Typeface mushafFace;
    private SharedPreferences prefs;

    private int page = 1;
    private Integer selSurah = null;
    private Integer selAyah = null;
    private int fontStep = DEFAULT_FONT_STEP;
    private boolean night = false;

    private LinearLayout root, content, toolbar, headRow;
    private ScrollView scroll;
    private TextView btnPickSurah, btnPickPage, btnFontDec, btnFontInc, btnNight, btnClose;
    private TextView headJuz, headPage, headSurah;
    private TextView btnPrev, btnSave, btnNext, hint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mushaf_reader);

        data = QuranNativeData.get(this);
        prefs = getSharedPreferences("QuranTrackerPrefs", Context.MODE_PRIVATE);

        try {
            mushafFace = Typeface.createFromAsset(getAssets(), "fonts/UthmanicHafs.ttf");
        } catch (Exception e) {
            mushafFace = null;
        }

        bindViews();

        if (!data.isLoaded() || mushafFace == null) {
            showFatalError(!data.isLoaded()
                    ? "تعذّر تحميل بيانات المصحف"
                    : "تعذّر تحميل خط المصحف");
            return;
        }

        int savedFont = prefs.getInt("quran_mushaf_native_font", DEFAULT_FONT_STEP);
        fontStep = (savedFont >= 1 && savedFont <= FONT_SIZES_SP.length) ? savedFont : DEFAULT_FONT_STEP;
        night = prefs.getBoolean("quran_mushaf_native_night", false);

        int start = getIntent().getIntExtra(EXTRA_START_PAGE, prefs.getInt("widget_current_page", 1));
        page = Math.max(1, Math.min(QuranNativeData.TOTAL_PAGES, start));

        wireControls();
        applyPalette();
        renderPage();
    }

    private void bindViews() {
        root = findViewById(R.id.reader_root);
        toolbar = findViewById(R.id.reader_toolbar);
        headRow = findViewById(R.id.reader_head);
        content = findViewById(R.id.reader_content);
        scroll = findViewById(R.id.reader_scroll);
        btnPickSurah = findViewById(R.id.btn_pick_surah);
        btnPickPage = findViewById(R.id.btn_pick_page);
        btnFontDec = findViewById(R.id.btn_font_dec);
        btnFontInc = findViewById(R.id.btn_font_inc);
        btnNight = findViewById(R.id.btn_night);
        btnClose = findViewById(R.id.btn_close);
        headJuz = findViewById(R.id.head_juz);
        headPage = findViewById(R.id.head_page);
        headSurah = findViewById(R.id.head_surah);
        btnPrev = findViewById(R.id.btn_prev);
        btnSave = findViewById(R.id.btn_save);
        btnNext = findViewById(R.id.btn_next);
        hint = findViewById(R.id.reader_hint);
    }

    private void showFatalError(String message) {
        content.removeAllViews();
        TextView err = new TextView(this);
        err.setText(message);
        err.setTextColor(0xFFE8A33D);
        err.setTextSize(15f);
        err.setGravity(Gravity.CENTER);
        err.setPadding(24, 120, 24, 24);
        content.addView(err);
        toolbar.setVisibility(View.GONE);
        headRow.setVisibility(View.GONE);
        btnPrev.setVisibility(View.GONE);
        btnNext.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);
        hint.setText("");
    }

    private void wireControls() {
        // Arrows point the reading direction: ‹ (visually right in RTL) goes
        // to the next page, › goes back — matching the WebView tab and how a
        // mushaf app numbers forward.
        btnNext.setOnClickListener(v -> goTo(page + 1, false));
        btnPrev.setOnClickListener(v -> goTo(page - 1, false));
        btnSave.setOnClickListener(v -> saveAndFinish());
        btnClose.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnFontInc.setOnClickListener(v -> {
            if (fontStep < FONT_SIZES_SP.length) {
                fontStep++;
                prefs.edit().putInt("quran_mushaf_native_font", fontStep).apply();
                renderPage();
            }
        });
        btnFontDec.setOnClickListener(v -> {
            if (fontStep > 1) {
                fontStep--;
                prefs.edit().putInt("quran_mushaf_native_font", fontStep).apply();
                renderPage();
            }
        });
        btnNight.setOnClickListener(v -> {
            night = !night;
            prefs.edit().putBoolean("quran_mushaf_native_night", night).apply();
            applyPalette();
            renderPage();
        });

        btnPickSurah.setOnClickListener(v -> showSurahPicker());
        btnPickPage.setOnClickListener(v -> showPagePicker());
    }

    private void showSurahPicker() {
        String[] labels = new String[QuranNativeData.TOTAL_SURAHS];
        for (int s = 1; s <= QuranNativeData.TOTAL_SURAHS; s++) {
            labels[s - 1] = s + ". " + data.nameOfSurah(s);
        }
        new AlertDialog.Builder(this)
                .setTitle("اختر السورة")
                .setItems(labels, (dialog, which) -> {
                    int surah = which + 1;
                    goTo(data.firstPageOfSurah(surah), false);
                })
                .show();
    }

    private void showPagePicker() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(1);
        picker.setMaxValue(QuranNativeData.TOTAL_PAGES);
        picker.setValue(page);
        picker.setWrapSelectorWheel(false);
        FrameWrap wrap = new FrameWrap(this, picker);
        new AlertDialog.Builder(this)
                .setTitle("اذهب إلى صفحة")
                .setView(wrap)
                .setPositiveButton("انتقال", (d, w) -> goTo(picker.getValue(), false))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    /** Centers a NumberPicker in a dialog without pulling in a layout file for one control. */
    private static final class FrameWrap extends android.widget.FrameLayout {
        FrameWrap(Context c, View child) {
            super(c);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER;
            addView(child, lp);
            setPadding(0, 24, 0, 0);
        }
    }

    private void goTo(int newPage, boolean keepSelection) {
        page = Math.max(1, Math.min(QuranNativeData.TOTAL_PAGES, newPage));
        if (!keepSelection) {
            selSurah = null;
            selAyah = null;
        }
        renderPage();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    // ===== Rendering =====

    private void applyPalette() {
        int bg = night ? NIGHT_BG : DAY_BG;
        int chip = night ? NIGHT_CHIP : DAY_CHIP;
        int ink = night ? NIGHT_INK : DAY_INK;

        root.setBackgroundColor(bg);
        for (TextView chipBtn : new TextView[]{btnPickSurah, btnPickPage, btnFontDec, btnFontInc, btnNight, btnClose}) {
            chipBtn.setBackgroundColor(chip);
            chipBtn.setTextColor(ink);
        }
        btnNight.setText(night ? "☀️" : "🌙");
        btnPrev.setBackgroundColor(chip);
        btnNext.setBackgroundColor(chip);
        btnPrev.setTextColor(ink);
        btnNext.setTextColor(ink);
    }

    private void renderPage() {
        content.removeAllViews();

        List<QuranNativeData.AyahRef> ayahs = data.ayahsOnPage(page);
        int accent = night ? NIGHT_ACCENT : DAY_ACCENT;
        int muted = night ? NIGHT_MUTED : DAY_MUTED;
        int ink = night ? NIGHT_INK : DAY_INK;
        int chip = night ? NIGHT_CHIP : DAY_CHIP;
        float textSize = FONT_SIZES_SP[fontStep - 1];

        int juz = data.juzOfPage(page);
        int hizb = data.hizbOfPage(page);
        int surahHere = ayahs.isEmpty() ? data.surahOfPage(page) : ayahs.get(0).surah;

        headJuz.setText("الجزء " + toArabicDigits(juz));
        headPage.setText(toArabicDigits(page));
        headSurah.setText(toArabicDigits(surahHere) + " " + data.nameOfSurah(surahHere));
        headJuz.setTextColor(muted);
        headPage.setTextColor(muted);
        headSurah.setTextColor(muted);

        SpannableStringBuilder run = null;
        TextView runView = null;

        for (QuranNativeData.AyahRef ref : ayahs) {
            if (ref.first) {
                run = null; // a new surah starts a fresh run, like the WebView tab
                content.addView(surahTitleView(ref.surah, chip, accent));
                if (ref.surah != 1 && ref.surah != 9) {
                    content.addView(basmalaView(ink, textSize));
                }
            }
            if (run == null) {
                run = new SpannableStringBuilder();
                runView = ayahRunView(ink, textSize);
                content.addView(runView);
                final TextView boundRunView = runView;
                runView.setTag(run); // keep the builder reachable for appends below
            }

            appendAyah(run, ref.surah, ref.ayah, accent, ink);
            runView.setText(run);
        }

        btnPickSurah.setText(data.nameOfSurah(surahHere));
        btnPickPage.setText("صفحة " + page);
        updateSaveLabel();
    }

    private TextView surahTitleView(int surah, int chipColor, int accent) {
        TextView tv = new TextView(this);
        tv.setText("سورة " + data.nameOfSurah(surah));
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(accent);
        tv.setTextSize(15f);
        tv.setTypeface(null, Typeface.BOLD); // UI chrome, not the mushaf face — bold is fine here
        tv.setBackgroundColor(chipColor);
        int padH = dp(12), padV = dp(9);
        tv.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView basmalaView(int ink, float baseSize) {
        TextView tv = new TextView(this);
        tv.setTypeface(mushafFace, Typeface.NORMAL);
        tv.setText(data.textOf(1, 1)); // ayah 1:1 IS the basmala, verbatim from the verified text
        tv.setTextColor(ink);
        tv.setTextSize(baseSize * 0.82f);
        tv.setGravity(Gravity.CENTER);
        tv.setLetterSpacing(0f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView ayahRunView(int ink, float textSize) {
        TextView tv = new TextView(this);
        tv.setTypeface(mushafFace, Typeface.NORMAL);   // NORMAL only — never BOLD on the mushaf face
        tv.setTextColor(ink);
        tv.setTextSize(textSize);
        tv.setLetterSpacing(0f);                        // required: no tracking on Quranic text
        tv.setLineSpacing(0f, 1.9f);
        tv.setGravity(Gravity.START);
        tv.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setHighlightColor(0x00000000); // the selected-ayah span draws its own background
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void appendAyah(SpannableStringBuilder sb, int surah, int ayah, int accent, int ink) {
        int start = sb.length();
        sb.append(data.textOf(surah, ayah));
        int textEnd = sb.length();

        boolean selected = selSurah != null && selSurah == surah && selAyah != null && selAyah == ayah;
        if (selected) {
            sb.setSpan(new BackgroundColorSpan(withAlpha(accent, 0x30)), start, textEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        sb.append(" ");
        int markStart = sb.length();
        sb.append(toArabicDigits(ayah));
        int markEnd = sb.length();
        sb.setSpan(new AyahMarkSpan(toArabicDigits(ayah), accent, accent, 1.6f), markStart, markEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(" ");
        int spanEnd = sb.length();

        sb.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                onAyahTapped(surah, ayah);
            }

            @Override
            public void updateDrawState(@NonNull android.text.TextPaint ds) {
                // No underline/color change from the link styling — selection is
                // shown by the background span above, not by link decoration.
                ds.setUnderlineText(false);
            }
        }, start, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void onAyahTapped(int surah, int ayah) {
        boolean same = selSurah != null && selSurah == surah && selAyah != null && selAyah == ayah;
        if (same) {
            selSurah = null;
            selAyah = null;
        } else {
            selSurah = surah;
            selAyah = ayah;
        }
        renderPage();
    }

    private void updateSaveLabel() {
        if (selSurah != null && selAyah != null) {
            btnSave.setText("💾  حفظ عند الآية " + selAyah);
        } else {
            btnSave.setText("💾  حفظ التقدم");
        }
    }

    private void saveAndFinish() {
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT_PAGE, page);
        if (selSurah != null && selAyah != null) {
            result.putExtra(EXTRA_RESULT_HAS_AYAH, true);
            result.putExtra(EXTRA_RESULT_SURAH, (int) selSurah);
            result.putExtra(EXTRA_RESULT_AYAH, (int) selAyah);
        } else {
            result.putExtra(EXTRA_RESULT_HAS_AYAH, false);
        }
        setResult(RESULT_OK, result);
        Toast.makeText(this, "جارٍ حفظ التقدم…", Toast.LENGTH_SHORT).show();
        finish();
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String toArabicDigits(int n) {
        char[] indic = {'٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'};
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(n).toCharArray()) {
            sb.append(Character.isDigit(c) ? indic[c - '0'] : c);
        }
        return sb.toString();
    }
}
