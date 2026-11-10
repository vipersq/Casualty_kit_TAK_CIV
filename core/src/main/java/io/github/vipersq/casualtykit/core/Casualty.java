package io.github.vipersq.casualtykit.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A casualty record: the content of one DD Form 1380 plus position and sync metadata. */
public class Casualty {
    public String uid = "CASKIT-" + UUID.randomUUID();
    /** Revision counter, incremented on every local edit; used to resolve sync conflicts. */
    public int rev = 1;
    public long updated;
    /** Callsign / UID of the device that made the last edit. */
    public String editor = "";

    public double lat, lon;
    public double hae = Double.NaN;

    /** Battle roster number, e.g. "SK1234" (initials + last 4). Used as the map label. */
    public String rosterId = "";
    public String name = "", unit = "", allergies = "", bloodType = "";
    public Tccc.Nationality nationality = Tccc.Nationality.US_MILITARY;
    public Tccc.Precedence precedence = Tccc.Precedence.ROUTINE;
    public Tccc.PatientType patientType = Tccc.PatientType.AMBULATORY;
    public Tccc.Status status = Tccc.Status.ACTIVE;

    public long timeOfInjury;
    public Set<Tccc.Mechanism> mechanisms = EnumSet.noneOf(Tccc.Mechanism.class);
    public String mechanismOther = "";
    public List<Injury> injuries = new ArrayList<>();
    public List<Vitals> vitals = new ArrayList<>();
    public List<Treatment> treatments = new ArrayList<>();
    public String notes = "";
    public String firstResponder = "";

    public String label() { return rosterId.isEmpty() ? "CAS-" + uid.substring(uid.length() - 4).toUpperCase() : rosterId; }

    public Vitals latestVitals() {
        Vitals best = null;
        for (Vitals v : vitals) if (best == null || v.time >= best.time) best = v;
        return best;
    }

    /** The tourniquet that has been on longest, or null. Drives the "time on" display. */
    public Treatment earliestTourniquet() {
        Treatment best = null;
        for (Treatment t : treatments) if (t.isTourniquet() && (best == null || t.time < best.time)) best = t;
        return best;
    }

    /** Call after any local edit so other devices accept the change. */
    public void touch(long now, String editorCallsign) { rev++; updated = now; editor = editorCallsign == null ? "" : editorCallsign; }

    /** True when {@code other} should replace this record: higher revision wins, then the later edit, then editor name. */
    public boolean isSupersededBy(Casualty other) {
        if (other.rev != rev) return other.rev > rev;
        if (other.updated != updated) return other.updated > updated;
        return other.editor.compareTo(editor) > 0;
    }
}
