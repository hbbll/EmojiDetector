package com.khozy.emotion;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialCardView imageCard =
                findViewById(R.id.imageCard);

        MaterialCardView videoCard =
                findViewById(R.id.videoCard);

        MaterialCardView cameraCard =
                findViewById(R.id.cameraCard);

        imageCard.setOnClickListener(v -> {

            Intent intent = new Intent(this, ImageActivity.class);

            startActivity(intent);
        });

        videoCard.setOnClickListener(v -> {

            Intent intent =
                    new Intent(this, VideoActivity.class);

            startActivity(intent);
        });

        cameraCard.setOnClickListener(v -> {

            Intent intent =
                    new Intent(this, CameraActivity.class);

            startActivity(intent);
        });
    }
}