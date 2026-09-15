package com.abosultan.darbakmaps.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Temporary render surface. It deliberately does not pretend to be the real Saudi map. */
public final class MapPlaceholderView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MapPlaceholderView(Context context) { super(context); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xFF0C1B35);
        paint.setStrokeWidth(1f);
        paint.setColor(0xFF18365A);
        for (int x = 0; x < getWidth(); x += 72) canvas.drawLine(x, 0, x, getHeight(), paint);
        for (int y = 0; y < getHeight(); y += 72) canvas.drawLine(0, y, getWidth(), y, paint);

        paint.setColor(DarbakUi.ACCENT);
        paint.setStyle(Paint.Style.FILL);
        float cx = getWidth() * .48f, cy = getHeight() * .50f;
        Path arrow = new Path();
        arrow.moveTo(cx, cy - 34);
        arrow.lineTo(cx + 23, cy + 28);
        arrow.lineTo(cx, cy + 17);
        arrow.lineTo(cx - 23, cy + 28);
        arrow.close();
        canvas.drawPath(arrow, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(0xFFF3F8FF);
        canvas.drawPath(arrow, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
