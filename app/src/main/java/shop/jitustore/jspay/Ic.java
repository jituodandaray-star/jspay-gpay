package shop.jitustore.jspay;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Khud ke bane line-style icons (emoji nahi). 24x24 ke dabbe mein banate hain. */
public class Ic extends Drawable {
    private final String n;
    private final int col;
    private final int px;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Ic(String name, int color, int sizePx) {
        n = name;
        col = color;
        px = sizePx;
    }

    @Override public int getIntrinsicWidth() { return px; }
    @Override public int getIntrinsicHeight() { return px; }
    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    private void poly(Canvas c, boolean close, float... v) {
        Path q = new Path();
        q.moveTo(v[0], v[1]);
        for (int i = 2; i + 1 < v.length; i += 2) q.lineTo(v[i], v[i + 1]);
        if (close) q.close();
        c.drawPath(q, p);
    }

    private void line(Canvas c, float a, float b, float x, float y) { c.drawLine(a, b, x, y, p); }

    private void arc(Canvas c, float l, float t, float r, float b, float st, float sw) {
        c.drawArc(new RectF(l, t, r, b), st, sw, false, p);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        float s = Math.min(b.width(), b.height()) / 24f;
        c.save();
        c.translate(b.left, b.top);
        c.scale(s, s);
        p.setColor(col);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (n) {
            case "key":
                c.drawCircle(8f, 15f, 4f, p);
                line(c, 11f, 12f, 20f, 3f);
                line(c, 16f, 7f, 19f, 10f);
                break;
            case "bell": {
                Path q = new Path();
                q.moveTo(6f, 17f);
                q.lineTo(6f, 11f);
                q.arcTo(new RectF(6f, 5f, 18f, 17f), 180f, 180f);
                q.lineTo(18f, 17f);
                q.lineTo(20f, 19f);
                q.lineTo(4f, 19f);
                q.close();
                c.drawPath(q, p);
                line(c, 10f, 21.5f, 14f, 21.5f);
                break;
            }
            case "batt":
                c.drawRoundRect(new RectF(3f, 7f, 19f, 17f), 2f, 2f, p);
                line(c, 21f, 11f, 21f, 13f);
                line(c, 7f, 10f, 7f, 14f);
                line(c, 11f, 10f, 11f, 14f);
                break;
            case "globe":
                c.drawCircle(12f, 12f, 9f, p);
                line(c, 3f, 12f, 21f, 12f);
                c.drawOval(new RectF(8f, 3f, 16f, 21f), p);
                break;
            case "crown":
                poly(c, true, 3f, 8f, 7f, 12f, 12f, 5f, 17f, 12f, 21f, 8f, 19f, 19f, 5f, 19f);
                break;
            case "out":
                poly(c, false, 9f, 4f, 5f, 4f, 5f, 20f, 9f, 20f);
                poly(c, false, 16f, 8f, 20f, 12f, 16f, 16f);
                line(c, 20f, 12f, 9f, 12f);
                break;
            case "sync":
                arc(c, 4f, 4f, 20f, 20f, -40f, 270f);
                poly(c, false, 20f, 3f, 20f, 8f, 15f, 8f);
                break;
            case "vol":
                poly(c, true, 4f, 9f, 8f, 9f, 13f, 5f, 13f, 19f, 8f, 15f, 4f, 15f);
                arc(c, 10f, 8f, 20f, 16f, -55f, 110f);
                arc(c, 8f, 4f, 24f, 20f, -50f, 100f);
                break;
            case "rup":
                line(c, 7f, 5f, 17f, 5f);
                line(c, 7f, 9f, 17f, 9f);
                arc(c, 3f, 5f, 13f, 13f, -90f, 180f);
                line(c, 8f, 13f, 15f, 20f);
                break;
            case "hist":
                c.drawCircle(12f, 12f, 9f, p);
                poly(c, false, 12f, 7f, 12f, 12f, 15f, 14f);
                break;
            case "shield":
                poly(c, true, 12f, 3f, 20f, 6f, 20f, 12f, 12f, 21f, 4f, 12f, 4f, 6f);
                poly(c, false, 8.5f, 12f, 11f, 14.5f, 16f, 9.5f);
                break;
            case "info":
                c.drawCircle(12f, 12f, 9f, p);
                line(c, 12f, 11f, 12f, 17f);
                line(c, 12f, 7.5f, 12f, 7.6f);
                break;
            case "power":
                arc(c, 5f, 5f, 19f, 19f, -50f, 280f);
                line(c, 12f, 3f, 12f, 12f);
                break;
            case "warn":
                poly(c, true, 12f, 3f, 22f, 20f, 2f, 20f);
                line(c, 12f, 10f, 12f, 15f);
                line(c, 12f, 17.5f, 12f, 17.6f);
                break;
            case "check":
                poly(c, false, 5f, 12.5f, 9.5f, 17f, 19f, 7f);
                break;
            case "moon": {
                p.setStyle(Paint.Style.FILL);
                Path a = new Path();
                a.addCircle(12f, 12f, 8f, Path.Direction.CW);
                Path m = new Path();
                m.addCircle(16f, 9f, 7f, Path.Direction.CW);
                a.op(m, Path.Op.DIFFERENCE);
                c.drawPath(a, p);
                break;
            }
            default:
                c.drawCircle(12f, 12f, 8f, p);
        }
        c.restore();
    }
}
