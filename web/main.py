from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from config import APP_CONFIG, CORS_CONFIG, STATIC_DIR
from routers import frontend, health, image, frame, webcam, video, logs

app = FastAPI(**APP_CONFIG)

# Mount static files
app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")

# Add CORS middleware
app.add_middleware(CORSMiddleware, **CORS_CONFIG)

# Register routes
app.get("/")(frontend.frontend)
app.get("/image")(frontend.image_page)
app.get("/video")(frontend.video_page)
app.get("/camera")(frontend.camera_page)
app.get("/info")(frontend.info_page)
app.post("/api/v1/detect/image")(image.detect_emotion_from_image)
app.post("/api/v1/detect/frame")(frame.detect_frame)
app.post("/api/v1/detect/webcam")(webcam.detect_emotion_from_webcam)
app.post("/api/v1/detect/video")(video.detect_emotion_from_video)
app.get("/api/v1/logs")(logs.get_logs)
app.get("/health")(health.health_check)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
