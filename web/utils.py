import json
from datetime import datetime
from pathlib import Path
from typing import Dict

from config import LOGS_DIR, logger


def _history_dir(name: str) -> Path:
    """
    Return a history subdirectory path and ensure it exists.
    This is defensive: if module globals are ever partially initialized
    (e.g., reload edge-cases), endpoints can still function.
    """
    base_dir = Path(__file__).resolve().parent.parent
    dir_path = base_dir / "history" / name
    dir_path.mkdir(parents=True, exist_ok=True)
    return dir_path


def log_prediction(prediction_type: str, data: Dict):
    logs_dir = globals().get("LOGS_DIR") or _history_dir("logs")
    log_file = logs_dir / f"{prediction_type}_{datetime.now().strftime('%Y%m%d')}.json"
    
    log_entry = {
        "timestamp": datetime.now().isoformat(),
        "type": prediction_type,
        "data": data
    }
    
    # Append to log file
    try:
        if log_file.exists():
            with open(log_file, 'r') as f:
                logs = json.load(f)
        else:
            logs = []
        
        logs.append(log_entry)
        
        with open(log_file, 'w') as f:
            json.dump(logs, f, indent=2)
    except Exception as e:
        logger.error(f"Error logging prediction: {e}")
