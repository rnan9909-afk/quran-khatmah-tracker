// Helper to get local date string in YYYY-MM-DD format (prevents timezone rollover bugs)
function getLocalDateString(d = new Date()) {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

// Curated gradients for Khatmah cards
// Names of the 114 surahs (index 0 = surah 1)
const SURAH_NAMES = ["الفاتحة","البقرة","آل عمران","النساء","المائدة","الأنعام","الأعراف","الأنفال","التوبة","يونس","هود","يوسف","الرعد","إبراهيم","الحجر","النحل","الإسراء","الكهف","مريم","طه","الأنبياء","الحج","المؤمنون","النور","الفرقان","الشعراء","النمل","القصص","العنكبوت","الروم","لقمان","السجدة","الأحزاب","سبأ","فاطر","يس","الصافات","ص","الزمر","غافر","فصلت","الشورى","الزخرف","الدخان","الجاثية","الأحقاف","محمد","الفتح","الحجرات","ق","الذاريات","الطور","النجم","القمر","الرحمن","الواقعة","الحديد","المجادلة","الحشر","الممتحنة","الصف","الجمعة","المنافقون","التغابن","الطلاق","التحريم","الملك","القلم","الحاقة","المعارج","نوح","الجن","المزمل","المدثر","القيامة","الإنسان","المرسلات","النبأ","النازعات","عبس","التكوير","الانفطار","المطففين","الانشقاق","البروج","الطارق","الأعلى","الغاشية","الفجر","البلد","الشمس","الليل","الضحى","الشرح","التين","العلق","القدر","البينة","الزلزلة","العاديات","القارعة","التكاثر","العصر","الهمزة","الفيل","قريش","الماعون","الكوثر","الكافرون","النصر","المسد","الإخلاص","الفلق","الناس"];

// Total number of ayahs in each of the 114 surahs (Hafs numbering)
const SURAH_AYAH_COUNTS = [7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6];

// All khatmahs now use the app's dark-green theme color (no per-khatmah colors).
const THEME_KHATMAH_GRADIENT =
    'radial-gradient(120% 120% at 85% 0%, rgba(171,232,63,0.18), transparent 55%), ' +
    'linear-gradient(150deg, #2C3A18 0%, #1A220F 55%, #141813 100%)';

// Application State
let quranPages = [];
let readingHistory = [];    // Format: { date: 'YYYY-MM-DD', count: number }
let completedKhatmahs = []; // Format: { id: string, startDate: string, endDate: string, daysTaken: number }

// Multiple Active Khatmahs State
let activeKhatmahs = [];
let activeKhatmahId = '';
let selectedKhatmahSettingsId = '';

// Shortcut variables (synced to the current active Khatmah)
let currentPage = 1;
let startPage = 1; // Start page of the current active Khatmah
let dailyGoal = 10;
let khatmahStartDate = '';

// Calendar month and year state
let currentCalendarMonth = new Date().getMonth();
let currentCalendarYear = new Date().getFullYear();

// Streak Stats
let currentStreak = 0;
let maxStreak = 0;

// Weekly makeup (تعويض): compensated days + the week the last makeup was used
let makeupDays = [];        // ['YYYY-MM-DD', ...] days marked as compensated
let lastMakeupWeek = '';    // Sunday date string of the week the makeup was last used
let makeupAppliedDate = '';  // the day the makeup was performed (its goal is doubled)

// DOM Elements
const elCurrentPageVal = document.getElementById('current-page-val');
const elJuzVal = document.getElementById('juz-val');
const elHizbVal = document.getElementById('hizb-val');
const elReadPagesVal = document.getElementById('read-pages-val');
const elRemainingPagesVal = document.getElementById('remaining-pages-val');
const elCompletionProgressBar = document.getElementById('completion-progress-bar');
const elCompletionPercentageVal = document.getElementById('completion-percentage-val');

// Plan Status (where you should be now)
const elPlanExpectedPage = document.getElementById('plan-expected-page');
const elPlanExpectedSurah = document.getElementById('plan-expected-surah');
const elPlanStatusBadge = document.getElementById('plan-status-badge');
const elPlanStatusText = document.getElementById('plan-status-text');
const elPlanStatusIcon = document.getElementById('plan-status-icon');

// Navigation Tabs
const tabPanes = document.querySelectorAll('.tab-pane');
const navItems = document.querySelectorAll('.nav-item');

// level and Streak badges
const elUserLevelName = document.getElementById('user-level-name');
const elUserStreakVal = document.getElementById('user-streak-val');

// Finish Khatmah Button
const elBtnFinishKhatmah = document.getElementById('btn-finish-khatmah');

// Heatmap Calendar Tab Elements
const elHeatmapGrid = document.getElementById('heatmap-grid');
const elCalCurrentStreak = document.getElementById('cal-current-streak');
const elCalMaxStreak = document.getElementById('cal-max-streak');
const elCalTotalDays = document.getElementById('cal-total-days');

// Achievements Elements
const elRecFastestVal = document.getElementById('rec-fastest-val');
const elRecTotalKhatmahs = document.getElementById('rec-total-khatmahs');
const elKhatmahsList = document.getElementById('khatmahs-list');
const elEmptyKhatmahsMsg = document.getElementById('empty-khatmahs-msg');

// Settings / Goals Elements
const elEstimatedDateVal = document.getElementById('estimated-date-val');
const elDailyAvgVal = document.getElementById('daily-avg-val');
const elGoalTargetVal = document.getElementById('goal-target-val');
const elGoalTodayVal = document.getElementById('goal-today-val');
const elGoalRemainingVal = document.getElementById('goal-remaining-val');

// Backup Elements
const elFileImportInput = document.getElementById('file-import-input');

// Settings Elements (Time picker and Delete button)
const elNotificationSwitch = document.getElementById('notification-switch');
const elTimeHour = document.getElementById('time-hour');
const elTimeMinute = document.getElementById('time-minute');
const elTimePickerContainer = document.getElementById('time-picker-container');
const elBtnDeleteActiveKhatmah = document.getElementById('btn-delete-active-khatmah');

// Floating Quran Button Elements
const elFloatingBtnCard = document.getElementById('floating-btn-card');
const elFloatingBtnSwitch = document.getElementById('floating-btn-switch');
const elFloatingQuranBtn = document.getElementById('floating-quran-btn');
const elQuranAppSelect = document.getElementById('quran-app-select');

// Modals
const modalPage = document.getElementById('modal-page');
const modalGoal = document.getElementById('modal-goal');
const elInputPageNumber = document.getElementById('input-page-number');
const elInputGoalNumber = document.getElementById('input-goal-number');
const elPageErrorMsg = document.getElementById('page-error-msg');
const elGoalErrorMsg = document.getElementById('goal-error-msg');

const modalAddKhatmah = document.getElementById('modal-add-khatmah');
const elInputKhatmahName = document.getElementById('input-khatmah-name');
const elInputKhatmahGoal = document.getElementById('input-khatmah-goal');
const elKhatmahAddError = document.getElementById('khatmah-add-error');

// Multiple Khatmahs Dropdown Selector
const elKhatmahSelect = document.getElementById('khatmah-select');

// Home Tab Statistics Elements
const elStatSurahsVal = document.getElementById('stat-surahs-val');
const elStatAyahsVal = document.getElementById('stat-ayahs-val');
const elStatLettersVal = document.getElementById('stat-letters-val');

// Initialize App
document.addEventListener('DOMContentLoaded', async () => {
    setupNavigation();
    loadStateFromStorage();
    await loadQuranDatabase();
    setupEventListeners();
    initNotificationSettings();
    initFloatingButtonSettings();
    initUpdateSystem();
    initPullToRefresh();
    
    // Process streaks and render UI
    refreshCalculations();
});

// Setup Navigation
function setupNavigation() {
    navItems.forEach(item => {
        item.addEventListener('click', () => {
            const targetTab = item.getAttribute('data-tab');
            
            navItems.forEach(n => n.classList.remove('active'));
            tabPanes.forEach(p => p.classList.remove('active'));
            
            item.classList.add('active');
            document.getElementById(targetTab).classList.add('active');
            
            // Re-render calendar when calendar tab is active to ensure proper layout sizing
            if (targetTab === 'tab-calendar') {
                renderCalendar();
            }
        });
    });
}

// Load Persistent State
function loadStateFromStorage() {
    try {
        readingHistory = JSON.parse(localStorage.getItem('quran_reading_history') || '[]');
    } catch(e) {
        readingHistory = [];
    }

    try {
        completedKhatmahs = JSON.parse(localStorage.getItem('quran_khatmahs') || '[]');
    } catch(e) {
        completedKhatmahs = [];
    }

    try {
        makeupDays = JSON.parse(localStorage.getItem('quran_makeup_days') || '[]');
    } catch(e) {
        makeupDays = [];
    }
    lastMakeupWeek = localStorage.getItem('quran_last_makeup_week') || '';
    makeupAppliedDate = localStorage.getItem('quran_makeup_applied_date') || '';

    // Load active Khatmahs list
    try {
        activeKhatmahs = JSON.parse(localStorage.getItem('quran_active_khatmahs') || '[]');
        // Reset any page 100 stuck values to 1
        activeKhatmahs.forEach(k => {
            if (k.currentPage === 100) {
                k.currentPage = 1;
            }
        });
    } catch(e) {
        activeKhatmahs = [];
    }

    activeKhatmahId = localStorage.getItem('quran_active_khatmah_id') || '';

    // Migration / Default initialization
    if (activeKhatmahs.length === 0) {
        let oldPage = parseInt(localStorage.getItem('quran_current_page') || '1');
        if (oldPage === 100) oldPage = 1;
        const oldGoal = parseInt(localStorage.getItem('quran_daily_goal') || '10');
        const oldStart = localStorage.getItem('quran_khatmah_start_date') || new Date().toISOString();

        activeKhatmahs = [{
            id: 'khatmah_default',
            name: 'الختمة الأساسية',
            currentPage: oldPage,
            startPage: 1,
            dailyGoal: oldGoal,
            startDate: oldStart
        }];
        activeKhatmahId = 'khatmah_default';
    }

    bindActiveKhatmah();
}

function bindActiveKhatmah() {
    let activeKhatmah = activeKhatmahs.find(k => k.id === activeKhatmahId);
    if (!activeKhatmah) {
        activeKhatmah = activeKhatmahs[0];
        activeKhatmahId = activeKhatmah.id;
    }

    currentPage = activeKhatmah.currentPage;
    startPage = activeKhatmah.startPage || 1;
    dailyGoal = activeKhatmah.dailyGoal;
    khatmahStartDate = activeKhatmah.startDate;

    updateKhatmahSelectorOptions();
}

function updateKhatmahSelectorOptions() {
    const grid = document.getElementById('khatmahs-active-grid');
    if (!grid) return;
    grid.innerHTML = '';
    
    activeKhatmahs.forEach((k, idx) => {
        const card = document.createElement('div');
        const isActive = k.id === activeKhatmahId;
        card.className = `khatmah-select-card-item ${isActive ? 'active' : ''}`;
        
        // All khatmahs share the app's theme color
        card.style.background = THEME_KHATMAH_GRADIENT;
        
        const progressPct = ((k.currentPage / 604) * 100).toFixed(0);
        
        card.innerHTML = `
            <div class="khatmah-card-header" style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                <span class="khatmah-card-name" style="max-width: 95px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${k.name}</span>
                <div style="display: flex; align-items: center; gap: 4px;">
                    ${isActive ? `
                        <span class="active-badge-dot" style="margin-left: 2px;">
                            <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="4">
                                <polyline points="20 6 9 17 4 12"></polyline>
                            </svg>
                        </span>
                    ` : ''}
                    <button class="khatmah-card-settings-btn" data-id="${k.id}" style="background: rgba(255,255,255,0.18); border: none; border-radius: 50%; width: 22px; height: 22px; cursor: pointer; display: flex; align-items: center; justify-content: center; color: white; transition: background 0.2s;" aria-label="إعدادات الختمة">
                        <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="12" cy="12" r="3"></circle>
                            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
                        </svg>
                    </button>
                </div>
            </div>
            <div class="khatmah-card-body" style="display: flex; flex-direction: column; gap: 2px; align-items: stretch; width: 100%;">
                <div style="display: flex; justify-content: space-between; align-items: baseline;">
                    <div class="khatmah-card-page">صفحة <span class="num">${k.currentPage}</span></div>
                    <div class="khatmah-card-progress">${progressPct}%</div>
                </div>
                <div class="khatmah-card-goal-info" style="font-size: 0.65rem; opacity: 0.9; display: inline-flex; align-items: center; gap: 4px; cursor: pointer; text-decoration: underline; background: rgba(255,255,255,0.12); padding: 2px 6px; border-radius: 8px; width: fit-content; margin-top: 3px;">
                    <svg viewBox="0 0 24 24" width="9" height="9" fill="none" stroke="currentColor" stroke-width="3" style="display: inline-block;">
                        <circle cx="12" cy="12" r="10"></circle>
                        <polyline points="12 6 12 12 16 14"></polyline>
                    </svg>
                    <span>الورد: ${k.dailyGoal} صفحة</span>
                </div>
            </div>
        `;
        
        card.addEventListener('click', () => {
            if (activeKhatmahId !== k.id) {
                activeKhatmahId = k.id;
                bindActiveKhatmah();
                saveStateToStorage();
                refreshCalculations();
                showAndroidToast("تم تغيير الختمة النشطة");
            }
        });

        const goalInfoEl = card.querySelector('.khatmah-card-goal-info');
        if (goalInfoEl) {
            goalInfoEl.addEventListener('click', (e) => {
                e.stopPropagation();
                if (activeKhatmahId !== k.id) {
                    activeKhatmahId = k.id;
                    bindActiveKhatmah();
                    saveStateToStorage();
                    refreshCalculations();
                    showAndroidToast("تم تغيير الختمة النشطة");
                }
                openGoalModal();
            });
        }

        const settingsBtn = card.querySelector('.khatmah-card-settings-btn');
        if (settingsBtn) {
            settingsBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                openKhatmahSettingsModal(k.id);
            });
        }
        
        grid.appendChild(card);
    });

    if (elBtnDeleteActiveKhatmah) {
        if (activeKhatmahs.length > 1) {
            elBtnDeleteActiveKhatmah.removeAttribute('disabled');
            elBtnDeleteActiveKhatmah.style.opacity = '1';
        } else {
            elBtnDeleteActiveKhatmah.setAttribute('disabled', 'true');
            elBtnDeleteActiveKhatmah.style.opacity = '0.5';
        }
    }
}

