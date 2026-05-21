package com.khozy.emotion;

import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VideoActivity extends AppCompatActivity {

    // Emulator:
    // private static final String API_BASE_URL = "http://10.0.2.2:8000";

    // Physical device:
    private static final String API_BASE_URL = "http://127.0.0.1:8000";

    private final OkHttpClient httpClient =
            new OkHttpClient();

    private ImageView previewImage;
    private ProgressBar progress;
    private TextView resultText;
    private MaterialButton detectButton;

    private @Nullable Uri selectedVideoUri;

    private final ActivityResultLauncher<String> pickVideoLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {

                        if (uri == null) return;

                        selectedVideoUri = uri;

                        loadVideoThumbnail(uri);

                        resultText.setText(
                                "Video selected.\nTap Analyze Video."
                        );
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        previewImage =
                findViewById(R.id.previewImage);

        progress =
                findViewById(R.id.progress);

        resultText =
                findViewById(R.id.resultText);

        detectButton =
                findViewById(R.id.detectButton);

        MaterialButton pickButton =
                findViewById(R.id.pickVideoButton);

        MaterialButton closeButton =
                findViewById(R.id.closeButton);

        pickButton.setOnClickListener(v ->
                pickVideoLauncher.launch("video/*")
        );

        detectButton.setOnClickListener(v -> {

            if (selectedVideoUri == null) {

                resultText.setText(
                        "Pick a video first."
                );

                return;
            }

            uploadVideo();
        });

        closeButton.setOnClickListener(v ->
                finish()
        );
    }

    private void setLoading(boolean loading) {

        progress.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );

        detectButton.setEnabled(!loading);
    }

    private void uploadVideo() {

        setLoading(true);

        resultText.setText("Uploading video...");

        byte[] bytes;
        String mimeType = "video/mp4";
        String filename = "video.mp4";

        try {

            if (selectedVideoUri == null) {
                throw new IllegalStateException(
                        "No video selected"
                );
            }

            String resolvedMime =
                    getContentResolver()
                            .getType(selectedVideoUri);

            if (resolvedMime != null) {
                mimeType = resolvedMime;
            }

            String displayName =
                    getDisplayName(selectedVideoUri);

            if (displayName != null) {
                filename = displayName;
            }

            bytes = readAllBytes(selectedVideoUri);

        } catch (Exception e) {

            setLoading(false);

            resultText.setText(
                    "Failed to read video:\n"
                            + e.getMessage()
            );

            return;
        }

        RequestBody fileBody =
                RequestBody.create(
                        bytes,
                        MediaType.parse(mimeType)
                );

        RequestBody multipart =
                new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                                "file",
                                filename,
                                fileBody
                        )
                        .addFormDataPart(
                                "sample_rate",
                                "30"
                        )
                        .build();

        Request request =
                new Request.Builder()
                        .url(API_BASE_URL
                                + "/api/v1/detect/video")
                        .post(multipart)
                        .build();

        httpClient.newCall(request)
                .enqueue(new Callback() {

                    @Override
                    public void onFailure(
                            Call call,
                            java.io.IOException e
                    ) {

                        runOnUiThread(() -> {

                            setLoading(false);

                            resultText.setText(
                                    "Network error:\n"
                                            + e.getMessage()
                            );
                        });
                    }

                    @Override
                    public void onResponse(
                            Call call,
                            Response response
                    ) {

                        try (Response res = response) {

                            String body =
                                    res.body() != null
                                            ? res.body().string()
                                            : "";

                            runOnUiThread(() ->
                                    handleResponse(
                                            res.code(),
                                            res.isSuccessful(),
                                            body
                                    )
                            );

                        } catch (Exception e) {

                            runOnUiThread(() -> {

                                setLoading(false);

                                resultText.setText(
                                        e.getMessage()
                                );
                            });
                        }
                    }
                });
    }

    private void handleResponse(
            int statusCode,
            boolean ok,
            String body
    ) {

        setLoading(false);

        if (!ok) {

            resultText.setText(
                    "Error "
                            + statusCode
                            + "\n"
                            + body
            );

            return;
        }

        try {

            JSONObject obj =
                    new JSONObject(body);

            int totalFrames =
                    obj.optInt("total_frames");

            int analyzedFrames =
                    obj.optInt("frames_analyzed");

            JSONArray results =
                    obj.optJSONArray("results");

            StringBuilder sb =
                    new StringBuilder();

            sb.append("Video Analysis\n\n");

            sb.append("Total Frames: ")
                    .append(totalFrames)
                    .append("\n");

            sb.append("Analyzed Frames: ")
                    .append(analyzedFrames)
                    .append("\n\n");

            if (results != null) {

                for (int i = 0; i < results.length(); i++) {

                    JSONObject item =
                            results.getJSONObject(i);

                    sb.append("Frame ")
                            .append(
                                    item.optInt("frame_number")
                            )
                            .append("\n");

                    sb.append("Emotion: ")
                            .append(
                                    item.optString("emotion")
                            )
                            .append("\n");

                    sb.append("Confidence: ")
                            .append(
                                    item.optDouble("confidence")
                            )
                            .append("\n\n");
                }
            }

            resultText.setText(sb.toString());

        } catch (Exception e) {

            resultText.setText(
                    "Invalid response\n"
                            + e.getMessage()
            );
        }
    }

    private void loadVideoThumbnail(Uri uri) {

        try {

            MediaMetadataRetriever retriever =
                    new MediaMetadataRetriever();

            retriever.setDataSource(this, uri);

            previewImage.setImageBitmap(
                    retriever.getFrameAtTime()
            );

            retriever.release();

        } catch (Exception ignored) {
        }
    }

    private byte[] readAllBytes(Uri uri)
            throws Exception {

        try (
                InputStream in =
                        getContentResolver()
                                .openInputStream(uri);

                ByteArrayOutputStream out =
                        new ByteArrayOutputStream()
        ) {

            if (in == null) {
                throw new IllegalStateException(
                        "Could not open stream"
                );
            }

            byte[] buf = new byte[8192];

            int n;

            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }

            return out.toByteArray();
        }
    }

    private @Nullable String getDisplayName(Uri uri) {

        if (!"content".equals(uri.getScheme())) {
            return null;
        }

        Cursor cursor = null;

        try {

            cursor =
                    getContentResolver()
                            .query(
                                    uri,
                                    null,
                                    null,
                                    null,
                                    null
                            );

            if (cursor != null
                    && cursor.moveToFirst()) {

                int index =
                        cursor.getColumnIndex(
                                OpenableColumns.DISPLAY_NAME
                        );

                if (index >= 0) {
                    return cursor.getString(index);
                }
            }

        } catch (Exception ignored) {

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }
}