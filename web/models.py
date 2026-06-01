from typing import Dict, Optional
from pydantic import BaseModel


class EmotionResult(BaseModel):
    emotion: str
    confidence: float
    all_emotions: Dict[str, float]
    timestamp: str
    face_box: Optional[Dict[str, int]] = None
    annotated_image_base64: Optional[str] = None


class VideoEmotionResult(BaseModel):
    frame_number: int
    emotion: str
    confidence: float
    all_emotions: Dict[str, float]
    timestamp: str