// Save State Helper
function saveStateToStorage() {
    const activeKhatmah = activeKhatmahs.find(k => k.id === activeKhatmahId);
    if (activeKhatmah) {
        activeKhatmah.currentPage = currentPage;
        activeKhatmah.startPage = startPage;
        activeKhatmah.dailyGoal = dailyGoal;
        activeKhatmah.startDate = khatmahStartDate;
    }

    localStorage.setItem('quran_active_khatmahs', JSON.stringify(activeKhatmahs));
    localStorage.setItem('quran_active_khatmah_id', activeKhatmahId);
    localStorage.setItem('quran_reading_history', JSON.stringify(readingHistory));
    localStorage.setItem('quran_khatmahs', JSON.stringify(completedKhatmahs));
    localStorage.setItem('quran_makeup_days', JSON.stringify(makeupDays));
    localStorage.setItem('quran_last_makeup_week', lastMakeupWeek);
    localStorage.setItem('quran_makeup_applied_date', makeupAppliedDate);

    // Backward compatibility for native code
    localStorage.setItem('quran_current_page', currentPage.toString());
    localStorage.setItem('quran_daily_goal', dailyGoal.toString());
    localStorage.setItem('quran_khatmah_start_date', khatmahStartDate);
}

// Load Quran Pages Database from JSON
async function loadQuranDatabase() {
    if (typeof quranPagesData !== 'undefined') {
        quranPages = quranPagesData;
    } else {
        console.warn('quranPagesData global variable not found, using empty fallback');
        // Fallback static data
        quranPages = Array(604).fill(null).map((_, idx) => ({
            page: idx + 1,
            surah_number: 1,
            surah_name: "تحميل...",
            ayah_number: 1,
            juz: 1,
            hizb: 1
        }));
    }
}

// Open Page update modal
function openPageModal() {
    elInputPageNumber.value = currentPage;
    elPageErrorMsg.style.display = 'none';
    modalPage.classList.add('open');
    elInputPageNumber.focus();
}

// Open Goal update modal
function openGoalModal() {
    elInputGoalNumber.value = dailyGoal;
    elGoalErrorMsg.style.display = 'none';
    modalGoal.classList.add('open');
    elInputGoalNumber.focus();
}

// Open Khatmah Settings modal
function openKhatmahSettingsModal(khatmahId) {
    const k = activeKhatmahs.find(item => item.id === khatmahId);
    if (!k) return;
    
    selectedKhatmahSettingsId = khatmahId;

    document.getElementById('edit-khatmah-name').value = k.name;
    document.getElementById('edit-khatmah-goal').value = k.dailyGoal;

    const editStartDate = document.getElementById('edit-khatmah-start-date');
    if (editStartDate) {
        editStartDate.value = k.startDate ? k.startDate.split('T')[0] : getLocalDateString();
    }

    // Check if we can delete this Khatmah (only if total active > 1)
    const deleteBtn = document.getElementById('btn-delete-selected-khatmah');
    if (activeKhatmahs.length > 1) {
        deleteBtn.removeAttribute('disabled');
        deleteBtn.style.opacity = '1';
    } else {
        deleteBtn.setAttribute('disabled', 'true');
        deleteBtn.style.opacity = '0.5';
    }
    
    document.getElementById('modal-khatmah-settings').classList.add('open');
}

