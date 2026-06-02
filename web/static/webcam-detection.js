// Webcam detection functionality

let webcamStream = null;
let webcamInterval = null;

async function startWebcam() {
  const videoEl = document.getElementById("webcamVideo");
  const resultEl = document.getElementById("webcamResult");
  const canvas = document.getElementById("webcamCanvas");

  try {
    webcamStream = await navigator.mediaDevices.getUserMedia({ video: true });
    videoEl.srcObject = webcamStream;

    // Wait for video to load metadata
    videoEl.onloadedmetadata = () => {
      canvas.width = videoEl.videoWidth;
      canvas.height = videoEl.videoHeight;
    };

    resultEl.textContent = "Kamera ishga tushdi. Emotsiyalarni real vaqtda aniqlash...";

    // Start real-time detection
    webcamInterval = setInterval(() => {
      detectWebcamFrame(videoEl, canvas, resultEl);
    }, 500); // Detect every 500ms

  } catch (error) {
    resultEl.textContent = "Kameraga kirishda xatolik: " + error.message;
  }
}

function stopWebcam() {
  if (webcamInterval) {
    clearInterval(webcamInterval);
    webcamInterval = null;
  }

  if (webcamStream) {
    webcamStream.getTracks().forEach(track => track.stop());
    webcamStream = null;
    document.getElementById("webcamVideo").srcObject = null;
    document.getElementById("webcamResult").textContent = "Kamera to'xtatildi.";
  }

  // Clear webcam canvas
  const canvas = document.getElementById("webcamCanvas");
  if (canvas) {
    const ctx = canvas.getContext("2d");
    ctx.clearRect(0, 0, canvas.width, canvas.height);
  }
}

function toggleFullscreen() {
  const videoContainer = document.querySelector(".video-container");

  if (!document.fullscreenElement) {
    videoContainer.requestFullscreen().catch(err => {
      console.error("Error attempting to enable fullscreen:", err);
    });
  } else {
    document.exitFullscreen();
  }
}

async function detectWebcamFrame(videoEl, canvas, resultEl) {
  if (!videoEl.videoWidth || !videoEl.videoHeight) return;

  // Capture frame from video
  const tempCanvas = document.createElement("canvas");
  tempCanvas.width = videoEl.videoWidth;
  tempCanvas.height = videoEl.videoHeight;
  const tempCtx = tempCanvas.getContext("2d");
  tempCtx.drawImage(videoEl, 0, 0);

  // Convert to blob
  tempCanvas.toBlob(async (blob) => {
    if (!blob) return;

    const formData = new FormData();
    formData.append("file", blob, "webcam_frame.jpg");

    try {
      const response = await fetch("/api/v1/detect/image", {
        method: "POST",
        body: formData,
      });

      const data = await response.json();

      if (response.ok && data.faces && data.faces.length > 0) {
        // Draw face rectangles on canvas
        drawWebcamFaceRectangles(data.faces, videoEl, canvas);

        // Display detailed results
        let html = `<h2>${data.faces.length} ta yuz aniqlandi</h2>`;

        data.faces.forEach((face, index) => {
          const emotionEmoji = getEmotionEmoji(face.emotion);
          html += `
            <div class="face-result">
              <div class="face-info">
                <h3>${emotionEmoji} ${getEmotionTranslation(face.emotion)}</h3>
                <p>Ishonch: ${(face.confidence * 100).toFixed(1)}%</p>
                <ul>
          `;

          for (const [emotion, value] of Object.entries(face.all_emotions)) {
            const percentage = (value * 100).toFixed(1);
            const colorClass = getPercentageColor(parseFloat(percentage));
            html += `<li><span>${getEmotionTranslation(emotion)}</span><span class="${colorClass}">${percentage}%</span></li>`;
          }

          html += `</ul></div></div>`;
        });

        resultEl.innerHTML = html;
      } else {
        // Clear canvas if no faces
        const ctx = canvas.getContext("2d");
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        resultEl.textContent = "Hech qanday yuz aniqlanmadi";
      }
    } catch (error) {
      // Silent fail to avoid spamming errors
    }
  }, "image/jpeg", 0.8);
}
