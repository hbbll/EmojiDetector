// Video playback and detection functionality

let videoInterval = null;

function handleVideoSelect(event) {
  const file = event.target.files[0];
  if (!file) return;

  const videoPlayer = document.getElementById("videoPlayer");
  const videoContainer = document.getElementById("videoContainer");
  const controls = document.getElementById("controls");

  videoPlayer.src = URL.createObjectURL(file);
  videoContainer.classList.remove("hidden");
  controls.classList.remove("hidden");

  videoPlayer.onloadedmetadata = () => {
    const slider = document.getElementById("frameSlider");
    slider.max = videoPlayer.duration;
    document.getElementById("duration").textContent = formatTime(videoPlayer.duration);

    const canvas = document.getElementById("videoCanvas");
    // We'll set the actual display size in a moment
  };
}

function initVideoPlayer() {
  const video = document.getElementById("videoPlayer");
  const slider = document.getElementById("frameSlider");
  const playPauseBtn = document.getElementById("playPauseBtn");
  const currentTimeDisplay = document.getElementById("currentTime");

  if (!video || !slider) return;

  video.addEventListener("timeupdate", () => {
    if (!video.seeking) {
      slider.value = video.currentTime;
    }
    currentTimeDisplay.textContent = formatTime(video.currentTime);
  });

  slider.addEventListener("input", () => {
    video.currentTime = slider.value;
  });

  playPauseBtn.addEventListener("click", () => {
    if (video.paused) {
      video.play();
      playPauseBtn.textContent = "Pauza";
      startVideoAutoDetection();
    } else {
      video.pause();
      playPauseBtn.textContent = "Ijro etish";
      stopVideoAutoDetection();
    }
  });

  video.addEventListener("ended", () => {
    playPauseBtn.textContent = "Ijro etish";
    stopVideoAutoDetection();
  });
}

function nextFrame() {
  const video = document.getElementById("videoPlayer");
  if (!video || !video.duration) return;

  // Move forward by 1/30th of a second (assuming 30fps)
  video.currentTime = Math.min(video.duration, video.currentTime + 1 / 30);

  // Auto-detect the new frame
  detectCurrentFrame();
}

function prevFrame() {
  const video = document.getElementById("videoPlayer");
  if (!video || !video.duration) return;

  // Move backward by 1/30th of a second (assuming 30fps)
  video.currentTime = Math.max(0, video.currentTime - 1 / 30);

  // Auto-detect the new frame
  detectCurrentFrame();
}

function startVideoAutoDetection() {
  if (videoInterval) return;
  videoInterval = setInterval(detectCurrentFrame, 1000); // Detect every 1 second when playing
}

function stopVideoAutoDetection() {
  if (videoInterval) {
    clearInterval(videoInterval);
    videoInterval = null;
  }
}

async function detectCurrentFrame() {
  const video = document.getElementById("videoPlayer");
  const canvas = document.getElementById("videoCanvas");
  const resultEl = document.getElementById("videoResult");

  if (!video || !video.videoWidth || !video.videoHeight) return;

  // Capture frame from video
  const tempCanvas = document.createElement("canvas");
  tempCanvas.width = video.videoWidth;
  tempCanvas.height = video.videoHeight;
  const tempCtx = tempCanvas.getContext("2d");
  tempCtx.drawImage(video, 0, 0);

  // Convert to blob
  tempCanvas.toBlob(async (blob) => {
    if (!blob) return;

    const formData = new FormData();
    formData.append("file", blob, "frame.jpg");

    try {
      const response = await fetch("/api/v1/detect/frame", {
        method: "POST",
        body: formData,
      });

      const data = await response.json();

      if (response.ok && data.faces) {
        drawVideoFaceRectangles(data.faces);

        if (data.faces.length > 0) {
          const emotions = data.faces.map(f => `${getEmotionEmoji(f.emotion)} ${getEmotionTranslation(f.emotion)}`).join(", ");
          resultEl.innerHTML = `<h3>Aniqlangan: ${emotions}</h3>`;
        } else {
          resultEl.innerHTML = `<p>${formatTime(video.currentTime)} da hech qanday yuz aniqlanmadi</p>`;
        }
      }
    } catch (error) {
      console.error("Detection error:", error);
    }
  }, "image/jpeg", 0.8);
}

async function uploadVideo() {
  const input = document.getElementById("videoInput");
  const file = input.files && input.files[0];

  if (!file) {
    alert("Avval video faylini tanlang");
    return;
  }

  const resultEl = document.getElementById("videoResult");
  resultEl.textContent = "Video qayta ishlanmoqda…";

  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch("/api/v1/detect/video", {
    method: "POST",
    body: formData,
  });

  const data = await response.json();

  if (!response.ok) {
    resultEl.textContent = data?.detail ? `Xatolik: ${data.detail}` : "Xatolik";
    return;
  }

  let html = `
    <h2>Video tahlil natijalari</h2>
    <p>Jami kadrlar: ${data.total_frames}</p>
    <p>FPS: ${data.fps}</p>
    <p>Tahlil qilingan kadrlar: ${data.frames_analyzed}</p>
    <h3>Natijalar:</h3>
  `;

  data.results.forEach((result, index) => {
    if (result.faces && result.faces.length > 0) {
      html += `<p>Kadr ${result.frame_number} (${result.timestamp_at}s): `;
      result.faces.forEach(face => {
        const emotionEmoji = getEmotionEmoji(face.emotion);
        html += `${emotionEmoji} ${getEmotionTranslation(face.emotion)} (${(face.confidence * 100).toFixed(1)}%) `;
      });
      html += `</p>`;
    }
  });

  resultEl.innerHTML = html;
}
