import os
import uuid
from datetime import datetime
from typing import Dict

import cv2
from fastapi import File, HTTPException, UploadFile

from config import UPLOAD_DIR, detector, logger
from utils import _history_dir, log_prediction


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
            return {
                "faces": [],
                "timestamp": datetime.now().isoformat()
            }
        
        faces = []
        for face_data in result:
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

        response_data = {
            "faces": faces,
            "timestamp": datetime.now().isoformat(),
            "annotated_image_base64": None
        }

        if annotate:
            import base64
            annotated = image.copy()
            for i, face in enumerate(faces):
                if face["face_box"] is not None:
                    x, y, w, h = face["face_box"]["x"], face["face_box"]["y"], face["face_box"]["w"], face["face_box"]["h"]
                    cv2.rectangle(annotated, (x, y), (x + w, y + h), (0, 255, 0), 3)
                    # Add label for quick visual confirmation
                    cv2.putText(
                        annotated,
                        f"{face['emotion']} ({round(face['confidence'], 2)})",
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
