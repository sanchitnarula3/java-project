/**
 * OMR Scanner — Camera & Application Logic
 *
 * Handles:
 * - Tab navigation
 * - Quiz creation & answer key management
 * - Camera access & frame capture
 * - Sending frames to the backend for scanning
 * - Displaying results in the dashboard
 */

// ═══════════════════════════════════════
//  State
// ═══════════════════════════════════════

const state = {
    activeQuiz: null,        // Current quiz object
    answerKey: {},            // { 1: 'A', 2: 'B', ... }
    cameraStream: null,      // MediaStream
    facingMode: 'environment',
    autoScanInterval: null,
    isScanning: false,
    lastResult: null,
    desktopCameraActive: false,
    desktopCameraTimer: null,
    desktopCameraReady: false,
};

const API_BASE = document.querySelector('meta[name="app-context"]')?.content || ''; // Tomcat context path

// ═══════════════════════════════════════
//  DOM References
// ═══════════════════════════════════════

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => document.querySelectorAll(sel);

const els = {
    // Nav
    navTabs: $$('.nav-tab'),

    // Setup
    quizTitle: $('#quizTitle'),
    numQuestions: $('#numQuestions'),
    maximumMarks: $('#maximumMarks'),
    savedQuizSelect: $('#savedQuizSelect'),
    btnLoadQuiz: $('#btnLoadQuiz'),
    btnCreateQuiz: $('#btnCreateQuiz'),
    quizCreationCard: $('#quizCreationCard'),
    answerKeyCard: $('#answerKeyCard'),
    activeQuizLabel: $('#activeQuizLabel'),
    answerGrid: $('#answerGrid'),
    answeredCount: $('#answeredCount'),
    totalCount: $('#totalCount'),
    btnClearAll: $('#btnClearAll'),
    btnSaveKey: $('#btnSaveKey'),
    statusBanner: $('#statusBanner'),

    // Camera
    cameraFeed: $('#cameraFeed'),
    desktopCameraFeed: $('#desktopCameraFeed'),
    cameraCanvas: $('#cameraCanvas'),
    noCameraMsg: $('#noCameraMsg'),
    btnStartCamera: $('#btnStartCamera'),
    btnCapture: $('#btnCapture'),
    btnToggleCamera: $('#btnToggleCamera'),
    btnAutoScan: $('#btnAutoScan'),
    btnChooseImage: $('#btnChooseImage'),
    imageFileInput: $('#imageFileInput'),
    autoScanIndicator: $('#autoScanIndicator'),
    scanStatus: $('#scanStatus'),
    alignText: $('#alignText'),

    // Results
    studentBannerCard: $('#studentBannerCard'),
    detectedRollBadge: $('#detectedRollBadge'),
    btnEditRoll: $('#btnEditRoll'),
    scoreOverview: $('#scoreOverview'),
    scoreRing: $('#scoreRing'),
    scoreValue: $('#scoreValue'),
    marksValue: $('#marksValue'),
    correctNum: $('#correctNum'),
    wrongNum: $('#wrongNum'),
    blankNum: $('#blankNum'),
    ambiguousNum: $('#ambiguousNum'),
    detailCard: $('#detailCard'),
    resultsBody: $('#resultsBody'),
    rosterCard: $('#rosterCard'),
    rosterBody: $('#rosterBody'),
    btnRefreshRoster: $('#btnRefreshRoster'),
    btnDownloadCsv: $('#btnDownloadCsv'),
    resultTimestamp: $('#resultTimestamp'),
    resultsSubtitle: $('#resultsSubtitle'),
    emptyState: $('#emptyState'),
};

// ═══════════════════════════════════════
//  Tab Navigation
// ═══════════════════════════════════════

