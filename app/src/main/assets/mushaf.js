/* ============================================================
   Mushaf reader
   ------------------------------------------------------------
   Page-accurate: quran_pages.js records which ayah opens each of
   the 604 pages, so a page's contents are every ayah from its own
   start up to the next page's start. The text is reflowed to the
   device width rather than set line-for-line — we have the page
   boundaries, not the Madani line breaks — so a page here holds
   exactly the ayahs of the printed page, wrapped to fit.

   Loaded after app.js, and leans on its state deliberately:
   recordProgress(), getPageSurahs() and the home selectors are
   reused so a reading saved here is indistinguishable from one
   logged on the home card.
   ============================================================ */

let mushafPage = 1;
let mushafSelected = null;     // {surah, ayah} the reader tapped, or null
let mushafFontStep = 3;        // index into MUSHAF_FONT_SIZES
let mushafNight = false;
let mushafBuilt = false;

// Step 3 is the default. Sized so a line carries enough words that
// justification does not have to stretch the gaps to fill it.
const MUSHAF_FONT_SIZES = [1.05, 1.20, 1.35, 1.55, 1.80, 2.05, 2.35]; // rem

/** Every ayah printed on a page, in order. */
function mushafPageAyahs(page) {
    if (typeof quranTextData === 'undefined' || !quranPages.length) return [];
    const start = quranPages[page - 1];
    const next = quranPages[page];            // undefined on the last page
    if (!start) return [];

    const out = [];
    let s = start.surah_number;
    let a = start.ayah_number;

    while (s <= 114 && out.length < 400) {
        if (next && s === next.surah_number && a === next.ayah_number) break;
        const surah = quranTextData[s - 1];
        if (!surah) break;
        if (a > surah.length) { s++; a = 1; continue; }   // roll into the next surah
        out.push({ surah: s, ayah: a, text: surah[a - 1], first: a === 1 });
        a++;
    }
    return out;
}

/** Arabic-Indic digits, as a mushaf prints them. */
function toArabicDigits(n) {
    return String(n).replace(/[0-9]/g, d => '٠١٢٣٤٥٦٧٨٩'[Number(d)]);
}

function mushafRender() {
    const head = document.getElementById('mushaf-page-head');
    const body = document.getElementById('mushaf-text');
    if (!body) return;

    const ayahs = mushafPageAyahs(mushafPage);
    const info = quranPages[mushafPage - 1] || {};

    // Running head in mushaf order: juz on one side, page in the middle,
    // surah on the other — the same three marks a printed page carries.
    if (head) {
        const surahNo = (ayahs[0] && ayahs[0].surah)
            || info.surah_number || 1;
        head.innerHTML = '';
        const part = (txt) => {
            const el = document.createElement('span');
            el.innerText = txt;
            head.appendChild(el);
        };
        part('الجزء ' + toArabicDigits(info.juz || 1));
        part(toArabicDigits(mushafPage));
        part(toArabicDigits(surahNo) + ' ' + (SURAH_NAMES[surahNo - 1] || ''));
    }

    body.innerHTML = '';
    let run = null;

    ayahs.forEach(item => {
        if (item.first) {
            run = null;                       // a new surah breaks the text run
            const title = document.createElement('div');
            title.className = 'mushaf-surah-title';
            title.innerText = 'سورة ' + (SURAH_NAMES[item.surah - 1] || item.surah);
            body.appendChild(title);
            // Al-Fatiha counts the basmala as its own first ayah and At-Tawbah
            // has none; every other surah shows it as a heading.
            if (item.surah !== 1 && item.surah !== 9) {
                const b = document.createElement('div');
                b.className = 'mushaf-basmala';
                b.innerText = 'بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ';
                body.appendChild(b);
            }
        }
        if (!run) {
            run = document.createElement('p');
            run.className = 'mushaf-run';
            body.appendChild(run);
        }

        const span = document.createElement('span');
        span.className = 'mushaf-ayah';
        span.dataset.surah = item.surah;
        span.dataset.ayah = item.ayah;
        if (mushafSelected && mushafSelected.surah === item.surah
            && mushafSelected.ayah === item.ayah) {
            span.classList.add('selected');
        }
        // U+06DD END OF AYAH takes the following digits inside its ornament,
        // so the mushaf font draws the real marker — no CSS circle needed.
        span.appendChild(
            document.createTextNode(item.text + ' ۝' + toArabicDigits(item.ayah) + ' '));
        run.appendChild(span);
    });

    const surahHere = (ayahs[0] && ayahs[0].surah) || info.surah_number || 1;
    const lbl = document.getElementById('mushaf-surah-label');
    if (lbl) lbl.innerText = SURAH_NAMES[surahHere - 1] || '';
    const pg = document.getElementById('mushaf-page-label');
    if (pg) pg.innerText = mushafPage;

    mushafUpdateSaveLabel();
}

function mushafUpdateSaveLabel() {
    const el = document.getElementById('mushaf-save-label');
    if (!el) return;
    el.innerText = mushafSelected
        ? 'حفظ عند الآية ' + mushafSelected.ayah
        : 'حفظ التقدم';
}

