package com.quran.tracker;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Page / surah / ayah data for the native mushaf reader.
 *
 * This exists because the WebView could not be trusted to render the mushaf
 * correctly: @font-face never took effect on that page under any loading
 * strategy tried (linked file, data: URI, FontFace from an ArrayBuffer, from
 * a blob: URL), so the Uthmani text kept falling back to a system face and
 * losing marks that must sit on a specific letter. Reading straight from
 * assets/quran_pages.json and assets/quran_text.json — the same two files
 * the WebView tab used — and drawing with a Typeface loaded through
 * AssetManager sidesteps that failure mode entirely: there is no browser
 * font-loading path left to fail.
 */
final class QuranNativeData {

    static final int TOTAL_PAGES = 604;
    static final int TOTAL_SURAHS = 114;

    static final String[] SURAH_NAMES = {
            "الفاتحة","البقرة","آل عمران","النساء","المائدة","الأنعام","الأعراف","الأنفال","التوبة","يونس",
            "هود","يوسف","الرعد","إبراهيم","الحجر","النحل","الإسراء","الكهف","مريم","طه",
            "الأنبياء","الحج","المؤمنون","النور","الفرقان","الشعراء","النمل","القصص","العنكبوت","الروم",
            "لقمان","السجدة","الأحزاب","سبأ","فاطر","يس","الصافات","ص","الزمر","غافر",
            "فصلت","الشورى","الزخرف","الدخان","الجاثية","الأحقاف","محمد","الفتح","الحجرات","ق",
            "الذاريات","الطور","النجم","القمر","الرحمن","الواقعة","الحديد","المجادلة","الحشر","الممتحنة",
            "الصف","الجمعة","المنافقون","التغابن","الطلاق","التحريم","الملك","القلم","الحاقة","المعارج",
            "نوح","الجن","المزمل","المدثر","القيامة","الإنسان","المرسلات","النبأ","النازعات","عبس",
            "التكوير","الانفطار","المطففين","الانشقاق","البروج","الطارق","الأعلى","الغاشية","الفجر","البلد",
            "الشمس","الليل","الضحى","الشرح","التين","العلق","القدر","البينة","الزلزلة","العاديات",
            "القارعة","التكاثر","العصر","الهمزة","الفيل","قريش","الماعون","الكوثر","الكافرون","النصر",
            "المسد","الإخلاص","الفلق","الناس"
    };

    private static QuranNativeData instance;

    private final int[] pageSurah = new int[TOTAL_PAGES];
    private final int[] pageAyah = new int[TOTAL_PAGES];
    private final int[] pageJuz = new int[TOTAL_PAGES];
    private final int[] pageHizb = new int[TOTAL_PAGES];
    private final int[] surahFirstPage = new int[TOTAL_SURAHS];

    /** ayahText[surah-1][ayah-1], basmala already lifted out where it applies. */
    private String[][] ayahText;

    private boolean loaded = false;

    static synchronized QuranNativeData get(Context context) {
        if (instance == null) {
            instance = new QuranNativeData();
            instance.load(context.getApplicationContext());
        }
        return instance;
    }

    private QuranNativeData() {
    }

    boolean isLoaded() {
        return loaded;
    }

    private static String readAsset(Context context, String name) throws Exception {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private void load(Context context) {
        try {
            JSONArray pages = new JSONArray(readAsset(context, "quran_pages.json"));
            for (int i = 0; i < pages.length() && i < TOTAL_PAGES; i++) {
                JSONObject o = pages.getJSONObject(i);
                pageSurah[i] = o.optInt("surah_number", 1);
                pageAyah[i] = o.optInt("ayah_number", 1);
                pageJuz[i] = o.optInt("juz", 1);
                pageHizb[i] = o.optInt("hizb", 1);
            }
            for (int s = 0; s < TOTAL_SURAHS; s++) surahFirstPage[s] = 0;
            for (int p = 0; p < TOTAL_PAGES; p++) {
                int s = pageSurah[p] - 1;
                if (s >= 0 && s < TOTAL_SURAHS && surahFirstPage[s] == 0) surahFirstPage[s] = p + 1;
            }

            JSONArray text = new JSONArray(readAsset(context, "quran_text.json"));
            ayahText = new String[TOTAL_SURAHS][];
            for (int s = 0; s < text.length() && s < TOTAL_SURAHS; s++) {
                JSONArray ayahs = text.getJSONArray(s);
                String[] arr = new String[ayahs.length()];
                for (int a = 0; a < ayahs.length(); a++) arr[a] = ayahs.getString(a);
                ayahText[s] = arr;
            }
            loaded = true;
        } catch (Exception e) {
            loaded = false;
        }
    }

    private static int clampPage(int page) {
        return Math.max(1, Math.min(TOTAL_PAGES, page));
    }

    int surahOfPage(int page) {
        return pageSurah[clampPage(page) - 1];
    }

    int juzOfPage(int page) {
        return pageJuz[clampPage(page) - 1];
    }

    int hizbOfPage(int page) {
        return pageHizb[clampPage(page) - 1];
    }

    int firstPageOfSurah(int surah) {
        if (surah < 1 || surah > TOTAL_SURAHS) return 1;
        int p = surahFirstPage[surah - 1];
        return p == 0 ? 1 : p;
    }

    String nameOfSurah(int surah) {
        return (surah >= 1 && surah <= TOTAL_SURAHS) ? SURAH_NAMES[surah - 1] : String.valueOf(surah);
    }

    String textOf(int surah, int ayah) {
        if (!loaded || surah < 1 || surah > TOTAL_SURAHS) return "";
        String[] arr = ayahText[surah - 1];
        if (arr == null || ayah < 1 || ayah > arr.length) return "";
        return arr[ayah - 1];
    }

    int ayahCountOf(int surah) {
        if (!loaded || surah < 1 || surah > TOTAL_SURAHS) return 0;
        String[] arr = ayahText[surah - 1];
        return arr == null ? 0 : arr.length;
    }

    /** One entry per ayah printed on the page, in order. */
    static final class AyahRef {
        final int surah, ayah;
        final boolean first;

        AyahRef(int surah, int ayah, boolean first) {
            this.surah = surah;
            this.ayah = ayah;
            this.first = first;
        }
    }

    List<AyahRef> ayahsOnPage(int page) {
        List<AyahRef> out = new ArrayList<>();
        if (!loaded) return out;
        int p = clampPage(page);
        int s = pageSurah[p - 1];
        int a = pageAyah[p - 1];
        Integer stopSurah = (p < TOTAL_PAGES) ? pageSurah[p] : null;
        Integer stopAyah = (p < TOTAL_PAGES) ? pageAyah[p] : null;

        while (s <= TOTAL_SURAHS && out.size() < 400) {
            if (stopSurah != null && s == stopSurah && a == stopAyah) break;
            int count = ayahCountOf(s);
            if (count == 0) break;
            if (a > count) {
                s++;
                a = 1;
                continue;
            }
            out.add(new AyahRef(s, a, a == 1));
            a++;
        }
        return out;
    }
}
