// Canvas operations for drawing face rectangles

let detectedFaces = [];

function drawFaceRectangles(faces) {
  const previewEl = document.getElementById("preview");
  const canvas = document.getElementById("faceCanvas");

  if (!previewEl || !canvas || !previewEl.complete || !previewEl.naturalWidth) return;

  // Set canvas size to match the displayed image size
  canvas.width = previewEl.offsetWidth;
  canvas.height = previewEl.offsetHeight;

  const ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  // Get the actual image dimensions and the displayed dimensions
  const imgWidth = previewEl.naturalWidth;
  const imgHeight = previewEl.naturalHeight;
  const displayWidth = previewEl.offsetWidth;
  const displayHeight = previewEl.offsetHeight;

  // Calculate the scale and offsets to handle 'object-fit: contain'
  const imgRatio = imgWidth / imgHeight;
  const displayRatio = displayWidth / displayHeight;

  let renderWidth, renderHeight, offsetX, offsetY;

  if (displayRatio > imgRatio) {
    // Height is the limiting factor (letterboxed on sides)
    renderHeight = displayHeight;
    renderWidth = displayHeight * imgRatio;
    offsetX = (displayWidth - renderWidth) / 2;
    offsetY = 0;
  } else {
    // Width is the limiting factor (letterboxed on top/bottom)
    renderWidth = displayWidth;
    renderHeight = displayWidth / imgRatio;
    offsetX = 0;
    offsetY = (displayHeight - renderHeight) / 2;
  }

  const scaleX = renderWidth / imgWidth;
  const scaleY = renderHeight / imgHeight;

  // Store detected faces with scaled coordinates for hover detection
  detectedFaces = [];

  faces.forEach(face => {
    if (face.face_box) {
      const { x, y, w, h } = face.face_box;

      // Scale and offset coordinates
      const scaledX = x * scaleX + offsetX;
      const scaledY = y * scaleY + offsetY;
      const scaledW = w * scaleX;
      const scaledH = h * scaleY;

      // Store face data for hover detection
      detectedFaces.push({
        x: scaledX,
        y: scaledY,
        w: scaledW,
        h: scaledH,
        data: face
      });

      // Draw rectangle
      ctx.strokeStyle = "#4CAF50";
      ctx.lineWidth = 3;
      ctx.strokeRect(scaledX, scaledY, scaledW, scaledH);

      // Draw emotion label
      const emotionEmoji = getEmotionEmoji(face.emotion);
      ctx.fillStyle = "#4CAF50";
      ctx.font = "bold 16px Arial";
      ctx.fillText(`${emotionEmoji} ${getEmotionTranslation(face.emotion)}`, scaledX, scaledY - 8);
    }
  });

  // Add hover event listener
  canvas.onmousemove = (e) => handleCanvasHover(e, canvas, "facePopup");
  canvas.onmouseleave = () => hidePopup("facePopup");
}

function drawVideoFaceRectangles(faces) {
  const video = document.getElementById("videoPlayer");
  const canvas = document.getElementById("videoCanvas");

  if (!video || !canvas || !video.videoWidth) return;

  // Set canvas size to match the displayed video size
  canvas.width = video.offsetWidth;
  canvas.height = video.offsetHeight;

  const ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  // Get the actual video dimensions and the displayed dimensions
  const videoWidth = video.videoWidth;
  const videoHeight = video.videoHeight;
  const displayWidth = video.offsetWidth;
  const displayHeight = video.offsetHeight;

  // Calculate the scale and offsets to handle 'object-fit: contain' style
  const videoRatio = videoWidth / videoHeight;
  const displayRatio = displayWidth / displayHeight;

  let renderWidth, renderHeight, offsetX, offsetY;

  if (displayRatio > videoRatio) {
    // Height is the limiting factor (letterboxed on sides)
    renderHeight = displayHeight;
    renderWidth = displayHeight * videoRatio;
    offsetX = (displayWidth - renderWidth) / 2;
    offsetY = 0;
  } else {
    // Width is the limiting factor (letterboxed on top/bottom)
    renderWidth = displayWidth;
    renderHeight = displayWidth / videoRatio;
    offsetX = 0;
    offsetY = (displayHeight - renderHeight) / 2;
  }

  const scaleX = renderWidth / videoWidth;
  const scaleY = renderHeight / videoHeight;

  detectedFaces = [];

  faces.forEach(face => {
    if (face.face_box) {
      const { x, y, w, h } = face.face_box;

      // Scale and offset coordinates
      const scaledX = x * scaleX + offsetX;
      const scaledY = y * scaleY + offsetY;
      const scaledW = w * scaleX;
      const scaledH = h * scaleY;

      detectedFaces.push({
        x: scaledX,
        y: scaledY,
        w: scaledW,
        h: scaledH,
        data: face
      });

      ctx.strokeStyle = "#4CAF50";
      ctx.lineWidth = 3;
      ctx.strokeRect(scaledX, scaledY, scaledW, scaledH);

      const emotionEmoji = getEmotionEmoji(face.emotion);
      ctx.fillStyle = "#4CAF50";
      ctx.font = "bold 16px Arial";
      ctx.fillText(`${emotionEmoji} ${getEmotionTranslation(face.emotion)}`, scaledX, scaledY - 8);
    }
  });

  canvas.onmousemove = (e) => handleCanvasHover(e, canvas, "videoPopup");
  canvas.onmouseleave = () => hidePopup("videoPopup");
}

