package io.github.vipersq.casualtykit.atak;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import io.github.vipersq.casualtykit.core.Casualty;
import io.github.vipersq.casualtykit.core.Tccc;

/**
 * Map marker icons, drawn in code so there is one per combination of status and precedence:
 * a round badge in the precedence colour with a cross while the casualty is on scene, an arrow once
 * evacuation is requested or done, a tick for returned to duty, and a black badge for the dead.
 */
final class MarkerIcons {
    private MarkerIcons() {}

    /** Drawn size in pixels; ATAK scales it to {@link #DISPLAY}. */
    private static final int PX = 96;
    /** On-map size. Stock ATAK icons are 32, so this is half as big again. */
    static final int DISPLAY = 48;
    private static final Map<String, String> CACHE = new HashMap<>();

    /** Identifies the picture, so a marker is only redrawn when it would actually change. */
    static String key(Casualty c) {
        return c.status.name() + (c.status.isOpen() ? "/" + c.precedence.argb : "");
    }

    /** The icon as a base64:// URI that ATAK accepts for marker icons. */
    static synchronized String uri(Casualty c) {
        String key = key(c);
        String uri = CACHE.get(key);
        if (uri == null) {
            Bitmap b = draw(c.status, c.precedence);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
            b.recycle();
            uri = "base64://" + Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP | Base64.URL_SAFE);
            CACHE.put(key, uri);
        }
        return uri;
    }

    private static Bitmap draw(Tccc.Status status, Tccc.Precedence precedence) {
        Bitmap bmp = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float m = PX / 2f;
        int fillColour, ringColour = 0xFFFFFFFF, glyphColour = 0xFFFFFFFF;
        switch (status) {
            case KIA: case DECEASED: fillColour = Tccc.DEAD_ARGB; break;
            case EVACUATED: fillColour = Style.GREY; break;
            case RETURNED_TO_DUTY: fillColour = Style.BG; ringColour = Style.GREEN; glyphColour = Style.GREEN; break;
            default: fillColour = precedence.argb; break;
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // dark halo so the badge stands out on bright imagery
        p.setColor(0x66000000);
        c.drawCircle(m, m, 47, p);
        p.setColor(ringColour);
        c.drawCircle(m, m, 44, p);
        p.setColor(fillColour);
        c.drawCircle(m, m, 37, p);

        p.setColor(glyphColour);
        switch (status) {
            case ACTIVE: {
                c.drawRoundRect(new RectF(m - 9, m - 24, m + 9, m + 24), 4, 4, p);
                c.drawRoundRect(new RectF(m - 24, m - 9, m + 24, m + 9), 4, 4, p);
                break;
            }
            case EVAC_REQUESTED: case EVACUATED: {
                Path arrow = new Path();
                arrow.moveTo(m, m - 27); arrow.lineTo(m + 22, m - 1); arrow.lineTo(m + 9, m - 1);
                arrow.lineTo(m + 9, m + 25); arrow.lineTo(m - 9, m + 25); arrow.lineTo(m - 9, m - 1);
                arrow.lineTo(m - 22, m - 1); arrow.close();
                c.drawPath(arrow, p);
                break;
            }
            case RETURNED_TO_DUTY: {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(10); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
                Path tick = new Path();
                tick.moveTo(m - 20, m + 2); tick.lineTo(m - 6, m + 16); tick.lineTo(m + 21, m - 15);
                c.drawPath(tick, p);
                break;
            }
            case KIA: {
                p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(30);
                c.drawText("KIA", m, m + 11, p);
                break;
            }
            default: { // DECEASED
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(10); p.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(m - 17, m - 17, m + 17, m + 17, p);
                c.drawLine(m + 17, m - 17, m - 17, m + 17, p);
                break;
            }
        }
        return bmp;
    }
}