els.navTabs.forEach(tab => {
    tab.addEventListener('click', () => {
        const target = tab.dataset.tab;

        // Update active tab
        els.navTabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');

        // Show corresponding content
        $$('.tab-content').forEach(tc => tc.classList.remove('active'));
        $(`#tab${capitalize(target)}`).classList.add('active');

        // Auto-start camera when switching to camera tab
        if (target === 'camera' && !state.cameraStream) {
            startCamera();
        }

        // Refresh roster when switching to results tab
        if (target === 'results' && state.activeQuiz) {
            loadSubmissions(state.activeQuiz.id);
        }
    });
});

function capitalize(str) {
    return str.charAt(0).toUpperCase() + str.slice(1);
}

// ═══════════════════════════════════════
//  Quiz Creation
// ═══════════════════════════════════════

els.btnCreateQuiz.addEventListener('click', async () => {
    const title = els.quizTitle.value.trim() || 'Untitled Quiz';
    const numQ = parseInt(els.numQuestions.value);
    const maximumMarks = Number(els.maximumMarks.value);
    if (!Number.isFinite(maximumMarks) || maximumMarks <= 0) {
        showToast('error', 'Enter a maximum mark greater than zero');
        return;
    }

    try {
        els.btnCreateQuiz.disabled = true;
        els.btnCreateQuiz.textContent = 'Creating…';

        const res = await fetch(`${API_BASE}/api/quizzes`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, numberOfQuestions: numQ, maximumMarks }),
        });

        if (!res.ok) throw new Error('Failed to create quiz');
        const quiz = await res.json();

        state.activeQuiz = quiz;
        state.answerKey = {};

        showToast('success', `Quiz "${quiz.title}" created`);
        showAnswerKeyEditor(quiz);
        loadSavedQuizzes();

    } catch (err) {
        showToast('error', err.message);
    } finally {
        els.btnCreateQuiz.disabled = false;
        els.btnCreateQuiz.innerHTML = `
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Create Quiz`;
    }
});

function showAnswerKeyEditor(quiz) {
    state.answerKey = { ...(quiz.answerKey || {}) };
    els.answerKeyCard.classList.remove('hidden');
    els.activeQuizLabel.textContent = `${quiz.title} · ${quiz.numberOfQuestions} questions · ${quiz.maximumMarks ?? 10} marks · ID: ${quiz.id}`;
    els.totalCount.textContent = quiz.numberOfQuestions;

    // Build answer rows
    els.answerGrid.innerHTML = '';
    for (let q = 1; q <= quiz.numberOfQuestions; q++) {
        const row = document.createElement('div');
        row.className = 'answer-row';
        row.innerHTML = `
            <span class="question-num">Q${q}</span>
            <div class="option-buttons">
                ${['A','B','C','D'].map(opt => `
                    <button class="option-btn" data-q="${q}" data-opt="${opt}">${opt}</button>
                `).join('')}
            </div>
        `;
        els.answerGrid.appendChild(row);
    }

    // Option button click handlers
    els.answerGrid.querySelectorAll('.option-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const q = parseInt(btn.dataset.q);
            const opt = btn.dataset.opt;

            // Deselect siblings
            btn.parentElement.querySelectorAll('.option-btn').forEach(b => b.classList.remove('selected'));
            btn.classList.add('selected');

            state.answerKey[q] = opt;
            updateKeyProgress();
        });
            if ((quiz.answerKey || {})[btn.dataset.q] === btn.dataset.opt) btn.classList.add('selected');
    });

    if (els.btnDownloadCsv) els.btnDownloadCsv.href = `${API_BASE}/api/scan/csv/${quiz.id}`;

    updateKeyProgress();

    if (Object.keys(state.answerKey).length === quiz.numberOfQuestions) {
        els.btnCapture.disabled = false;
        els.btnChooseImage.disabled = false;
        updateScanStatus('ready', `Ready — ${quiz.title}`);
        els.statusBanner.classList.remove('hidden');
    }

    // Smooth scroll to answer key card
    els.answerKeyCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function loadSavedQuizzes() {
    if (!els.savedQuizSelect) return;
    try {
        const res = await fetch(`${API_BASE}/api/quizzes`);
        if (!res.ok) return;
        const quizzes = await res.json();
        els.savedQuizSelect.innerHTML = '';
        if (!quizzes.length) {
            els.savedQuizSelect.add(new Option('No saved quizzes yet', ''));
            els.btnLoadQuiz.disabled = true;
            return;
        }
        quizzes.forEach(quiz => {
            const answerCount = Object.keys(quiz.answerKey || {}).length;
            const option = new Option(`${quiz.title} · ${answerCount}/${quiz.numberOfQuestions} answers · ${quiz.maximumMarks ?? 10} marks`, quiz.id);
            els.savedQuizSelect.add(option);
        });
        els.btnLoadQuiz.disabled = false;
    } catch (error) {
        console.error('Could not load saved quizzes', error);
    }
}

