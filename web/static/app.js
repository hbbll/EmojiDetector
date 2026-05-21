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

document.addEventListener("DOMContentLoaded", () => {
  const input = document.getElementById("imageInput");

  input.addEventListener("change", () => {
    if (!input.files || !input.files.length) return;
    setCurrentFile(input.files[0]);
  });

  // Paste image from clipboard anywhere on the page.
  document.addEventListener("paste", (event) => {
    const items = event.clipboardData?.items;
    if (!items) return;

    for (const item of items) {
      if (!item.type || !item.type.startsWith("image/")) continue;
      const blob = item.getAsFile();
      if (!blob) continue;

      const ext = blob.type.split("/")[1] || "png";
      const file = new File([blob], `pasted.${ext}`, { type: blob.type });
      setCurrentFile(file);
      break;
    }
  });
});

async function uploadImage() {
  const input = document.getElementById("imageInput");
  const file = currentFile || (input.files && input.files[0]);

  if (!file) {
    alert("Select or paste an image first");
    return;
  }

  const resultEl = document.getElementById("result");
  resultEl.textContent = "Detecting…";

  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch("/api/v1/detect/image", {
    method: "POST",
    body: formData,
  });

  const data = await response.json();

  if (!response.ok) {
    resultEl.textContent = data?.detail ? `Error: ${data.detail}` : "Error";
    return;
  }

  let emotionText = "";

  switch (data.emotion) {
    case "happy":
      emotionText = "😊 Happy";
      break;

    case "sad":
      emotionText = "😢 Sad";
      break;

    case "angry":
      emotionText = "😠 Angry";
      break;

    case "surprise":
      emotionText = "😲 Surprise";
      break;

    case "fear":
      emotionText = "😨 Fear";
      break;

    case "neutral":
      emotionText = "😐 Neutral";
      break;

    case "disgust":
      emotionText = "🤢 Disgust";
      break;

    default:
      emotionText = data.emotion;
  }

  let html = `
    <h2>${emotionText}</h2>
    <p>Confidence: ${data.confidence}</p>
  `;

  html += "<ul>";
  for (const [emotion, value] of Object.entries(data.all_emotions)) {
    html += `<li> ${emotion}: ${value}</li> `;
  }
  html += "</ul>";

  resultEl.innerHTML = html;
}
