package com.khozy.emotion;

import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImageActivity extends AppCompatActivity {
    private static final String API_BASE_URL = "http://127.0.0.1:8000";

    private final OkHttpClient httpClient = new OkHttpClient();

    private FrameLayout previewContainer;
    private ImageView previewImage;
    private DrawingOverlayView drawingOverlay;
    private MaterialButton detectButton;
    private ProgressBar progress;
    private TextView resultText;

    private @Nullable Uri selectedImageUri;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                selectedImageUri = uri;
                previewImage.setImageURI(uri);
                drawingOverlay.clear();
                resultText.setText("Ready. Tap Detect Emotion.");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        previewContainer = findViewById(R.id.previewContainer);
        previewImage = findViewById(R.id.previewImage);
        drawingOverlay = findViewById(R.id.drawingOverlay);
        detectButton = findViewById(R.id.detectButton);
        progress = findViewById(R.id.progress);
        resultText = findViewById(R.id.resultText);
        resultText.setText("Pick an image, then tap Detect Emotion.");

        Button pickImageButton = findViewById(R.id.pickImageButton);
        pickImageButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

//        MaterialButton clearButton = findViewById(R.id.clearButton);
//        clearButton.setOnClickListener(v -> drawingOverlay.clear());

        detectButton.setOnClickListener(v -> {
            if (selectedImageUri == null) {
                resultText.setText("Pick an image first.");
                return;
            }
            uploadAndDetect(API_BASE_URL);
        });
    }

    private void setLoading(boolean isLoading) {
        progress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        detectButton.setEnabled(!isLoading);
    }

    private void uploadAndDetect(String baseUrl) {
        setLoading(true);
        resultText.setText("Uploading…");

        byte[] bytes;
        String mimeType = "image/jpeg";
        String filename = "image.jpg";
        int imageW;
        int imageH;

        try {
            if (selectedImageUri == null) throw new IllegalStateException("No image selected");
            String resolvedMime = getContentResolver().getType(selectedImageUri);
            if (resolvedMime != null && !resolvedMime.isEmpty()) mimeType = resolvedMime;
            String displayName = getDisplayName(selectedImageUri);
            if (displayName != null && !displayName.isEmpty()) filename = displayName;
            bytes = readAllBytes(selectedImageUri);
            int[] size = readImageSize(selectedImageUri);
            imageW = size[0];
            imageH = size[1];
        } catch (Exception e) {
            setLoading(false);
            resultText.setText("Failed to read image: " + e.getMessage());
            return;
        }

        MediaType mediaType = MediaType.parse(mimeType);
        RequestBody fileBody = RequestBody.create(bytes, mediaType);

        RequestBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, fileBody)
                .build();

        Request request = new Request.Builder()
                .url(baseUrl + "/api/v1/detect/image")
                .post(multipart)
                .build();

        final int finalImageW = imageW;
        final int finalImageH = imageH;

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    resultText.setText("Network error: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response res = response) {
                    String body = res.body() != null ? res.body().string() : "";
                    runOnUiThread(() -> handleResponse(res.code(), res.isSuccessful(), body, finalImageW, finalImageH));
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        resultText.setText("Failed to parse response: " + e.getMessage());
                    });
                }
            }
        });
    }

    private void handleResponse(int statusCode, boolean ok, String body, int imageW, int imageH) {
        setLoading(false);

        if (!ok) {
            drawingOverlay.clear();
            // FastAPI usually returns: {"detail": "..."}
            try {
                JSONObject obj = new JSONObject(body);
                String detail = obj.optString("detail", null);
                resultText.setText("Error (" + statusCode + "): " + (detail != null ? detail : body));
            } catch (Exception ignored) {
                resultText.setText("Error (" + statusCode + "): " + body);
            }
            return;
        }

        try {
            JSONObject obj = new JSONObject(body);
            String emotion = obj.optString("emotion", "unknown");
            double confidence = obj.optDouble("confidence", 0.0);

            StringBuilder sb = new StringBuilder();
            sb.append("Emotion: ").append(emojiEmotionLabel(emotion)).append('\n');
            sb.append("Confidence: ").append(confidence).append('\n');

            JSONObject faceBox = obj.optJSONObject("face_box");
            if (faceBox != null) {
                int x = faceBox.optInt("x", -1);
                int y = faceBox.optInt("y", -1);
                int w = faceBox.optInt("w", -1);
                int h = faceBox.optInt("h", -1);
                if (x >= 0 && y >= 0 && w > 0 && h > 0) {
                    drawingOverlay.setFaceBoxInImage(x, y, w, h, imageW, imageH);
                } else {
                    drawingOverlay.clear();
                }
            } else {
                drawingOverlay.clear();
            }

            JSONObject all = obj.optJSONObject("all_emotions");
            if (all != null) {
                sb.append('\n').append("All emotions:").append('\n');
                Iterator<String> keys = all.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    sb.append("- ").append(key).append(": ").append(all.optDouble(key)).append('\n');
                }
            }

            resultText.setText(sb.toString().trim());
        } catch (Exception e) {
            resultText.setText("Unexpected response: " + body);
        }
    }

    private byte[] readAllBytes(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Could not open input stream");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private int[] readImageSize(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Could not open input stream");
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, opts);
            return new int[]{opts.outWidth, opts.outHeight};
        }
    }

    private static String emojiEmotionLabel(String emotion) {
        if (emotion == null) return "unknown";
        switch (emotion) {
            case "happy":
                return "😊 Happy";
            case "sad":
                return "😢 Sad";
            case "angry":
                return "😠 Angry";
            case "surprise":
                return "😲 Surprise";
            case "fear":
                return "😨 Fear";
            case "neutral":
                return "😐 Neutral";
            case "disgust":
                return "🤢 Disgust";
            default:
                return emotion;
        }
    }

    private @Nullable String getDisplayName(Uri uri) {
        if (!"content".equals(uri.getScheme())) return null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    // (No base URL input anymore; base URL is fixed.)
}
