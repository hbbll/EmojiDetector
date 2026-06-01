import cv2
import numpy as np
from fastapi import File, HTTPException, UploadFile

from config import detector, logger


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
                "faces": []
            }

        faces = []
        for face_data in result:
            emotions = face_data["emotions"]
            box = face_data["box"]
            dominant = max(emotions, key=emotions.get)

            faces.append({
                "emotion": dominant,
                "confidence": emotions[dominant],
                "all_emotions": emotions,
                "face_box": {
                    "x": int(box[0]),
                    "y": int(box[1]),
                    "w": int(box[2]),
                    "h": int(box[3]),
                }
            })

        return {
            "faces": faces
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
