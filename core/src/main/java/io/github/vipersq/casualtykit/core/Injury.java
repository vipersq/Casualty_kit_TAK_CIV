package io.github.vipersq.casualtykit.core;

/** A wound marked on the DD1380 body diagram. */
public class Injury {
    /** Body region, e.g. "L thigh", "R chest anterior". */
    public String location = "";
    /** e.g. "GSW", "amputation", "burn 9%", "fracture". */
    public String type = "";
    /** Position on the body diagram, normalised 0..1 within the view; negative when not placed on the diagram. */
    public float x = -1f, y = -1f;
    /** {@link BodyMap#FRONT} or {@link BodyMap#BACK}; empty when not placed on the diagram. */
    public String view = "";

    public Injury() {}
    public Injury(String location, String type) { this.location = location; this.type = type; }
    public Injury at(String view, float x, float y) { this.view = view; this.x = x; this.y = y; return this; }
    public boolean onDiagram() { return x >= 0f && y >= 0f && !view.isEmpty(); }
    public String summary() { return (type + " " + location).trim(); }
}
