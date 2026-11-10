package io.github.vipersq.casualtykit.atak;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

import io.github.vipersq.casualtykit.core.BodyMap;
import io.github.vipersq.casualtykit.core.Injury;

/**
 * Front and back body diagrams side by side. Tapping a body region reports the region and the exact
 * spot; injuries already placed are drawn as numbered markers matching the list under the diagram.
 */
class BodyMapView extends View {
    interface Listener { void onTap(String view, BodyMap.Region region, float nx, float ny); }

    /** Layout in diagram units: two views with a gap between them and a caption strip above. */
    private static final float GAP = 20f, CAPTION = 16f;
    private static final float TOTAL_W = BodyMap.WIDTH * 2 + GAP, TOTAL_H = BodyMap.HEIGHT + CAPTION;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG), marker = new Paint(Paint.ANTI_ALIAS_FLAG),
            markerRing = new Paint(Paint.ANTI_ALIAS_FLAG), markerText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final float density;
    private final int touchSlop;
    private List<Injury> injuries = new ArrayList<>();
    private Listener listener;
    private String pressedView;
    private BodyMap.Region pressedRegion;
    private float downX, downY;

    BodyMapView(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * density);
        stroke.setColor(Style.BODY_STROKE);
        text.setColor(Style.MUTED);
        text.setTextAlign(Paint.Align.CENTER);
        marker.setColor(Style.RED);
        markerRing.setStyle(Paint.Style.STROKE);
        markerRing.setStrokeWidth(2f * density);
        markerRing.setColor(0xFFFFFFFF);
        markerText.setColor(0xFFFFFFFF);
        markerText.setTextAlign(Paint.Align.CENTER);
        markerText.setTypeface(Typeface.DEFAULT_BOLD);
    }

    void setListener(Listener l) { listener = l; }

    /** The list is kept by reference; call {@link #invalidate()} after changing it. */
    void setInjuries(List<Injury> list) { injuries = list; invalidate(); }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        if (w <= 0) w = (int) (320 * density);
        // Cap the height so the diagram never fills more than a comfortable part of the pane.
        int h = (int) Math.min(w * TOTAL_H / TOTAL_W, 420 * density);
        setMeasuredDimension(w, h);
    }

    private float scale() { return Math.min(getWidth() / TOTAL_W, getHeight() / TOTAL_H); }
    private float originX(int viewIndex) { return (getWidth() - TOTAL_W * scale()) / 2f + viewIndex * (BodyMap.WIDTH + GAP) * scale(); }
    private float originY() { return CAPTION * scale(); }

    @Override
    protected void onDraw(Canvas c) {
        float s = scale();
        text.setTextSize(Math.max(9 * density, 7.5f * s));
        for (int k = 0; k < 2; k++) {
            String view = k == 0 ? BodyMap.FRONT : BodyMap.BACK;
            float ox = originX(k), oy = originY();
            text.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(view, ox + BodyMap.WIDTH * s / 2, CAPTION * s * 0.75f, text);
            text.setTypeface(Typeface.DEFAULT);
            // Which side of the casualty is on the viewer's left in this view.
            c.drawText(k == 0 ? "R" : "L", ox + 7 * s, oy + 50 * s, text);
            c.drawText(k == 0 ? "L" : "R", ox + 93 * s, oy + 50 * s, text);
            for (BodyMap.Region r : BodyMap.regions(view)) {
                float inset = 0.8f * s;
                tmp.set(ox + r.x0 * s + inset, oy + r.y0 * s + inset, ox + r.x1 * s - inset, oy + r.y1 * s - inset);
                fill.setColor(r == pressedRegion && view.equals(pressedView) ? Style.BODY_PRESSED : Style.BODY);
                if (r.oval) { c.drawOval(tmp, fill); c.drawOval(tmp, stroke); }
                else { float rad = 3.2f * s; c.drawRoundRect(tmp, rad, rad, fill); c.drawRoundRect(tmp, rad, rad, stroke); }
            }
        }
        float rad = Math.max(9 * density, 4.2f * s);
        markerText.setTextSize(rad * 1.15f);
        for (int i = 0; i < injuries.size(); i++) {
            Injury in = injuries.get(i);
            if (!in.onDiagram()) continue;
            float cx = originX(BodyMap.BACK.equals(in.view) ? 1 : 0) + in.x * BodyMap.WIDTH * s;
            float cy = originY() + in.y * BodyMap.HEIGHT * s;
            c.drawCircle(cx, cy, rad, marker);
            c.drawCircle(cx, cy, rad, markerRing);
            c.drawText(String.valueOf(i + 1), cx, cy + rad * 0.4f, markerText);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                locate(downX, downY, false);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getX() - downX) > touchSlop || Math.abs(e.getY() - downY) > touchSlop) clearPress();
                return true;
            case MotionEvent.ACTION_UP:
                if (pressedRegion != null) { locate(downX, downY, true); performClick(); }
                clearPress();
                return true;
            case MotionEvent.ACTION_CANCEL:
                clearPress();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    private void clearPress() { pressedRegion = null; pressedView = null; invalidate(); }

    private void locate(float px, float py, boolean fire) {
        float s = scale();
        if (s <= 0) return;
        for (int k = 0; k < 2; k++) {
            float ux = (px - originX(k)) / s, uy = (py - originY()) / s;
            if (ux < 0 || ux > BodyMap.WIDTH || uy < 0 || uy > BodyMap.HEIGHT) continue;
            String view = k == 0 ? BodyMap.FRONT : BodyMap.BACK;
            BodyMap.Region r = BodyMap.regionAt(view, ux, uy);
            pressedRegion = r; pressedView = r == null ? null : view;
            if (fire && r != null && listener != null) listener.onTap(view, r, ux / BodyMap.WIDTH, uy / BodyMap.HEIGHT);
            return;
        }
        pressedRegion = null; pressedView = null;
    }
}
