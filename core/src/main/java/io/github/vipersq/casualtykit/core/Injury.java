package io.github.vipersq.casualtykit.core;

/** A wound marked on the DD1380 body diagram. */
public class Injury {
    /** Body region, e.g. "L thigh", "R chest anterior". */
    public String location = "";
    /** e.g. "GSW", "amputation", "burn 9%", "fracture". */
    public String type = "";
    public Injury() {}
    public Injury(String location, String type) { this.location = location; this.type = type; }
    public String summary() { return (type + " " + location).trim(); }
}
