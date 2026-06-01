from datetime import datetime


async def health_check():
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat()
    }
