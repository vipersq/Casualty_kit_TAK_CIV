package io.github.vipersq.casualtykit.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A 9-line MEDEVAC/CASEVAC request, normally generated from the selected casualty cards. */
public class NineLine {
    public String uid = "CASKIT-9L-" + UUID.randomUUID();
    public int rev = 1;
    public long updated;
    public String title = "MED.1";

    /** Line 1: pickup site. locationText is the grid as shown to the user (MGRS), supplied by the host app. */
    public double lat, lon;
    public String locationText = "";
    /** Line 2. */
    public String freq = "", callsign = "";
    /** Line 3: count per {@link Tccc.Precedence}, indexed by ordinal. */
    public int[] precedence = new int[Tccc.Precedence.values().length];
    /** Line 4. */
    public boolean hoist, extraction, ventilator;
    public String equipmentOther = "";
    /** Line 5. */
    public int litter, ambulatory;
    /** Line 6 and 7. */
    public Tccc.Security security = Tccc.Security.NO_ENEMY;
    public Tccc.Marking marking = Tccc.Marking.NONE;
    public String markingOther = "";
    /** Line 8: count per {@link Tccc.Nationality}, indexed by ordinal. */
    public int[] nationality = new int[Tccc.Nationality.values().length];
    /** Line 9: NBC contamination (wartime) or terrain (peacetime). */
    public String nbc = "";
    public boolean terrainSlope, terrainRough, terrainLoose;
    public String terrainSlopeDir = "N", terrainOther = "";
    public String remarks = "";

    /** Casualty cards this request covers; their MIST reports travel with the request. */
    public List<String> casualtyUids = new ArrayList<>();
    public List<String[]> zmist = new ArrayList<>();

    /** Fill lines 3, 5 and 8 and the MIST list from the cards. Deceased and already evacuated casualties are skipped. */
    public static NineLine fromCasualties(String title, List<Casualty> cards, double lat, double lon) {
        NineLine n = new NineLine();
        n.title = title; n.lat = lat; n.lon = lon;
        for (Casualty c : cards) {
            if (c.status == Tccc.Status.DECEASED || c.status == Tccc.Status.EVACUATED
                    || c.status == Tccc.Status.RETURNED_TO_DUTY) continue;
            n.precedence[c.precedence.ordinal()]++;
            n.nationality[c.nationality.ordinal()]++;
            if (c.patientType == Tccc.PatientType.LITTER) n.litter++; else n.ambulatory++;
            n.casualtyUids.add(c.uid);
            n.zmist.add(new String[] { c.label(), Readout.mechanism(c), Readout.injuries(c), Readout.signs(c), Readout.treatments(c) });
        }
        return n;
    }

    public int total() { return litter + ambulatory; }
}
