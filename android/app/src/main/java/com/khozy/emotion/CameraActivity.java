package com.khozy.emotion;

import android.Manifest;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CameraActivity extends AppCompatActivity {

    private static final String API_BASE_URL = "http://127.0.0.1:8000"; // ← your PC IP

    // Map each FER emotion label to an emoji
    private static final Map<String, String> EMOTION_EMOJI = new LinkedHashMap<String, String>() {{
        put("happy",     "😄");
        put("sad",       "😢");
        put("angry",     "😠");
        put("surprise",  "😮");
        put("fear",      "😨");
        put("disgust",   "🤢");
        put("neutral",   "😐");
    }};

    private final OkHttpClient httpClient = new OkHttpClient();

    private PreviewView    cameraPreview;
    private DrawingOverlayView cameraOverlay;
    private TextView       tvEmojiLabel;    // large emoji + label
    private TextView       tvConfidence;    // "84%"
    private ProgressBar    progressConfidence;
    private TextView       tvBreakdown;     // per-emotion bars
    private MaterialButton btnSwitch;

    private ProcessCameraProvider cameraProvider;
    private long lastAnalysisTime  = 0;
    private int  currentLensFacing = CameraSelector.LENS_FACING_FRONT;

    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) startCamera();
                        else tvEmojiLabel.setText("Camera permission denied");
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        cameraPreview       = findViewById(R.id.cameraPreview);
        cameraOverlay       = findViewById(R.id.cameraOverlay);
        tvEmojiLabel        = findViewById(R.id.tvEmojiLabel);
        tvConfidence        = findViewById(R.id.tvConfidence);
        progressConfidence  = findViewById(R.id.progressConfidence);
        tvBreakdown         = findViewById(R.id.tvBreakdown);
        btnSwitch           = findViewById(R.id.switchCameraButton);

        btnSwitch.setOnClickListener(v -> {
            currentLensFacing = (currentLensFacing == CameraSelector.LENS_FACING_FRONT)
                    ? CameraSelector.LENS_FACING_BACK
                    : CameraSelector.LENS_FACING_FRONT;
            btnSwitch.setText(currentLensFacing == CameraSelector.LENS_FACING_FRONT ? "Rear" : "Front");
            startCamera();
        });

        findViewById(R.id.closeButton).setOnClickListener(v -> finish());

        requestCameraPermission.launch(Manifest.permission.CAMERA);
    }

    // ─── Camera setup ───────────────────────────────────────────────────────────

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::analyzeFrame);

                // ✅ Use currentLensFacing, not the hardcoded DEFAULT_FRONT_CAMERA constant
                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(currentLensFacing)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, analysis);

            } catch (Exception e) {
                tvEmojiLabel.setText("Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ─── Frame analysis ─────────────────────────────────────────────────────────

    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        long now = System.currentTimeMillis();
        if (now - lastAnalysisTime < 1000) {
            imageProxy.close();
            return;
        }
        lastAnalysisTime = now;

        Image image = imageProxy.getImage();
        if (image == null) {
            imageProxy.close();
            return;
        }

        try {
            byte[] jpegBytes = imageProxyToJpeg(imageProxy);
            uploadFrame(jpegBytes, imageProxy.getWidth(), imageProxy.getHeight());
        } catch (Exception e) {
            runOnUiThread(() -> tvEmojiLabel.setText(e.getMessage()));
        } finally {
            imageProxy.close();
        }
    }

    // ─── Network ────────────────────────────────────────────────────────────────

    private void uploadFrame(byte[] jpegBytes, int imageW, int imageH) {
        RequestBody fileBody = RequestBody.create(
                jpegBytes, MediaType.parse("image/jpeg"));

        RequestBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "frame.jpg", fileBody)
                .build();

        Request request = new Request.Builder()
                .url(API_BASE_URL + "/api/v1/detect/frame")
                .post(multipart)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> tvEmojiLabel.setText("Network error"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response res = response) {
                    String body = res.body() != null ? res.body().string() : "";
                    if (!res.isSuccessful()) {
                        runOnUiThread(() -> tvEmojiLabel.setText("Server error " + res.code()));
                        return;
                    }
                    JSONObject obj = new JSONObject(body);
                    parseAndDisplay(obj, imageW, imageH);
                } catch (Exception e) {
                    runOnUiThread(() -> tvEmojiLabel.setText(e.getMessage()));
                }
            }
        });
    }

    // ─── UI update ──────────────────────────────────────────────────────────────

    private void parseAndDisplay(JSONObject obj, int imageW, int imageH) {
        // Handle new response format with multiple faces
        if (obj.has("faces")) {
            try {
                org.json.JSONArray facesArray = obj.getJSONArray("faces");
                
                if (facesArray.length() == 0) {
                    runOnUiThread(() -> {
                        tvEmojiLabel.setText("👤  No face detected");
                        tvConfidence.setText("");
                        progressConfidence.setProgress(0);
                        tvBreakdown.setText("");
                        cameraOverlay.clear();
                    });
                    return;
                }

                // Build display text for all faces
                StringBuilder displayText = new StringBuilder();
                StringBuilder breakdownText = new StringBuilder();
                java.util.List<android.graphics.RectF> faceBoxes = new java.util.ArrayList<>();

                for (int i = 0; i < facesArray.length(); i++) {
                    JSONObject face = facesArray.getJSONObject(i);
                    String emotion = face.optString("emotion", "unknown");
                    double confidence = face.optDouble("confidence", 0.0);
                    JSONObject faceBox = face.optJSONObject("face_box");
                    JSONObject allEmotions = face.optJSONObject("all_emotions");

                    String emoji = EMOTION_EMOJI.getOrDefault(emotion, "🤔");
                    int pct = (int) Math.round(confidence * 100);

                    displayText.append(emoji).append(" ").append(capitalize(emotion));
                    if (i < facesArray.length() - 1) {
                        displayText.append(" | ");
                    }

                    // Build per-emotion breakdown for this face
                    if (allEmotions != null) {
                        breakdownText.append(String.format("Face %d:\n", i + 1));
                        for (String key : EMOTION_EMOJI.keySet()) {
                            double val = allEmotions.optDouble(key, 0.0);
                            int emotionPct = (int) Math.round(val * 100);
                            int bars = emotionPct / 10;
                            String bar = "█".repeat(bars) + "░".repeat(10 - bars);
                            String emotionEmoji = EMOTION_EMOJI.getOrDefault(key, "");
                            breakdownText.append(String.format("%s %-9s %s %3d%%\n",
                                    emotionEmoji, key, bar, emotionPct));
                        }
                        breakdownText.append("\n");
                    }

                    // Collect face boxes
                    if (faceBox != null) {
                        faceBoxes.add(new android.graphics.RectF(
                                faceBox.optInt("x"),
                                faceBox.optInt("y"),
                                faceBox.optInt("x") + faceBox.optInt("w"),
                                faceBox.optInt("y") + faceBox.optInt("h")
                        ));
                    }
                }

                String finalDisplayText = displayText.toString();
                String finalBreakdown = breakdownText.toString().trim();

                // Extract first face confidence outside runOnUiThread to handle JSONException
                int firstPct = 0;
                try {
                    JSONObject firstFace = facesArray.getJSONObject(0);
                    double firstConfidence = firstFace.optDouble("confidence", 0.0);
                    firstPct = (int) Math.round(firstConfidence * 100);
                } catch (org.json.JSONException e) {
                    firstPct = 0;
                }
                final int finalFirstPct = firstPct;

                runOnUiThread(() -> {
                    tvEmojiLabel.setText(finalDisplayText);
                    
                    // Show confidence of first face (or average if preferred)
                    tvConfidence.setText(facesArray.length() > 1 
                            ? finalFirstPct + "% (" + facesArray.length() + " faces)" 
                            : finalFirstPct + "%");
                    progressConfidence.setProgress(finalFirstPct);
                    tvBreakdown.setText(finalBreakdown);

                    if (!faceBoxes.isEmpty()) {
                        cameraOverlay.setFaceBoxesInImage(faceBoxes, imageW, imageH);
                    } else {
                        cameraOverlay.clear();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> tvEmojiLabel.setText("Error parsing response"));
            }
            return;
        }

        // Legacy single-face format (for backward compatibility)
        String emotion    = obj.optString("emotion", "unknown");
        double confidence = obj.optDouble("confidence", 0.0);
        JSONObject faceBox = obj.optJSONObject("face_box");

        // Build per-emotion breakdown string
        StringBuilder breakdown = new StringBuilder();
        JSONObject allEmotions = obj.optJSONObject("all_emotions");
        if (allEmotions != null) {
            // Iterate in our preferred display order
            for (String key : EMOTION_EMOJI.keySet()) {
                double val = allEmotions.optDouble(key, 0.0);
                int pct = (int) Math.round(val * 100);
                int bars = pct / 10; // 0–10 block chars
                String bar = "█".repeat(bars) + "░".repeat(10 - bars);
                String emoji = EMOTION_EMOJI.getOrDefault(key, "");
                breakdown.append(String.format("%s %-9s %s %3d%%\n",
                        emoji, key, bar, pct));
            }
        }

        String emoji = EMOTION_EMOJI.getOrDefault(emotion, "🤔");
        int pct      = (int) Math.round(confidence * 100);
        String finalBreakdown = breakdown.toString().trim();

        runOnUiThread(() -> {
            if ("no_face".equals(emotion)) {
                tvEmojiLabel.setText("👤  No face detected");
                tvConfidence.setText("");
                progressConfidence.setProgress(0);
                tvBreakdown.setText("");
                cameraOverlay.clear();
                return;
            }

            tvEmojiLabel.setText(emoji + "  " + capitalize(emotion));
            tvConfidence.setText(pct + "%");
            progressConfidence.setProgress(pct);
            tvBreakdown.setText(finalBreakdown);

            if (faceBox != null) {
                cameraOverlay.setFaceBoxInImage(
                        faceBox.optInt("x"),
                        faceBox.optInt("y"),
                        faceBox.optInt("w"),
                        faceBox.optInt("h"),
                        imageW, imageH);
            } else {
                cameraOverlay.clear();
            }
        });
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private byte[] imageProxyToJpeg(ImageProxy imageProxy) {
    ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();

    ByteBuffer yBuf = planes[0].getBuffer();
    ByteBuffer uBuf = planes[1].getBuffer();
    ByteBuffer vBuf = planes[2].getBuffer();

    int yRowStride  = planes[0].getRowStride();
    int uvRowStride = planes[1].getRowStride();
    int uvPixStride = planes[1].getPixelStride();

    int W = imageProxy.getWidth();
    int H = imageProxy.getHeight();

    // Build a tightly-packed NV21 byte array
    byte[] nv21 = new byte[W * H * 3 / 2];

    // Copy Y plane row by row (strip padding)
    for (int row = 0; row < H; row++) {
        yBuf.position(row * yRowStride);
        yBuf.get(nv21, row * W, W);
    }

    // Interleave V and U into the NV21 chroma plane (VU order)
    int chromaOffset = W * H;
    for (int row = 0; row < H / 2; row++) {
        for (int col = 0; col < W / 2; col++) {
            int uvIndex = row * uvRowStride + col * uvPixStride;
            nv21[chromaOffset++] = vBuf.get(uvIndex); // V
            nv21[chromaOffset++] = uBuf.get(uvIndex); // U
        }
    }

    YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, W, H, null);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    yuvImage.compressToJpeg(new Rect(0, 0, W, H), 90, out);
    return out.toByteArray();
}


}