els.btnLoadQuiz?.addEventListener('click', async () => {
    const quizId = els.savedQuizSelect.value;
    if (!quizId) return;
    try {
        const res = await fetch(`${API_BASE}/api/quizzes/${encodeURIComponent(quizId)}`);
        if (!res.ok) throw new Error('Could not load this saved quiz');
        const quiz = await res.json();
        state.activeQuiz = quiz;
        els.quizTitle.value = quiz.title || '';
        els.numQuestions.value = String(quiz.numberOfQuestions);
        els.maximumMarks.value = String(quiz.maximumMarks ?? 10);
        showAnswerKeyEditor(quiz);
        showToast('success', `Loaded ${quiz.title}`);
    } catch (error) {
        showToast('error', error.message);
    }
});

function updateKeyProgress() {
    const count = Object.keys(state.answerKey).length;
    const total = state.activeQuiz ? state.activeQuiz.numberOfQuestions : 15;
    els.answeredCount.textContent = count;
    els.btnSaveKey.disabled = (count < total);
}

els.btnClearAll.addEventListener('click', () => {
    state.answerKey = {};
    els.answerGrid.querySelectorAll('.option-btn').forEach(b => b.classList.remove('selected'));
    updateKeyProgress();
});

// ═══════════════════════════════════════
//  Save Answer Key
// ═══════════════════════════════════════

els.btnSaveKey.addEventListener('click', async () => {
    if (!state.activeQuiz) return;

    try {
        els.btnSaveKey.disabled = true;
        els.btnSaveKey.textContent = 'Saving…';

        const res = await fetch(`${API_BASE}/api/quizzes/${state.activeQuiz.id}/answer-key`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ answers: state.answerKey }),
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.error || 'Failed to save answer key');
        }

        const updatedQuiz = await res.json();
        state.activeQuiz = updatedQuiz;

        showToast('success', 'Answer key saved successfully');
        loadSavedQuizzes();
        els.statusBanner.classList.remove('hidden');

        // Enable scan button
        els.btnCapture.disabled = false;
        els.btnChooseImage.disabled = false;
        updateScanStatus('ready', `Ready — ${updatedQuiz.title}`);

    } catch (err) {
        showToast('error', err.message);
    } finally {
        els.btnSaveKey.disabled = false;
        els.btnSaveKey.innerHTML = `
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/>
                <polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/>
            </svg>
            Save Answer Key`;
    }
});

// ═══════════════════════════════════════
//  Camera
// ═══════════════════════════════════════