// Set up UI Event Listeners
function setupEventListeners() {
    const elBtnUpdatePage = document.getElementById('btn-update-page');
    if (elBtnUpdatePage) elBtnUpdatePage.addEventListener('click', openPageModal);
    const elBtnCardUpdate = document.getElementById('btn-card-update');
    if (elBtnCardUpdate) {
        elBtnCardUpdate.addEventListener('click', openPageModal);
    }

    // Close Page modal
    document.getElementById('btn-close-page-modal').addEventListener('click', () => {
        modalPage.classList.remove('open');
    });

    // Save Page Progress
    document.getElementById('btn-save-page').addEventListener('click', () => {
        const val = parseInt(elInputPageNumber.value);
        if (isNaN(val) || val < 1 || val > 604) {
            elPageErrorMsg.style.display = 'block';
            return;
        }

        recordProgress(val);
        modalPage.classList.remove('open');
        showAndroidToast("تم حفظ الصفحة بنجاح");
    });

    // Finish Khatmah Event
    elBtnFinishKhatmah.addEventListener('click', async () => {
        await finishKhatmah();
    });

    const elBtnChangeGoal = document.getElementById('btn-change-goal');
    if (elBtnChangeGoal) {
        elBtnChangeGoal.addEventListener('click', openGoalModal);
    }
    
    const elBtnEditGoalTrigger = document.getElementById('btn-edit-goal-trigger');
    if (elBtnEditGoalTrigger) {
        elBtnEditGoalTrigger.addEventListener('click', openGoalModal);
    }
    
    const elBlueCardGoalVal = document.getElementById('blue-card-goal-val');
    if (elBlueCardGoalVal) {
        elBlueCardGoalVal.addEventListener('click', openGoalModal);
    }

    // Close Goal modal
    document.getElementById('btn-close-goal-modal').addEventListener('click', () => {
        modalGoal.classList.remove('open');
    });

    // Save Goal
    document.getElementById('btn-save-goal').addEventListener('click', () => {
        const val = parseInt(elInputGoalNumber.value);
        if (isNaN(val) || val < 1) {
            elGoalErrorMsg.style.display = 'block';
            return;
        }

        dailyGoal = val;
        saveStateToStorage();
        refreshCalculations();
        modalGoal.classList.remove('open');
        showAndroidToast("تم تحديث الهدف اليومي");
    });

    // Settings Toggle
    elNotificationSwitch.addEventListener('change', () => {
        if (elNotificationSwitch.checked) {
            elTimePickerContainer.classList.remove('disabled');
            requestAndroidNotificationPermission();
        } else {
            elTimePickerContainer.classList.add('disabled');
        }
    });

    // Floating Quran Button toggle
    if (elFloatingBtnSwitch) {
        elFloatingBtnSwitch.addEventListener('change', () => {
            const enabled = elFloatingBtnSwitch.checked;
            if (typeof Android !== 'undefined' && Android.setFloatingButtonEnabled) {
                Android.setFloatingButtonEnabled(enabled);
            }
            updateFloatingButtonVisibility(enabled);
            showAndroidToast(enabled ? "تم تفعيل زر القرآن العائم" : "تم إيقاف زر القرآن العائم");
        });
    }

    // Target app picker
    if (elQuranAppSelect) {
        elQuranAppSelect.addEventListener('change', () => {
            if (typeof Android !== 'undefined' && Android.setSelectedQuranApp) {
                Android.setSelectedQuranApp(elQuranAppSelect.value);
            }
            showAndroidToast(elQuranAppSelect.value ? "تم تحديد التطبيق" : "تم إلغاء التحديد");
        });
    }

    // Floating Quran Button tap -> open the Quran app with the floating return button
    if (elFloatingQuranBtn) {
        elFloatingQuranBtn.addEventListener('click', () => {
            if (typeof Android !== 'undefined' && Android.launchQuranWithFloatingButton) {
                Android.launchQuranWithFloatingButton(currentPage);
            } else {
                showAndroidToast("هذه الميزة متاحة داخل التطبيق فقط");
            }
        });
    }

    // Themed surah/ayah pickers on the current-page card
    const elSurahPicker = document.getElementById('surah-picker');
    if (elSurahPicker) elSurahPicker.addEventListener('click', openSurahPicker);
    const elAyahPicker = document.getElementById('ayah-picker');
    if (elAyahPicker) elAyahPicker.addEventListener('click', openAyahPicker);

    const elClosePicker = document.getElementById('btn-close-picker');
    if (elClosePicker) {
        elClosePicker.addEventListener('click', () => {
            document.getElementById('modal-picker').classList.remove('open');
        });
    }
    const elPickerModal = document.getElementById('modal-picker');
    if (elPickerModal) {
        elPickerModal.addEventListener('click', (e) => {
            if (e.target === elPickerModal) elPickerModal.classList.remove('open');
        });
    }

    // Save Settings Button
    document.getElementById('btn-save-settings').addEventListener('click', () => {
        const enabled = elNotificationSwitch.checked;
        const hour = parseInt(elTimeHour.value);
        const minute = parseInt(elTimeMinute.value);
        saveNotificationSettings(enabled, hour, minute);
    });

    // Export Data Button
    document.getElementById('btn-export-data').addEventListener('click', () => {
        exportBackupData();
    });

    // Import Data Trigger Button
    document.getElementById('btn-import-data-trigger').addEventListener('click', () => {
        if (typeof Android !== 'undefined' && Android.importData) {
            Android.importData();
        } else {
            elFileImportInput.click(); // web fallback
        }
    });

    // Import File Selector Change
    elFileImportInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) {
            importBackupData(file);
        }
    });

    // --- Calendar Navigation Event Listeners ---
    const btnCalPrev = document.getElementById('btn-cal-prev');
    if (btnCalPrev) {
        btnCalPrev.addEventListener('click', () => {
            currentCalendarMonth--;
            if (currentCalendarMonth < 0) {
                currentCalendarMonth = 11;
                currentCalendarYear--;
            }
            renderCalendar();
        });
    }

    const btnCalNext = document.getElementById('btn-cal-next');
    if (btnCalNext) {
        btnCalNext.addEventListener('click', () => {
            currentCalendarMonth++;
            if (currentCalendarMonth > 11) {
                currentCalendarMonth = 0;
                currentCalendarYear++;
            }
            renderCalendar();
        });
    }

    const elBtnAddKhatmahTrigger = document.getElementById('btn-add-khatmah-trigger');
    if (elBtnAddKhatmahTrigger) {
        elBtnAddKhatmahTrigger.addEventListener('click', () => {
            if (elInputKhatmahName) elInputKhatmahName.value = '';
            if (elInputKhatmahGoal) elInputKhatmahGoal.value = '10';
            const startDateInput = document.getElementById('input-khatmah-start-date');
            if (startDateInput) startDateInput.value = getLocalDateString();
            if (elKhatmahAddError) elKhatmahAddError.style.display = 'none';
            if (modalAddKhatmah) modalAddKhatmah.classList.add('open');
        });
    }

    const elBtnCloseKhatmahModal = document.getElementById('btn-close-khatmah-modal');
    if (elBtnCloseKhatmahModal) {
        elBtnCloseKhatmahModal.addEventListener('click', () => {
            if (modalAddKhatmah) modalAddKhatmah.classList.remove('open');
        });
    }

    const elBtnSaveNewKhatmah = document.getElementById('btn-save-new-khatmah');
    if (elBtnSaveNewKhatmah) {
        elBtnSaveNewKhatmah.addEventListener('click', () => {
            const name = elInputKhatmahName.value.trim();
            const goal = parseInt(elInputKhatmahGoal.value);
            if (!name || isNaN(goal) || goal < 1) {
                if (elKhatmahAddError) elKhatmahAddError.style.display = 'block';
                return;
            }

            // Use the chosen start date (local, no UTC shift); default today
            const startDateInput = document.getElementById('input-khatmah-start-date');
            const startDateVal = startDateInput && startDateInput.value
                ? startDateInput.value + 'T00:00:00'
                : getLocalDateString() + 'T00:00:00';

            const newKhatmah = {
                id: 'khatmah_' + Date.now(),
                name: name,
                currentPage: 1, // Start at page 1
                startPage: 1,   // Start page is 1
                dailyGoal: goal,
                startDate: startDateVal
            };

            activeKhatmahs.push(newKhatmah);
            activeKhatmahId = newKhatmah.id;

            bindActiveKhatmah();
            saveStateToStorage();
            refreshCalculations();

            if (modalAddKhatmah) modalAddKhatmah.classList.remove('open');
            showAndroidToast("تم إنشاء الختمة الجديدة");
        });
    }

    if (elBtnDeleteActiveKhatmah) {
        elBtnDeleteActiveKhatmah.addEventListener('click', async () => {
            if (activeKhatmahs.length <= 1) {
                showAndroidToast("لا يمكن حذف الختمة الوحيدة");
                return;
            }

            const confirmed = await showCustomConfirm("هل أنت متأكد من حذف الختمة الحالية؟ سيتم مسح تقدمها نهائياً ولا يمكن استرجاعه.", "حذف الختمة");
            if (confirmed) {
                const idx = activeKhatmahs.findIndex(k => k.id === activeKhatmahId);
                if (idx > -1) {
                    activeKhatmahs.splice(idx, 1);
                    activeKhatmahId = activeKhatmahs[0].id;
                    bindActiveKhatmah();
                    saveStateToStorage();
                    refreshCalculations();
                    showAndroidToast("تم حذف الختمة");
                }
            }
        });
    }

    // --- Fast Page increment/decrement buttons (+ and -) on the card ---
    const elBtnPageDec = document.getElementById('btn-page-dec');
    if (elBtnPageDec) {
        elBtnPageDec.addEventListener('click', (e) => {
            e.stopPropagation();
            if (currentPage > 1) {
                recordProgress(currentPage - 1);
                showAndroidToast("تم تسجيل الصفحة السابقة");
            }
        });
    }

    const elBtnPageInc = document.getElementById('btn-page-inc');
    if (elBtnPageInc) {
        elBtnPageInc.addEventListener('click', (e) => {
            e.stopPropagation();
            if (currentPage < 604) {
                recordProgress(currentPage + 1);
                showAndroidToast("تم تسجيل الصفحة التالية");
            }
        });
    }

    // --- Quick increment chips (+1, +5, +10, +20) ---
    document.querySelectorAll('[data-inc]').forEach(chip => {
        chip.addEventListener('click', () => {
            const amount = parseInt(chip.getAttribute('data-inc'));
            if (!isNaN(amount)) {
                const newPage = Math.min(604, currentPage + amount);
                recordProgress(newPage);
                showAndroidToast(`تمت إضافة ${amount} صفحات`);
            }
        });
    });

    // --- Weekly Makeup Button ---
    const elBtnUseMakeup = document.getElementById('btn-use-makeup');
    if (elBtnUseMakeup) {
        elBtnUseMakeup.addEventListener('click', () => useWeeklyMakeup());
    }

    // --- Reset Today's Progress Button ---
    const elBtnResetToday = document.getElementById('btn-reset-today');
    if (elBtnResetToday) {
        elBtnResetToday.addEventListener('click', async (e) => {
            e.stopPropagation();
            const confirmed = await showCustomConfirm("هل يريد تصفير المنجز اليومي لليوم؟", "تصفير المنجز اليومي");
            if (confirmed) {
                const todayStr = getLocalDateString();
                const todayRecord = readingHistory.find(r => r.date === todayStr);
                if (todayRecord) {
                    todayRecord.count = 0;
                } else {
                    readingHistory.push({ date: todayStr, count: 0 });
                }
                saveStateToStorage();
                refreshCalculations();
                showAndroidToast("تم تصفير المنجز اليومي");
            }
        });
    }

    // --- Khatmah settings modal event listeners ---
    const btnCloseKhatmahSettingsModal = document.getElementById('btn-close-khatmah-settings-modal');
    if (btnCloseKhatmahSettingsModal) {
        btnCloseKhatmahSettingsModal.addEventListener('click', () => {
            document.getElementById('modal-khatmah-settings').classList.remove('open');
        });
    }

    const btnSaveKhatmahSettings = document.getElementById('btn-save-khatmah-settings');
    if (btnSaveKhatmahSettings) {
        btnSaveKhatmahSettings.addEventListener('click', async () => {
            const name = document.getElementById('edit-khatmah-name').value.trim();
            const goal = parseInt(document.getElementById('edit-khatmah-goal').value);
            
            if (!name || isNaN(goal) || goal < 1) {
                await showCustomAlert("يرجى إدخال اسم صحيح وتحديد هدف صحيح", "تنبيه");
                return;
            }
            
            const editStartDateEl = document.getElementById('edit-khatmah-start-date');
            // Store the picked local date as-is (no UTC conversion -> no day shift)
            const newStartDate = editStartDateEl && editStartDateEl.value
                ? editStartDateEl.value + 'T00:00:00'
                : null;

            const k = activeKhatmahs.find(item => item.id === selectedKhatmahSettingsId);
            if (k) {
                k.name = name;
                k.dailyGoal = goal;
                if (newStartDate) k.startDate = newStartDate;

                // If this is the active Khatmah, update shortcut variables too
                if (k.id === activeKhatmahId) {
                    dailyGoal = goal;
                    if (newStartDate) khatmahStartDate = newStartDate;
                }

                saveStateToStorage();
                refreshCalculations();
                
                document.getElementById('modal-khatmah-settings').classList.remove('open');
                showAndroidToast("تم حفظ تعديلات الختمة");
            }
        });
    }

    const btnResetKhatmahProgress = document.getElementById('btn-reset-khatmah-progress');
    if (btnResetKhatmahProgress) {
        btnResetKhatmahProgress.addEventListener('click', async () => {
            const confirmed = await showCustomConfirm("هل تريد بالتأكيد إعادة ضبط تقدم هذه الختمة والبدء من الصفحة 1؟", "إعادة ضبط الختمة");
            if (confirmed) {
                const k = activeKhatmahs.find(item => item.id === selectedKhatmahSettingsId);
                if (k) {
                    k.currentPage = 1;
                    k.startPage = 1;
                    k.startDate = new Date().toISOString();
                    
                    if (k.id === activeKhatmahId) {
                        currentPage = 1;
                        startPage = 1;
                        khatmahStartDate = k.startDate;
                    }
                    
                    saveStateToStorage();
                    refreshCalculations();
                    
                    document.getElementById('modal-khatmah-settings').classList.remove('open');
                    showAndroidToast("تم إعادة ضبط الختمة للبداية");
                }
            }
        });
    }

    const btnDeleteSelectedKhatmah = document.getElementById('btn-delete-selected-khatmah');
    if (btnDeleteSelectedKhatmah) {
        btnDeleteSelectedKhatmah.addEventListener('click', async () => {
            if (activeKhatmahs.length <= 1) {
                showAndroidToast("لا يمكن حذف الختمة الوحيدة");
                return;
            }
            
            const confirmed = await showCustomConfirm("هل أنت متأكد من حذف هذه الختمة نهائياً؟ سيتم مسح تقدمها تماماً ولا يمكن استرجاعه.", "حذف الختمة");
            if (confirmed) {
                const idx = activeKhatmahs.findIndex(item => item.id === selectedKhatmahSettingsId);
                if (idx > -1) {
                    activeKhatmahs.splice(idx, 1);
                    
                    // If we deleted the active Khatmah, switch to the first remaining one
                    if (selectedKhatmahSettingsId === activeKhatmahId) {
                        activeKhatmahId = activeKhatmahs[0].id;
                    }
                    
                    bindActiveKhatmah();
                    saveStateToStorage();
                    refreshCalculations();
                    
                    document.getElementById('modal-khatmah-settings').classList.remove('open');
                    showAndroidToast("تم حذف الختمة");
                }
            }
        });
    }
}

// Refresh calculation layers and update entire UI
function refreshCalculations() {
    calculateStreaks();
    updateUI();
    renderCalendar();
    renderAchievements();
}

// Record Reading Progress
function recordProgress(newPage) {
    const prevPage = currentPage;
    currentPage = newPage;
    
    const pagesRead = newPage - prevPage;

    if (pagesRead !== 0) {
        const todayStr = getLocalDateString();
        
        // Find if we already recorded reading today
        const todayRecord = readingHistory.find(r => r.date === todayStr);
        if (todayRecord) {
            todayRecord.count = Math.max(0, todayRecord.count + pagesRead);
        } else {
            readingHistory.push({ date: todayStr, count: Math.max(0, pagesRead) });
        }
    }

    // If page is 1 (and user is starting a new Khatmah), save starting date
    if (prevPage === 1 && newPage > 1 && !khatmahStartDate) {
        khatmahStartDate = new Date().toISOString();
    }

    saveStateToStorage();
    refreshCalculations();
}

// Finish Current Khatmah and Reset progress to page 2 (Al-Baqarah)
async function finishKhatmah() {
    if (currentPage < 604) return;

    const todayStr = getLocalDateString();
    const startStr = khatmahStartDate ? khatmahStartDate.split('T')[0] : todayStr;
    
    // Calculate days taken (minimum 1 day)
    const dStart = new Date(startStr);
    const dEnd = new Date(todayStr);
    const diffTime = Math.abs(dEnd.getTime() - dStart.getTime());
    const daysTaken = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) || 1;

    const newKhatmah = {
        id: Date.now().toString(),
        startDate: startStr,
        endDate: todayStr,
        daysTaken: daysTaken
    };

    completedKhatmahs.push(newKhatmah);
    
    // Reset page to 1 and set new start date and startPage to 1
    currentPage = 1;
    startPage = 1;
    khatmahStartDate = new Date().toISOString();

    saveStateToStorage();
    refreshCalculations();

    // Show completion alert dialog
    await showCustomAlert(`🎉 مبارك الختمة! 🏆\nلقد أتممت قراءة المصحف الشريف بالكامل (604 صفحة) في غضون ${daysTaken} يومًا.\nنسأل الله تعالى أن يتقبل منك صالح الأعمال، وتم بدء ختمة جديدة.`, "مبارك الختمة!");
}

