from fastapi import FastAPI, File, UploadFile, HTTPException, BackgroundTasks
from fastapi.responses import JSONResponse, StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
import cv2
import numpy as np
from fer import FER
import os
import uuid
import json
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Optional
import logging
from pydantic import BaseModel
import base64

from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse


logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Emotion Detection API",
    description="API for detecting human emotions from facial expressions using FER",
    version="1.0.0"
)

STATIC_DIR = Path(__file__).parent / "static"
app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

detector = FER()

BASE_DIR = Path(__file__).parent.parent
UPLOAD_DIR = BASE_DIR / "history/uploads"
LOGS_DIR = BASE_DIR / "history/logs"
TEMP_DIR = BASE_DIR / "history/temp"

UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
LOGS_DIR.mkdir(parents=True, exist_ok=True)
TEMP_DIR.mkdir(parents=True, exist_ok=True)

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

# @app.get("/")
# async def root():
#     return {
#         "message": "Emotion Detection API",
#         "version": "1.0.0",
#         "endpoints": {
#             "upload_image": "/api/v1/detect/image",
#             "webcam": "/api/v1/detect/webcam",
#             "video": "/api/v1/detect/video",
#             "docs": "/docs"
#         }
#     }

@app.get("/")
async def frontend():
    return FileResponse(str(STATIC_DIR / "index.html"))

@app.post("/api/v1/detect/image", response_model=EmotionResult)
async def detect_emotion_from_image(
    file: UploadFile = File(..., description="Image file to analyze"),
    annotate: bool = False
):
    try:
        if not file.content_type.startswith("image/"):
            raise HTTPException(status_code=400, detail="File must be an image")
        
        file_extension = file.filename.split(".")[-1]
        unique_filename = f"{uuid.uuid4()}.{file_extension}"
        upload_dir = globals().get("UPLOAD_DIR") or _history_dir("uploads")
        file_path = upload_dir / unique_filename
        
        contents = await file.read()
        with open(file_path, "wb") as f:
            f.write(contents)
        
        image = cv2.imread(str(file_path))
        if image is None:
            raise HTTPException(status_code=400, detail="Invalid image file")
        
        # FER expects RGB images; OpenCV loads as BGR by default.
        rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        result = detector.detect_emotions(rgb_image)
        
        if not result:
            raise HTTPException(status_code=404, detail="No face detected in image")
        
        emotions = result[0]["emotions"]
        box = result[0].get("box")
        face_box = None
        if box is not None and len(box) == 4:
            face_box = {"x": int(box[0]), "y": int(box[1]), "w": int(box[2]), "h": int(box[3])}
        dominant_emotion = max(emotions, key=emotions.get)
        confidence = emotions[dominant_emotion]

        response_data = {
            "emotion": dominant_emotion,
            "confidence": round(confidence, 4),
            "all_emotions": {k: round(v, 4) for k, v in emotions.items()},
            "timestamp": datetime.now().isoformat(),
            "face_box": face_box,
            "annotated_image_base64": None
        }

        if annotate and face_box is not None:
            annotated = image.copy()
            x, y, w, h = face_box["x"], face_box["y"], face_box["w"], face_box["h"]
            cv2.rectangle(annotated, (x, y), (x + w, y + h), (0, 255, 0), 3)
            # Add label for quick visual confirmation
            cv2.putText(
                annotated,
                f"{dominant_emotion} ({round(confidence, 2)})",
                (x, max(0, y - 10)),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0, 255, 0),
                2,
                cv2.LINE_AA,
            )
            ok, buffer = cv2.imencode(".jpg", annotated)
            if ok:
                response_data["annotated_image_base64"] = base64.b64encode(buffer).decode("utf-8")
        
        log_prediction("image", {
            "filename": file.filename,
            "saved_as": unique_filename,
            **response_data
        })
        
        os.remove(file_path)
        
        return response_data
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error processing image: {e}")
        raise HTTPException(status_code=500, detail=f"Internal server error: {str(e)}")

