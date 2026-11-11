package io.github.vipersq.casualtykit.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Geometry of the body diagram: tappable regions for the front and back views.
 *
 * Each view is a box {@link #WIDTH} x {@link #HEIGHT} units. The front view is drawn as seen by the
 * medic facing the casualty, so the casualty's RIGHT side is on the viewer's left; the back view is
 * the opposite. Injury positions are stored normalised (0..1) so they are independent of screen size.
 *
 * Every region is a closed outline. The same outline is used for drawing and for hit testing, so
 * what the user sees is exactly what can be tapped.
 */
public final class BodyMap {
    private BodyMap() {}

    public static final float WIDTH = 100f, HEIGHT = 220f;
    public static final String FRONT = "FRONT", BACK = "BACK";
    /** A tap this close to a region (in units) still counts, so small parts such as hands are easy to hit. */
    public static final float TOLERANCE = 2.5f;

    public static final class Region {
        /** Location text stored on the injury, e.g. "R thigh" or "Upper back". */
        public final String name;
        /** Closed outline as x0,y0,x1,y1,... in view units. */
        public final float[] outline;
        public final float x0, y0, x1, y1;

        Region(String name, float[] outline) {
            this.name = name; this.outline = outline;
            float ax = Float.MAX_VALUE, ay = Float.MAX_VALUE, bx = -Float.MAX_VALUE, by = -Float.MAX_VALUE;
            for (int i = 0; i < outline.length; i += 2) {
                ax = Math.min(ax, outline[i]); bx = Math.max(bx, outline[i]);
                ay = Math.min(ay, outline[i + 1]); by = Math.max(by, outline[i + 1]);
            }
            x0 = ax; y0 = ay; x1 = bx; y1 = by;
        }

        public boolean contains(float x, float y) {
            if (x < x0 || x > x1 || y < y0 || y > y1) return false;
            boolean in = false;
            int n = outline.length / 2;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                float xi = outline[2 * i], yi = outline[2 * i + 1], xj = outline[2 * j], yj = outline[2 * j + 1];
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
            }
            return in;
        }

        /** Distance from a point to the outline; 0 when the point is inside. */
        public float distance(float x, float y) {
            if (contains(x, y)) return 0f;
            float best = Float.MAX_VALUE;
            int n = outline.length / 2;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                float ax = outline[2 * j], ay = outline[2 * j + 1], bx = outline[2 * i], by = outline[2 * i + 1];
                float dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
                float t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / len2));
                float px = ax + t * dx - x, py = ay + t * dy - y;
                best = Math.min(best, px * px + py * py);
            }
            return (float) Math.sqrt(best);
        }

        /** A point guaranteed to be inside the region, for tests and labels. */
        public float[] centre() {
            float cx = 0, cy = 0;
            int n = outline.length / 2;
            for (int i = 0; i < n; i++) { cx += outline[2 * i]; cy += outline[2 * i + 1]; }
            return new float[] { cx / n, cy / n };
        }
    }

    public static List<Region> regions(String view) {
        return BACK.equals(view) ? BACK_REGIONS : FRONT_REGIONS;
    }

    /**
     * Region under a point given in view units. A point just outside the body snaps to the nearest
     * region within {@link #TOLERANCE}; further away the result is null.
     */
    public static Region regionAt(String view, float x, float y) {
        Region near = null;
        float best = TOLERANCE;
        for (Region r : regions(view)) {
            float d = r.distance(x, y);
            if (d == 0f) return r;
            if (d <= best) { best = d; near = r; }
        }
        return near;
    }

    // Outlines for the centre line and for the side of the body on the viewer's right (x > 50).
    // The other side is the mirror image.
    private static final float[] NECK = { 45.6f, 25, 54.4f, 25, 55.4f, 33.4f, 44.6f, 33.4f };
    private static final float[] CHEST = { 44.4f, 34.2f, 55.6f, 34.2f, 65.6f, 37.6f, 67.6f, 46, 66.6f, 60, 65.2f, 70,
            34.8f, 70, 33.4f, 60, 32.4f, 46, 34.4f, 37.6f };
    private static final float[] ABDOMEN = { 35, 71, 65, 71, 63.6f, 83, 64.4f, 96, 35.6f, 96, 36.4f, 83 };
    private static final float[] PELVIS = { 35.6f, 97, 64.4f, 97, 66.6f, 107, 66.8f, 114, 53, 117.6f, 50, 121,
            47, 117.6f, 33.2f, 114, 33.4f, 107 };
    private static final float[] UPPER_ARM = { 66.8f, 37.4f, 72, 39.6f, 75, 46, 76.4f, 60, 77.4f, 74.2f, 69.8f, 75.2f,
            68.8f, 60, 68.6f, 47.6f };
    private static final float[] FOREARM = { 70, 76.2f, 77.6f, 75.2f, 80.4f, 90, 82.4f, 105.8f, 76.2f, 107.4f, 72.6f, 92 };
    private static final float[] HAND = { 76.4f, 108.4f, 82.6f, 106.8f, 85, 113, 85, 120, 81.8f, 124.6f, 77.6f, 123, 76.6f, 116 };
    private static final float[] THIGH = { 51, 122, 53.6f, 118.6f, 67, 115, 67.4f, 126, 65.4f, 150, 63.8f, 163,
            53.4f, 163, 52.4f, 150, 51, 132 };
    private static final float[] LOWER_LEG = { 53.4f, 164, 63.8f, 164, 65.4f, 176, 63.8f, 192, 61.6f, 204, 55.4f, 204,
            54, 192, 52.8f, 176 };
    private static final float[] FOOT = { 55.2f, 205, 61.8f, 205, 65.6f, 210.6f, 66.8f, 215.4f, 54.6f, 215.4f, 54.2f, 210 };

    // Must stay below the outline constants: static fields initialise in textual order.
    private static final List<Region> FRONT_REGIONS = build(true);
    private static final List<Region> BACK_REGIONS = build(false);

    private static List<Region> build(boolean front) {
        // The side of the casualty that appears on the viewer's left / right in this view.
        String left = front ? "R" : "L", right = front ? "L" : "R";
        String sfx = front ? "" : " (back)";
        List<Region> r = new ArrayList<>();
        r.add(new Region("Head" + sfx, smooth(head())));
        r.add(new Region("Neck" + sfx, smooth(NECK)));
        r.add(new Region(front ? "Chest" : "Upper back", smooth(CHEST)));
        r.add(new Region(front ? "Abdomen" : "Lower back", smooth(ABDOMEN)));
        r.add(new Region(front ? "Pelvis" : "Buttocks", smooth(PELVIS)));
        String[] parts = { " upper arm", " forearm", " hand", " thigh", " lower leg", " foot" };
        float[][] shapes = { UPPER_ARM, FOREARM, HAND, THIGH, LOWER_LEG, FOOT };
        for (int i = 0; i < parts.length; i++) r.add(new Region(left + parts[i] + sfx, smooth(mirror(shapes[i]))));
        for (int i = 0; i < parts.length; i++) r.add(new Region(right + parts[i] + sfx, smooth(shapes[i])));
        return Collections.unmodifiableList(r);
    }

    /** Egg-shaped head: an ellipse that narrows towards the chin. */
    private static float[] head() {
        int n = 20;
        float[] p = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            double sy = Math.sin(a), taper = sy > 0 ? 1 - 0.2 * sy : 1;
            p[2 * i] = (float) (50 + 9.6 * Math.cos(a) * taper);
            p[2 * i + 1] = (float) (13.4 + 11.2 * sy);
        }
        return p;
    }

    private static float[] mirror(float[] p) {
        int n = p.length / 2;
        float[] out = new float[p.length];
        for (int i = 0; i < n; i++) { out[2 * (n - 1 - i)] = WIDTH - p[2 * i]; out[2 * (n - 1 - i) + 1] = p[2 * i + 1]; }
        return out;
    }

    /** Two rounds of corner cutting turn the control polygon into a smooth outline. */
    private static float[] smooth(float[] p) {
        for (int round = 0; round < 2; round++) {
            int n = p.length / 2;
            float[] out = new float[n * 4];
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float ax = p[2 * i], ay = p[2 * i + 1], bx = p[2 * j], by = p[2 * j + 1];
                out[4 * i] = 0.75f * ax + 0.25f * bx; out[4 * i + 1] = 0.75f * ay + 0.25f * by;
                out[4 * i + 2] = 0.25f * ax + 0.75f * bx; out[4 * i + 3] = 0.25f * ay + 0.75f * by;
            }
            p = out;
        }
        return p;
    }
}
