package io.github.vipersq.casualtykit.core;

/** One row of the DD1380 "signs & symptoms" table. A null field means "not taken". */
public class Vitals {
    public long time;
    public Integer pulse, systolic, diastolic, respRate, spo2, pain;
    public Tccc.Avpu avpu;
    /** e.g. "manual", "garmin", "polar-h10" - where the reading came from. */
    public String source;

    public Vitals() {}
    public Vitals(long time) { this.time = time; }
    public Vitals pulse(int v) { pulse = v; return this; }
    public Vitals bp(int sys, int dia) { systolic = sys; diastolic = dia; return this; }
    public Vitals rr(int v) { respRate = v; return this; }
    public Vitals spo2(int v) { spo2 = v; return this; }
    public Vitals avpu(Tccc.Avpu v) { avpu = v; return this; }
    public Vitals pain(int v) { pain = v; return this; }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (pulse != null) sb.append("HR ").append(pulse).append(' ');
        if (systolic != null) sb.append("BP ").append(systolic).append('/').append(diastolic == null ? "-" : diastolic).append(' ');
        if (respRate != null) sb.append("RR ").append(respRate).append(' ');
        if (spo2 != null) sb.append("SpO2 ").append(spo2).append("% ");
        if (avpu != null) sb.append("AVPU ").append(avpu.name().charAt(0)).append(' ');
        if (pain != null) sb.append("Pain ").append(pain).append(' ');
        return sb.toString().trim();
    }
}
