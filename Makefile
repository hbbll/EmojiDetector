.PHONY: help install run dev clean backend android-install android-run reverse

# Default target
help:
	@echo "Available targets:"
	@echo "  make install    - Install Python dependencies"
	@echo "  make run        - Start FastAPI backend on port 8000"
	@echo "  make dev        - Run in development mode (android + reverse + backend)"
	@echo "  make clean      - Clean up build artifacts and cache"
	@echo "  make backend    - Start FastAPI backend only"
	@echo "  make reverse    - Reverse-forward device/emulator localhost:8000"
	@echo "  make android-install - Install debug APK to connected device/emulator"
	@echo "  make android-run     - Launch app on connected device/emulator"

# Install Python dependencies
install:
	cd web && if [ -x venv/bin/python ]; then \
		./venv/bin/pip install -r requirements.txt; \
	else \
		python3 -m venv venv && ./venv/bin/pip install -r requirements.txt; \
	fi

# Start FastAPI backend on port 8000
backend:
	cd web && if [ -x venv/bin/python ]; then \
		./venv/bin/python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload; \
	else \
		python3 -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload; \
	fi

# Alias for backend
run: backend

# Reverse-forward device/emulator localhost:8000 -> host localhost:8000
reverse:
	adb reverse tcp:8000 tcp:8000

# Install debug APK to connected device/emulator
android-install:
	cd android && ./gradlew :app:installDebug

# Launch app on connected device/emulator (after install)
android-run: android-install
	adb shell monkey -p com.khozy.emotion -c android.intent.category.LAUNCHER 1

# Development mode: run android, reverse, and backend
dev: android-run reverse backend

# Clean up build artifacts and cache
clean:
	cd android && ./gradlew clean
	cd web && rm -rf __pycache__ .pytest_cache *.pyc
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete 2>/dev/null || true
