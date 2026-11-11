package io.github.vipersq.casualtykit.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class CoreTest {
    static final long T0 = 1794340800000L; // 2026-11-10T20:00:00Z

    static Casualty sample() {
        Casualty c = new Casualty();
        c.lat = 51.0688; c.lon = -1.7945; c.hae = 62.4;
        c.rosterId = "JS1234"; c.name = "J. \"Smudge\" Smith <A&B>"; c.unit = "1 PLT"; c.allergies = "Penicillin"; c.bloodType = "O+";
        c.nationality = Tccc.Nationality.NONUS_MILITARY;
        c.precedence = Tccc.Precedence.URGENT_SURGICAL; c.patientType = Tccc.PatientType.LITTER;
        c.timeOfInjury = T0;
        c.mechanisms.add(Tccc.Mechanism.IED); c.mechanisms.add(Tccc.Mechanism.OTHER); c.mechanismOther = "secondary frag";
        c.injuries.add(new Injury("L thigh", "amputation"));
        c.injuries.add(new Injury("R forearm", "frag"));
        c.vitals.add(new Vitals(T0 + 120_000).pulse(128).bp(88, 54).rr(26).spo2(93).avpu(Tccc.Avpu.VERBAL).pain(8));
        c.vitals.add(new Vitals(T0 + 600_000).pulse(112).rr(22));
        c.treatments.add(new Treatment(T0 + 60_000, Tccc.March.M, Treatment.TQ_EXTREMITY, "L leg high & tight"));
        c.treatments.add(new Treatment(T0 + 300_000, Tccc.March.MEDS, Treatment.ANALGESIC, "Ketamine 50 mg IM"));
        c.notes = "Line 1\nLine 2 żółć";
        c.firstResponder = "MEDIC-2";
        c.touch(T0 + 600_000, "M6OER");
        return c;
    }

    @Test void casualtyRoundTrip() {
        Casualty a = sample();
        String xml = CotCodec.toCot(a, T0 + 700_000, 3_600_000);
        assertTrue(xml.contains("type=\"b-m-p-s-m\""));
        assertTrue(xml.contains("stale=\"2026-11-10T21:11:40.000Z\""), xml);
        Casualty b = CotCodec.casualtyFromCot(xml);
        assertNotNull(b);
        assertEquals(a.uid, b.uid); assertEquals(2, b.rev); assertEquals("M6OER", b.editor);
        assertEquals(a.lat, b.lat, 1e-6); assertEquals(a.lon, b.lon, 1e-6); assertEquals(62.4, b.hae, 0.05);
        assertEquals(a.name, b.name); assertEquals(a.notes, b.notes); assertEquals("Penicillin", b.allergies);
        assertEquals(Tccc.Precedence.URGENT_SURGICAL, b.precedence); assertEquals(Tccc.PatientType.LITTER, b.patientType);
        assertEquals(a.mechanisms, b.mechanisms); assertEquals("secondary frag", b.mechanismOther);
        assertEquals(2, b.injuries.size()); assertEquals("amputation L thigh", b.injuries.get(0).summary());
        assertEquals(2, b.vitals.size());
        assertEquals(Integer.valueOf(88), b.vitals.get(0).systolic); assertNull(b.vitals.get(1).spo2);
        assertEquals(Tccc.Avpu.VERBAL, b.vitals.get(0).avpu);
        assertEquals(2, b.treatments.size()); assertTrue(b.treatments.get(0).isTourniquet());
        assertEquals("Ketamine 50 mg IM", b.treatments.get(1).detail);
        // encoding the decoded record gives the same wire form
        assertEquals(xml, CotCodec.toCot(b, T0 + 700_000, 3_600_000));
    }

    @Test void foreignAndHostileInput() {
        assertNull(CotCodec.casualtyFromCot("<event uid='x' type='a-f-G'><point lat='1' lon='2'/><detail/></event>"));
        assertNull(CotCodec.casualtyFromCot("not xml"));
        String xxe = "<!DOCTYPE e [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><event uid='x'><detail><__casualtykit><notes>&x;</notes></__casualtykit></detail></event>";
        assertNull(CotCodec.casualtyFromCot(xxe));
        Casualty c = CotCodec.casualtyFromCot("<event uid='x'><point lat='bad' lon='2'/><detail><__casualtykit rev='z' precedence='NOPE'><vitals pulse='abc'/></__casualtykit></detail></event>");
        assertNotNull(c); assertEquals(1, c.rev); assertEquals(Tccc.Precedence.ROUTINE, c.precedence); assertNull(c.vitals.get(0).pulse);
    }

    @Test void nineLineFromCardsAndRoundTrip() {
        Casualty a = sample();
        Casualty b = new Casualty(); b.precedence = Tccc.Precedence.PRIORITY; b.rosterId = "AB0001";
        Casualty dead = new Casualty(); dead.status = Tccc.Status.DECEASED;
        NineLine n = NineLine.fromCasualties("MED.1", Arrays.asList(a, b, dead), 51.07, -1.79);
        n.freq = "45.500"; n.callsign = "HAWK 21"; n.locationText = "30U WB 14412 59321";
        n.security = Tccc.Security.ENEMY_CAUTION; n.marking = Tccc.Marking.SMOKE; n.hoist = true; n.nbc = "N";
        assertEquals(2, n.total()); assertEquals(1, n.litter); assertEquals(1, n.ambulatory);
        assertEquals(1, n.precedence[Tccc.Precedence.URGENT_SURGICAL.ordinal()]);
        assertEquals(1, n.nationality[Tccc.Nationality.NONUS_MILITARY.ordinal()]);

        String text = Readout.nineLine(n);
        assertTrue(text.contains("1: 30U WB 14412 59321")); assertTrue(text.contains("3: 1B 1C"), text);
        assertTrue(text.contains("5: 1L 1A")); assertTrue(text.contains("6: E ")); assertTrue(text.contains("7: C SMOKE"));
        assertTrue(text.contains("8: 1A 1C"), text); assertTrue(text.contains("9: NBC N"));

        String xml = CotCodec.toCot(n, T0, 3_600_000);
        // what a stock ATAK/WinTAK client reads
        assertTrue(xml.contains("type=\"b-r-f-h-c\"")); assertTrue(xml.contains("urgent=\"1\"")); assertTrue(xml.contains("priority=\"1\""));
        assertTrue(xml.contains("security=\"2\"")); assertTrue(xml.contains("hlz_marking=\"2\"")); assertTrue(xml.contains("hoist=\"true\""));
        assertTrue(xml.contains("nonus_military=\"1\"")); assertTrue(xml.contains("title=\"ZMIST1\"")); assertFalse(xml.contains("routine="));

        NineLine m = CotCodec.nineLineFromCot(xml);
        assertEquals(text, Readout.nineLine(m));
        assertEquals(n.casualtyUids, m.casualtyUids); assertEquals("JS1234", m.zmist.get(0)[0]);
    }

    @Test void readsStockCasevac() {
        String stock = "<event version='2.0' uid='abc' type='b-r-f-h-c' time='2026-11-10T20:00:00Z' start='2026-11-10T20:00:00Z' stale='2026-11-10T21:00:00Z' how='h-g-i-g-o'>"
                + "<point lat='51.1' lon='-1.8' hae='9999999.0' ce='9999999.0' le='9999999.0'/><detail><contact callsign='MED.3'/>"
                + "<_medevac_ casevac='false' title='MED.3' freq='30.0' urgent='2' litter='1' ambulatory='1' security='3' hlz_marking='0' us_military='2' terrain_slope='true' terrain_slope_dir='E'>"
                + "<zMistsMap><zMist title='ZMIST1' z='Z1' m='GSW' i='chest' s='HR 120' t='seal'/></zMistsMap></_medevac_></detail></event>";
        NineLine n = CotCodec.nineLineFromCot(stock);
        assertNotNull(n);
        assertEquals(2, n.precedence[0]); assertEquals(Tccc.Security.ENEMY_ESCORT, n.security); assertEquals(Tccc.Marking.PANELS, n.marking);
        assertTrue(n.terrainSlope); assertEquals("E", n.terrainSlopeDir); assertEquals("MED.3", n.callsign); assertEquals("GSW", n.zmist.get(0)[1]);
    }

    @Test void mergeIsLastWriterWinsAndOrderIndependent() {
        Casualty base = sample();
        Casualty x = CotCodec.casualtyFromCot(CotCodec.toCot(base, T0, 1000)); x.notes = "X"; x.touch(T0 + 1000, "ALPHA");
        Casualty y = CotCodec.casualtyFromCot(CotCodec.toCot(base, T0, 1000)); y.notes = "Y"; y.touch(T0 + 2000, "BRAVO");
        CasualtyStore s1 = new CasualtyStore(), s2 = new CasualtyStore();
        s1.putLocal(base); s2.putLocal(base);
        assertTrue(s1.mergeRemote(x)); assertTrue(s1.mergeRemote(y));
        assertTrue(s2.mergeRemote(y)); assertFalse(s2.mergeRemote(x));
        assertEquals("Y", s1.get(base.uid).notes); assertEquals("Y", s2.get(base.uid).notes);
        assertFalse(s1.mergeRemote(base), "stale revision must not overwrite");
        assertFalse(s1.mergeRemote(y), "duplicate must be ignored");
    }

    @Test void sortingAndAlerts() {
        Casualty r = new Casualty(); r.precedence = Tccc.Precedence.ROUTINE; r.timeOfInjury = 1;
        Casualty u1 = new Casualty(); u1.precedence = Tccc.Precedence.URGENT; u1.timeOfInjury = 50;
        Casualty u2 = new Casualty(); u2.precedence = Tccc.Precedence.URGENT; u2.timeOfInjury = 10;
        CasualtyStore s = new CasualtyStore(); s.putLocal(r); s.putLocal(u1); s.putLocal(u2);
        List<Casualty> o = s.sorted();
        assertSame(u2, o.get(0)); assertSame(u1, o.get(1)); assertSame(r, o.get(2));

        VitalsAlerts al = new VitalsAlerts();
        Vitals ok = new Vitals(1).pulse(80).bp(120, 80).rr(16).spo2(98).avpu(Tccc.Avpu.ALERT);
        Vitals bad = new Vitals(2).pulse(130).bp(85, 50).rr(16).spo2(98);
        Vitals worse = new Vitals(3).pulse(140).bp(80, 40).spo2(85);
        assertTrue(al.check(ok).isEmpty()); assertTrue(al.check(null).isEmpty());
        assertEquals(Arrays.asList("HR high 130", "SBP low 85"), al.newAlerts(ok, bad));
        assertEquals(Arrays.asList("SpO2 low 85"), al.newAlerts(bad, worse));
    }

    @Test void mistText() {
        String m = Readout.mist(sample());
        assertTrue(m.startsWith("MIST JS1234 [B URGENT_SURGICAL, LITTER]"), m);
        assertTrue(m.contains("M: IED, secondary frag @102000ZNOV26"), m);
        assertTrue(m.contains("S: HR 112 RR 22 @102010ZNOV26"), m);
        assertTrue(m.contains("T: TQ-EXTREMITY (L leg high & tight); ANALGESIC (Ketamine 50 mg IM)"));
    }

    @Test void bodyMapRegions() {
        assertEquals("Head", BodyMap.regionAt(BodyMap.FRONT, 50, 14).name);
        assertEquals("Neck", BodyMap.regionAt(BodyMap.FRONT, 50, 29).name);
        assertEquals("Chest", BodyMap.regionAt(BodyMap.FRONT, 50, 50).name);
        assertEquals("Upper back", BodyMap.regionAt(BodyMap.BACK, 50, 50).name);
        assertEquals("Abdomen", BodyMap.regionAt(BodyMap.FRONT, 50, 85).name);
        assertEquals("Buttocks", BodyMap.regionAt(BodyMap.BACK, 50, 105).name);
        // Front view: casualty's right is on the viewer's left. Back view is mirrored.
        assertEquals("R thigh", BodyMap.regionAt(BodyMap.FRONT, 40, 135).name);
        assertEquals("L thigh", BodyMap.regionAt(BodyMap.FRONT, 60, 135).name);
        assertEquals("L thigh (back)", BodyMap.regionAt(BodyMap.BACK, 40, 135).name);
        assertEquals("R forearm (back)", BodyMap.regionAt(BodyMap.BACK, 76, 92).name);
        assertEquals("R upper arm", BodyMap.regionAt(BodyMap.FRONT, 28, 58).name);
        assertEquals("R hand", BodyMap.regionAt(BodyMap.FRONT, 20, 115).name);
        assertEquals("L lower leg", BodyMap.regionAt(BodyMap.FRONT, 59, 185).name);
        assertEquals("L foot", BodyMap.regionAt(BodyMap.FRONT, 60, 211).name);
        // well off the body
        assertNull(BodyMap.regionAt(BodyMap.FRONT, 5, 5));
        assertNull(BodyMap.regionAt(BodyMap.FRONT, 50, 200), "gap between the lower legs");
        assertNull(BodyMap.regionAt(BodyMap.FRONT, 92, 60));
        // a near miss snaps to the closest part, so small parts are easy to hit
        BodyMap.Region hand = BodyMap.regionAt(BodyMap.FRONT, 20, 115);
        assertFalse(hand.contains(hand.x1 + 1.5f, 115));
        assertSame(hand, BodyMap.regionAt(BodyMap.FRONT, hand.x1 + 1.5f, 115));
        for (String v : new String[] { BodyMap.FRONT, BodyMap.BACK }) {
            List<BodyMap.Region> rs = BodyMap.regions(v);
            assertEquals(17, rs.size());
            java.util.Set<String> names = new java.util.HashSet<>();
            for (BodyMap.Region r : rs) {
                assertTrue(names.add(r.name), "duplicate " + r.name);
                assertTrue(r.x0 >= 0 && r.x1 <= BodyMap.WIDTH && r.y0 >= 0 && r.y1 <= BodyMap.HEIGHT, r.name);
                float[] c = r.centre();
                assertSame(r, BodyMap.regionAt(v, c[0], c[1]), r.name + " must own its own centre");
                // regions must not overlap: no other region contains this centre
                for (BodyMap.Region o : rs) if (o != r) assertFalse(o.contains(c[0], c[1]), o.name + " overlaps " + r.name);
            }
        }
        // the figure is symmetric: mirrored points land on the opposite side's part
        for (float[] pt : new float[][] { { 72, 58 }, { 76, 92 }, { 80, 115 }, { 59, 140 }, { 59, 185 }, { 60, 211 } }) {
            String a = BodyMap.regionAt(BodyMap.FRONT, pt[0], pt[1]).name, b = BodyMap.regionAt(BodyMap.FRONT, 100 - pt[0], pt[1]).name;
            assertEquals(a.replaceFirst("^L ", "R "), b);
        }
    }

    @Test void injuryDiagramPositionRoundTrip() {
        Casualty a = sample();
        a.injuries.add(new Injury("R thigh", "GSW").at(BodyMap.FRONT, 0.4f, 0.591f));
        Casualty b = CotCodec.casualtyFromCot(CotCodec.toCot(a, T0, 1000));
        assertFalse(b.injuries.get(0).onDiagram(), "old-style injury keeps working without a position");
        Injury i = b.injuries.get(2);
        assertTrue(i.onDiagram()); assertEquals(BodyMap.FRONT, i.view);
        assertEquals(0.4f, i.x, 0.0006f); assertEquals(0.591f, i.y, 0.0006f);
        Casualty bad = CotCodec.casualtyFromCot("<event uid='x'><point lat='1' lon='2'/><detail><__casualtykit><injury><wound loc='a' kind='b' view='SIDE' x='0.5' y='0.5'/><wound loc='c' kind='d' view='FRONT' x='7' y='0.5'/></injury></__casualtykit></detail></event>");
        assertFalse(bad.injuries.get(0).onDiagram()); assertFalse(bad.injuries.get(1).onDiagram());
    }

    @Test void tourniquetAndElapsed() {
        Casualty c = sample();
        assertEquals(T0 + 60_000, c.earliestTourniquet().time);
        c.treatments.add(new Treatment(T0 + 10_000, Tccc.March.M, Treatment.TQ_JUNCTIONAL, ""));
        assertEquals(T0 + 10_000, c.earliestTourniquet().time);
        assertNull(new Casualty().earliestTourniquet());
        assertEquals("0 min", Readout.elapsed(T0, T0 + 59_000));
        assertEquals("45 min", Readout.elapsed(T0, T0 + 45 * 60_000));
        assertEquals("2 h 05 min", Readout.elapsed(T0, T0 + 125 * 60_000));
        assertEquals("0 min", Readout.elapsed(T0, T0 - 5000));
        CasualtyStore s = new CasualtyStore(); s.putLocal(c);
        assertTrue(s.remove(c.uid)); assertFalse(s.remove(c.uid)); assertEquals(0, s.size());
    }

    @Test void kiaAndDeceased() {
        Casualty mil = sample(); mil.status = Tccc.Status.KIA;
        Casualty civ = new Casualty(); civ.status = Tccc.Status.DECEASED; civ.nationality = Tccc.Nationality.NONUS_CIVILIAN;
        Casualty live = new Casualty();
        assertTrue(Tccc.Status.KIA.isDead()); assertTrue(Tccc.Status.DECEASED.isDead()); assertFalse(Tccc.Status.EVACUATED.isDead());
        assertFalse(Tccc.Status.KIA.isOpen()); assertTrue(Tccc.Status.EVAC_REQUESTED.isOpen()); assertFalse(Tccc.Status.RETURNED_TO_DUTY.isOpen());
        // the dead are never counted in an evacuation request
        NineLine n = NineLine.fromCasualties("MED.1", Arrays.asList(mil, civ, live), 0, 0);
        assertEquals(1, n.total()); assertEquals(Arrays.asList(live.uid), n.casualtyUids);
        // status survives the wire, and the marker colour is dark instead of the precedence colour
        String xml = CotCodec.toCot(mil, T0, 1000);
        assertTrue(xml.contains("status=\"KIA\"")); assertTrue(xml.contains("argb=\"" + Tccc.DEAD_ARGB + "\""), xml);
        assertEquals(Tccc.Status.KIA, CotCodec.casualtyFromCot(xml).status);
        assertEquals(Tccc.Status.DECEASED, CotCodec.casualtyFromCot(CotCodec.toCot(civ, T0, 1000)).status);
        assertTrue(Readout.mist(mil).startsWith("MIST JS1234 [KIA]"), Readout.mist(mil));
        // on the board the dead sink below everyone else, whatever their precedence was
        Casualty gone = new Casualty(); gone.status = Tccc.Status.EVACUATED; gone.precedence = Tccc.Precedence.URGENT;
        mil.precedence = Tccc.Precedence.URGENT; live.precedence = Tccc.Precedence.ROUTINE;
        CasualtyStore st = new CasualtyStore(); st.putLocal(mil); st.putLocal(gone); st.putLocal(live);
        assertEquals(Arrays.asList(live.uid, gone.uid, mil.uid), Arrays.asList(st.sorted().get(0).uid, st.sorted().get(1).uid, st.sorted().get(2).uid));
        // a status this version does not know falls back to ACTIVE instead of failing
        assertEquals(Tccc.Status.ACTIVE, CotCodec.casualtyFromCot(xml.replace("status=\"KIA\"", "status=\"FUTURE\"")).status);
    }
}