// Calculate Current Streak and Maximum Streak
function calculateStreaks() {
    // Combine actual reading days with compensated (makeup) days
    const dateSet = new Set(readingHistory.map(r => r.date));
    makeupDays.forEach(d => dateSet.add(d));

    if (dateSet.size === 0) {
        currentStreak = 0;
        maxStreak = 0;
        return;
    }

    // Sort unique dates ascending
    const sorted = Array.from(dateSet).sort((a, b) => new Date(a) - new Date(b));

    let tempStreak = 0;
    let localMax = 0;
    let lastDate = null;

    for (let i = 0; i < sorted.length; i++) {
        const itemDate = new Date(sorted[i] + 'T00:00:00');

        if (lastDate === null) {
            tempStreak = 1;
        } else {
            const diffDays = Math.round((itemDate - lastDate) / (1000 * 60 * 60 * 24));

            if (diffDays === 1) {
                tempStreak++;            // consecutive day
            } else if (diffDays > 1) {
                if (tempStreak > localMax) localMax = tempStreak;
                tempStreak = 1;          // break in streak
            }
        }
        lastDate = itemDate;
    }

    if (tempStreak > localMax) localMax = tempStreak;
    maxStreak = localMax;

    // Streak is still active if the last day was today or yesterday
    const todayStr = getLocalDateString();
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const yesterdayStr = getLocalDateString(yesterday);

    const lastStr = sorted[sorted.length - 1];

    currentStreak = (lastStr === todayStr || lastStr === yesterdayStr) ? tempStreak : 0;
}

// Calculate User Level based on streak and completed Khatmahs
function calculateUserLevel() {
    const khatmahsCount = completedKhatmahs.length;
    
    if (khatmahsCount >= 20) {
        return { name: "سفير القرآن" };
    } else if (khatmahsCount >= 10) {
        return { name: "رفيق القرآن" };
    } else if (khatmahsCount >= 5) {
        return { name: "صاحب ختمات" };
    } else if (khatmahsCount >= 3 || currentStreak >= 30) {
        return { name: "حافظ للورد" };
    } else if (khatmahsCount >= 1 || currentStreak >= 7) {
        return { name: "قارئ مجتهد" };
    } else {
        return { name: "قارئ مبتدئ" };
    }
}

// Render Monthly Calendar
function renderCalendar() {
    const label = document.getElementById('calendar-month-label');
    const grid = document.getElementById('calendar-days-grid');
    if (!grid || !label) return;
    
    grid.innerHTML = '';
    
    // Arabic month names
    const monthNames = [
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", 
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    ];
    
    label.innerText = `${monthNames[currentCalendarMonth]} ${currentCalendarYear}`;
    
    // First day of the month
    const firstDay = new Date(currentCalendarYear, currentCalendarMonth, 1);
    // Day of week (0 = Sunday, 1 = Monday, ..., 6 = Saturday)
    let startDayOfWeek = firstDay.getDay();
    
    // Number of days in the month
    const totalDays = new Date(currentCalendarYear, currentCalendarMonth + 1, 0).getDate();
    
    // Add empty cells for padding
    for (let i = 0; i < startDayOfWeek; i++) {
        const cell = document.createElement('div');
        cell.className = 'calendar-day-cell empty';
        grid.appendChild(cell);
    }
    
    // Today date components for comparison
    const now = new Date();
    const todayYear = now.getFullYear();
    const todayMonth = now.getMonth();
    const todayDay = now.getDate();
    
    // Add cells for each day
    for (let day = 1; day <= totalDays; day++) {
        const cell = document.createElement('div');
        cell.className = 'calendar-day-cell';
        cell.innerText = day;
        
        // Date string format YYYY-MM-DD
        const monthStr = String(currentCalendarMonth + 1).padStart(2, '0');
        const dayStr = String(day).padStart(2, '0');
        const dateKey = `${currentCalendarYear}-${monthStr}-${dayStr}`;
        
        // Check if date is in the future
        const isFuture = (currentCalendarYear > todayYear) || 
                         (currentCalendarYear === todayYear && currentCalendarMonth > todayMonth) || 
                         (currentCalendarYear === todayYear && currentCalendarMonth === todayMonth && day > todayDay);
        
        if (isFuture) {
            cell.classList.add('future-grey');
        } else {
            // Find if there is reading count on this date in readingHistory
            const record = readingHistory.find(r => r.date === dateKey);
            const count = record ? record.count : 0;

            if (makeupDays.includes(dateKey)) {
                // Compensated day — counts as a completed (green) day
                cell.classList.add('read-green');
                cell.setAttribute('title', 'يوم معوّض ✅');
            } else if (count > 0) {
                const dayGoal = goalForDate(dateKey);
                if (count >= dayGoal) {
                    cell.classList.add('read-green');
                    cell.setAttribute('title', `تم تحقيق الهدف! قرأت ${count} صفحة`);
                } else {
                    cell.classList.add('read-yellow');
                    cell.setAttribute('title', `تحت الهدف اليومي. قرأت ${count} صفحة (الهدف: ${dayGoal})`);
                }
            } else {
                cell.classList.add('read-red');
                cell.setAttribute('title', 'انقطاع عن القراءة');
            }
        }
        
        grid.appendChild(cell);
    }
    
    // Update calendar helper stats
    if (elCalCurrentStreak) elCalCurrentStreak.innerText = `${currentStreak} يوم`;
    if (elCalMaxStreak) elCalMaxStreak.innerText = `${maxStreak} يوم`;
    if (elCalTotalDays) elCalTotalDays.innerText = `${readingHistory.length} يوم`;

    // Keep the monthly chart in sync with the displayed month
    renderMonthlyChart();
}

// Render Achievements screen
function renderAchievements() {
    if (!elKhatmahsList) return;

    elRecTotalKhatmahs.innerText = `${completedKhatmahs.length} ختمة`;

    // 1. Calculate fastest Khatmah
    if (completedKhatmahs.length > 0) {
        const fastest = [...completedKhatmahs].sort((a, b) => a.daysTaken - b.daysTaken)[0];
        elRecFastestVal.innerText = `${fastest.daysTaken} يوم`;
        
        // Hide empty message
        elEmptyKhatmahsMsg.style.display = 'none';
        
        // Clear old list logs (excluding empty message)
        const oldLogs = elKhatmahsList.querySelectorAll('.khatmah-log-card');
        oldLogs.forEach(l => l.remove());

        // Render Khatmah history list
        completedKhatmahs.slice().reverse().forEach((kh, index) => {
            const card = document.createElement('div');
            card.className = 'khatmah-log-card';

            const khNumber = completedKhatmahs.length - index;
            const arStart = formatArabicDate(kh.startDate);
            const arEnd = formatArabicDate(kh.endDate);

            card.innerHTML = `
                <div class="khatmah-log-right">
                    <div class="khatmah-log-icon">
                        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"></path>
                            <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"></path>
                        </svg>
                    </div>
                    <div class="khatmah-log-title">
                        <span class="kh-name">الختمة رقم ${khNumber}</span>
                        <span class="days-count">في ${kh.daysTaken} يومًا متواصلًا</span>
                    </div>
                </div>
                <div class="khatmah-log-dates">
                    <span>بدء: ${arStart}</span>
                    <span>ختم: ${arEnd}</span>
                </div>
            `;
            elKhatmahsList.appendChild(card);
        });
    } else {
        elRecFastestVal.innerText = "لا يوجد ختمات بعد";
        elEmptyKhatmahsMsg.style.display = 'block';
        
        // Remove any old logs
        const oldLogs = elKhatmahsList.querySelectorAll('.khatmah-log-card');
        oldLogs.forEach(l => l.remove());
    }
}

// Update UI Components
function updateUI() {
    if (quranPages.length === 0) return;

    // Keep active Khatmahs grid in sync
    updateKhatmahSelectorOptions();

    const pageInfo = quranPages[currentPage - 1] || quranPages[0];

    // 1. Home Screen Details
    elCurrentPageVal.innerText = currentPage;
    refreshSurahAyahSelectors();
    elJuzVal.innerText = pageInfo.juz;
    elHizbVal.innerText = pageInfo.hizb;

    // Plan status: where you should be now vs where you are
    updatePlanStatus();

    // Weekly makeup availability
    updateMakeupUI();

    // New dashboards: juz progress, weekly report, monthly chart, badges
    renderJuzProgress();
    renderWeeklyReport();
    renderMonthlyChart();
    renderBadges();

    // Update daily progress indicator inside the blue card
    const elBlueCardGoalValText = document.getElementById('blue-card-goal-val');
    const elBlueCardProgressRatioText = document.getElementById('blue-card-progress-ratio');
    const elBlueCardProgressBar = document.getElementById('blue-card-progress-bar');
    
    const todayStr = getLocalDateString();
    const todayRecord = readingHistory.find(r => r.date === todayStr);
    const todayRead = todayRecord ? todayRecord.count : 0;

    // Today's effective goal (doubled on a makeup day)
    const todayGoal = goalForDate(todayStr);

    if (elBlueCardGoalValText) elBlueCardGoalValText.innerText = todayGoal;

    if (elBlueCardProgressRatioText) {
        elBlueCardProgressRatioText.innerText = `${todayRead} / ${todayGoal} اليوم`;
    }

    if (elBlueCardProgressBar) {
        const dailyProgressPct = todayGoal > 0 ? Math.min(100, (todayRead / todayGoal) * 100) : 0;
        elBlueCardProgressBar.style.width = `${dailyProgressPct}%`;
    }

    // Weekly goal = daily goal × 7, progress = pages read since the start of this week
    const weeklyGoal = dailyGoal * 7;
    const weeklyRead = getWeeklyReadCount();

    const elWeeklyGoalValText = document.getElementById('weekly-card-goal-val');
    const elWeeklyProgressRatioText = document.getElementById('weekly-card-progress-ratio');
    const elWeeklyProgressBar = document.getElementById('weekly-card-progress-bar');

    if (elWeeklyGoalValText) elWeeklyGoalValText.innerText = weeklyGoal;
    if (elWeeklyProgressRatioText) {
        elWeeklyProgressRatioText.innerText = `${weeklyRead} / ${weeklyGoal} هذا الأسبوع`;
    }
    if (elWeeklyProgressBar) {
        const weeklyProgressPct = weeklyGoal > 0 ? Math.min(100, (weeklyRead / weeklyGoal) * 100) : 0;
        elWeeklyProgressBar.style.width = `${weeklyProgressPct}%`;
    }

    // Show/Hide finish button when user reaches page 604
    if (currentPage === 604) {
        elBtnFinishKhatmah.style.display = 'flex';
    } else {
        elBtnFinishKhatmah.style.display = 'none';
    }

    const readCount = currentPage;
    const remainingCount = 604 - currentPage;
    const progressPct = ((readCount / 604) * 100).toFixed(2);

    elReadPagesVal.innerText = readCount;
    elRemainingPagesVal.innerText = remainingCount;
    elCompletionProgressBar.style.width = `${progressPct}%`;
    elCompletionPercentageVal.innerText = `${progressPct}%`;

    // Calculate current Khatmah stats
    const surahsCount = calculateSurahsReadInRange(startPage, currentPage);
    const ayahsCount = currentPage >= startPage ? calculateAyahsRead(currentPage) - calculateAyahsRead(startPage - 1) : 0;
    const lettersCount = Math.round((ayahsCount / 6236) * 323671);

    if (elStatSurahsVal) elStatSurahsVal.innerText = surahsCount;
    if (elStatAyahsVal) elStatAyahsVal.innerText = ayahsCount.toLocaleString();
    if (elStatLettersVal) elStatLettersVal.innerText = lettersCount.toLocaleString();

    // 2. Levels Badge Update
    const levelInfo = calculateUserLevel();
    elUserLevelName.innerText = levelInfo.name;
    elUserStreakVal.innerText = `${currentStreak} يوم متواصل`;

    // 3. Settings & Stats Card calculations
    const dailyAvg = calculateDailyAverage();
    elDailyAvgVal.innerText = dailyAvg.toFixed(1);

    if (remainingCount === 0) {
        elEstimatedDateVal.innerText = "أتممت الختمة! اضغط الزر الذهبي بالأعلى لتسجيلها 🎉";
    } else if (dailyAvg > 0) {
        const daysToFinish = Math.ceil(remainingCount / dailyAvg);
        const finishDate = new Date();
        finishDate.setDate(finishDate.getDate() + daysToFinish);
        elEstimatedDateVal.innerText = formatArabicDate(getLocalDateString(finishDate));
    } else {
        const daysToFinish = Math.ceil(remainingCount / dailyGoal);
        const finishDate = new Date();
        finishDate.setDate(finishDate.getDate() + daysToFinish);
        elEstimatedDateVal.innerText = `${formatArabicDate(getLocalDateString(finishDate))} (تقديري)`;
    }

    // 4. Goals elements
    const todayGoalForStats = goalForDate(getLocalDateString());
    if (elGoalTargetVal) elGoalTargetVal.innerText = todayGoalForStats;
    if (elGoalTodayVal) elGoalTodayVal.innerText = todayRead;

    const goalRemaining = Math.max(0, todayGoalForStats - todayRead);
    if (elGoalRemainingVal) elGoalRemainingVal.innerText = goalRemaining;

    // Push latest progress to the home-screen widget
    pushWidgetUpdate(Math.round(parseFloat(progressPct)), currentPage, todayRead, todayGoalForStats);
}