async function startCamera() {
    if (navigator.userAgent.includes('OMRScannerDesktop')) {
        const bridge = window.omrDesktopCamera;
        if (!bridge) {
            els.noCameraMsg.classList.remove('hidden');
            updateScanStatus('error', 'Desktop camera is still initializing. Try again in a moment.');
            return;
        }
        try {
            bridge.start();
            state.desktopCameraActive = true;
            state.desktopCameraReady = false;
            els.btnCapture.disabled = true;
            els.cameraFeed.style.display = 'none';
            els.desktopCameraFeed.classList.remove('hidden');
            els.noCameraMsg.classList.remove('hidden');
            els.noCameraMsg.querySelector('p, span')?.replaceChildren('Starting camera…');
            if (state.desktopCameraTimer) clearInterval(state.desktopCameraTimer);
            state.desktopCameraTimer = setInterval(() => {
                const cameraStatus = bridge.status();
                if (cameraStatus !== 'READY') {
                    if (!['STARTING', 'OPENING_CAMERA', 'WAITING'].includes(cameraStatus)) {
                        clearInterval(state.desktopCameraTimer);
                        state.desktopCameraTimer = null;
                        state.desktopCameraActive = false;
                        state.desktopCameraReady = false;
                        els.btnCapture.disabled = true;
                        els.noCameraMsg.querySelector('p').textContent = cameraStatus || 'Could not start the desktop camera';
                        els.noCameraMsg.classList.remove('hidden');
                        updateScanStatus('error', cameraStatus || 'Could not start the desktop camera');
                        showToast('error', cameraStatus || 'Could not start the desktop camera');
                    } else if (cameraStatus === 'OPENING_CAMERA') {
                        els.noCameraMsg.querySelector('p').textContent = 'Opening camera…';
                    } else if (cameraStatus === 'WAITING') {
                        els.noCameraMsg.querySelector('p').textContent = 'Waiting for the first camera frame…';
                    }
                    return;
                }
                els.noCameraMsg.classList.add('hidden');
                const frame = bridge.frameDataUrl();
                if (frame && els.desktopCameraFeed.src !== frame) els.desktopCameraFeed.src = frame;
                if (!state.desktopCameraReady) {
                    state.desktopCameraReady = true;
                    if (state.activeQuiz?.answerKey && Object.keys(state.activeQuiz.answerKey).length > 0) {
                        els.btnCapture.disabled = false;
                        els.btnChooseImage.disabled = false;
                        updateScanStatus('ready', `Ready — ${state.activeQuiz.title}`);
                    } else {
                        updateScanStatus('idle', 'Set up a quiz and answer key first');
                    }
                }
            }, 80);
        } catch (err) {
            state.desktopCameraActive = false;
            state.desktopCameraReady = false;
        els.btnCapture.disabled = true;
            els.noCameraMsg.classList.remove('hidden');
            updateScanStatus('error', err.message);
            showToast('error', err.message);
        }
        return;
    }

    if (!navigator.mediaDevices?.getUserMedia) {
        els.noCameraMsg.classList.remove('hidden');
        const message = 'Camera unavailable — allow camera access in browser settings or choose an answer sheet image';
        updateScanStatus('idle', message);
        return;
    }
    try {
        const constraints = {
            video: {
                facingMode: state.facingMode,
                width: { ideal: 1280 },
                height: { ideal: 960 },
            },
        };

        const stream = await navigator.mediaDevices.getUserMedia(constraints);
        state.cameraStream = stream;

        els.cameraFeed.srcObject = stream;
        els.noCameraMsg.classList.add('hidden');

        if (state.activeQuiz && state.activeQuiz.answerKey && Object.keys(state.activeQuiz.answerKey).length > 0) {
            els.btnCapture.disabled = false;
            updateScanStatus('ready', `Ready — ${state.activeQuiz.title}`);
        } else {
            updateScanStatus('idle', 'Set up a quiz and answer key first');
        }

    } catch (err) {
        console.error('Camera error:', err);
        els.noCameraMsg.classList.remove('hidden');
        showToast('info', 'Camera permission was denied or unavailable. Allow it in browser settings, then reload.');
    }
}

els.btnStartCamera.addEventListener('click', startCamera);

els.btnToggleCamera.addEventListener('click', async () => {
    if (navigator.userAgent.includes('OMRScannerDesktop')) {
        showToast('info', 'This Mac has one camera available.');
        return;
    }
    state.facingMode = state.facingMode === 'environment' ? 'user' : 'environment';

    if (state.cameraStream) {
        state.cameraStream.getTracks().forEach(t => t.stop());
        state.cameraStream = null;
    }

    await startCamera();
});

// ═══════════════════════════════════════
//  Capture & Scan
// ═══════════════════════════════════════

els.btnCapture.addEventListener('click', () => {
    if (!state.isScanning) {
        captureAndScan();
    }
});

