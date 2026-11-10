package io.github.vipersq.casualtykit.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Flags vital signs outside configurable limits. The defaults are placeholders for testing and
 * MUST be reviewed by the unit's medical lead before operational use.
 */
public class VitalsAlerts {
    public int pulseLow = 50, pulseHigh = 120, systolicLow = 90, respLow = 10, respHigh = 30, spo2Low = 90;

    public List<String> check(Vitals v) {
        List<String> out = new ArrayList<>();
        if (v == null) return out;
        if (v.pulse != null && v.pulse < pulseLow) out.add("HR low " + v.pulse);
        if (v.pulse != null && v.pulse > pulseHigh) out.add("HR high " + v.pulse);
        if (v.systolic != null && v.systolic < systolicLow) out.add("SBP low " + v.systolic);
        if (v.respRate != null && v.respRate < respLow) out.add("RR low " + v.respRate);
        if (v.respRate != null && v.respRate > respHigh) out.add("RR high " + v.respRate);
        if (v.spo2 != null && v.spo2 < spo2Low) out.add("SpO2 low " + v.spo2);
        if (v.avpu == Tccc.Avpu.PAIN || v.avpu == Tccc.Avpu.UNRESPONSIVE) out.add("AVPU " + v.avpu.name().charAt(0));
        return out;
    }

    /** Alerts that are present now but were not present for the previous reading (avoids repeat alarms). */
    public List<String> newAlerts(Vitals previous, Vitals current) {
        List<String> now = check(current), before = new ArrayList<>();
        for (String s : check(previous)) before.add(s.replaceAll(" \\d+$", ""));
        List<String> out = new ArrayList<>();
        for (String s : now) if (!before.contains(s.replaceAll(" \\d+$", ""))) out.add(s);
        return out;
    }
}
