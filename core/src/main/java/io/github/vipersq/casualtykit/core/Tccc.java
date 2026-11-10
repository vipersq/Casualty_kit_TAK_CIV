package io.github.vipersq.casualtykit.core;

/** Enumerations taken from the DD Form 1380 (TCCC card) and the 9-line MEDEVAC request. */
public final class Tccc {
    private Tccc() {}

    /** 9-line, line 3. Letter is the brevity code used on the radio. */
    public enum Precedence {
        URGENT('A', 0xFFFF0000), URGENT_SURGICAL('B', 0xFFFF0000), PRIORITY('C', 0xFFFFBF00),
        ROUTINE('D', 0xFF00B050), CONVENIENCE('E', 0xFF00B050);
        public final char letter; public final int argb;
        Precedence(char l, int c) { letter = l; argb = c; }
    }
    /** 9-line, line 5. */
    public enum PatientType { LITTER, AMBULATORY }
    /** 9-line, line 8. */
    public enum Nationality {
        US_MILITARY('A'), US_CIVILIAN('B'), NONUS_MILITARY('C'), NONUS_CIVILIAN('D'), EPW('E'), CHILD('F');
        public final char letter;
        Nationality(char l) { letter = l; }
    }
    /** DD1380 mechanism of injury. */
    public enum Mechanism { ARTILLERY, BLUNT, BURN, FALL, GRENADE, GSW, IED, LANDMINE, MVC, RPG, OTHER }
    public enum Avpu { ALERT, VERBAL, PAIN, UNRESPONSIVE }
    public enum Status { ACTIVE, EVAC_REQUESTED, EVACUATED, RETURNED_TO_DUTY, DECEASED }
    /** MARCH grouping used for treatments on the card. */
    public enum March {
        M("Massive haemorrhage"), A("Airway"), R("Respiration"), C("Circulation"),
        H("Hypothermia / head injury"), MEDS("Medication"), OTHER("Other");
        public final String label;
        March(String l) { label = l; }
    }
    /** 9-line, line 6 (index matches the stock ATAK CASEVAC tool). */
    public enum Security { NO_ENEMY('N'), POSSIBLE_ENEMY('P'), ENEMY_CAUTION('E'), ENEMY_ESCORT('X');
        public final char letter; Security(char l) { letter = l; } }
    /** 9-line, line 7 (index matches the stock ATAK CASEVAC tool). */
    public enum Marking { PANELS('A'), PYRO('B'), SMOKE('C'), NONE('D'), OTHER('E');
        public final char letter; Marking(char l) { letter = l; } }
}
