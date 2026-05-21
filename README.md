# EmotionDetector

Bu loyiha rasm (va video) orqali yuz ifodasidan **emotion (kayfiyat/emosiya)** aniqlash uchun mo‘ljallangan.

Repo ichida 2 ta asosiy qism bor:

- `web/` — FastAPI backend + oddiy web frontend (rasm tanlash va clipboard’dan rasm *paste* qilish)
- `android/` — Android ilova (rasm tanlab backend’ga yuboradi, natijani ko‘rsatadi va yuzni to‘rtburchak bilan belgilaydi)

## Talablar

- Python 3 (backend uchun)
- Android Studio / Android SDK (android ilova uchun)
- `adb` (Android qurilma/emulyator bilan ishlash uchun)
- `just` (qulay run buyruqlari uchun, ixtiyoriy)

## Tez start (Justfile bilan)

Repo root’da `Justfile` bor.

1) Backend’ni ishga tushirish:

```bash
just backend
```

Backend odatda `http://0.0.0.0:8000` da ishlaydi.

2) Android qurilmada `localhost:8000` ni host kompyuteringizdagi `:8000` ga ulash:

```bash
just reverse
```

Bu `adb reverse tcp:8000 tcp:8000` ni bajaradi.

3) Android ilovani o‘rnatib ishga tushirish:

```bash
just android-run
```

## Backend (FastAPI) haqida

Backend fayli: `web/app.py`.

### Endpoint’lar

- `POST /api/v1/detect/image` — rasm yuborib emotion aniqlash
  - `multipart/form-data` field nomi: `file`
  - Javob: `emotion`, `confidence`, `all_emotions`, `timestamp`
  - Qo‘shimcha: `face_box` (x,y,w,h) va ixtiyoriy `annotated_image_base64`
  - Query: `annotate=true|false` (default: `false`)
- `POST /api/v1/detect/video` — video yuborib emotion aniqlash (frame’lardan sample qiladi)
  - Query: `sample_rate` (default: `1`)
- `GET /health` — health check
- `GET /api/v1/logs` — loglarni olish

### Backend’ni qo‘lda ishga tushirish

Agar `just` ishlatmasangiz:

```bash
cd web
./venv/bin/python -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload
```

Agar `venv` bo‘lmasa, o‘zingiz `pip install -r requirements.txt` qilib o‘rnating, so‘ng:

```bash
cd web
python3 -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload
```

## Web frontend

Frontend fayllar:

- `web/static/index.html`
- `web/static/app.js`
- `web/static/styles.css`

Imkoniyatlar:

- Rasm tanlash (file input)
- Clipboard’dan rasm *paste* qilish (`Ctrl+V` / `⌘+V`)
- Natijani ko‘rsatish (emoji bilan)

Brauzerda ochish:

- Backend ishga tushgan bo‘lsa, root sahifa: `http://localhost:8000/`

## Android ilova

Android ilova:

- Rasm tanlaydi
- `POST /api/v1/detect/image` ga yuboradi
- Natijani (emoji + confidence + barcha emosiyalar) ko‘rsatadi
- Backend qaytargan `face_box` bo‘yicha preview ustiga **to‘rtburchak** chizadi

Muhim:

- Android ilova `http://127.0.0.1:8000` ga so‘rov yuboradi.
- Shuning uchun qurilmada **`adb reverse`** ishlashi kerak:

```bash
adb reverse tcp:8000 tcp:8000
```

## Video ishlaydimi?

Ha. Backend’da `POST /api/v1/detect/video` endpoint bor.

Hozirgi web frontend faqat rasm uchun ulangan; video UI qo‘shilmagan.

## Muammolar (FAQ)

### Android’da natija chiqmayapti

Tekshiring:

- Backend ishga tushganmi (`just backend`)
- `adb reverse` muvaffaqiyatli bajarilyaptimi (`just reverse`)
- Qurilma `adb devices` da ko‘rinadimi

### “No face detected in image”

Rasmda yuz aniq ko‘rinadigan, yorug‘ va kattaroq bo‘lishi kerak.

## Strukturasi

```text
EmotionDetector/
  android/         # Android app (OkHttp orqali backend’ga ulanadi)
  web/             # FastAPI backend + static frontend
  Justfile         # just buyruqlari (backend, reverse, android-run)
```