els.btnChooseImage.addEventListener('click', () => els.imageFileInput.click());
els.imageFileInput.addEventListener('change', async () => {
    const file = els.imageFileInput.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
        showToast('error', 'Choose an image file');
        els.imageFileInput.value = '';
        return;
    }
    await sendImageToScanner(file, file.name || 'answer-sheet.jpg');
    els.imageFileInput.value = '';
});

async function captureAndScan() {
    if (state.isScanning || !state.activeQuiz) return;
    try {
        if (state.desktopCameraActive && window.omrDesktopCamera) {
            const dataUrl = window.omrDesktopCamera.captureDataUrl();
            if (!dataUrl) throw new Error('No camera frame is available yet. Wait for the preview and try again.');
            const [header, base64] = dataUrl.split(',');
            const bytes = Uint8Array.from(atob(base64), character => character.charCodeAt(0));
            const blob = new Blob([bytes], { type: header.match(/data:(.*?);/)?.[1] || 'image/jpeg' });
            await sendImageToScanner(blob, 'desktop-camera.jpg');
            return;
        }
        const video = els.cameraFeed;
        const canvas = els.cameraCanvas;
        if (!video.videoWidth || !video.videoHeight) throw new Error('Camera is not ready. Choose an answer sheet image instead.');
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(video, 0, 0);
        const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.85));
        if (!blob) throw new Error('Could not capture a frame from the camera');
        await sendImageToScanner(blob, 'frame.jpg');
    } catch (err) {
        updateScanStatus('error', err.message);
        showToast('error', err.message);
    }
}

async function sendImageToScanner(imageBlob, fileName) {
    if (state.isScanning || !state.activeQuiz) return;
    state.isScanning = true;
    els.btnCapture.classList.add('scanning');
    updateScanStatus('scanning', 'Scanning…');
    try {
        const formData = new FormData();
        formData.append('image', imageBlob, fileName);
        formData.append('quizId', state.activeQuiz.id);
        const res = await fetch(`${API_BASE}/api/scan`, { method: 'POST', body: formData });
        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.error || 'Scan failed');
        }
        const result = await res.json();
        state.lastResult = result;

        if (result.status === 'SUCCESS') {
            const marks = `${Number(result.marksAwarded || 0).toFixed(2)}/${Number(result.maximumMarks || 0).toFixed(2)} marks`;
            updateScanStatus('ready', `Scanned — ${marks}`);
            showToast('success', `${marks} · ${result.correctCount}/${result.totalQuestions} correct`);
            displayResults(result);
        } else if (result.status === 'NO_SHEET_DETECTED') {
            updateScanStatus('idle', 'No sheet detected — align paper and try again');
            showToast('info', result.statusMessage || 'No sheet detected');
        } else {
            updateScanStatus('error', result.statusMessage || 'Scan error');
            showToast('error', result.statusMessage || 'Processing error');
        }

    } catch (err) {
        updateScanStatus('error', err.message);
        showToast('error', err.message);
    } finally {
        state.isScanning = false;
        els.btnCapture.classList.remove('scanning');
    }
}

// ═══════════════════════════════════════
//  Auto-Scan
// ═══════════════════════════════════════

els.btnAutoScan.addEventListener('click', () => {
    if (state.autoScanInterval) {
        clearInterval(state.autoScanInterval);
        state.autoScanInterval = null;
        els.autoScanIndicator.classList.add('hidden');
        els.btnAutoScan.style.color = '';
        els.btnAutoScan.style.borderColor = '';
    } else {
        state.autoScanInterval = setInterval(() => {
            if (!state.isScanning) captureAndScan();
        }, 2000);
        els.autoScanIndicator.classList.remove('hidden');
        els.btnAutoScan.style.color = 'var(--accent-primary-light)';
        els.btnAutoScan.style.borderColor = 'var(--accent-primary)';
    }
});