/**
 * Entry point for the home-screen widget and the quick-adjust sheet.
 *
 * The native side can move the position but must not compute progress — the
 * daily count, streak, makeup days and khatmah completion all come out of
 * recordProgress(). Routing widget changes back through it is what keeps the
 * widget and the app telling the same story.
 *
 * Returns 'wait' while the app is still booting so the caller can retry, and
 * 'ok' once the change is saved.
 */
window.applyWidgetPosition = function (page) {
    try {
        if (!Array.isArray(quranPages) || quranPages.length === 0) return 'wait';
        const p = Math.max(1, Math.min(604, parseInt(page, 10) || 1));
        if (p !== currentPage) {
            recordProgress(p);
        } else {
            refreshCalculations();
        }
        return 'ok';
    } catch (e) {
        console.error('applyWidgetPosition failed', e);
        return 'err';
    }
};

// Send progress to the native home-screen widget
function pushWidgetUpdate(percent, page, todayRead, todayGoal) {
    if (typeof Android === 'undefined' || !Android.updateWidget) return;
    try {
        const active = activeKhatmahs.find(k => k.id === activeKhatmahId);
        const name = active ? active.name : '';
        Android.updateWidget(percent, page, todayRead, todayGoal, currentStreak, name);
    } catch (e) {
        console.error('widget update failed', e);
    }
}

// Show the page/surah the user should have reached today per their plan,
// and whether they are behind, on track, or ahead.
function updatePlanStatus() {
    if (!elPlanStatusBadge) return;

    // Days elapsed since the khatmah started, inclusive of today.
    const startStr = khatmahStartDate ? khatmahStartDate.split('T')[0] : getLocalDateString();
    const start = new Date(startStr + 'T00:00:00');
    const today = new Date(getLocalDateString() + 'T00:00:00');
    const msPerDay = 86400000;
    let daysElapsed = Math.floor((today - start) / msPerDay) + 1;

    // The khatmah hasn't started yet (start date is in the future)
    if (daysElapsed < 1) {
        const startPageInfo = quranPages[(startPage || 1) - 1] || quranPages[0];
        if (elPlanExpectedPage) elPlanExpectedPage.innerText = startPage || 1;
        if (elPlanExpectedSurah) elPlanExpectedSurah.innerText = startPageInfo ? startPageInfo.surah_name : '—';
        elPlanStatusBadge.classList.remove('plan-behind', 'plan-ontrack', 'plan-ahead');
        elPlanStatusBadge.classList.add('plan-ontrack');
        if (elPlanStatusIcon) elPlanStatusIcon.innerText = '🗓️';
        if (elPlanStatusText) elPlanStatusText.innerText = `ختمتك تبدأ ${formatArabicDate(startStr)}`;
        return;
    }

    // Expected page = pages that should be finished by end of today.
    const expectedRaw = (startPage - 1) + (daysElapsed * dailyGoal);
    const expectedPage = Math.min(604, Math.max(1, expectedRaw));

    const expInfo = quranPages[expectedPage - 1] || quranPages[0];
    if (elPlanExpectedPage) elPlanExpectedPage.innerText = expectedPage;
    if (elPlanExpectedSurah) elPlanExpectedSurah.innerText = expInfo ? expInfo.surah_name : '—';

    // Compare current progress to the plan.
    const diff = currentPage - expectedPage;
    let state, icon, text;

    if (currentPage >= 604) {
        state = 'ahead'; icon = '🎉'; text = 'أتممت الختمة، بارك الله فيك';
    } else if (diff < 0) {
        state = 'behind'; icon = '⏰'; text = `متأخر بـ ${Math.abs(diff)} صفحة عن خطتك`;
    } else if (diff === 0) {
        state = 'ontrack'; icon = '✅'; text = 'ماشٍ على الخطة تماماً';
    } else {
        state = 'ahead'; icon = '🔥'; text = `متقدّم بـ ${diff} صفحة عن خطتك`;
    }

    elPlanStatusBadge.classList.remove('plan-behind', 'plan-ontrack', 'plan-ahead');
    elPlanStatusBadge.classList.add('plan-' + state);
    if (elPlanStatusIcon) elPlanStatusIcon.innerText = icon;
    if (elPlanStatusText) elPlanStatusText.innerText = text;
}

// ===== Surah + Ayah selectors for the current page =====

// Every surah on a page (handles pages with the end of one surah + the start
// of another, or three short surahs like الإخلاص/الفلق/الناس), each with the
// range of ayahs that actually fall on this page.
function getPageSurahs(page) {
    const info = quranPages[page - 1] || quranPages[0];
    const next = quranPages[page]; // 0-indexed -> following page

    // Surahs whose page range covers this page (from the fixed SURAH_RANGES)
    const nums = SURAH_RANGES
        .filter(r => r.start <= page && page <= r.end)
        .map(r => r.number)
        .sort((a, b) => a - b);

    if (nums.length === 0) nums.push(info.surah_number);

    const result = [];
    nums.forEach((s, i) => {
        const isFirst = i === 0;
        const isLast = i === nums.length - 1;
        let first, last;

        if (isFirst) {
            first = (info.surah_number === s) ? info.ayah_number : 1;
        } else {
            first = 1; // a later surah on the page necessarily starts here
        }

        if (isLast) {
            last = (next && next.surah_number === s)
                ? next.ayah_number - 1            // surah continues on the next page
                : SURAH_AYAH_COUNTS[s - 1];        // surah ends on this page
        } else {
            last = SURAH_AYAH_COUNTS[s - 1];       // fully finishes before the next surah
        }

        if (!last || last < first) last = first;
        result.push({ surah: s, name: surahDisplayName(s), first, last });
    });
    return result;
}

function surahDisplayName(num) {
    return SURAH_NAMES[num - 1] ? `سورة ${SURAH_NAMES[num - 1]}` : `سورة ${num}`;
}

let surahAyahPage = -1;   // last page the selectors were built for
let pageSurahsCache = [];
let selectedSurahIdx = 0;
let selectedAyah = 1;

// Rebuild the surah/ayah state when the page changes; keep the user's manual
// choice while still on the same page.
function refreshSurahAyahSelectors() {
    if (surahAyahPage !== currentPage) {
        surahAyahPage = currentPage;
        pageSurahsCache = getPageSurahs(currentPage);
        selectedSurahIdx = 0;
        selectedAyah = pageSurahsCache[0] ? pageSurahsCache[0].first : 1;
    }
    updateSurahAyahDisplay();
}

function updateSurahAyahDisplay() {
    const surahText = document.getElementById('surah-picker-text');
    const ayahText = document.getElementById('ayah-picker-text');
    const surahField = document.getElementById('surah-picker');
    const s = pageSurahsCache[selectedSurahIdx];
    if (surahText) surahText.innerText = s ? s.name : '—';
    if (ayahText) ayahText.innerText = selectedAyah;
    if (surahField) surahField.classList.toggle('single', pageSurahsCache.length <= 1);
}

// Open the themed picker for the surah on multi-surah pages
function openSurahPicker() {
    if (pageSurahsCache.length <= 1) return;
    const items = pageSurahsCache.map((s, idx) => ({ value: idx, label: s.name }));
    openPicker('اختر السورة', items, selectedSurahIdx, (val) => {
        selectedSurahIdx = val;
        const s = pageSurahsCache[selectedSurahIdx];
        selectedAyah = s ? s.first : 1;
        updateSurahAyahDisplay();
    });
}

// Open the themed picker for the ayah of the current surah
function openAyahPicker() {
    const s = pageSurahsCache[selectedSurahIdx];
    if (!s) return;
    const items = [];
    for (let a = s.first; a <= s.last; a++) items.push({ value: a, label: String(a) });
    openPicker('اختر رقم الآية', items, selectedAyah, (val) => {
        selectedAyah = val;
        updateSurahAyahDisplay();
    });
}

// Generic themed picker (bottom-sheet style) shared by surah & ayah
function openPicker(title, items, currentValue, onSelect) {
    const modal = document.getElementById('modal-picker');
    const titleEl = document.getElementById('picker-title');
    const list = document.getElementById('picker-list');
    if (!modal || !list) return;

    titleEl.innerText = title;
    list.innerHTML = '';
    items.forEach(it => {
        const row = document.createElement('button');
        row.className = 'picker-option' + (it.value === currentValue ? ' selected' : '');
        row.innerHTML = `<span class="po-check">✓</span><span class="po-label">${it.label}</span>`;
        row.addEventListener('click', () => {
            onSelect(it.value);
            modal.classList.remove('open');
        });
        list.appendChild(row);
    });

    modal.classList.add('open');
    const sel = list.querySelector('.picker-option.selected');
    if (sel) setTimeout(() => sel.scrollIntoView({ block: 'center' }), 30);
}

// ===== Juz progress, weekly report, monthly chart, badges =====

let juzLastPage = null; // { juzNumber: lastPageOfThatJuz }

function computeJuzLastPages() {
    juzLastPage = {};
    for (let i = 0; i < quranPages.length; i++) {
        const j = quranPages[i].juz;
        const p = i + 1;
        if (!juzLastPage[j] || p > juzLastPage[j]) juzLastPage[j] = p;
    }
}

function getCompletedJuz() {
    if (!juzLastPage) computeJuzLastPages();
    let done = 0;
    for (let j = 1; j <= 30; j++) {
        if (juzLastPage[j] && currentPage >= juzLastPage[j]) done++;
    }
    return done;
}

// Render the 30-juz progress strip
function renderJuzProgress() {
    const grid = document.getElementById('juz-grid');
    const countText = document.getElementById('juz-count-text');
    if (!grid) return;
    if (!juzLastPage) computeJuzLastPages();

    const currentJuz = (quranPages[currentPage - 1] || {}).juz || 1;
    const done = getCompletedJuz();
    if (countText) countText.innerText = `${done} / 30 جزء`;

    grid.innerHTML = '';
    for (let j = 1; j <= 30; j++) {
        const seg = document.createElement('div');
        seg.className = 'juz-seg';
        if (juzLastPage[j] && currentPage >= juzLastPage[j]) {
            seg.classList.add('done');
        } else if (j === currentJuz) {
            seg.classList.add('current');
        }
        seg.setAttribute('title', `الجزء ${j}`);
        seg.innerText = j;
        grid.appendChild(seg);
    }
}

// Sum of pages read within an inclusive date-string range
function pagesInRange(startStr, endStr) {
    return readingHistory.reduce((s, r) =>
        (r.date >= startStr && r.date <= endStr) ? s + (r.count || 0) : s, 0);
}

