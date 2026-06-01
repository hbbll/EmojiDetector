import cv2
from datetime import datetime
from fastapi import HTTPException

from config import detector, logger


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
                    "faces": faces,
                    "timestamp": datetime.now().isoformat()
                }
                
                if return_frames:
                    import base64
                    _, buffer = cv2.imencode('.jpg', frame)
                    result_data["frame_data"] = base64.b64encode(buffer).decode('utf-8')
                
                results.append(result_data)
            
            frame_count += 1
        
        cap.release()
        
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
