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
 */
public final class BodyMap {
    private BodyMap() {}

    public static final float WIDTH = 100f, HEIGHT = 220f;
    public static final String FRONT = "FRONT", BACK = "BACK";

    public static final class Region {
        /** Location text stored on the injury, e.g. "R thigh" or "Upper back". */
        public final String name;
        public final float x0, y0, x1, y1;
        public final boolean oval;

        Region(String name, float x0, float y0, float x1, float y1, boolean oval) {
            this.name = name; this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.oval = oval;
        }

        public boolean contains(float x, float y) {
            if (x < x0 || x > x1 || y < y0 || y > y1) return false;
            if (!oval) return true;
            float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, rx = (x1 - x0) / 2, ry = (y1 - y0) / 2;
            float dx = (x - cx) / rx, dy = (y - cy) / ry;
            return dx * dx + dy * dy <= 1f;
        }
    }

    private static final List<Region> FRONT_REGIONS = build(true);
    private static final List<Region> BACK_REGIONS = build(false);

    public static List<Region> regions(String view) {
        return BACK.equals(view) ? BACK_REGIONS : FRONT_REGIONS;
    }

    /** Region under a point given in view units, or null when the point is off the body. */
    public static Region regionAt(String view, float x, float y) {
        for (Region r : regions(view)) if (r.contains(x, y)) return r;
        return null;
    }

    private static List<Region> build(boolean front) {
        // The side of the casualty that appears on the viewer's left / right in this view.
        String left = front ? "R" : "L", right = front ? "L" : "R";
        String sfx = front ? "" : " (back)";
        List<Region> r = new ArrayList<>();
        r.add(new Region("Head" + sfx, 39, 2, 61, 30, true));
        r.add(new Region("Neck" + sfx, 45, 29, 55, 37, false));
        r.add(new Region(front ? "Chest" : "Upper back", 31, 37, 69, 72, false));
        r.add(new Region(front ? "Abdomen" : "Lower back", 33, 72, 67, 98, false));
        r.add(new Region(front ? "Pelvis" : "Buttocks", 32, 98, 68, 114, false));
        limb(r, left, sfx, 19, 30, 17, 28, 15, 27, 33, 49, 35, 48, 32, 48);
        limb(r, right, sfx, 70, 81, 72, 83, 73, 85, 51, 67, 52, 65, 52, 68);
        return Collections.unmodifiableList(r);
    }

    private static void limb(List<Region> r, String side, String sfx, float ua0, float ua1, float fa0, float fa1,
            float h0, float h1, float t0, float t1, float l0, float l1, float f0, float f1) {
        r.add(new Region(side + " upper arm" + sfx, ua0, 39, ua1, 76, false));
        r.add(new Region(side + " forearm" + sfx, fa0, 76, fa1, 108, false));
        r.add(new Region(side + " hand" + sfx, h0, 108, h1, 122, false));
        r.add(new Region(side + " thigh" + sfx, t0, 114, t1, 160, false));
        r.add(new Region(side + " lower leg" + sfx, l0, 160, l1, 202, false));
        r.add(new Region(side + " foot" + sfx, f0, 202, f1, 214, false));
    }
}
