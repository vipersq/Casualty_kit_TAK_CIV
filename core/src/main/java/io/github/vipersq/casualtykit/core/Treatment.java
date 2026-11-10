package io.github.vipersq.casualtykit.core;

/** A treatment given, grouped by MARCH. code is a short fixed token, detail is free text. */
public class Treatment {
    /** Suggested codes per group; free text codes are also accepted. */
    public static final String TQ_EXTREMITY = "TQ-EXTREMITY", TQ_JUNCTIONAL = "TQ-JUNCTIONAL", TQ_TRUNCAL = "TQ-TRUNCAL",
            DRESSING_HEMOSTATIC = "DRESSING-HEMOSTATIC", DRESSING_PRESSURE = "DRESSING-PRESSURE",
            NPA = "NPA", CRIC = "CRIC", ET_TUBE = "ET-TUBE", SGA = "SGA",
            O2 = "O2", NEEDLE_D = "NEEDLE-D", CHEST_TUBE = "CHEST-TUBE", CHEST_SEAL = "CHEST-SEAL",
            FLUID = "FLUID", BLOOD = "BLOOD-PRODUCT", TXA = "TXA",
            HYPOTHERMIA_PREVENTION = "HYPOTHERMIA-PREVENTION", EYE_SHIELD = "EYE-SHIELD", SPLINT = "SPLINT",
            ANALGESIC = "ANALGESIC", ANTIBIOTIC = "ANTIBIOTIC", COMBAT_PILL_PACK = "COMBAT-PILL-PACK";

    public long time;
    public Tccc.March group = Tccc.March.OTHER;
    public String code = "";
    /** Name / dose / route / location, e.g. "R leg, high and tight" or "Ketamine 50 mg IM". */
    public String detail = "";

    public Treatment() {}
    public Treatment(long time, Tccc.March group, String code, String detail) {
        this.time = time; this.group = group; this.code = code; this.detail = detail == null ? "" : detail;
    }
    public boolean isTourniquet() { return code.startsWith("TQ-"); }
    public String summary() { return detail.isEmpty() ? code : code + " (" + detail + ")"; }
}