function mushafGoTo(page, keepSelection) {
    const p = Math.max(1, Math.min(604, page));
    mushafPage = p;
    if (!keepSelection) mushafSelected = null;
    mushafRender();
    const surface = document.getElementById('mushaf-surface');
    if (surface) surface.scrollTop = 0;
}

function mushafApplyFont() {
    const body = document.getElementById('mushaf-text');
    if (body) body.style.fontSize = MUSHAF_FONT_SIZES[mushafFontStep - 1] + 'rem';
    localStorage.setItem('quran_mushaf_font', String(mushafFontStep));
}

function mushafApplyNight() {
    const pane = document.getElementById('tab-mushaf');
    if (pane) pane.classList.toggle('night', mushafNight);
    const btn = document.getElementById('mushaf-night-toggle');
    if (btn) btn.classList.toggle('on', mushafNight);
    localStorage.setItem('quran_mushaf_night', mushafNight ? '1' : '0');
}

/**
 * Write the reader's position into the tracker.
 *
 * Goes through the same recordProgress() the home card uses, so the day's
 * count, the streak and the goals move exactly as they would there. Then the
 * home surah/ayah selectors are steered onto the specific ayah tapped, which
 * is what makes "توقفت عند هذه الآية" show up on the home screen.
 */
function mushafSaveProgress() {
    const page = mushafPage;
    recordProgress(page);

    if (mushafSelected) {
        // recordProgress() has just rebuilt the selectors for this page; point
        // them at the chosen ayah instead of the page's first.
        surahAyahPage = page;
        pageSurahsCache = getPageSurahs(page);
        const idx = pageSurahsCache.findIndex(x => x.surah === mushafSelected.surah);
        selectedSurahIdx = idx >= 0 ? idx : 0;
        const range = pageSurahsCache[selectedSurahIdx];
        selectedAyah = range
            ? Math.min(Math.max(mushafSelected.ayah, range.first), range.last)
            : mushafSelected.ayah;
        updateSurahAyahDisplay();
        saveStateToStorage();
    }

    const where = mushafSelected
        ? 'صفحة ' + page + ' • ' + SURAH_NAMES[mushafSelected.surah - 1] + ' ' + mushafSelected.ayah
        : 'صفحة ' + page;
    showAndroidToast('تم حفظ التقدم — ' + where);
}

function setupMushaf() {
    if (mushafBuilt) return;
    mushafBuilt = true;

    const savedFont = parseInt(localStorage.getItem('quran_mushaf_font') || '3', 10);
    mushafFontStep = (savedFont >= 1 && savedFont <= MUSHAF_FONT_SIZES.length) ? savedFont : 3;
    mushafNight = localStorage.getItem('quran_mushaf_night') === '1';

    const body = document.getElementById('mushaf-text');
    if (body) {
        body.addEventListener('click', (e) => {
            const span = e.target.closest('.mushaf-ayah');
            if (!span) return;
            const s = parseInt(span.dataset.surah, 10);
            const a = parseInt(span.dataset.ayah, 10);
            // Tapping the selected ayah again clears the choice.
            if (mushafSelected && mushafSelected.surah === s && mushafSelected.ayah === a) {
                mushafSelected = null;
            } else {
                mushafSelected = { surah: s, ayah: a };
            }
            mushafRender();
        });
    }

    const on = (id, fn) => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('click', fn);
    };

    on('mushaf-prev', () => mushafGoTo(mushafPage - 1));
    on('mushaf-next', () => mushafGoTo(mushafPage + 1));
    on('mushaf-save', mushafSaveProgress);

    on('mushaf-font-inc', () => {
        if (mushafFontStep < MUSHAF_FONT_SIZES.length) { mushafFontStep++; mushafApplyFont(); }
    });
    on('mushaf-font-dec', () => {
        if (mushafFontStep > 1) { mushafFontStep--; mushafApplyFont(); }
    });
    on('mushaf-night-toggle', () => { mushafNight = !mushafNight; mushafApplyNight(); });

    on('mushaf-pick-surah', () => {
        const items = SURAH_NAMES.map((n, i) => ({ value: i + 1, label: (i + 1) + '. ' + n }));
        const current = (quranPages[mushafPage - 1] || {}).surah_number || 1;
        openPicker('اختر السورة', items, current, (surah) => {
            const first = quranPages.findIndex(p => p.surah_number === surah);
            mushafGoTo(first >= 0 ? first + 1 : 1);
        });
    });

    on('mushaf-pick-page', () => {
        const items = [];
        for (let p = 1; p <= 604; p++) {
            const inf = quranPages[p - 1] || {};
            items.push({ value: p, label: p + ' — ' + SURAH_NAMES[(inf.surah_number || 1) - 1] });
        }
        openPicker('اذهب إلى صفحة', items, mushafPage, (p) => mushafGoTo(p));
    });

    mushafApplyFont();
    mushafApplyNight();
}

/** Opening the tab lands the reader wherever the tracker says they are. */
function openMushafTab() {
    setupMushaf();
    mushafGoTo(currentPage, false);
}
