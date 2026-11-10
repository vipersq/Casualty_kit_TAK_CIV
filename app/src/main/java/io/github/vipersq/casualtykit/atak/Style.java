package io.github.vipersq.casualtykit.atak;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/** Colours and shape helpers shared by every screen, so the plugin has one consistent look. */
final class Style {
    private Style() {}

    static final int BG = 0xFF14171C, CARD = 0xFF1F242B, FIELD = 0xFF161A20, STROKE = 0xFF2E353F;
    static final int TEXT = 0xFFF2F4F7, MUTED = 0xFF9AA4B2;
    static final int GREEN = 0xFF2ECC71, RED = 0xFFE5484D, AMBER = 0xFFF5A524, BLUE = 0xFF4DA3FF, GREY = 0xFF5B6573;
    static final int BODY = 0xFF3A424D, BODY_STROKE = 0xFF6B7685, BODY_PRESSED = 0xFF4DA3FF;

    static GradientDrawable round(int fill, int stroke, float radiusPx, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    /** Rounded background that darkens while pressed. */
    static Drawable pressable(int fill, int stroke, float radiusPx, int strokePx) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { android.R.attr.state_pressed }, round(blend(fill, 0xFF000000, 0.25f), stroke, radiusPx, strokePx));
        s.addState(new int[] {}, round(fill, stroke, radiusPx, strokePx));
        return s;
    }

    static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Black or white, whichever reads better on the given background. */
    static int on(int bg) {
        double lum = 0.299 * ((bg >> 16) & 0xFF) + 0.587 * ((bg >> 8) & 0xFF) + 0.114 * (bg & 0xFF);
        return lum > 150 ? 0xFF10140F : 0xFFFFFFFF;
    }
}
