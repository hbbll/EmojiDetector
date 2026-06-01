// Main initialization and event listeners

document.addEventListener("DOMContentLoaded", () => {
  const imageInput = document.getElementById("imageInput");
  if (imageInput) {
    imageInput.addEventListener("change", () => {
      if (!imageInput.files || !imageInput.files.length) return;
      setCurrentFile(imageInput.files[0]);
    });
  }

  const videoInput = document.getElementById("videoInput");
  if (videoInput) {
    videoInput.addEventListener("change", handleVideoSelect);
    initVideoPlayer();
  }

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
      if (imageInput) setCurrentFile(file);
      break;
    }
  });
});
