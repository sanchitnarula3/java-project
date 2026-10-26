<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<% if (!(session.getAttribute("teacher") instanceof com.omr.model.Teacher)) { response.sendRedirect(request.getContextPath()+"/login.jsp"); return; } %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <meta name="app-context" content="${pageContext.request.contextPath}">
    <meta name="description" content="OMR & MCQ Real-Time Evaluation System — Scan answer sheets and get instant results.">
    <title>OMR Scanner — Real-Time MCQ Evaluation</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/style.css?v=3">
</head>
<body>

    <!-- ═══════════════ NAVIGATION ═══════════════ -->
    <nav class="navbar" id="mainNav">
        <div class="nav-brand">
            <div class="brand-icon">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="3" width="18" height="18" rx="3"/>
                    <circle cx="8.5" cy="8.5" r="1.5" fill="currentColor"/>
                    <circle cx="15.5" cy="8.5" r="1.5"/>
                    <circle cx="8.5" cy="15.5" r="1.5"/>
                    <circle cx="15.5" cy="15.5" r="1.5" fill="currentColor"/>
                </svg>
            </div>
            <span class="brand-name">OMR Scanner</span>
        </div>
        <div class="nav-tabs">
            <button class="nav-tab active" data-tab="setup" id="navSetup">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 20V10M6 20V4M18 20v-6"/>
                </svg>
                <span>Setup</span>
            </button>
            <button class="nav-tab" data-tab="camera" id="navCamera">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z"/>
                    <circle cx="12" cy="13" r="4"/>
                </svg>
                <span>Scan</span>
            </button>
            <button class="nav-tab" data-tab="results" id="navResults">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
                    <polyline points="22 4 12 14.01 9 11.01"/>
                </svg>
                <span>Results</span>
            </button>
        </div>
        <form class="logout-form" action="${pageContext.request.contextPath}/auth/logout" method="post"><button class="logout-button" type="submit">Sign out</button></form>
    </nav>

    <!-- ═══════════════ SETUP TAB ═══════════════ -->
    <section class="tab-content active" id="tabSetup">
        <div class="container">
            <div class="section-header">
                <h1>Quiz Setup</h1>
                <p class="subtitle">Create a quiz and enter the answer key before scanning</p>
            </div>

            <!-- Quiz Creation Card -->
            <div class="card glass-card" id="quizCreationCard">
                <div class="card-header">
                    <div class="card-icon create-icon">
                        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                        </svg>
                    </div>
                    <h2>Create Quiz</h2>
                </div>
                <div class="form-group">
                    <label for="quizTitle">Quiz Title</label>
                    <input type="text" id="quizTitle" placeholder="e.g., Physics Chapter 3 Test" autocomplete="off">
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label for="numQuestions">Questions</label>
                        <select id="numQuestions">
                            <option value="5">5</option>
                            <option value="10">10</option>
                            <option value="15" selected>15</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="maximumMarks">Maximum marks</label>
                        <input type="number" id="maximumMarks" min="0.01" max="1000" step="0.5" value="10">
                    </div>
                </div>
                <button class="btn btn-primary btn-full" id="btnCreateQuiz">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                    </svg>
                    Create Quiz
                </button>
            </div>

            <div class="card glass-card" id="savedQuizCard" style="margin-top:18px;">
                <div class="card-header"><h2>Continue a Saved Quiz</h2></div>
                <div class="form-row">
                    <div class="form-group" style="flex:1;">
                        <label for="savedQuizSelect">Saved quiz and answer key</label>
                        <select id="savedQuizSelect"><option value="">No saved quizzes yet</option></select>
                    </div>
                    <button class="btn btn-secondary" id="btnLoadQuiz" type="button" disabled>Load Quiz</button>
                </div>
            </div>

            <!-- Answer Key Card (hidden until quiz created) -->
            <div class="card glass-card hidden" id="answerKeyCard">
                <div class="card-header">
                    <div class="card-icon key-icon">
                        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 11-7.778 7.778 5.5 5.5 0 017.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/>
                        </svg>
                    </div>
                    <div>
                        <h2>Answer Key</h2>
                        <span class="quiz-label" id="activeQuizLabel"></span>
                    </div>
                </div>
                <p class="instruction-text">Select the correct answer (A, B, C, or D) for each question.</p>

                <div class="answer-grid" id="answerGrid">
                    <!-- Dynamically populated -->
                </div>

                <div class="key-actions">
                    <button class="btn btn-ghost" id="btnClearAll">Clear All</button>
                    <div class="key-progress">
                        <span id="answeredCount">0</span> / <span id="totalCount">15</span> answered
                    </div>
                    <button class="btn btn-primary" id="btnSaveKey" disabled>
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/>
                            <polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/>
                        </svg>
                        Save Answer Key
                    </button>
                </div>

            </div>

            <!-- Status Banner -->
            <div class="status-banner hidden" id="statusBanner">
                <div class="status-icon">✓</div>
                <div class="status-text">
                    <strong>Ready to Scan!</strong>
                    <span>Switch to the Scan tab to start evaluating answer sheets.</span>
                </div>
            </div>
        </div>
    </section>

    <!-- ═══════════════ CAMERA TAB ═══════════════ -->
    <section class="tab-content" id="tabCamera">
        <div class="container">
            <div class="camera-wrapper">
                <!-- Camera viewfinder -->
                <div class="viewfinder" id="viewfinder">
                    <video id="cameraFeed" autoplay playsinline></video>
                    <img id="desktopCameraFeed" alt="Live camera preview" class="hidden">
                    <canvas id="cameraCanvas" style="display:none;"></canvas>

                    <!-- Alignment overlay -->
                    <div class="alignment-overlay" id="alignmentOverlay">
                        <div class="corner tl"></div>
                        <div class="corner tr"></div>
                        <div class="corner bl"></div>
                        <div class="corner br"></div>
                        <div class="align-text" id="alignText">Align OMR sheet within the frame</div>
                    </div>

                    <!-- No camera fallback -->
                    <div class="no-camera" id="noCameraMsg">
                        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                            <path d="M1 1l22 22M21 21H3a2 2 0 01-2-2V8a2 2 0 012-2h3m3-3h6l2 3h4a2 2 0 012 2v9.34"/>
                            <path d="M14.12 14.12A3 3 0 109.88 9.88"/>
                        </svg>
                        <p>Camera not available</p>
                        <button class="btn btn-primary" id="btnStartCamera">Enable Camera</button>
                    </div>
                </div>

                <!-- Camera controls -->
                <div class="camera-controls">
                    <div class="scan-status" id="scanStatus">
                        <div class="status-dot idle"></div>
                        <span>Idle — No quiz loaded</span>
                    </div>

                    <div class="control-buttons">
                        <button class="btn btn-icon" id="btnToggleCamera" title="Switch camera">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/>
                                <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                            </svg>
                        </button>
                        <button class="btn btn-scan" id="btnCapture" disabled>
                            <div class="scan-ring"></div>
                            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z"/>
                                <circle cx="12" cy="13" r="4"/>
                            </svg>
                        </button>
                        <button class="btn btn-icon" id="btnAutoScan" title="Toggle auto-scan">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <polygon points="5 3 19 12 5 21 5 3"/>
                            </svg>
                        </button>
                    </div>
                    <button class="btn btn-ghost" id="btnChooseImage" type="button" disabled>Choose answer sheet image</button>
                    <input id="imageFileInput" type="file" accept="image/*" hidden>

                    <div class="auto-scan-indicator hidden" id="autoScanIndicator">
                        <div class="pulse-dot"></div>
                        Auto-scanning every 2s
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- ═══════════════ RESULTS TAB ═══════════════ -->
    <section class="tab-content" id="tabResults">
        <div class="container">
            <div class="section-header">
                <h1>Scan Results</h1>
                <p class="subtitle" id="resultsSubtitle">No scans performed yet</p>
            </div>

            <!-- Student Details & Roll Number Banner -->
            <div class="card glass-card hidden" id="studentBannerCard" style="margin-bottom: 20px; padding: 16px 24px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px;">
                <div style="display: flex; align-items: center; gap: 12px;">
                    <span style="color: var(--text-secondary); font-size: 0.95rem;">Scanned Student:</span>
                    <strong id="detectedRollBadge" style="font-size: 1.25rem; font-family: monospace; letter-spacing: 1px; color: var(--text-primary); background: var(--bg-elevated); padding: 4px 12px; border-radius: 6px; border: 1px solid var(--border);">—</strong>
                </div>
                <div style="display: flex; gap: 10px;">
                    <button class="btn btn-secondary btn-sm" id="btnEditRoll" type="button" title="Correct roll number if misread">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 013 3L7 19l-4 1 1-4L16.5 3.5z"/>
                        </svg>
                        Edit Roll No.
                    </button>
                </div>
            </div>

            <!-- Score Overview Cards -->
            <div class="score-overview hidden" id="scoreOverview">
                <div class="score-card score-main" id="scoreMain">
                    <div class="score-ring">
                        <svg viewBox="0 0 120 120">
                            <circle cx="60" cy="60" r="52" class="ring-bg"/>
                            <circle cx="60" cy="60" r="52" class="ring-fill" id="scoreRing"
                                    stroke-dasharray="326.73" stroke-dashoffset="326.73"/>
                        </svg>
                        <div class="score-value" id="scoreValue">0%</div>
                    </div>
                    <div class="score-label" id="marksValue">Marks</div>
                </div>
                <div class="score-card correct">
                    <div class="score-number" id="correctNum">0</div>
                    <div class="score-label">Correct</div>
                </div>
                <div class="score-card wrong">
                    <div class="score-number" id="wrongNum">0</div>
                    <div class="score-label">Wrong</div>
                </div>
                <div class="score-card blank">
                    <div class="score-number" id="blankNum">0</div>
                    <div class="score-label">Blank</div>
                </div>
                <div class="score-card ambiguous">
                    <div class="score-number" id="ambiguousNum">0</div>
                    <div class="score-label">Ambiguous</div>
                </div>
            </div>

            <!-- Per-Question Results Table -->
            <div class="card glass-card hidden" id="detailCard">
                <div class="card-header">
                    <h2>Per-Question Breakdown</h2>
                    <span class="timestamp" id="resultTimestamp"></span>
                </div>
                <div class="results-table-wrapper">
                    <table class="results-table" id="resultsTable">
                        <thead>
                            <tr>
                                <th>Q#</th>
                                <th>Correct</th>
                                <th>Detected</th>
                                <th>Confidence</th>
                                <th>Result</th>
                            </tr>
                        </thead>
                        <tbody id="resultsBody">
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- Class Submissions & CSV Download Card -->
            <div class="card glass-card hidden" id="rosterCard" style="margin-top: 28px;">
                <div class="card-header" style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px;">
                    <div>
                        <h2>Subject Gradebook & Submissions</h2>
                        <span class="subtitle" id="rosterSubtitle">All evaluated answer sheets for this subject</span>
                    </div>
                    <div style="display: flex; gap: 10px;">
                        <button class="btn btn-secondary btn-sm" id="btnRefreshRoster" type="button" title="Refresh submissions">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/>
                                <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                            </svg>
                            Refresh
                        </button>
                        <a class="btn btn-primary btn-sm" id="btnDownloadCsv" href="#" target="_blank" style="text-decoration: none; display: inline-flex; align-items: center; gap: 6px;">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
                                <polyline points="7 10 12 15 17 10"/>
                                <line x1="12" y1="15" x2="12" y2="3"/>
                            </svg>
                            Download CSV Report
                        </a>
                    </div>
                </div>

                <div class="results-table-wrapper" style="margin-top: 16px;">
                    <table class="results-table" id="rosterTable">
                        <thead>
                            <tr>
                                <th>#</th>
                                <th>Roll Number</th>
                                <th>Marks / Total Marks</th>
                                <th>Percentage</th>
                                <th>Correct</th>
                                <th>Wrong</th>
                                <th>Status</th>
                                <th>Scanned At</th>
                            </tr>
                        </thead>
                        <tbody id="rosterBody">
                            <!-- Dynamically loaded from database -->
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- Empty State -->
            <div class="empty-state" id="emptyState">
                <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
                    <rect x="3" y="3" width="18" height="18" rx="3"/>
                    <path d="M9 9h.01M15 9h.01M9 15h.01M15 15h.01"/>
                </svg>
                <h3>No Results Yet</h3>
                <p>Create a quiz and scan an answer sheet to see results here.</p>
            </div>
        </div>
    </section>

    <!-- Toast notifications -->
    <div class="toast-container" id="toastContainer"></div>

    <script src="${pageContext.request.contextPath}/assets/camera.js?v=3"></script>
</body>
</html>