function updateScanStatus(type, text) {
    const dot = els.scanStatus.querySelector('.status-dot');
    dot.className = `status-dot ${type}`;
    els.scanStatus.querySelector('span').textContent = text;

    // Update alignment text
    if (type === 'scanning') {
        els.alignText.textContent = 'Scanning…';
    } else if (type === 'ready') {
        els.alignText.textContent = 'Align OMR sheet within the frame';
    } else {
        els.alignText.textContent = text;
    }
}

// ═══════════════════════════════════════
//  Display Results
// ═══════════════════════════════════════

function displayResults(result) {
    // Show score overview
    els.scoreOverview.classList.remove('hidden');
    els.emptyState.classList.add('hidden');
    els.detailCard.classList.remove('hidden');

    // Animate score ring
    const pct = result.scorePercentage || 0;
    const circumference = 326.73; // 2 * π * 52
    const offset = circumference - (pct / 100) * circumference;
    els.scoreRing.style.strokeDashoffset = offset;
    els.scoreValue.textContent = `${pct}%`;
    if (els.marksValue) els.marksValue.textContent = `${Number(result.marksAwarded || 0).toFixed(2)} / ${Number(result.maximumMarks || state.activeQuiz?.maximumMarks || 0).toFixed(2)} marks`;

    // Update score cards
    els.correctNum.textContent = result.correctCount || 0;
    els.wrongNum.textContent = result.wrongCount || 0;
    els.blankNum.textContent = result.blankCount || 0;
    els.ambiguousNum.textContent = result.ambiguousCount || 0;

    // Update subtitle
    els.resultsSubtitle.textContent = state.activeQuiz
        ? `${state.activeQuiz.title} — ${result.totalQuestions} questions`
        : 'Latest scan';

    // Timestamp
    els.resultTimestamp.textContent = new Date(result.timestamp).toLocaleString();

    // Build per-question table
    els.resultsBody.innerHTML = '';
    const evalResults = result.evaluationResults || {};
    const detected = result.detectedAnswers || {};
    const confidence = result.confidenceScores || {};
    const answerKey = state.activeQuiz ? (state.activeQuiz.answerKey || {}) : {};

    const numQ = result.totalQuestions || 15;
    for (let q = 1; q <= numQ; q++) {
        const correct = answerKey[q] || '—';
        const det = detected[q] || '—';
        const conf = confidence[q] != null ? confidence[q] : 0;
        const outcome = evalResults[q] || 'PENDING';

        const confPct = Math.round(conf * 100);
        const confColor = conf >= 0.7 ? 'var(--success)' : conf >= 0.4 ? 'var(--warning)' : 'var(--error)';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>Q${q}</strong></td>
            <td>${correct}</td>
            <td>${det}</td>
            <td>
                <div class="confidence-bar">
                    <div class="conf-track">
                        <div class="conf-fill" style="width:${confPct}%;background:${confColor}"></div>
                    </div>
                    <span class="conf-value">${confPct}%</span>
                </div>
            </td>
            <td><span class="badge badge-${outcome.toLowerCase()}">${outcome}</span></td>
        `;
        els.resultsBody.appendChild(tr);
    }

    // Show student roll number banner
    if (els.studentBannerCard) {
        els.studentBannerCard.classList.remove('hidden');
        if (els.detectedRollBadge) {
            els.detectedRollBadge.textContent = result.rollNumber || 'Unknown';
        }
    }

    // Update CSV export link
    const quizId = result.quizId || state.activeQuiz?.id;
    if (els.btnDownloadCsv && quizId) {
        els.btnDownloadCsv.href = `${API_BASE}/api/scan/csv/${quizId}`;
    }

    // Auto-navigate to results tab
    els.navTabs.forEach(t => t.classList.remove('active'));
    $('#navResults').classList.add('active');
    $$('.tab-content').forEach(tc => tc.classList.remove('active'));
    $('#tabResults').classList.add('active');

    // Load full roster submissions for this subject from database
    if (quizId) {
        loadSubmissions(quizId);
    }
}

async function loadSubmissions(quizId) {
    if (!quizId) return;
    try {
        const res = await fetch(`${API_BASE}/api/scan/submissions/${quizId}`);
        if (!res.ok) return;
        const list = await res.json();

        if (els.rosterBody) {
            els.rosterBody.innerHTML = '';
            if (list && list.length > 0) {
                if (els.rosterCard) els.rosterCard.classList.remove('hidden');
                list.forEach((sub, idx) => {
                    const row = document.createElement('tr');
                    const timeStr = sub.timestamp ? new Date(sub.timestamp).toLocaleTimeString() : '—';
                    const pct = sub.scorePercentage != null ? `${sub.scorePercentage}%` : '—';
                    row.innerHTML = `
                        <td>${idx + 1}</td>
                        <td><strong style="font-family: monospace; letter-spacing: 0.5px; color: var(--text-primary);">${sub.rollNumber || 'Unknown'}</strong></td>
                        <td><strong>${Number(sub.marksAwarded || 0).toFixed(2)} / ${Number(sub.maximumMarks || 0).toFixed(2)}</strong></td>
                        <td><span class="badge ${sub.scorePercentage >= 50 ? 'badge-correct' : 'badge-wrong'}">${pct}</span></td>
                        <td>${sub.correctCount}</td>
                        <td>${sub.wrongCount}</td>
                        <td><span class="badge badge-${(sub.status || 'SUCCESS').toLowerCase()}">${sub.status || 'SUCCESS'}</span></td>
                        <td><small style="color: var(--text-secondary);">${timeStr}</small></td>
                    `;
                    els.rosterBody.appendChild(row);
                });
            } else {
                if (els.rosterCard) els.rosterCard.classList.remove('hidden');
                els.rosterBody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text-secondary);padding:18px;">No submissions recorded yet for this subject.</td></tr>';
            }
        }
    } catch (err) {
        console.error('Failed to load roster submissions', err);
    }
}

if (els.btnRefreshRoster) {
    els.btnRefreshRoster.addEventListener('click', () => {
        const qId = state.activeQuiz?.id || state.lastResult?.quizId;
        if (qId) {
            loadSubmissions(qId);
            showToast('info', 'Submissions list refreshed');
        }
    });
}

if (els.btnEditRoll) {
    els.btnEditRoll.addEventListener('click', async () => {
        if (!state.lastResult) return;
        const currentRoll = state.lastResult.rollNumber || '';
        const newRoll = prompt('Enter the 10-digit Roll Number for this student:', currentRoll === 'Unknown' ? '' : currentRoll);
        if (newRoll && newRoll.trim()) {
            const clean = newRoll.trim();
            const qId = state.lastResult.quizId || state.activeQuiz?.id;
            if (!/^\d{10}$/.test(clean)) {
                showToast('error', 'Roll number must contain exactly 10 digits');
                return;
            }
            try {
                const res = await fetch(`${API_BASE}/api/scan/submissions/${encodeURIComponent(qId)}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ oldRollNumber: currentRoll, newRollNumber: clean, timestamp: state.lastResult.timestamp }),
                });
                const updated = await res.json();
                if (!res.ok) throw new Error(updated.error || 'Could not update the roll number');
                state.lastResult = updated;
                if (els.detectedRollBadge) els.detectedRollBadge.textContent = clean;
                await loadSubmissions(qId);
                showToast('success', `Roll number updated to ${clean}`);
            } catch (error) {
                showToast('error', error.message);
            }
        }
    });
}

// ═══════════════════════════════════════
//  Toast Notifications
// ═══════════════════════════════════════

function showToast(type, message) {
    const container = $('#toastContainer');
    const icons = { success: '✓', error: '✕', info: 'ℹ' };

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `
        <span class="toast-icon">${icons[type] || 'ℹ'}</span>
        <span>${message}</span>
    `;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.animation = 'toastOut 300ms ease forwards';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

// ═══════════════════════════════════════
//  Init
// ═══════════════════════════════════════

// Focus title input on load
els.quizTitle.focus();
loadSavedQuizzes();
