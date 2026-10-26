# OMR MCQ System (Jakarta EE)

A Java application built with Jakarta Servlet 6, JSP, JavaFX, embedded Apache Tomcat 10.1, and OpenCV. It provides teacher registration/login, quiz setup, answer-sheet scanning, and results in a desktop window. It uses neither Spring Boot nor a database.

## Requirements

- JDK 21 or newer
- Maven 3.9+ (or the included Maven wrapper)

## Run as a desktop window

```sh
cd /Users/sanchitnarula/Desktop/JavaProject
./scripts/run-desktop.sh
```

The script builds the Jakarta web app, starts it in embedded Tomcat, and opens it in a JavaFX desktop window. JavaFX dependencies are fetched by Maven. To create a macOS `.app` bundle, run `./scripts/package-desktop.sh`; the app image is written under `desktop/dist/`. The packaged app includes its Java runtime and libraries and is built for the Mac architecture on which the script runs.

For web development in a browser, `./scripts/run-local.sh` still launches the app on <http://localhost:8180/omr-mcq-system/> using the separately installed Tomcat.

## Data behavior

Teacher accounts, quizzes, answer keys, and scan records are stored locally under `~/.omr-mcq-system/` as JSON/properties files, so they remain available after the app closes. Passwords are stored as PBKDF2-HMAC-SHA256 hashes with random per-account salts. No database connection or database credentials are used. The desktop app captures live camera frames natively with OpenCV and sends them to the scanner; macOS may ask you to allow camera access on first use. The scanner also supports choosing a saved image.

The scanner supports the Thapar answer sheet in `OMR.pdf`: 15 questions in three groups of five, four options per question, and the 10-column roll-number grid. It can align either the full upright A4 page or the bordered answer panel. Keep the printed page flat, well lit, and fully visible in the camera preview. A scan with an unreadable roll number is retained as `Unknown`; correct it from Results before exporting the CSV. Quiz maximum marks are configurable (10 by default for this sheet), and marks are scaled from the number of correct answers.

## Main endpoints

- `POST /api/quizzes` — create a quiz (`title`, `numberOfQuestions`, and `maximumMarks`)
- `GET /api/quizzes` — list quizzes
- `GET /api/quizzes/{id}` — retrieve a quiz and answer key
- `PUT /api/quizzes/{id}/answer-key` — save `{ "answers": { "1": "A" } }`
- `POST /api/scan` — multipart upload with `image` and `quizId`
- `GET /api/scan/results/{quizId}` — retrieve the most recent result for the quiz in this server process
- `GET /api/scan/submissions/{quizId}` — list saved submissions
- `GET /api/scan/csv/{quizId}` — download marks and detected answers as CSV

The scanner reads marked bubbles on the supplied Thapar form; it does not read printed MCQ text or grade handwritten answers. The older 600×800 sample layout remains available internally as a regression fixture.

The scanner currently targets a fixed 600×800, four-option OMR layout. Printed-paper recognition is not implemented.
