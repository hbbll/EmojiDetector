import json
from typing import Optional
from fastapi import HTTPException

from config import LOGS_DIR, logger


async def get_logs(
    prediction_type: Optional[str] = None,
    date: Optional[str] = None
):
    try:
        logs = []
        
        if date:
            log_pattern = f"*_{date}.json"
        else:
            log_pattern = "*.json"
        
        for log_file in LOGS_DIR.glob(log_pattern):
            if prediction_type and not log_file.stem.startswith(prediction_type):
                continue
            
            with open(log_file, 'r') as f:
                file_logs = json.load(f)
                logs.extend(file_logs)
        
        return {
            "count": len(logs),
            "logs": logs
        }
        
    except Exception as e:
        logger.error(f"Error retrieving logs: {e}")
        raise HTTPException(status_code=500, detail=f"Internal server error: {str(e)}")
