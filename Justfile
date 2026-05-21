set dotenv-load := false
set shell := ["bash", "-cu"]

default:
  just --list

# Start FastAPI backend on :8000 (uses the existing venv if present).
backend:
  if [[ -x web/venv/bin/python ]]; then \
    cd web && ./venv/bin/python -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload; \
  else \
    cd web && python3 -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload; \
  fi

# Reverse-forward device/emulator localhost:8000 -> host localhost:8000 (USB debugging required).
reverse:
  adb reverse tcp:8000 tcp:8000

# Install debug APK to a connected device/emulator.
android-install:
  cd android && ./gradlew :app:installDebug

# Launch the app on the connected device/emulator (after install).
android-run: android-install
  adb shell monkey -p com.khozy.emotion -c android.intent.category.LAUNCHER 1

dev: android-run reverse backend 