@app.post("/api/v1/detect/webcam")
async def detect_emotion_from_webcam(
    duration: int = 5,
    return_frames: bool = False
):
    try:
        cap = cv2.VideoCapture(0)
        if not cap.isOpened():
            raise HTTPException(status_code=500, detail="Could not access webcam")
        
        results = []
        start_time = datetime.now()
        frame_count = 0
        
        while (datetime.now() - start_time).total_seconds() < duration:
            ret, frame = cap.read()
            if not ret:
                break
            
            emotion_result = detector.detect_emotions(frame)
            
            if emotion_result:
                emotions = emotion_result[0]["emotions"]
                dominant_emotion = max(emotions, key=emotions.get)
                confidence = emotions[dominant_emotion]
                
                result_data = {
                    "frame_number": frame_count,
                    "emotion": dominant_emotion,
                    "confidence": round(confidence, 4),
                    "all_emotions": {k: round(v, 4) for k, v in emotions.items()},
                    "timestamp": datetime.now().isoformat()
                }
                
                if return_frames:
                    _, buffer = cv2.imencode('.jpg', frame)
                    import base64
                    result_data["frame_data"] = base64.b64encode(buffer).decode('utf-8')
                
                results.append(result_data)
            
            frame_count += 1
        
        cap.release()
        
        # log_prediction("webcam", {
        #     "duration": duration,
        #     "frames_captured": frame_count,
        #     "faces_detected": len(results)
        # })
        
        return {
            "duration": duration,
            "frames_captured": frame_count,
            "faces_detected": len(results),
            "results": results,
            "timestamp": datetime.now().isoformat()
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error processing webcam: {e}")
        raise HTTPException(status_code=500, detail=f"Internal server error: {str(e)}")

@app.post("/api/v1/detect/frame")
async def detect_frame(file: UploadFile = File(...)):
    try:
        contents = await file.read()

        npimg = np.frombuffer(contents, np.uint8)
        image = cv2.imdecode(npimg, cv2.IMREAD_COLOR)

        if image is None:
            raise HTTPException(status_code=400, detail="Invalid frame")

        rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

        result = detector.detect_emotions(rgb)

        if not result:
            return {
                "emotion": "no_face",
                "confidence": 0,
                "face_box": None
            }

        emotions = result[0]["emotions"]
        box = result[0]["box"]

        dominant = max(emotions, key=emotions.get)

        return {
            "emotion": dominant,
            "confidence": emotions[dominant],
            "all_emotions": emotions,
            "face_box": {
                "x": int(box[0]),
                "y": int(box[1]),
                "w": int(box[2]),
                "h": int(box[3]),
            }
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/v1/detect/video")
async def detect_emotion_from_video(
    file: UploadFile = File(..., description="Video file to analyze"),
    sample_rate: int = 1
):
    try:
        if not file.content_type.startswith("video/"):
            raise HTTPException(status_code=400, detail="File must be a video")
        
        file_extension = file.filename.split(".")[-1]
        unique_filename = f"{uuid.uuid4()}.{file_extension}"
        temp_dir = globals().get("TEMP_DIR") or _history_dir("temp")
        file_path = temp_dir / unique_filename
        
        contents = await file.read()
        with open(file_path, "wb") as f:
            f.write(contents)
        
        cap = cv2.VideoCapture(str(file_path))
        if not cap.isOpened():
            raise HTTPException(status_code=400, detail="Invalid video file")
        
        fps = cap.get(cv2.CAP_PROP_FPS)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        
        results = []
        frame_count = 0
        
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            
            if frame_count % sample_rate == 0:
                emotion_result = detector.detect_emotions(frame)
                
                if emotion_result:
                    emotions = emotion_result[0]["emotions"]
                    dominant_emotion = max(emotions, key=emotions.get)
                    confidence = emotions[dominant_emotion]
                    
                    result_data = {
                        "frame_number": frame_count,
                        "timestamp_at": round(frame_count / fps, 2),
                        "emotion": dominant_emotion,
                        "confidence": round(confidence, 4),
                        "all_emotions": {k: round(v, 4) for k, v in emotions.items()}
                    }
                    
                    results.append(result_data)
            
            frame_count += 1
        
        cap.release()
        
        os.remove(file_path)
        
        log_prediction("video", {
            "filename": file.filename,
            "total_frames": total_frames,
            "sample_rate": sample_rate,
            "frames_analyzed": len(results)
        })
        
        return {
            "filename": file.filename,
            "total_frames": total_frames,
            "fps": round(fps, 2),
            "sample_rate": sample_rate,
            "frames_analyzed": len(results),
            "results": results,
            "timestamp": datetime.now().isoformat()
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error processing video: {e}")
        raise HTTPException(status_code=500, detail=f"Internal server error: {str(e)}")

@app.get("/api/v1/logs")
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

@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat()
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
