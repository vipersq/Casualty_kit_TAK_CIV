package io.github.vipersq.casualtykit.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Plain-text reports (MIST and 9-line) for chat, voice read-back and the remarks of the map marker. */
public final class Readout {
    private Readout() {}

    public static String dtg(long t) {
        if (t <= 0) return "UNK";
        SimpleDateFormat f = new SimpleDateFormat("ddHHmm'Z'MMMyy", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(t)).toUpperCase(Locale.US);
    }

    /** Elapsed time as "45 min" or "2 h 05 min"; "0 min" for anything under a minute or in the future. */
    public static String elapsed(long from, long now) {
        long min = Math.max(0, (now - from) / 60000L);
        return min < 60 ? min + " min" : (min / 60) + " h " + String.format(Locale.US, "%02d", min % 60) + " min";
    }

    public static String mechanism(Casualty c) {
        StringBuilder sb = new StringBuilder();
        for (Tccc.Mechanism m : c.mechanisms) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(m == Tccc.Mechanism.OTHER && !c.mechanismOther.isEmpty() ? c.mechanismOther : m.name());
        }
        return sb.length() == 0 ? "UNK" : sb.toString();
    }
    public static String injuries(Casualty c) {
        StringBuilder sb = new StringBuilder();
        for (Injury i : c.injuries) { if (sb.length() > 0) sb.append("; "); sb.append(i.summary()); }
        return sb.length() == 0 ? "UNK" : sb.toString();
    }
    public static String signs(Casualty c) {
        Vitals v = c.latestVitals();
        return v == null || v.summary().isEmpty() ? "UNK" : v.summary() + " @" + dtg(v.time);
    }
    public static String treatments(Casualty c) {
        StringBuilder sb = new StringBuilder();
        for (Treatment t : c.treatments) { if (sb.length() > 0) sb.append("; "); sb.append(t.summary()); }
        return sb.length() == 0 ? "NONE" : sb.toString();
    }

    public static String mist(Casualty c) {
        return "MIST " + c.label() + " [" + c.precedence.letter + " " + c.precedence + ", " + c.patientType + "]\n"
                + "M: " + mechanism(c) + " @" + dtg(c.timeOfInjury) + "\n"
                + "I: " + injuries(c) + "\n"
                + "S: " + signs(c) + "\n"
                + "T: " + treatments(c)
                + (c.allergies.isEmpty() ? "" : "\nAllergies: " + c.allergies);
    }

    public static String nineLine(NineLine n) {
        StringBuilder l3 = new StringBuilder(), l8 = new StringBuilder(), l4 = new StringBuilder(), l9 = new StringBuilder();
        for (Tccc.Precedence p : Tccc.Precedence.values())
            if (n.precedence[p.ordinal()] > 0) l3.append(n.precedence[p.ordinal()]).append(p.letter).append(' ');
        for (Tccc.Nationality x : Tccc.Nationality.values())
            if (n.nationality[x.ordinal()] > 0) l8.append(n.nationality[x.ordinal()]).append(x.letter).append(' ');
        if (n.hoist) l4.append("B hoist ");
        if (n.extraction) l4.append("C extraction ");
        if (n.ventilator) l4.append("D ventilator ");
        if (!n.equipmentOther.isEmpty()) l4.append(n.equipmentOther);
        if (l4.length() == 0) l4.append("A none");
        if (!n.nbc.isEmpty()) l9.append("NBC ").append(n.nbc).append(' ');
        if (n.terrainSlope) l9.append("slope ").append(n.terrainSlopeDir).append(' ');
        if (n.terrainRough) l9.append("rough ");
        if (n.terrainLoose) l9.append("loose ");
        if (!n.terrainOther.isEmpty()) l9.append(n.terrainOther);
        if (l9.length() == 0) l9.append("none");
        return "9-LINE " + n.title + "\n"
                + "1: " + (n.locationText.isEmpty() ? String.format(Locale.US, "%.5f %.5f", n.lat, n.lon) : n.locationText) + "\n"
                + "2: " + n.freq + " / " + n.callsign + "\n"
                + "3: " + l3.toString().trim() + "\n"
                + "4: " + l4.toString().trim() + "\n"
                + "5: " + n.litter + "L " + n.ambulatory + "A\n"
                + "6: " + n.security.letter + " " + n.security + "\n"
                + "7: " + n.marking.letter + " " + n.marking + (n.markingOther.isEmpty() ? "" : " " + n.markingOther) + "\n"
                + "8: " + l8.toString().trim() + "\n"
                + "9: " + l9.toString().trim()
                + (n.remarks.isEmpty() ? "" : "\nRemarks: " + n.remarks);
    }
}
