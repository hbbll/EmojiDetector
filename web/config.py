import logging
from pathlib import Path
from fer import FER

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Directory paths
BASE_DIR = Path(__file__).parent.parent
UPLOAD_DIR = BASE_DIR / "history/uploads"
LOGS_DIR = BASE_DIR / "history/logs"
TEMP_DIR = BASE_DIR / "history/temp"
STATIC_DIR = Path(__file__).parent / "static"

# Create directories
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
LOGS_DIR.mkdir(parents=True, exist_ok=True)
TEMP_DIR.mkdir(parents=True, exist_ok=True)

# Emotion detector
detector = FER(mtcnn=True)

# FastAPI app configuration
APP_CONFIG = {
    "title": "Emotion Detection API",
    "description": "API for detecting human emotions from facial expressions using FER",
    "version": "1.0.0"
}

# CORS configuration
CORS_CONFIG = {
    "allow_origins": ["*"],
    "allow_credentials": True,
    "allow_methods": ["*"],
    "allow_headers": ["*"]
}
