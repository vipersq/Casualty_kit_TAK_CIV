package io.github.vipersq.casualtykit.core;

import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Cursor-on-Target encoding. See docs/COT-SPEC.md.
 *
 * A casualty is a spot marker (b-m-p-s-m) so every TAK client shows it, with the full card in a
 * {@code __casualtykit} detail that only this plugin reads. A 9-line is the stock CASEVAC type
 * (b-r-f-h-c) with the standard {@code _medevac_} detail, so stock ATAK/WinTAK open it in their own
 * 9-line tool, plus a small {@code __casualtykit9} detail carrying what the stock schema cannot.
 */
public final class CotCodec {
    private CotCodec() {}

    public static final String TYPE_CASUALTY = "b-m-p-s-m";
    public static final String TYPE_NINE_LINE = "b-r-f-h-c";
    public static final String DETAIL_CASUALTY = "__casualtykit";
    public static final String DETAIL_NINE_LINE = "__casualtykit9";
    public static final int SCHEMA = 1;

    // ---------------------------------------------------------------- casualty

    public static String toCot(Casualty c, long now, long staleMs) {
        try {
            Document d = newDoc();
            Element detail = event(d, c.uid, TYPE_CASUALTY, now, staleMs, c.lat, c.lon, c.hae);
            child(d, detail, "contact").setAttribute("callsign", c.label());
            child(d, detail, "color").setAttribute("argb", String.valueOf(c.precedence.argb));
            child(d, detail, "remarks").setTextContent(Readout.mist(c));

            Element k = child(d, detail, DETAIL_CASUALTY);
            k.setAttribute("v", String.valueOf(SCHEMA));
            k.setAttribute("rev", String.valueOf(c.rev));
            k.setAttribute("updated", String.valueOf(c.updated));
            k.setAttribute("editor", c.editor);
            k.setAttribute("status", c.status.name());
            k.setAttribute("precedence", c.precedence.name());
            k.setAttribute("type", c.patientType.name());
            k.setAttribute("firstResponder", c.firstResponder);

            Element p = child(d, k, "patient");
            p.setAttribute("roster", c.rosterId); p.setAttribute("name", c.name); p.setAttribute("unit", c.unit);
            p.setAttribute("nationality", c.nationality.name());
            p.setAttribute("allergies", c.allergies); p.setAttribute("blood", c.bloodType);

            Element inj = child(d, k, "injury");
            inj.setAttribute("time", String.valueOf(c.timeOfInjury));
            inj.setAttribute("moiOther", c.mechanismOther);
            StringBuilder moi = new StringBuilder();
            for (Tccc.Mechanism m : c.mechanisms) { if (moi.length() > 0) moi.append(','); moi.append(m.name()); }
            inj.setAttribute("moi", moi.toString());
            for (Injury i : c.injuries) {
                Element w = child(d, inj, "wound"); w.setAttribute("loc", i.location); w.setAttribute("kind", i.type);
            }
            for (Vitals v : c.vitals) {
                Element e = child(d, k, "vitals");
                e.setAttribute("time", String.valueOf(v.time));
                opt(e, "pulse", v.pulse); opt(e, "sys", v.systolic); opt(e, "dia", v.diastolic);
                opt(e, "rr", v.respRate); opt(e, "spo2", v.spo2); opt(e, "pain", v.pain);
                if (v.avpu != null) e.setAttribute("avpu", v.avpu.name());
                if (v.source != null) e.setAttribute("src", v.source);
            }
            for (Treatment t : c.treatments) {
                Element e = child(d, k, "tx");
                e.setAttribute("time", String.valueOf(t.time)); e.setAttribute("group", t.group.name());
                e.setAttribute("code", t.code); e.setAttribute("detail", t.detail);
            }
            if (!c.notes.isEmpty()) child(d, k, "notes").setTextContent(c.notes);
            return write(d);
        } catch (Exception e) {
            throw new IllegalStateException("CoT encode failed", e);
        }
    }