// Render the automatic weekly report (this week vs last week)
function renderWeeklyReport() {
    const elPages = document.getElementById('report-pages');
    const elCompare = document.getElementById('report-compare');
    const elGreen = document.getElementById('report-green-days');
    const elLongest = document.getElementById('report-longest');
    if (!elPages) return;

    const thisStart = getWeekStartString();
    const todayStr = getLocalDateString();

    // Last week's Sunday..Saturday
    const lastStartDate = new Date(thisStart + 'T00:00:00');
    lastStartDate.setDate(lastStartDate.getDate() - 7);
    const lastStartStr = getLocalDateString(lastStartDate);
    const lastEndDate = new Date(thisStart + 'T00:00:00');
    lastEndDate.setDate(lastEndDate.getDate() - 1);
    const lastEndStr = getLocalDateString(lastEndDate);

    const pagesThis = pagesInRange(thisStart, todayStr);
    const pagesLast = pagesInRange(lastStartStr, lastEndStr);

    elPages.innerText = pagesThis;

    if (elCompare) {
        const diff = pagesThis - pagesLast;
        if (pagesLast === 0 && pagesThis === 0) {
            elCompare.innerText = 'لا بيانات سابقة';
            elCompare.className = 'report-compare';
        } else if (diff > 0) {
            elCompare.innerText = `▲ ${diff} عن الأسبوع الماضي`;
            elCompare.className = 'report-compare up';
        } else if (diff < 0) {
            elCompare.innerText = `▼ ${Math.abs(diff)} عن الأسبوع الماضي`;
            elCompare.className = 'report-compare down';
        } else {
            elCompare.innerText = '= مثل الأسبوع الماضي';
            elCompare.className = 'report-compare';
        }
    }

    // Green days this week (goal met or compensated)
    let greenDays = 0;
    const start = new Date(thisStart + 'T00:00:00');
    const today = new Date(todayStr + 'T00:00:00');
    for (let d = new Date(start); d <= today; d.setDate(d.getDate() + 1)) {
        const ds = getLocalDateString(d);
        const rec = readingHistory.find(r => r.date === ds);
        const count = rec ? rec.count : 0;
        if (makeupDays.includes(ds) || (count > 0 && count >= goalForDate(ds))) greenDays++;
    }
    if (elGreen) elGreen.innerText = greenDays;
    if (elLongest) elLongest.innerText = maxStreak;
}

// Render a simple daily bar chart for the month shown in the calendar
function renderMonthlyChart() {
    const chart = document.getElementById('month-chart');
    const monthLbl = document.getElementById('chart-month-lbl');
    if (!chart) return;

    const year = currentCalendarYear;
    const month = currentCalendarMonth; // 0-based
    const daysInMonth = new Date(year, month + 1, 0).getDate();

    const monthNames = ['يناير','فبراير','مارس','أبريل','مايو','يونيو','يوليو','أغسطس','سبتمبر','أكتوبر','نوفمبر','ديسمبر'];
    if (monthLbl) monthLbl.innerText = `${monthNames[month]} ${year}`;

    // Collect counts and the max for scaling
    const counts = [];
    let maxCount = 0;
    for (let day = 1; day <= daysInMonth; day++) {
        const ds = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
        const rec = readingHistory.find(r => r.date === ds);
        const c = rec ? rec.count : 0;
        counts.push({ day, count: c, date: ds });
        if (c > maxCount) maxCount = c;
    }

    chart.innerHTML = '';
    counts.forEach(({ day, count, date }) => {
        const col = document.createElement('div');
        col.className = 'chart-col';
        const barWrap = document.createElement('div');
        barWrap.className = 'chart-bar-wrap';
        const bar = document.createElement('div');
        bar.className = 'chart-bar';
        const pct = maxCount > 0 ? Math.max(count > 0 ? 6 : 0, (count / maxCount) * 100) : 0;
        bar.style.height = `${pct}%`;
        if (count > 0 && count >= goalForDate(date)) bar.classList.add('met');
        else if (count > 0) bar.classList.add('partial');
        bar.setAttribute('title', `${day}: ${count} صفحة`);
        barWrap.appendChild(bar);
        const lbl = document.createElement('span');
        lbl.className = 'chart-day-lbl';
        lbl.innerText = day;
        col.appendChild(barWrap);
        col.appendChild(lbl);
        chart.appendChild(col);
    });
}

// Achievement badges
const ACHIEVEMENTS = [
    { id: 'juz1',   icon: '📖', name: 'أول جزء',        desc: 'أتممت الجزء الأول',        check: () => getCompletedJuz() >= 1 },
    { id: 'juz5',   icon: '📚', name: 'خمسة أجزاء',      desc: 'أتممت 5 أجزاء',            check: () => getCompletedJuz() >= 5 },
    { id: 'juz10',  icon: '📗', name: 'عشرة أجزاء',      desc: 'أتممت 10 أجزاء',           check: () => getCompletedJuz() >= 10 },
    { id: 'half',   icon: '🌗', name: 'منتصف الختمة',    desc: 'وصلت منتصف المصحف',        check: () => currentPage >= 302 },
    { id: 'juz20',  icon: '📘', name: 'عشرون جزءاً',     desc: 'أتممت 20 جزءاً',           check: () => getCompletedJuz() >= 20 },
    { id: 'week',   icon: '🔥', name: 'أسبوع متواصل',    desc: '7 أيام قراءة متتالية',     check: () => maxStreak >= 7 },
    { id: 'month',  icon: '💎', name: 'شهر متواصل',      desc: '30 يوماً متتالياً',        check: () => maxStreak >= 30 },
    { id: 'finish', icon: '🏆', name: 'ختمة كاملة',      desc: 'أتممت ختمة كاملة',         check: () => completedKhatmahs.length >= 1 || currentPage >= 604 }
];

function renderBadges() {
    const grid = document.getElementById('badges-grid');
    const countEl = document.getElementById('badges-count');
    if (!grid) return;

    let earned = 0;
    grid.innerHTML = '';
    ACHIEVEMENTS.forEach(a => {
        const unlocked = !!a.check();
        if (unlocked) earned++;
        const item = document.createElement('div');
        item.className = 'badge-item' + (unlocked ? ' unlocked' : ' locked');
        item.innerHTML = `
            <span class="badge-emoji">${unlocked ? a.icon : '🔒'}</span>
            <span class="badge-name">${a.name}</span>
            <span class="badge-desc">${a.desc}</span>
        `;
        grid.appendChild(item);
    });
    if (countEl) countEl.innerText = `${earned} / ${ACHIEVEMENTS.length}`;
}

// ===== Weekly makeup (تعويض) =====

// Sunday date string of the week containing the given date (week = Sun..Sat)
function getWeekStartString(d = new Date()) {
    const base = new Date(getLocalDateString(d) + 'T00:00:00');
    base.setDate(base.getDate() - base.getDay()); // back to Sunday
    return getLocalDateString(base);
}

// Daily goal for a given date — doubled on the day a makeup was performed
function goalForDate(dateStr) {
    return (makeupAppliedDate && dateStr === makeupAppliedDate) ? dailyGoal * 2 : dailyGoal;
}

// Most recent past day (within the last 7 days) that is below the goal and
// not already compensated. Returns 'YYYY-MM-DD' or null.
function findCompensableDay() {
    const today = new Date(getLocalDateString() + 'T00:00:00');
    for (let i = 1; i <= 7; i++) {
        const d = new Date(today);
        d.setDate(today.getDate() - i);
        const ds = getLocalDateString(d);
        if (makeupDays.includes(ds)) continue;
        const rec = readingHistory.find(r => r.date === ds);
        const count = rec ? rec.count : 0;
        if (count < dailyGoal) return ds;
    }
    return null;
}

// Use the once-per-week makeup on the most recent missed day
async function useWeeklyMakeup() {
    const weekKey = getWeekStartString();
    if (lastMakeupWeek === weekKey) {
        await showCustomAlert("لقد استخدمت التعويض هذا الأسبوع. يُتاح مرة واحدة فقط كل أسبوع.", "التعويض غير متاح");
        return;
    }

    const day = findCompensableDay();
    if (!day) {
        await showCustomAlert("لا يوجد يوم فائت خلال الأسبوع الماضي يحتاج إلى تعويض.", "لا حاجة للتعويض");
        return;
    }

    const confirmed = await showCustomConfirm(
        `سيتم تعويض يوم ${formatArabicDate(day)} واعتباره يوماً مكتملاً (أخضر) ويحافظ على تتابعك، وفي المقابل يتضاعف هدف اليوم إلى ${dailyGoal * 2} صفحة اليوم فقط ويعود ${dailyGoal} غداً. يُتاح التعويض مرة واحدة كل أسبوع. هل تريد المتابعة؟`,
        "تعويض يوم فائت"
    );
    if (!confirmed) return;

    makeupDays.push(day);
    lastMakeupWeek = weekKey;
    makeupAppliedDate = getLocalDateString(); // today's goal is doubled
    saveStateToStorage();
    refreshCalculations();
    if (document.getElementById('tab-calendar').classList.contains('active')) renderCalendar();
    showAndroidToast(`تم تعويض اليوم ✅ هدف اليوم أصبح ${dailyGoal * 2} صفحة`);
}

// Update the makeup card button/description state
function updateMakeupUI() {
    const btn = document.getElementById('btn-use-makeup');
    const desc = document.getElementById('makeup-desc');
    if (!btn || !desc) return;

    if (lastMakeupWeek === getWeekStartString()) {
        btn.disabled = true;
        btn.style.opacity = '0.5';
        desc.innerText = 'استخدمت التعويض هذا الأسبوع';
        return;
    }

    const day = findCompensableDay();
    if (!day) {
        btn.disabled = true;
        btn.style.opacity = '0.5';
        desc.innerText = 'لا يوجد يوم فائت يحتاج إلى تعويض';
    } else {
        btn.disabled = false;
        btn.style.opacity = '1';
        desc.innerText = `عوّض يوم ${formatArabicDate(day)} — متاح مرة كل أسبوع`;
    }
}

// Sum of pages read since the start of the current week (week starts Saturday)
function getWeeklyReadCount() {
    const now = new Date();
    // Week runs Sunday -> Saturday. JS getDay(): Sun=0 ... Sat=6.
    const daysSinceSunday = now.getDay();
    const weekStart = new Date(now);
    weekStart.setDate(now.getDate() - daysSinceSunday);
    const weekStartStr = getLocalDateString(weekStart);
    const todayStr = getLocalDateString(now);

    return readingHistory.reduce((sum, item) => {
        if (item.date >= weekStartStr && item.date <= todayStr) {
            return sum + (item.count || 0);
        }
        return sum;
    }, 0);
}

// Calculate Daily Average
function calculateDailyAverage() {
    if (readingHistory.length === 0) return 0;
    
    const totalCount = readingHistory.reduce((sum, item) => sum + item.count, 0);
    
    const dates = readingHistory.map(r => new Date(r.date).getTime());
    const minDate = Math.min(...dates);
    const maxDate = new Date().getTime();
    
    const diffTime = Math.abs(maxDate - minDate);
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) || 1;
    
    return totalCount / diffDays;
}

// Formatter for Arabic Dates
function formatArabicDate(dateStr) {
    const d = new Date(dateStr);
    return d.toLocaleDateString('ar-SA', { year: 'numeric', month: 'long', day: 'numeric' });
}

// Init notification selectors
function initNotificationSettings() {
    if (typeof Android !== 'undefined' && Android.getNotificationSettings) {
        try {
            const settingsJson = Android.getNotificationSettings();
            const settings = JSON.parse(settingsJson);
            
            elNotificationSwitch.checked = settings.enabled;
            elTimeHour.value = settings.hour.toString();
            elTimeMinute.value = settings.minute.toString();
            
            if (settings.enabled) {
                elTimePickerContainer.classList.remove('disabled');
            } else {
                elTimePickerContainer.classList.add('disabled');
            }
        } catch(e) {
            console.error('Error init notification settings:', e);
        }
    } else {
        elNotificationSwitch.checked = false;
        elTimePickerContainer.classList.add('disabled');
    }
}

// ===== Floating Quran Button =====

// Init floating-button settings: only show the card when a Quran app exists.
function initFloatingButtonSettings() {
    // The settings card is visible by default (no JS gating) so it always
    // appears in the Settings tab. Here we only restore the saved toggle state
    // and sync the floating button's visibility.
    const bridgeReady = typeof Android !== 'undefined'
        && Android.isFloatingButtonEnabled;

    if (!bridgeReady) {
        updateFloatingButtonVisibility(false);
        return;
    }

    try {
        const enabled = Android.isFloatingButtonEnabled();
        elFloatingBtnSwitch.checked = enabled;
        updateFloatingButtonVisibility(enabled);
        populateAppList();
    } catch (e) {
        console.error('Error init floating button settings:', e);
    }
}

