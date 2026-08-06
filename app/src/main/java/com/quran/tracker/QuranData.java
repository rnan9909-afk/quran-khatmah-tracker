package com.quran.tracker;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Page / surah / ayah lookups for the native side (widget + quick-adjust sheet).
 *
 * The WebView owns the reading data, but the widget has to render and step
 * through positions without waiting for the app to open, so it needs the same
 * mushaf map the JS uses. Rather than duplicate the table we parse the very
 * same asset the WebView loads — assets/quran_pages.json, one entry per page:
 *
 *   { "page": 1, "surah_number": 1, "surah_name": "...", "ayah_number": 1, ... }
 *
 * The entry records which surah and ayah a page *starts* with, which is enough
 * to derive everything else: the surahs present on a page, the ayah range each
 * of them covers there, and the first page of any surah.
 */
final class QuranData {

    static final int TOTAL_PAGES = 604;
    static final int TOTAL_SURAHS = 114;

    /**
     * Ayahs per surah — the same table app.js uses as SURAH_AYAH_COUNTS. The
     * per-page asset only records where a page starts, so when a surah *ends*
     * on a page this is the only way to know the last ayah on it.
     */
    private static final int[] AYAH_COUNTS = {
            7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128,
            111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73,
            54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60,
            49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52,
            44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19,
            26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3,
            6, 3, 5, 4, 5, 6
    };

    private static QuranData instance;

    /** One entry per page, index 0 == page 1. */
    private final int[] pageSurah = new int[TOTAL_PAGES];
    private final int[] pageAyah = new int[TOTAL_PAGES];
    private final String[] pageSurahName = new String[TOTAL_PAGES];

    /** Index 0 == surah 1. */
    private final int[] surahFirstPage = new int[TOTAL_SURAHS];
    private final int[] surahLastPage = new int[TOTAL_SURAHS];
    private final String[] surahName = new String[TOTAL_SURAHS];

    private boolean loaded = false;

    static synchronized QuranData get(Context context) {
        if (instance == null) {
            instance = new QuranData();
            instance.load(context.getApplicationContext());
        }
        return instance;
    }

    private QuranData() {
    }

    private void load(Context context) {
        try (InputStream in = context.getAssets().open("quran_pages.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);

            JSONArray arr = new JSONArray(out.toString("UTF-8"));
            for (int i = 0; i < arr.length() && i < TOTAL_PAGES; i++) {
                JSONObject o = arr.getJSONObject(i);
                pageSurah[i] = o.optInt("surah_number", 1);
                pageAyah[i] = o.optInt("ayah_number", 1);
                pageSurahName[i] = cleanName(o.optString("surah_name", ""));
            }

            // First/last page of every surah, derived from the per-page starts.
            for (int s = 0; s < TOTAL_SURAHS; s++) {
                surahFirstPage[s] = 0;
                surahLastPage[s] = 0;
            }
            for (int p = 0; p < TOTAL_PAGES; p++) {
                int s = pageSurah[p] - 1;
                if (s < 0 || s >= TOTAL_SURAHS) continue;
                if (surahFirstPage[s] == 0) {
                    surahFirstPage[s] = p + 1;
                    surahName[s] = pageSurahName[p];
                }
            }
            // A surah runs until the page before the next surah's first page.
            for (int s = 0; s < TOTAL_SURAHS; s++) {
                if (surahFirstPage[s] == 0) continue;
                int next = 0;
                for (int t = s + 1; t < TOTAL_SURAHS; t++) {
                    if (surahFirstPage[t] != 0) { next = surahFirstPage[t]; break; }
                }
                surahLastPage[s] = (next == 0) ? TOTAL_PAGES : Math.max(surahFirstPage[s], next - 1);
            }
            loaded = true;
        } catch (Exception e) {
            // A broken asset must not take the widget down: fall back to a
            // page-only mode where surah/ayah simply read as page 1 of surah 1.
            for (int p = 0; p < TOTAL_PAGES; p++) {
                pageSurah[p] = 1;
                pageAyah[p] = 1;
                pageSurahName[p] = "";
            }
            loaded = false;
        }
    }

    boolean isLoaded() {
        return loaded;
    }

    /**
     * The asset spells names as "سُورَةُ ٱلْفَاتِحَةِ" with full diacritics. The widget
     * has very little room, so drop the leading word and the harakat and keep
     * the bare name — "الفاتحة".
     */
    private static String cleanName(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // Arabic combining marks: harakat, superscript alef, sukun, shadda…
            if ((c >= 0x064B && c <= 0x065F) || c == 0x0670 || c == 0x0640) continue;
            // Alef wasla / hamza variants normalise to plain alef
            if (c == 0x0671) c = 0x0627;
            sb.append(c);
        }
        String out = sb.toString().trim();
        if (out.startsWith("سورة ")) out = out.substring(5).trim();
        return out;
    }

