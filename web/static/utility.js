// Utility functions for emotion detection

function getEmotionEmoji(emotion) {
  const emojis = {
    "happy": "😊",
    "sad": "😢",
    "angry": "😠",
    "surprise": "😲",
    "fear": "😨",
    "neutral": "😐",
    "disgust": "🤢"
  };
  return emojis[emotion] || emotion;
}

function capitalize(str) {
  return str.charAt(0).toUpperCase() + str.slice(1);
}

function formatTime(seconds) {
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, "0")}`;
}

function getPercentageColor(percentage) {
  if (percentage >= 70) return 'percentage-high';
  if (percentage >= 40) return 'percentage-medium';
  return 'percentage-low';
}