// Fill the app picker with every installed app, preselecting the saved choice.
function populateAppList() {
    if (!elQuranAppSelect) return;
    if (!(typeof Android !== 'undefined' && Android.getInstalledApps)) return;

    try {
        const apps = JSON.parse(Android.getInstalledApps());
        const selected = (Android.getSelectedQuranApp)
            ? Android.getSelectedQuranApp() : '';

        elQuranAppSelect.innerHTML = '<option value="">— اختر تطبيقاً —</option>';
        apps.forEach(app => {
            const opt = document.createElement('option');
            opt.value = app.pkg;
            opt.textContent = app.label;
            if (app.pkg === selected) opt.selected = true;
            elQuranAppSelect.appendChild(opt);
        });
    } catch (e) {
        console.error('Error populating app list:', e);
    }
}

// Show/hide the floating Quran button.
function updateFloatingButtonVisibility(enabled) {
    if (!elFloatingQuranBtn) return;
    elFloatingQuranBtn.style.display = enabled ? 'flex' : 'none';
}

// Callback from Android after the user returns from the overlay permission screen.
function onOverlayPermissionResult(granted) {
    if (granted) {
        showAndroidToast("تم تفعيل الإذن. اضغط زر القرآن العائم مجدداً.");
    } else {
        showAndroidToast("لم يتم منح إذن العرض فوق التطبيقات.");
    }
}
window.onOverlayPermissionResult = onOverlayPermissionResult;

// Save notifications via bridge
function saveNotificationSettings(enabled, hour, minute) {
    if (typeof Android !== 'undefined' && Android.saveNotificationSettings) {
        if (enabled && !Android.hasNotificationPermission()) {
            Android.requestNotificationPermission();
            showAndroidToast("يرجى تفعيل الصلاحية للمنبه.");
            return;
        }

        Android.saveNotificationSettings(enabled, hour, minute);
        showAndroidToast("تم حفظ المنبه بنجاح");
    } else {
        showAndroidToast("حفظ الإعدادات (نسخة الويب التجريبية)");
    }
}

// Request Notification permission
function requestAndroidNotificationPermission() {
    if (typeof Android !== 'undefined' && Android.requestNotificationPermission) {
        if (!Android.hasNotificationPermission()) {
            Android.requestNotificationPermission();
        }
    }
}

// Show Android Native Toast
function showAndroidToast(message) {
    if (typeof Android !== 'undefined' && Android.showToast) {
        Android.showToast(message);
    } else {
        console.log('TOAST:', message);
    }
}

// Callback from Android when permission granted
function onPermissionGranted() {
    showAndroidToast("تم تفعيل صلاحية الإشعارات بنجاح.");
    initNotificationSettings();
}
window.onPermissionGranted = onPermissionGranted;

// 📥 Export data to local JSON file
// Build the full backup object (includes all khatmahs + progress)
function buildBackupObject() {
    return {
        quran_active_khatmahs: activeKhatmahs,
        quran_active_khatmah_id: activeKhatmahId,
        quran_current_page: currentPage,
        quran_daily_goal: dailyGoal,
        quran_khatmah_start_date: khatmahStartDate,
        quran_reading_history: readingHistory,
        quran_khatmahs: completedKhatmahs,
        quran_makeup_days: makeupDays,
        quran_last_makeup_week: lastMakeupWeek,
        quran_makeup_applied_date: makeupAppliedDate
    };
}

// 📥 Export — via native file save (SAF); web fallback for the browser
function exportBackupData() {
    const json = JSON.stringify(buildBackupObject(), null, 2);
    const filename = `khatmati_backup_${getLocalDateString()}.json`;

    if (typeof Android !== 'undefined' && Android.exportData) {
        Android.exportData(filename, json);
    } else {
        const dataStr = "data:application/json;charset=utf-8," + encodeURIComponent(json);
        const a = document.createElement('a');
        a.setAttribute('href', dataStr);
        a.setAttribute('download', filename);
        document.body.appendChild(a);
        a.click();
        a.remove();
        showAndroidToast("تم تصدير النسخة الاحتياطية");
    }
}

// Apply a parsed backup object to the app state
async function applyBackupObject(data) {
    try {
        const hasModern = Array.isArray(data.quran_active_khatmahs) && data.quran_active_khatmahs.length > 0;
        if (!hasModern && data.quran_current_page === undefined) {
            throw new Error("invalid backup");
        }

        readingHistory = data.quran_reading_history || [];
        completedKhatmahs = data.quran_khatmahs || [];
        makeupDays = data.quran_makeup_days || [];
        lastMakeupWeek = data.quran_last_makeup_week || '';
        makeupAppliedDate = data.quran_makeup_applied_date || '';

        if (hasModern) {
            activeKhatmahs = data.quran_active_khatmahs;
            activeKhatmahId = data.quran_active_khatmah_id || activeKhatmahs[0].id;
            bindActiveKhatmah(); // load globals from the active khatmah
        } else {
            // Legacy single-khatmah backup
            currentPage = parseInt(data.quran_current_page || '1');
            dailyGoal = parseInt(data.quran_daily_goal || '10');
            khatmahStartDate = data.quran_khatmah_start_date || (getLocalDateString() + 'T00:00:00');
        }

        saveStateToStorage();
        refreshCalculations();

        showAndroidToast("تم استيراد النسخة الاحتياطية");
        await showCustomAlert("👍 تم استيراد بياناتك بنجاح واسترجاع تقدّمك في الختمات والأهداف والتقويم!", "استيراد البيانات");
    } catch (e) {
        console.error('Error importing backup:', e);
        await showCustomAlert("❌ ملف النسخة الاحتياطية غير صالح أو تالف.", "فشل الاستيراد");
    }
}

// Callback from native import (file content as string)
function onDataImported(jsonStr) {
    let data;
    try {
        data = JSON.parse(jsonStr);
    } catch (e) {
        showCustomAlert("❌ الملف غير صالح.", "فشل الاستيراد");
        return;
    }
    applyBackupObject(data);
}
window.onDataImported = onDataImported;

// 📤 Web fallback: import from a chosen file input
function importBackupData(file) {
    const reader = new FileReader();
    reader.onload = (event) => onDataImported(event.target.result);
    reader.readAsText(file);
}

// Calculate number of unique Surahs read so far
function calculateSurahsRead(page) {
    if (page <= 0) return 0;
    if (page >= 604) return 114;
    const uniqueSurahs = new Set();
    for (let i = 0; i < page; i++) {
        if (quranPages[i]) {
            uniqueSurahs.add(quranPages[i].surah_number);
        }
    }
    return uniqueSurahs.size;
}

// Calculate number of Ayahs read so far
function calculateAyahsRead(page) {
    if (page >= 604) return 6236;
    if (page <= 0) return 0;

    const surahAyahs = SURAH_AYAH_COUNTS;

    const nextPageData = quranPages[page]; // quranPages is 0-indexed, so index page is page P + 1
    if (!nextPageData) return 0;

    const nextSurah = nextPageData.surah_number;
    const nextAyah = nextPageData.ayah_number;

    let total = 0;
    for (let s = 1; s < nextSurah; s++) {
        total += surahAyahs[s - 1];
    }
    total += (nextAyah - 1);
    return total;
}

// Fixed page ranges of all 114 Surahs
const SURAH_RANGES = [
  {number:1,start:1,end:1},{number:2,start:2,end:49},{number:3,start:50,end:76},{number:4,start:77,end:106},{number:5,start:106,end:127},{number:6,start:128,end:150},{number:7,start:151,end:176},{number:8,start:177,end:186},{number:9,start:187,end:207},{number:10,start:208,end:221},{number:11,start:221,end:235},{number:12,start:235,end:248},{number:13,start:249,end:255},{number:14,start:255,end:261},{number:15,start:262,end:267},{number:16,start:267,end:281},{number:17,start:282,end:293},{number:18,start:293,end:304},{number:19,start:305,end:312},{number:20,start:312,end:321},{number:21,start:322,end:331},{number:22,start:332,end:341},{number:23,start:342,end:349},{number:24,start:350,end:359},{number:25,start:359,end:366},{number:26,start:367,end:376},{number:27,start:377,end:385},{number:28,start:385,end:396},{number:29,start:396,end:404},{number:30,start:404,end:410},{number:31,start:411,end:414},{number:32,start:415,end:417},{number:33,start:418,end:427},{number:34,start:428,end:434},{number:35,start:434,end:440},{number:36,start:440,end:445},{number:37,start:446,end:452},{number:38,start:453,end:458},{number:39,start:458,end:467},{number:40,start:467,end:476},{number:41,start:477,end:482},{number:42,start:483,end:489},{number:43,start:489,end:495},{number:44,start:496,end:498},{number:45,start:499,end:502},{number:46,start:502,end:506},{number:47,start:507,end:510},{number:48,start:511,end:515},{number:49,start:515,end:517},{number:50,start:518,end:520},{number:51,start:520,end:523},{number:52,start:523,end:525},{number:53,start:526,end:528},{number:54,start:528,end:531},{number:55,start:531,end:534},{number:56,start:534,end:537},{number:57,start:537,end:541},{number:58,start:542,end:545},{number:59,start:545,end:548},{number:60,start:549,end:551},{number:61,start:551,end:552},{number:62,start:553,end:554},{number:63,start:554,end:555},{number:64,start:556,end:557},{number:65,start:558,end:559},{number:66,start:560,end:561},{number:67,start:562,end:564},{number:68,start:564,end:566},{number:69,start:566,end:568},{number:70,start:568,end:570},{number:71,start:570,end:571},{number:72,start:572,end:573},{number:73,start:574,end:575},{number:74,start:575,end:577},{number:75,start:577,end:578},{number:76,start:578,end:580},{number:77,start:580,end:581},{number:78,start:582,end:583},{number:79,start:583,end:584},{number:80,start:585,end:585},{number:81,start:586,end:586},{number:82,start:587,end:587},{number:83,start:587,end:589},{number:84,start:589,end:589},{number:85,start:590,end:590},{number:86,start:591,end:591},{number:87,start:591,end:592},{number:88,start:592,end:592},{number:89,start:593,end:594},{number:90,start:594,end:594},{number:91,start:595,end:595},{number:92,start:595,end:596},{number:93,start:596,end:596},{number:94,start:596,end:596},{number:95,start:597,end:597},{number:96,start:597,end:597},{number:97,start:598,end:598},{number:98,start:598,end:599},{number:99,start:599,end:599},{number:100,start:599,end:600},{number:101,start:600,end:600},{number:102,start:600,end:600},{number:103,start:601,end:601},{number:104,start:601,end:601},{number:105,start:601,end:601},{number:106,start:602,end:602},{number:107,start:602,end:602},{number:108,start:602,end:602},{number:109,start:603,end:603},{number:110,start:603,end:603},{number:111,start:603,end:603},{number:112,start:604,end:604},{number:113,start:604,end:604},{number:114,start:604,end:604}
];

// Calculate number of unique Surahs read in a page range (inclusive)
function calculateSurahsReadInRange(startP, endP) {
    if (endP < startP) return 0;
    let count = 0;
    for (const range of SURAH_RANGES) {
        if (Math.max(startP, range.start) <= Math.min(endP, range.end)) {
            count++;
        }
    }
    return count;
}

// Custom elegant alert and confirm dialog promises
function showCustomAlert(message, title = "تنبيه") {
    return new Promise((resolve) => {
        const modal = document.getElementById('modal-custom-dialog');
        const titleEl = document.getElementById('custom-dialog-title');
        const messageEl = document.getElementById('custom-dialog-message');
        const cancelBtn = document.getElementById('btn-custom-dialog-cancel');
        const okBtn = document.getElementById('btn-custom-dialog-ok');
        
        titleEl.innerText = title;
        messageEl.innerText = message;
        cancelBtn.style.display = 'none'; // Alert only has OK button
        okBtn.innerText = 'موافق';
        
        const onOk = () => {
            modal.classList.remove('open');
            okBtn.removeEventListener('click', onOk);
            resolve();
        };
        
        okBtn.addEventListener('click', onOk);
        modal.classList.add('open');
    });
}

