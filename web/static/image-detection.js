// Image upload and detection functionality

let currentFile = null;

function setCurrentFile(file) {
  currentFile = file;

  const previewEl = document.getElementById("preview");
  previewEl.src = URL.createObjectURL(file);

  const input = document.getElementById("imageInput");
  // Keep the native file input in sync (useful for accessibility).
  const dt = new DataTransfer();
  dt.items.add(file);
  input.files = dt.files;
}

async function uploadImage() {
  const input = document.getElementById("imageInput");
  const file = currentFile || (input.files && input.files[0]);

  if (!file) {
    alert("Avval rasmni tanlang yoki joylashtiring");
    return;
  }

  const resultEl = document.getElementById("result");
  resultEl.textContent = "Aniqlashmoqda…";

  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch("/api/v1/detect/image", {
    method: "POST",
    body: formData,
  });

  const data = await response.json();

  if (!response.ok) {
    resultEl.textContent = data?.detail ? `Xatolik: ${data.detail}` : "Xatolik";
    return;
  }

  // Handle new response format with multiple faces
  if (data.faces && data.faces.length > 0) {
    let html = `<h2>${data.faces.length} ta yuz aniqlandi</h2>`;

    // Create cropped face images
    const previewEl = document.getElementById("preview");
    const faceImages = await cropFaces(previewEl, data.faces);

    data.faces.forEach((face, index) => {
      const emotionEmoji = getEmotionEmoji(face.emotion);
      const faceImageSrc = faceImages[index] || '';
      html += `
        <div class="face-result">
          <div class="face-card-content">
            ${faceImageSrc ? `<div class="face-crop"><img src="${faceImageSrc}" alt="Face ${index + 1}" /></div>` : ''}
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

      html += `</ul></div></div></div>`;
    });

    resultEl.innerHTML = html;

    // Draw face rectangles on canvas
    drawFaceRectangles(data.faces);
  } else {
    resultEl.textContent = "Hech qanday yuz aniqlanmadi";
    clearCanvas("faceCanvas");
  }
}
