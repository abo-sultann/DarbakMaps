package com.abosultan.darbakmaps.map;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public final class DarbakPreviewMapView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public DarbakPreviewMapView(Context context) {
        super(context);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        canvas.drawColor(Color.rgb(232, 224, 197));
        drawContour(canvas, width * 0.12f, height * 0.30f, width * 0.34f, height * 0.17f);
        drawContour(canvas, width * 0.70f, height * 0.23f, width * 0.24f, height * 0.13f);
        drawContour(canvas, width * 0.76f, height * 0.73f, width * 0.31f, height * 0.16f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(28f);
        paint.setColor(Color.rgb(250, 247, 232));
        path.reset();
        path.moveTo(-20, height * 0.70f);
        path.cubicTo(width * 0.25f, height * 0.58f, width * 0.38f, height * 0.32f, width * 0.58f, height * 0.48f);
        path.cubicTo(width * 0.72f, height * 0.60f, width * 0.83f, height * 0.38f, width + 20f, height * 0.30f);
        canvas.drawPath(path, paint);

        paint.setStrokeWidth(4f);
        paint.setColor(Color.rgb(215, 174, 85));
        canvas.drawPath(path, paint);

        paint.setStrokeWidth(7f);
        paint.setColor(Color.rgb(27, 108, 78));
        path.reset();
        path.moveTo(width * 0.18f, height * 0.72f);
        path.cubicTo(width * 0.34f, height * 0.65f, width * 0.43f, height * 0.52f, width * 0.52f, height * 0.55f);
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(150, 16, 37, 30));
        paint.setTextSize(16f);
        canvas.drawText("معاينة — أضف حزمة الخريطة", 24f, height - 24f, paint);
    }

    private void drawContour(Canvas canvas, float x, float y, float width, float height) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.argb(55, 89, 100, 67));
        for (int i = 0; i < 4; i++) {
            canvas.drawOval(x + i * 11f, y + i * 8f, x + width - i * 13f, y + height - i * 7f, paint);
        }
    }
}