function showCustomConfirm(message, title = "تأكيد") {
    return new Promise((resolve) => {
        const modal = document.getElementById('modal-custom-dialog');
        const titleEl = document.getElementById('custom-dialog-title');
        const messageEl = document.getElementById('custom-dialog-message');
        const cancelBtn = document.getElementById('btn-custom-dialog-cancel');
        const okBtn = document.getElementById('btn-custom-dialog-ok');
        
        titleEl.innerText = title;
        messageEl.innerText = message;
        cancelBtn.style.display = 'block'; // Show cancel button
        cancelBtn.innerText = 'إلغاء';
        okBtn.innerText = 'تأكيد';
        
        const onOk = () => {
            modal.classList.remove('open');
            okBtn.removeEventListener('click', onOk);
            cancelBtn.removeEventListener('click', onCancel);
            resolve(true);
        };
        
        const onCancel = () => {
            modal.classList.remove('open');
            okBtn.removeEventListener('click', onOk);
            cancelBtn.removeEventListener('click', onCancel);
            resolve(false);
        };
        
        okBtn.addEventListener('click', onOk);
        cancelBtn.addEventListener('click', onCancel);
        modal.classList.add('open');
    });
}

// ============================================================
//  In-app update system
//  Java side: UpdateManager.java + MainActivity's Android bridge.
//  The Java side calls onUpdateAvailable / onUpdateNotAvailable /
//  onUpdateProgress / onUpdateDownloaded / onUpdateError below.
// ============================================================

let pendingUpdateInfo = null;
let updateCheckIsManual = false;

function initUpdateSystem() {
    const bridge = (typeof Android !== 'undefined' && Android.checkForUpdate) ? Android : null;

    // Show the real installed version in the "about" card.
    const versionEl = document.getElementById('about-version');
    if (versionEl && bridge && Android.getAppVersionName) {
        try {
            const v = Android.getAppVersionName();
            if (v) versionEl.innerText = 'نسخة ' + v;
        } catch (e) { /* keep the static text */ }
    }

    const checkBtn = document.getElementById('btn-check-update');
    if (checkBtn) {
        checkBtn.addEventListener('click', () => {
            if (!bridge) {
                setUpdateStatus('التحديث متاح داخل التطبيق فقط.');
                return;
            }
            updateCheckIsManual = true;
            checkBtn.disabled = true;
            setUpdateStatus('جارٍ التحقق…');
            Android.checkForUpdate(true);
            // Re-enable even if the network call hangs.
            setTimeout(() => { checkBtn.disabled = false; }, 20000);
        });
    }

    const laterBtn = document.getElementById('btn-update-later');
    if (laterBtn) {
        laterBtn.addEventListener('click', () => {
            if (pendingUpdateInfo && pendingUpdateInfo.mandatory) return; // forced update
            // Only hides the dialog for now — it reappears on the next launch
            // until the update is actually installed.
            closeUpdateModal();
        });
    }

    const nowBtn = document.getElementById('btn-update-now');
    if (nowBtn) {
        nowBtn.addEventListener('click', () => {
            if (!(typeof Android !== 'undefined' && Android.startUpdateDownload)) return;
            nowBtn.disabled = true;
            laterBtn.disabled = true;
            document.getElementById('update-progress-wrap').style.display = 'block';
            setUpdateProgress(0);
            Android.startUpdateDownload();
        });
    }
}

function setUpdateStatus(text) {
    const el = document.getElementById('update-status');
    if (el) el.innerText = text || '';
}

function setUpdateProgress(percent) {
    const fill = document.getElementById('update-progress-fill');
    const label = document.getElementById('update-progress-label');
    if (percent < 0) {
        // Unknown size — show an indeterminate-looking bar.
        if (fill) fill.style.width = '100%';
        if (label) label.innerText = 'جارٍ التنزيل…';
        return;
    }
    if (fill) fill.style.width = Math.max(0, Math.min(100, percent)) + '%';
    if (label) label.innerText = 'جارٍ التنزيل… ' + percent + '%';
}

function closeUpdateModal() {
    const modal = document.getElementById('modal-update');
    if (modal) modal.classList.remove('open');
}

/** Called from Java with the update manifest JSON when a newer build exists. */
function onUpdateAvailable(jsonText) {
    let info;
    try {
        info = JSON.parse(jsonText);
    } catch (e) {
        return;
    }
    pendingUpdateInfo = info;

    const checkBtn = document.getElementById('btn-check-update');
    if (checkBtn) checkBtn.disabled = false;
    setUpdateStatus('يتوفر إصدار جديد: ' + (info.versionName || ''));
    resolvePullRefresh('يتوفر تحديث جديد ' + (info.versionName || ''));

    // The dialog is shown on every launch while an update is pending, so the
    // user always knows a newer version is waiting.
    updateCheckIsManual = false;

    document.getElementById('update-version').innerText =
        'الإصدار ' + (info.versionName || '') + ' جاهز للتثبيت';
    document.getElementById('update-notes').innerText = info.notes || 'تحسينات وإصلاحات عامة.';

    const laterBtn = document.getElementById('btn-update-later');
    const nowBtn = document.getElementById('btn-update-now');
    nowBtn.disabled = false;
    laterBtn.disabled = false;
    laterBtn.style.display = info.mandatory ? 'none' : 'block';
    document.getElementById('update-title').innerText =
        info.mandatory ? 'تحديث مطلوب' : 'يتوفر تحديث جديد';

    document.getElementById('update-progress-wrap').style.display = 'none';
    document.getElementById('modal-update').classList.add('open');
}

/** Called from Java when the check succeeded but no newer build exists. */
function onUpdateNotAvailable(detail) {
    const checkBtn = document.getElementById('btn-check-update');
    if (checkBtn) checkBtn.disabled = false;
    updateCheckIsManual = false;
    setUpdateStatus('أنت على أحدث إصدار ✅' + (detail ? '  (' + detail + ')' : ''));
    resolvePullRefresh('التطبيق محدَّث — لا يوجد إصدار جديد ✅');
}

function onUpdateProgress(percent) {
    setUpdateProgress(percent);
}

/** The APK finished downloading; the system installer is opening. */
function onUpdateDownloaded() {
    const label = document.getElementById('update-progress-label');
    if (label) label.innerText = 'اكتمل التنزيل — جارٍ فتح شاشة التثبيت…';
    setUpdateProgress(100);
}

function onUpdateError(message) {
    const checkBtn = document.getElementById('btn-check-update');
    if (checkBtn) checkBtn.disabled = false;
    updateCheckIsManual = false;
    resolvePullRefresh('تعذّر التحقق من التحديث');

    const modal = document.getElementById('modal-update');
    const isModalOpen = modal && modal.classList.contains('open');

    if (isModalOpen) {
        const label = document.getElementById('update-progress-label');
        if (label) label.innerText = message || 'حدث خطأ أثناء التحديث.';
        const nowBtn = document.getElementById('btn-update-now');
        const laterBtn = document.getElementById('btn-update-later');
        if (nowBtn) { nowBtn.disabled = false; nowBtn.innerText = 'إعادة المحاولة'; }
        if (laterBtn) { laterBtn.disabled = false; laterBtn.style.display = 'block'; }
    } else {
        setUpdateStatus(message || 'تعذّر التحقق من التحديثات.');
    }
}

// ============================================================
//  Pull to refresh — home tab only.
//  Dragging the page down from the very top refreshes the UI and
//  checks for an app update at the same time.
// ============================================================

const PTR_TRIGGER_DISTANCE = 70;   // px the user must drag to fire a refresh
const PTR_MAX_PULL = 110;          // px the indicator can travel

/**
 * The whole document scrolls (.app-container is min-height:100vh), so the page
 * offset — not any inner element's scrollTop — decides whether we are at the top.
 */
function pageScrollTop() {
    return window.pageYOffset
        || (document.documentElement && document.documentElement.scrollTop)
        || (document.body && document.body.scrollTop)
        || 0;
}

function initPullToRefresh() {
    const indicator = document.getElementById('ptr-indicator');
    if (!indicator) return;

    let startY = 0;
    let startX = 0;
    let pulling = false;      // finger is down at the top of the page
    let engaged = false;      // the pull gesture actually took over
    let distance = 0;
    let refreshing = false;

    const homeIsActive = () => {
        const home = document.getElementById('tab-home');
        return home && home.classList.contains('active');
    };

    const moveIndicator = (px, opacity) => {
        indicator.style.transform = 'translate(-50%, ' + px + 'px)';
        indicator.style.opacity = opacity;
    };

    const reset = (animated) => {
        indicator.style.transition = animated ? 'transform 0.25s ease, opacity 0.25s ease' : '';
        indicator.classList.remove('loading');
        moveIndicator(-60, 0);
    };

    document.addEventListener('touchstart', (e) => {
        pulling = false;
        engaged = false;
        distance = 0;

        if (refreshing || !homeIsActive() || e.touches.length !== 1) return;
        if (pageScrollTop() > 0) return;   // only from the very top of the page

        startY = e.touches[0].clientY;
        startX = e.touches[0].clientX;
        pulling = true;
        indicator.style.transition = '';
    }, { passive: true });

    document.addEventListener('touchmove', (e) => {
        if (!pulling) return;

        const dy = e.touches[0].clientY - startY;
        const dx = e.touches[0].clientX - startX;

        // Any upward move, sideways swipe, or the page having scrolled away
        // from the top means this is a normal scroll — hand it back untouched.
        if (dy <= 0 || Math.abs(dx) > Math.abs(dy) || pageScrollTop() > 0) {
            if (engaged) reset(true);
            pulling = false;
            engaged = false;
            return;
        }

        // Ignore the first few pixels so taps and small drags behave normally.
        if (!engaged) {
            if (dy < 12) return;
            engaged = true;
        }

        // Rubber-band effect: the further you pull, the slower it follows.
        distance = Math.min(PTR_MAX_PULL, dy * 0.5);
        moveIndicator(distance - 20, Math.min(1, distance / PTR_TRIGGER_DISTANCE));

        // Stop the page itself from scrolling/bouncing while pulling.
        if (e.cancelable) e.preventDefault();
    }, { passive: false });

    const onTouchEnd = () => {
        if (!engaged) {
            pulling = false;
            return;
        }
        engaged = false;
        if (!pulling) return;
        pulling = false;

        if (distance >= PTR_TRIGGER_DISTANCE) {
            refreshing = true;
            indicator.style.transition = 'transform 0.2s ease';
            indicator.classList.add('loading');
            moveIndicator(45, 1);

            runPullRefresh(() => {
                refreshing = false;
                reset(true);
            });
        } else {
            reset(true);
        }
    };

    document.addEventListener('touchend', onTouchEnd, { passive: true });
    document.addEventListener('touchcancel', onTouchEnd, { passive: true });
}

/** Recomputes the UI and asks the server for a newer app version. */
function runPullRefresh(done) {
    let finished = false;
    const finish = (message) => {
        if (finished) return;
        finished = true;
        showPtrToast(message);
        done();
    };

    try {
        refreshCalculations();
    } catch (e) { /* keep going — the update check still matters */ }

    if (typeof Android !== 'undefined' && Android.checkForUpdate) {
        // A pending update opens its own dialog; these callbacks only close the spinner.
        pullRefreshPending = finish;
        updateCheckIsManual = true;
        Android.checkForUpdate(true);
        // Never leave the spinner running if the network hangs.
        setTimeout(() => finish('تم تحديث البيانات'), 12000);
    } else {
        setTimeout(() => finish('تم تحديث البيانات'), 600);
    }
}

/** Set while a pull-refresh waits for the update check to answer. */
let pullRefreshPending = null;

function resolvePullRefresh(message) {
    if (!pullRefreshPending) return;
    const fn = pullRefreshPending;
    pullRefreshPending = null;
    fn(message);
}

function showPtrToast(message) {
    if (!message) return;
    let toast = document.getElementById('ptr-toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'ptr-toast';
        toast.className = 'ptr-toast';
        document.body.appendChild(toast);
    }
    toast.innerText = message;
    // Restart the animation even if a previous toast is still visible.
    toast.classList.remove('show');
    void toast.offsetWidth;
    toast.classList.add('show');
    clearTimeout(toast._hideTimer);
    toast._hideTimer = setTimeout(() => toast.classList.remove('show'), 2200);
}