function drawWebcamFaceRectangles(faces, videoEl, canvas) {
  if (!videoEl.videoWidth || !videoEl.videoHeight) return;

  // Clear and set canvas size
  canvas.width = videoEl.offsetWidth;
  canvas.height = videoEl.offsetHeight;
  const ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  // Calculate scale factors
  const scaleX = canvas.width / videoEl.videoWidth;
  const scaleY = canvas.height / videoEl.videoHeight;

  // Store detected faces for hover detection
  detectedFaces = [];

  // Draw rectangles
  faces.forEach(face => {
    if (face.face_box) {
      const { x, y, w, h } = face.face_box;

      // Scale coordinates
      const scaledX = x * scaleX;
      const scaledY = y * scaleY;
      const scaledW = w * scaleX;
      const scaledH = h * scaleY;

      // Store face data for hover detection
      detectedFaces.push({
        x: scaledX,
        y: scaledY,
        w: scaledW,
        h: scaledH,
        data: face
      });

      // Draw rectangle
      ctx.strokeStyle = "#4CAF50";
      ctx.lineWidth = 3;
      ctx.strokeRect(scaledX, scaledY, scaledW, scaledH);

      // Draw emotion label
      const emotionEmoji = getEmotionEmoji(face.emotion);
      ctx.fillStyle = "#4CAF50";
      ctx.font = "bold 16px Arial";
      ctx.fillText(`${emotionEmoji} ${getEmotionTranslation(face.emotion)}`, scaledX, scaledY - 8);
    }
  });

  // Add hover event listener for webcam
  canvas.onmousemove = (e) => handleCanvasHover(e, canvas, "webcamPopup");
  canvas.onmouseleave = () => hidePopup("webcamPopup");
}

function clearCanvas(canvasId) {
  const canvas = document.getElementById(canvasId);
  if (canvas) {
    const ctx = canvas.getContext("2d");
    ctx.clearRect(0, 0, canvas.width, canvas.height);
  }
  detectedFaces = [];
}

function handleCanvasHover(e, canvas, popupId) {
  const rect = canvas.getBoundingClientRect();
  const mouseX = e.clientX - rect.left;
  const mouseY = e.clientY - rect.top;

  // Check if mouse is over any face rectangle
  for (const face of detectedFaces) {
    if (mouseX >= face.x && mouseX <= face.x + face.w &&
      mouseY >= face.y && mouseY <= face.y + face.h) {
      showPopup(face.data, e.clientX, e.clientY, popupId);
      return;
    }
  }

  hidePopup(popupId);
}

function showPopup(faceData, mouseX, mouseY, popupId) {
  const popup = document.getElementById(popupId);
  if (!popup) return;

  const emotionEmoji = getEmotionEmoji(faceData.emotion);

  let emotionBars = '';
  for (const [emotion, value] of Object.entries(faceData.all_emotions)) {
    const percentage = (value * 100).toFixed(1);
    const colorClass = getPercentageColor(parseFloat(percentage));
    emotionBars += `
      <div class="emotion-item">
        <span>${emotion}</span>
        <span class="${colorClass}">${percentage}%</span>
      </div>
    `;
  }

  popup.innerHTML = `
    <h4>${emotionEmoji} ${getEmotionTranslation(faceData.emotion)}</h4>
    <p>Ishonch: ${(faceData.confidence * 100).toFixed(1)}%</p>
    <div class="emotion-bar">
      ${emotionBars}
    </div>
  `;

  popup.style.display = 'block';

  // Position popup near the mouse but prevent overflow
  const popupRect = popup.getBoundingClientRect();
  const windowWidth = window.innerWidth;
  const windowHeight = window.innerHeight;

  let left = mouseX + 15;
  let top = mouseY + 15;

  // Prevent horizontal overflow
  if (left + popupRect.width > windowWidth) {
    left = mouseX - popupRect.width - 15;
  }

  // Prevent vertical overflow
  if (top + popupRect.height > windowHeight) {
    top = mouseY - popupRect.height - 15;
  }

  popup.style.left = left + 'px';
  popup.style.top = top + 'px';
}

function hidePopup(popupId) {
  const popup = document.getElementById(popupId);
  if (popup) {
    popup.style.display = 'none';
  }
}

async function cropFaces(imageEl, faces) {
  if (!imageEl || !imageEl.complete || !imageEl.naturalWidth) return [];

  const croppedImages = [];
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');

  for (const face of faces) {
    if (face.face_box) {
      const { x, y, w, h } = face.face_box;

      // Add some padding around the face
      const padding = 20;
      const cropX = Math.max(0, x - padding);
      const cropY = Math.max(0, y - padding);
      const cropW = Math.min(imageEl.naturalWidth - cropX, w + padding * 2);
      const cropH = Math.min(imageEl.naturalHeight - cropY, h + padding * 2);

      canvas.width = cropW;
      canvas.height = cropH;

      ctx.clearRect(0, 0, cropW, cropH);
      ctx.drawImage(imageEl, cropX, cropY, cropW, cropH, 0, 0, cropW, cropH);

      const dataUrl = canvas.toDataURL('image/jpeg', 0.9);
      croppedImages.push(dataUrl);
    } else {
      croppedImages.push('');
    }
  }

  return croppedImages;
}