    /** Returns null when the event is not one of our casualty records. */
    public static Casualty casualtyFromCot(String xml) {
        try {
            Element ev = parse(xml);
            Element k = first(ev, DETAIL_CASUALTY);
            if (k == null) return null;
            Casualty c = new Casualty();
            c.uid = ev.getAttribute("uid");
            Element pt = first(ev, "point");
            c.lat = dbl(pt, "lat", 0); c.lon = dbl(pt, "lon", 0); c.hae = dbl(pt, "hae", Double.NaN);
            if (c.hae >= 9999999) c.hae = Double.NaN;
            c.rev = (int) lng(k, "rev", 1); c.updated = lng(k, "updated", 0); c.editor = k.getAttribute("editor");
            c.status = en(Tccc.Status.class, k.getAttribute("status"), Tccc.Status.ACTIVE);
            c.precedence = en(Tccc.Precedence.class, k.getAttribute("precedence"), Tccc.Precedence.ROUTINE);
            c.patientType = en(Tccc.PatientType.class, k.getAttribute("type"), Tccc.PatientType.AMBULATORY);
            c.firstResponder = k.getAttribute("firstResponder");
            Element p = first(k, "patient");
            if (p != null) {
                c.rosterId = p.getAttribute("roster"); c.name = p.getAttribute("name"); c.unit = p.getAttribute("unit");
                c.nationality = en(Tccc.Nationality.class, p.getAttribute("nationality"), Tccc.Nationality.US_MILITARY);
                c.allergies = p.getAttribute("allergies"); c.bloodType = p.getAttribute("blood");
            }
            Element inj = first(k, "injury");
            if (inj != null) {
                c.timeOfInjury = lng(inj, "time", 0); c.mechanismOther = inj.getAttribute("moiOther");
                for (String s : inj.getAttribute("moi").split(",")) {
                    Tccc.Mechanism m = en(Tccc.Mechanism.class, s, null);
                    if (m != null) c.mechanisms.add(m);
                }
                NodeList ws = inj.getElementsByTagName("wound");
                for (int i = 0; i < ws.getLength(); i++) {
                    Element w = (Element) ws.item(i);
                    c.injuries.add(new Injury(w.getAttribute("loc"), w.getAttribute("kind")));
                }
            }
            NodeList vs = k.getElementsByTagName("vitals");
            for (int i = 0; i < vs.getLength(); i++) {
                Element e = (Element) vs.item(i);
                Vitals v = new Vitals(lng(e, "time", 0));
                v.pulse = integer(e, "pulse"); v.systolic = integer(e, "sys"); v.diastolic = integer(e, "dia");
                v.respRate = integer(e, "rr"); v.spo2 = integer(e, "spo2"); v.pain = integer(e, "pain");
                v.avpu = en(Tccc.Avpu.class, e.getAttribute("avpu"), null);
                v.source = e.hasAttribute("src") ? e.getAttribute("src") : null;
                c.vitals.add(v);
            }
            NodeList ts = k.getElementsByTagName("tx");
            for (int i = 0; i < ts.getLength(); i++) {
                Element e = (Element) ts.item(i);
                c.treatments.add(new Treatment(lng(e, "time", 0), en(Tccc.March.class, e.getAttribute("group"), Tccc.March.OTHER),
                        e.getAttribute("code"), e.getAttribute("detail")));
            }
            Element notes = first(k, "notes");
            if (notes != null) c.notes = notes.getTextContent();
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 9-line

    public static String toCot(NineLine n, long now, long staleMs) {
        try {
            Document d = newDoc();
            Element detail = event(d, n.uid, TYPE_NINE_LINE, now, staleMs, n.lat, n.lon, Double.NaN);
            child(d, detail, "contact").setAttribute("callsign", n.title);
            child(d, detail, "remarks").setTextContent(Readout.nineLine(n));

            // Standard detail, attribute names as written by the stock ATAK CASEVAC tool.
            Element m = child(d, detail, "_medevac_");
            m.setAttribute("casevac", "false");
            m.setAttribute("title", n.title);
            m.setAttribute("freq", n.freq);
            if (!n.remarks.isEmpty()) m.setAttribute("medline_remarks", n.remarks);
            int[] pr = n.precedence;
            // The stock schema has three precedence buckets; the exact A-E split is in our own detail.
            count(m, "urgent", pr[Tccc.Precedence.URGENT.ordinal()] + pr[Tccc.Precedence.URGENT_SURGICAL.ordinal()]);
            count(m, "priority", pr[Tccc.Precedence.PRIORITY.ordinal()]);
            count(m, "routine", pr[Tccc.Precedence.ROUTINE.ordinal()] + pr[Tccc.Precedence.CONVENIENCE.ordinal()]);
            boolean other = !n.equipmentOther.isEmpty();
            if (!n.hoist && !n.extraction && !n.ventilator && !other) m.setAttribute("equipment_none", "true");
            if (n.hoist) m.setAttribute("hoist", "true");
            if (n.extraction) m.setAttribute("extraction_equipment", "true");
            if (n.ventilator) m.setAttribute("ventilator", "true");
            if (other) { m.setAttribute("equipment_other", "true"); m.setAttribute("equipment_detail", n.equipmentOther); }
            count(m, "litter", n.litter);
            count(m, "ambulatory", n.ambulatory);
            m.setAttribute("security", String.valueOf(n.security.ordinal()));
            m.setAttribute("hlz_marking", String.valueOf(n.marking.ordinal()));
            if (!n.markingOther.isEmpty()) m.setAttribute("hlz_other", n.markingOther);
            String[] natAttr = { "us_military", "us_civilian", "nonus_military", "nonus_civilian", "epw", "child" };
            for (int i = 0; i < natAttr.length; i++) count(m, natAttr[i], n.nationality[i]);
            boolean anyTerrain = n.terrainSlope || n.terrainRough || n.terrainLoose || !n.terrainOther.isEmpty();
            if (!anyTerrain) m.setAttribute("terrain_none", "true");
            if (n.terrainSlope) { m.setAttribute("terrain_slope", "true"); m.setAttribute("terrain_slope_dir", n.terrainSlopeDir); }
            if (n.terrainRough) m.setAttribute("terrain_rough", "true");
            if (n.terrainLoose) m.setAttribute("terrain_loose", "true");
            if (!n.terrainOther.isEmpty()) { m.setAttribute("terrain_other", "true"); m.setAttribute("terrain_other_detail", n.terrainOther); }
            if (!n.zmist.isEmpty()) {
                Element map = child(d, m, "zMistsMap");
                int i = 1;
                for (String[] z : n.zmist) {
                    Element e = child(d, map, "zMist");
                    e.setAttribute("title", "ZMIST" + i++);
                    e.setAttribute("z", z[0]); e.setAttribute("m", z[1]); e.setAttribute("i", z[2]);
                    e.setAttribute("s", z[3]); e.setAttribute("t", z[4]);
                }
            }

            Element k = child(d, detail, DETAIL_NINE_LINE);
            k.setAttribute("v", String.valueOf(SCHEMA));
            k.setAttribute("rev", String.valueOf(n.rev));
            k.setAttribute("updated", String.valueOf(n.updated));
            k.setAttribute("callsign", n.callsign);
            k.setAttribute("grid", n.locationText);
            k.setAttribute("nbc", n.nbc);
            StringBuilder sb = new StringBuilder();
            for (int v : pr) { if (sb.length() > 0) sb.append(','); sb.append(v); }
            k.setAttribute("precedence", sb.toString());
            for (String uid : n.casualtyUids) child(d, k, "casualty").setAttribute("uid", uid);
            return write(d);
        } catch (Exception e) {
            throw new IllegalStateException("CoT encode failed", e);
        }
    }

    /** Reads our own 9-line and also a plain one written by the stock CASEVAC tool. Null if not a 9-line. */
    public static NineLine nineLineFromCot(String xml) {
        try {
            Element ev = parse(xml);
            Element m = first(ev, "_medevac_");
            if (m == null) return null;
            NineLine n = new NineLine();
            n.uid = ev.getAttribute("uid");
            Element pt = first(ev, "point");
            n.lat = dbl(pt, "lat", 0); n.lon = dbl(pt, "lon", 0);
            n.title = m.getAttribute("title"); n.freq = m.getAttribute("freq"); n.remarks = m.getAttribute("medline_remarks");
            n.precedence[Tccc.Precedence.URGENT.ordinal()] = (int) lng(m, "urgent", 0);
            n.precedence[Tccc.Precedence.PRIORITY.ordinal()] = (int) lng(m, "priority", 0);
            n.precedence[Tccc.Precedence.ROUTINE.ordinal()] = (int) lng(m, "routine", 0);
            n.hoist = bool(m, "hoist"); n.extraction = bool(m, "extraction_equipment"); n.ventilator = bool(m, "ventilator");
            n.equipmentOther = m.getAttribute("equipment_detail");
            n.litter = (int) lng(m, "litter", 0); n.ambulatory = (int) lng(m, "ambulatory", 0);
            n.security = idx(Tccc.Security.values(), m.getAttribute("security"), Tccc.Security.NO_ENEMY);
            n.marking = idx(Tccc.Marking.values(), m.getAttribute("hlz_marking"), Tccc.Marking.NONE);
            n.markingOther = m.getAttribute("hlz_other");
            String[] natAttr = { "us_military", "us_civilian", "nonus_military", "nonus_civilian", "epw", "child" };
            for (int i = 0; i < natAttr.length; i++) n.nationality[i] = (int) lng(m, natAttr[i], 0);
            n.terrainSlope = bool(m, "terrain_slope"); n.terrainRough = bool(m, "terrain_rough"); n.terrainLoose = bool(m, "terrain_loose");
            if (m.hasAttribute("terrain_slope_dir")) n.terrainSlopeDir = m.getAttribute("terrain_slope_dir");
            n.terrainOther = m.getAttribute("terrain_other_detail");
            NodeList zs = m.getElementsByTagName("zMist");
            for (int i = 0; i < zs.getLength(); i++) {
                Element z = (Element) zs.item(i);
                n.zmist.add(new String[] { z.getAttribute("z"), z.getAttribute("m"), z.getAttribute("i"), z.getAttribute("s"), z.getAttribute("t") });
            }
            Element k = first(ev, DETAIL_NINE_LINE);
            if (k != null) {
                n.rev = (int) lng(k, "rev", 1); n.updated = lng(k, "updated", 0);
                n.callsign = k.getAttribute("callsign"); n.locationText = k.getAttribute("grid"); n.nbc = k.getAttribute("nbc");
                String[] parts = k.getAttribute("precedence").split(",");
                if (parts.length == n.precedence.length)
                    for (int i = 0; i < parts.length; i++) n.precedence[i] = Integer.parseInt(parts[i].trim());
                NodeList cs = k.getElementsByTagName("casualty");
                for (int i = 0; i < cs.getLength(); i++) n.casualtyUids.add(((Element) cs.item(i)).getAttribute("uid"));
            } else {
                Element contact = first(ev, "contact");
                if (contact != null) n.callsign = contact.getAttribute("callsign");
            }
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- helpers

    static String iso(long t) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(t));
    }
    private static Document newDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }
    private static Element event(Document d, String uid, String type, long now, long staleMs, double lat, double lon, double hae) {
        Element ev = d.createElement("event");
        d.appendChild(ev);
        ev.setAttribute("version", "2.0"); ev.setAttribute("uid", uid); ev.setAttribute("type", type);
        ev.setAttribute("how", "h-g-i-g-o");
        ev.setAttribute("time", iso(now)); ev.setAttribute("start", iso(now)); ev.setAttribute("stale", iso(now + staleMs));
        Element pt = child(d, ev, "point");
        pt.setAttribute("lat", String.format(Locale.US, "%.7f", lat)); pt.setAttribute("lon", String.format(Locale.US, "%.7f", lon));
        pt.setAttribute("hae", Double.isNaN(hae) ? "9999999.0" : String.format(Locale.US, "%.1f", hae));
        pt.setAttribute("ce", "9999999.0"); pt.setAttribute("le", "9999999.0");
        return child(d, ev, "detail");
    }
    private static Element child(Document d, Element parent, String name) {
        Element e = d.createElement(name); parent.appendChild(e); return e;
    }
    private static void opt(Element e, String name, Integer v) { if (v != null) e.setAttribute(name, v.toString()); }
    private static void count(Element e, String name, int v) { if (v > 0) e.setAttribute(name, String.valueOf(v)); }
    private static String write(Document d) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(d), new StreamResult(w));
        return w.toString();
    }
    private static Element parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // Events arrive from the network: refuse DTDs and external entities.
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) { }
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) { }
        try { f.setExpandEntityReferences(false); } catch (Exception ignored) { }
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }
    private static Element first(Element parent, String name) {
        if (parent == null) return null;
        NodeList l = parent.getElementsByTagName(name);
        for (int i = 0; i < l.getLength(); i++) { Node n = l.item(i); if (n instanceof Element) return (Element) n; }
        return null;
    }
    private static long lng(Element e, String a, long def) {
        try { return e == null || !e.hasAttribute(a) ? def : Long.parseLong(e.getAttribute(a).trim()); } catch (NumberFormatException x) { return def; }
    }
    private static Integer integer(Element e, String a) {
        try { return e.hasAttribute(a) ? Integer.valueOf(e.getAttribute(a).trim()) : null; } catch (NumberFormatException x) { return null; }
    }
    private static double dbl(Element e, String a, double def) {
        try { return e == null || !e.hasAttribute(a) ? def : Double.parseDouble(e.getAttribute(a).trim()); } catch (NumberFormatException x) { return def; }
    }
    private static boolean bool(Element e, String a) { return "true".equals(e.getAttribute(a)); }
    private static <T extends Enum<T>> T en(Class<T> c, String s, T def) {
        try { return s == null || s.isEmpty() ? def : Enum.valueOf(c, s); } catch (IllegalArgumentException x) { return def; }
    }
    private static <T> T idx(T[] vals, String s, T def) {
        try { int i = Integer.parseInt(s.trim()); return i >= 0 && i < vals.length ? vals[i] : def; } catch (Exception x) { return def; }
    }
}