    // ===== Basic lookups =====

    private static int clampPage(int page) {
        if (page < 1) return 1;
        if (page > TOTAL_PAGES) return TOTAL_PAGES;
        return page;
    }

    int surahOfPage(int page) {
        return pageSurah[clampPage(page) - 1];
    }

    String surahNameOfPage(int page) {
        String n = pageSurahName[clampPage(page) - 1];
        return (n == null || n.isEmpty()) ? ("سورة " + surahOfPage(page)) : n;
    }

    String nameOfSurah(int surah) {
        if (surah < 1 || surah > TOTAL_SURAHS) return "";
        String n = surahName[surah - 1];
        return (n == null || n.isEmpty()) ? ("سورة " + surah) : n;
    }

    int firstPageOfSurah(int surah) {
        if (surah < 1 || surah > TOTAL_SURAHS) return 1;
        int p = surahFirstPage[surah - 1];
        return p == 0 ? 1 : p;
    }

    int lastPageOfSurah(int surah) {
        if (surah < 1 || surah > TOTAL_SURAHS) return TOTAL_PAGES;
        int p = surahLastPage[surah - 1];
        return p == 0 ? TOTAL_PAGES : p;
    }

    /** The ayah a page opens with — what the widget shows as "الآية". */
    int firstAyahOfPage(int page) {
        return pageAyah[clampPage(page) - 1];
    }

    /**
     * Last ayah that still falls on this page, for the surah the page starts
     * with. Derived from where the following page begins.
     */
    int lastAyahOfPage(int page) {
        int p = clampPage(page);
        int surah = pageSurah[p - 1];
        int surahEnd = ayahCount(surah);

        if (p == TOTAL_PAGES) return Math.max(firstAyahOfPage(p), surahEnd);

        // The page after this one tells us where this page stops: if it opens
        // in the same surah, this page ends one ayah earlier; otherwise the
        // surah itself ends here.
        if (pageSurah[p] == surah) {
            return Math.max(firstAyahOfPage(p), pageAyah[p] - 1);
        }
        return Math.max(firstAyahOfPage(p), surahEnd);
    }

    private static int ayahCount(int surah) {
        if (surah < 1 || surah > TOTAL_SURAHS) return 1;
        return AYAH_COUNTS[surah - 1];
    }

    // ===== Stepping, used by the widget arrows =====

    /** Page that starts the surah after the one covering {@code page}. */
    int pageOfNextSurah(int page) {
        int s = surahOfPage(page);
        if (s >= TOTAL_SURAHS) return firstPageOfSurah(TOTAL_SURAHS);
        return firstPageOfSurah(s + 1);
    }

    /**
     * Page that starts the previous surah. If the page sits in the middle of a
     * surah the first hop goes back to that surah's own opening page, which is
     * what "previous" means to a reader.
     */
    int pageOfPreviousSurah(int page) {
        int s = surahOfPage(page);
        int start = firstPageOfSurah(s);
        if (page > start) return start;
        if (s <= 1) return firstPageOfSurah(1);
        return firstPageOfSurah(s - 1);
    }

}
