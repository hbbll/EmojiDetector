package com.khozy.emotion;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DrawingOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<RectF> faceRectsInImage = new ArrayList<>();
    private int imageWidth;
    private int imageHeight;

    public DrawingOverlayView(Context context) {
        super(context);
        init();
    }

    public DrawingOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawingOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setColor(0xFF00FF00); // green
    }

    public void setFaceBoxInImage(int x, int y, int w, int h, int imgW, int imgH) {
        imageWidth = imgW;
        imageHeight = imgH;
        faceRectsInImage.clear();
        faceRectsInImage.add(new RectF(x, y, x + w, y + h));
        invalidate();
    }

    public void setFaceBoxesInImage(List<RectF> rects, int imgW, int imgH) {
        imageWidth = imgW;
        imageHeight = imgH;
        faceRectsInImage = new ArrayList<>(rects);
        invalidate();
    }

    public void clear() {
        faceRectsInImage.clear();
        imageWidth = 0;
        imageHeight = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!faceRectsInImage.isEmpty() && imageWidth > 0 && imageHeight > 0) {
            float viewW = getWidth();
            float viewH = getHeight();
            float scale = Math.min(viewW / imageWidth, viewH / imageHeight);
            float dx = (viewW - imageWidth * scale) / 2f;
            float dy = (viewH - imageHeight * scale) / 2f;

            for (RectF faceRectInImage : faceRectsInImage) {
                RectF mapped = new RectF(
                        dx + faceRectInImage.left * scale,
                        dy + faceRectInImage.top * scale,
                        dx + faceRectInImage.right * scale,
                        dy + faceRectInImage.bottom * scale
                );
                canvas.drawRect(mapped, paint);
            }
        }
    }
}
