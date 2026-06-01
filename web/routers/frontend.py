from fastapi.responses import FileResponse

from config import STATIC_DIR


async def frontend():
    return FileResponse(str(STATIC_DIR / "index.html"))


async def image_page():
    return FileResponse(str(STATIC_DIR / "image.html"))


async def video_page():
    return FileResponse(str(STATIC_DIR / "video.html"))


async def camera_page():
    return FileResponse(str(STATIC_DIR / "camera.html"))


async def info_page():
    return FileResponse(str(STATIC_DIR / "info.html"))
