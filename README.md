# CertGuard – Document & Certificate Expiry Tracker

Upload certificates / documents, store the digital copy in MySQL, read the issue and expiry dates
from the file itself, and get email reminders before (and after) they expire.

| Part      | Stack                                                              |
|-----------|--------------------------------------------------------------------|
| Frontend  | React 18 + Vite + TypeScript + Tailwind, served by nginx           |
| Backend   | Java 21, Spring Boot 3.3 (Web, JPA, Security/JWT, Mail, Scheduling) |
| Database  | MySQL 8, schema managed by Flyway                                  |
| Extraction| X.509 (JDK), PKCS#12 (JDK), PDF text (PDFBox), OCR (Tesseract)     |

## Quick start (Docker – how it runs on this machine)

```bash
cd certguard
cp .env.example .env        # already done here, with generated secrets
# edit .env -> MAIL_PASSWORD=<16-char Gmail App Password>
docker compose up -d --build
```

| URL                          | What                                  |
|------------------------------|---------------------------------------|
| http://localhost:5180        | Web portal (also reachable on the LAN) |
| http://localhost:8188/api    | Backend API (localhost only)          |
| localhost:3317               | MySQL (localhost only)                |

Demo account (seeded on first start): `demo@certguard.local` / `Demo@1234` – 5 sample certificates,
reminders off so no mail goes to the fake address. Set `SEED_DEMO=false` to skip it.

The **first account you register becomes the admin**. "Run expiry check now" as admin checks every
user's certificates; other users only check their own.

Common commands:

```bash
docker compose ps
docker compose logs -f backend
docker compose up -d backend          # after editing .env (reloads env vars)
docker compose up -d --build          # after code changes
docker compose down                   # stop (data kept in volume certguard_mysql-data)
```

## Email setup (Gmail)

Gmail does **not** accept your normal account password over SMTP. Create an App Password:

1. Google Account → Security → turn on **2-Step Verification**.
2. Google Account → Security → **App passwords** → create one named "CertGuard".
3. Put the 16-character value (no spaces) in `.env`:
   ```
   MAIL_USERNAME=ethanhuntim10@gmail.com
   MAIL_PASSWORD=abcdabcdabcdabcd
   ```
4. `docker compose up -d backend`, then Settings → **Send test email**.

Reminders go to each user's "Send reminders to" address (Settings), or their login email if empty.

## How reminders work

* Each user has default reminder offsets (default `30,15,7,1` days before expiry); each certificate can
  override them or turn reminders off.
* The cron job (`EXPIRY_CHECK_CRON`, default `0 0 8 * * *` = 08:00 daily, `APP_TIMEZONE` Asia/Kolkata):
  1. recomputes status: `VALID` → `EXPIRING_SOON` (inside the largest offset) → `EXPIRED`;
  2. sends the due reminder: the smallest offset ≥ days left (so a missed day or a certificate uploaded
     inside the window still gets one), an "expires today" mail, then a weekly "still expired" mail
     until the certificate is renewed or archived.
* Every send is logged in `notification_log`. A `SENT` row has a unique `dedup_key`
  (`certId:expiryDate:offset`), so the same reminder is never sent twice even if the job runs twice.
  `FAILED` rows (after 3 attempts) have no key, so the next run retries.
* Renewing a certificate (new file + new dates) re-arms reminders for the new expiry date.
* Statuses are also refreshed at startup and hourly (no email).

Change the schedule: edit `EXPIRY_CHECK_CRON` in `.env` (Spring format:
`second minute hour day month weekday`, e.g. `0 30 9 * * MON-FRI`) and `docker compose up -d backend`.

## Date extraction

| File                  | How dates are found                                              |
|-----------------------|------------------------------------------------------------------|
| .pem .crt .cer .der   | X.509 `notBefore` / `notAfter`, subject CN, issuer, serial        |
| .p12 .pfx             | Same, after you enter the keystore password (not stored)          |
| .pdf                  | Text layer via PDFBox; scanned PDFs → first 3 pages OCR'd         |
| .png .jpg             | Tesseract OCR                                                     |

Text is searched for labels such as *Date of Issue, Issued on, Valid from, Expiry, Expires, Valid until,
Valid till, Not after* followed by a date (`dd/MM/yyyy`, `dd-MM-yyyy`, `yyyy-MM-dd`, `5 March 2025`,
`March 5, 2025`, `05-Mar-2025`, …). With no labels, the earliest date is taken as issue and the latest as
expiry, and the UI warns. The user always confirms/edits before saving; the record keeps whether dates
were `AUTO` or `MANUAL`.

File type is checked by content (magic bytes), not just the extension. Max 10 MB. Identical files
(SHA-256) can't be stored twice by the same user.

## Running without Docker (development)

Needs Java 21, Node 20+, a MySQL 8 database, and optionally `tesseract`.

```bash
docker compose up -d db                      # or use your own MySQL
cd backend
set -a; . ../.env; set +a                    # DB_HOST defaults to localhost, DB_PORT to 3317
./mvnw spring-boot:run                       # http://localhost:8188
./mvnw test                                  # unit tests

cd ../frontend
npm install
npm run dev                                  # http://localhost:5180, proxies /api to :8188
```

(If the Docker backend is running, stop it first: `docker compose stop backend`.)

## API

All under `/api`, JWT in `Authorization: Bearer <token>` except auth.

| Method | Path                                   | Purpose                                  |
|--------|----------------------------------------|------------------------------------------|
| POST   | /auth/register, /auth/login            | Get a token                              |
| GET    | /certificates?q&type&status&archived&page&size&sort&dir | Search / filter / page |
| POST   | /certificates/extract (multipart file[, password]) | Read fields, nothing saved     |
| POST   | /certificates (multipart `data` JSON + `file`) | Create                           |
| GET    | /certificates/{id}                     | Detail + file versions + notification log |
| PUT    | /certificates/{id}                     | Update metadata / reminders              |
| DELETE | /certificates/{id}                     | Delete (files + log cascade)             |
| POST   | /certificates/{id}/renew (multipart)   | New version + new dates                  |
| POST   | /certificates/{id}/archive, /unarchive | Stop / resume reminders                  |
| GET    | /certificates/{id}/file[?fileId&inline]| Download stored copy                     |
| GET    | /dashboard/summary                     | Counts + next 90 days                    |
| GET/PUT| /settings                              | Notify email, default reminder days      |
| POST   | /settings/test-email                   | Send a test mail                         |
| POST   | /admin/run-expiry-check                | Run the cron job now                     |
| GET    | /health                                | Mail / OCR status                        |

## Layout

```
certguard/
├── docker-compose.yml   .env.example   .env (secrets, not for git)
├── backend/             Spring Boot (controller / service / repository / dto / entity /
│   │                    scheduler / mail / extraction / seed), Flyway in resources/db/migration
│   └── src/test/        JUnit 5 + Mockito: date extraction, file type, expiry rules, scheduler
└── frontend/            React app (pages/, components/, api/), nginx.conf
```
# certGuard
