import os
import uuid
from datetime import datetime
from fastapi import File, HTTPException, UploadFile

import cv2

from config import TEMP_DIR, detector, logger
from utils import _history_dir, log_prediction


async def detect_emotion_from_video(
    file: UploadFile = File(..., description="Video file to analyze"),
    sample_rate: int = 10  # Default to 10 to avoid processing every frame
):
    try:
        if not file.content_type.startswith("video/"):
            raise HTTPException(status_code=400, detail="File must be a video")
        
        file_extension = file.filename.split(".")[-1]
        unique_filename = f"{uuid.uuid4()}.{file_extension}"
        
        # Use TEMP_DIR from config or fallback
        try:
            from config import TEMP_DIR as CFG_TEMP_DIR
            temp_dir = CFG_TEMP_DIR
        except ImportError:
            temp_dir = _history_dir("temp")
            
        file_path = temp_dir / unique_filename
        
        # Write file in chunks to avoid memory issues
        with open(file_path, "wb") as f:
            while chunk := await file.read(1024 * 1024): # 1MB chunks
                f.write(chunk)
        
        cap = cv2.VideoCapture(str(file_path))
        if not cap.isOpened():
            if os.path.exists(file_path):
                os.remove(file_path)
            raise HTTPException(status_code=400, detail="Invalid video file or unsupported codec")
        
        fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        
        results = []
        frame_count = 0
        
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            
            if frame_count % sample_rate == 0:
                # Convert BGR to RGB for FER
                rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                emotion_result = detector.detect_emotions(rgb_frame)
                
                if emotion_result:
                    faces = []
                    for face_data in emotion_result:
                        emotions = face_data["emotions"]
                        box = face_data.get("box")
                        face_box = None
                        if box is not None and len(box) == 4:
                            face_box = {"x": int(box[0]), "y": int(box[1]), "w": int(box[2]), "h": int(box[3])}
                        
                        dominant_emotion = max(emotions, key=emotions.get)
                        confidence = emotions[dominant_emotion]

                        face_data_response = {
                            "emotion": dominant_emotion,
                            "confidence": round(confidence, 4),
                            "all_emotions": {k: round(v, 4) for k, v in emotions.items()},
                            "face_box": face_box
                        }
                        faces.append(face_data_response)
                    
                    result_data = {
                        "frame_number": frame_count,
                        "timestamp_at": round(frame_count / fps, 2),
                        "faces": faces
                    }
                    
                    results.append(result_data)
            
            frame_count += 1
        
        cap.release()
        
        if os.path.exists(file_path):
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
