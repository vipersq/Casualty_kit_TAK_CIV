package io.github.vipersq.casualtykit.atak;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKUtilities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import io.github.vipersq.casualtykit.core.BodyMap;
import io.github.vipersq.casualtykit.core.Casualty;
import io.github.vipersq.casualtykit.core.CotCodec;
import io.github.vipersq.casualtykit.core.Injury;
import io.github.vipersq.casualtykit.core.NineLine;
import io.github.vipersq.casualtykit.core.Readout;
import io.github.vipersq.casualtykit.core.Tccc;
import io.github.vipersq.casualtykit.core.Treatment;
import io.github.vipersq.casualtykit.core.Vitals;

/** All screens of the pane, built in code: triage board, TCCC card editor and 9-line form. */
class Ui {
    interface Pick { void on(int index); }
    interface Form { void on(String[] values); }

    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    private static final int CAPS = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
    private static final int NUM = InputType.TYPE_CLASS_NUMBER;
    private static final long TICK_MS = 30_000L;
    private static final String[] INJURY_TYPES = { "GSW", "Blast / fragmentation", "Penetrating", "Burn", "Amputation",
            "Fracture", "Laceration", "Blunt trauma", "Other..." };
    private static final String[] TQ_SITES = { "R arm", "L arm", "R leg", "L leg", "Junctional", "Truncal" };

    private final Context ctx;
    private final FrameLayout root;
    private final Repo repo;
    private final float dp;
    private boolean listVisible;
    /** Updaters for the elapsed-time labels on the visible screen; run every {@link #TICK_MS}. */
    private final List<Runnable> clocks = new ArrayList<>();
    private boolean ticking;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (clocks.isEmpty() || !root.isAttachedToWindow()) { ticking = false; return; }
            for (Runnable r : clocks) r.run();
            root.postDelayed(this, TICK_MS);
        }
    };

    Ui(Context pluginContext, FrameLayout root, Repo repo) {
        this.ctx = pluginContext;
        this.root = root;
        this.repo = repo;
        this.dp = pluginContext.getResources().getDisplayMetrics().density;
        repo.onRemoteChange = () -> { if (listVisible) showList(); };
    }

    // ------------------------------------------------------------------ triage board

    void showList() {
        LinearLayout col = screen();
        listVisible = true;
        List<Casualty> all = repo.store.sorted();

        col.addView(header("CASUALTIES", all.size() + " recorded"));

        // Count per evacuation precedence, the way a triage board is read.
        int[] counts = new int[Tccc.Precedence.values().length];
        for (Casualty c : all) if (isOpen(c)) counts[c.precedence.ordinal()]++;
        LinearLayout board = new LinearLayout(ctx);
        board.setOrientation(LinearLayout.HORIZONTAL);
        for (Tccc.Precedence p : Tccc.Precedence.values()) {
            TextView t = chip(p.letter + "  " + counts[p.ordinal()], counts[p.ordinal()] > 0 ? p.argb : Style.GREY);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(15);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(40), 1);
            lp.setMargins(px(2), 0, px(2), 0);
            board.addView(t, lp);
        }
        col.addView(board, margins(0, 0, 0, 10));

        col.addView(row(
                primary("+ AT MY POSITION", () -> newCasualty(true)),
                secondary("+ AT MAP CENTRE", () -> newCasualty(false))));
        col.addView(secondary("9-LINE MEDEVAC REQUEST", this::showNineLine), margins(0, 6, 0, 10));

        if (all.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("No casualties recorded.", 15, true, Style.TEXT));
            empty.addView(text("Add the first one with the buttons above. It appears on the map and is shared with the team when you send it.", 13, false, Style.MUTED));
            col.addView(empty);
        }
        for (final Casualty c : all) col.addView(listRow(c));
        col.addView(text("Unvalidated software. Not a medical device.", 11, false, Style.MUTED));
        startClocks();
    }

    private static boolean isOpen(Casualty c) {
        return c.status == Tccc.Status.ACTIVE || c.status == Tccc.Status.EVAC_REQUESTED;
    }

    private View listRow(final Casualty c) {
        final int colour = c.status == Tccc.Status.DECEASED ? Style.GREY : isOpen(c) ? c.precedence.argb : Style.GREY;
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setBackground(Style.pressable(Style.CARD, Style.STROKE, px(10), px(1)));
        row.setOnClickListener(v -> showCard(copy(c), false));

        TextView badge = text(String.valueOf(c.precedence.letter), 22, true, Style.on(colour));
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Style.round(colour, 0, px(8), 0));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(px(46), ViewGroup.LayoutParams.MATCH_PARENT);
        blp.setMargins(px(8), px(8), px(4), px(8));
        row.addView(badge, blp);

        LinearLayout body = column();
        body.setPadding(px(6), px(8), px(8), px(8));
        body.addView(text(c.label() + (c.name.isEmpty() ? "" : "   " + c.name), 16, true, Style.TEXT));
        body.addView(text(c.precedence.name().replace('_', ' ') + "  ·  " + c.patientType
                + (c.status == Tccc.Status.ACTIVE ? "" : "  ·  " + c.status.name().replace('_', ' ')), 12, false, Style.MUTED));
        Vitals v = c.latestVitals();
        body.addView(text(v == null ? "No vitals recorded" : v.summary(), 13, false, v == null ? Style.MUTED : Style.TEXT));

        LinearLayout chips = new LinearLayout(ctx);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        final TextView age = chip("", Style.GREY);
        chips.addView(age, chipParams());
        final Treatment tq = c.earliestTourniquet();
        final TextView tqChip = tq == null ? null : chip("", Style.AMBER);
        if (tqChip != null) chips.addView(tqChip, chipParams());
        if (!c.injuries.isEmpty()) chips.addView(chip(c.injuries.size() + (c.injuries.size() == 1 ? " injury" : " injuries"), Style.BLUE), chipParams());
        body.addView(chips, margins(0, 4, 0, 0));
        Runnable clock = () -> {
            long now = System.currentTimeMillis();
            age.setText("Injured " + Readout.elapsed(c.timeOfInjury, now) + " ago");
            if (tqChip != null) tqChip.setText("TQ on " + Readout.elapsed(tq.time, now));
        };
        clock.run();
        clocks.add(clock);

        List<String> al = repo.alerts.check(v);
        if (!al.isEmpty() && isOpen(c)) body.addView(text("ALERT  " + join(al), 13, true, Style.RED));
        row.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        FrameLayout wrap = new FrameLayout(ctx);
        wrap.addView(row);
        wrap.setPadding(0, 0, 0, px(8));
        return wrap;
    }

    private void newCasualty(boolean atSelf) {
        Casualty c = new Casualty();
        double[] p = repo.here(atSelf);
        c.lat = p[0]; c.lon = p[1];
        c.timeOfInjury = System.currentTimeMillis();
        c.firstResponder = repo.callsign();
        c.rev = 0;
        showCard(c, true);
    }

    private static Casualty copy(Casualty c) {
        return CotCodec.casualtyFromCot(CotCodec.toCot(c, System.currentTimeMillis(), Repo.STALE_MS));
    }

    // ------------------------------------------------------------------ card

    void showCard(final Casualty c, final boolean isNew) {
        final LinearLayout col = screen();
        col.addView(secondary("‹  BACK  (discard changes)", this::showList), margins(0, 0, 0, 8));
        col.addView(header(isNew ? "NEW CASUALTY" : "TCCC CARD  " + c.label(), repo.mgrs(c.lat, c.lon)));

        // --- identity
        LinearLayout id = card("CASUALTY");
        final EditText roster = edit("Battle roster no. (e.g. JS1234)", c.rosterId, CAPS);
        final EditText name = edit("Name", c.name, TEXT);
        final EditText unit = edit("Unit", c.unit, TEXT);
        final EditText allergies = edit("Allergies", c.allergies, TEXT);
        final EditText blood = edit("Blood type", c.bloodType, CAPS);
        id.addView(roster); id.addView(row(name, unit)); id.addView(row(allergies, blood));
        col.addView(id);

        // --- evacuation
        LinearLayout ev = card("EVACUATION");
        ev.addView(row(
                picker("Precedence", names(Tccc.Precedence.values()), c.precedence.ordinal(), i -> c.precedence = Tccc.Precedence.values()[i]),
                picker("Type", names(Tccc.PatientType.values()), c.patientType.ordinal(), i -> c.patientType = Tccc.PatientType.values()[i])));
        ev.addView(row(
                picker("Nationality", names(Tccc.Nationality.values()), c.nationality.ordinal(), i -> c.nationality = Tccc.Nationality.values()[i]),
                picker("Status", names(Tccc.Status.values()), c.status.ordinal(), i -> c.status = Tccc.Status.values()[i])));
        col.addView(ev);

        // --- mechanism
        LinearLayout moi = card("MECHANISM OF INJURY");
        final TextView toi = text("", 14, true, Style.TEXT);
        final Runnable showToi = () -> toi.setText("Time of injury  " + Readout.dtg(c.timeOfInjury)
                + "   (" + Readout.elapsed(c.timeOfInjury, System.currentTimeMillis()) + " ago)");
        showToi.run();
        clocks.add(showToi);
        moi.addView(toi);
        moi.addView(row(
                secondary("-5 MIN", () -> { c.timeOfInjury -= 5 * 60000L; showToi.run(); }),
                secondary("NOW", () -> { c.timeOfInjury = System.currentTimeMillis(); showToi.run(); }),
                secondary("+5 MIN", () -> { c.timeOfInjury = Math.min(System.currentTimeMillis(), c.timeOfInjury + 5 * 60000L); showToi.run(); })));
        LinearLayout moiRow = null;
        for (final Tccc.Mechanism m : Tccc.Mechanism.values()) {
            if (moiRow == null || moiRow.getChildCount() == 3) { moiRow = new LinearLayout(ctx); moi.addView(moiRow); }
            final CheckBox cb = check(m.name(), c.mechanisms.contains(m));
            cb.setOnClickListener(v -> { if (cb.isChecked()) c.mechanisms.add(m); else c.mechanisms.remove(m); });
            moiRow.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        final EditText moiOther = edit("Other mechanism (detail)", c.mechanismOther, TEXT);
        moi.addView(moiOther);
        col.addView(moi);

        // --- injuries on the body diagram
        LinearLayout inj = card("INJURIES");
        inj.addView(text("Tap the body where the casualty is injured.", 12, false, Style.MUTED));
        final BodyMapView body = new BodyMapView(ctx);
        body.setInjuries(c.injuries);
        inj.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final LinearLayout injBox = column();
        inj.addView(injBox);
        final Runnable[] drawInj = new Runnable[1];
        drawInj[0] = () -> {
            injBox.removeAllViews();
            for (int n = 0; n < c.injuries.size(); n++) {
                final Injury i = c.injuries.get(n);
                injBox.addView(removable((n + 1) + ".  " + i.summary(), () -> { c.injuries.remove(i); drawInj[0].run(); }));
            }
            body.invalidate();
        };
        drawInj[0].run();
        body.setListener((view, region, nx, ny) -> choose(region.name, INJURY_TYPES, k -> {
            if (k < INJURY_TYPES.length - 1) {
                c.injuries.add(new Injury(region.name, INJURY_TYPES[k]).at(view, nx, ny));
                drawInj[0].run();
            } else {
                form("Injury: " + region.name, new String[] { "Type of injury" }, new int[] { TEXT }, v -> {
                    if (v[0].isEmpty()) return;
                    c.injuries.add(new Injury(region.name, v[0]).at(view, nx, ny));
                    drawInj[0].run();
                });
            }
        }));
        inj.addView(secondary("+ INJURY WITHOUT DIAGRAM", () -> form("Add injury",
                new String[] { "Location (e.g. L thigh)", "Type (e.g. GSW, burn, fracture)" }, new int[] { TEXT, TEXT }, v -> {
                    if (v[0].isEmpty() && v[1].isEmpty()) return;
                    c.injuries.add(new Injury(v[0], v[1])); drawInj[0].run();
                })));
        col.addView(inj);

        // --- vitals
        LinearLayout vit = card("SIGNS & SYMPTOMS");
        final LinearLayout vitBox = column();
        vit.addView(vitBox);
        final Runnable[] drawVit = new Runnable[1];
        drawVit[0] = () -> {
            vitBox.removeAllViews();
            if (c.vitals.isEmpty()) vitBox.addView(text("No vitals recorded.", 13, false, Style.MUTED));
            for (final Vitals v : c.vitals) {
                List<String> al = repo.alerts.check(v);
                View line = removable(Readout.dtg(v.time) + "   " + v.summary(), () -> { c.vitals.remove(v); drawVit[0].run(); });
                vitBox.addView(line);
                if (!al.isEmpty()) vitBox.addView(text("ALERT  " + join(al), 12, true, Style.RED));
            }
        };
        drawVit[0].run();
        vit.addView(primary("+ VITALS", () -> vitalsDialog(v -> {
            c.vitals.add(v); drawVit[0].run();
            List<String> al = repo.alerts.check(v);
            if (!al.isEmpty()) toast("ALERT: " + join(al));
        })));
        col.addView(vit);

        // --- treatments
        LinearLayout tx = card("TREATMENTS (MARCH)");
        final LinearLayout txBox = column();
        final Runnable[] drawTx = new Runnable[1];
        drawTx[0] = () -> {
            txBox.removeAllViews();
            if (c.treatments.isEmpty()) txBox.addView(text("No treatments recorded.", 13, false, Style.MUTED));
            long now = System.currentTimeMillis();
            for (final Treatment t : c.treatments) {
                String line = Readout.dtg(t.time) + "   " + t.group + "  " + t.summary();
                txBox.addView(removable(line, () -> { c.treatments.remove(t); drawTx[0].run(); }));
                if (t.isTourniquet()) txBox.addView(text("Tourniquet on for " + Readout.elapsed(t.time, now), 12, true, Style.AMBER));
            }
        };
        clocks.add(drawTx[0]);
        tx.addView(text("Quick add (time is set to now)", 12, false, Style.MUTED));
        tx.addView(row(
                quick("TOURNIQUET", () -> choose("Tourniquet site", TQ_SITES, k -> {
                    String code = k < 4 ? Treatment.TQ_EXTREMITY : k == 4 ? Treatment.TQ_JUNCTIONAL : Treatment.TQ_TRUNCAL;
                    addTx(c, Tccc.March.M, code, TQ_SITES[k], drawTx[0]);
                })),
                quick("PRESSURE DRESSING", () -> addTx(c, Tccc.March.M, Treatment.DRESSING_PRESSURE, "", drawTx[0])),
                quick("CHEST SEAL", () -> addTx(c, Tccc.March.R, Treatment.CHEST_SEAL, "", drawTx[0]))));
        tx.addView(row(
                quick("NPA", () -> addTx(c, Tccc.March.A, Treatment.NPA, "", drawTx[0])),
                quick("TXA", () -> addTx(c, Tccc.March.C, Treatment.TXA, "", drawTx[0])),
                quick("HYPOTHERMIA KIT", () -> addTx(c, Tccc.March.H, Treatment.HYPOTHERMIA_PREVENTION, "", drawTx[0]))));
        tx.addView(txBox, margins(0, 8, 0, 4));
        drawTx[0].run();
        tx.addView(secondary("+ OTHER TREATMENT / MEDICATION", () -> {
            final Tccc.March[] groups = Tccc.March.values();
            String[] labels = new String[groups.length];
            for (int i = 0; i < groups.length; i++) labels[i] = groups[i].name() + " - " + groups[i].label;
            choose("Treatment group", labels, g -> {
                final Tccc.March group = groups[g];
                final String[] codes = codesFor(group);
                choose(group.label, codes, k -> {
                    final boolean other = k == codes.length - 1;
                    form(other ? "Other treatment" : codes[k],
                            other ? new String[] { "Treatment", "Detail (dose, route, site)" } : new String[] { "Detail (dose, route, site)" },
                            other ? new int[] { CAPS, TEXT } : new int[] { TEXT }, v -> {
                                String code = other ? v[0] : codes[k];
                                if (code.isEmpty()) return;
                                addTx(c, group, code, other ? v[1] : v[0], drawTx[0]);
                            });
                });
            });
        }));
        col.addView(tx);

        // --- notes
        LinearLayout nt = card("NOTES");
        final EditText notes = edit("Notes", c.notes, TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notes.setMinLines(2);
        notes.setGravity(Gravity.TOP | Gravity.START);
        final EditText responder = edit("First responder", c.firstResponder, TEXT);
        nt.addView(notes); nt.addView(responder);
        col.addView(nt);

        final Runnable apply = () -> {
            c.rosterId = str(roster); c.name = str(name); c.unit = str(unit); c.allergies = str(allergies); c.bloodType = str(blood);
            c.mechanismOther = str(moiOther); c.notes = str(notes); c.firstResponder = str(responder);
        };
        col.addView(row(
                primary("SAVE + SEND", () -> {
                    apply.run();
                    toast(repo.save(c, true) ? "Saved and sent: " + c.label() : "Saved, but ATAK rejected the message");
                    showList();
                }),
                secondary("SAVE ONLY", () -> { apply.run(); repo.save(c, false); toast("Saved on this device: " + c.label()); showList(); })));
        col.addView(row(
                secondary("MIST REPORT", () -> { apply.run(); report("MIST", Readout.mist(c)); }),
                secondary("SHOW ON MAP", () -> {
                    MapItem mi = MapView.getMapView().getRootGroup().deepFindUID(c.uid);
                    if (mi != null) ATAKUtilities.scaleToFit(mi); else toast("Save the card first");
                })), margins(0, 6, 0, 0));
        if (!isNew) {
            col.addView(danger("DELETE CARD FROM THIS DEVICE", () -> new AlertDialog.Builder(dialogContext())
                    .setTitle("Delete " + c.label() + "?")
                    .setMessage("The card and its marker are removed from this device only. Other devices keep their copy, and the card comes back if one of them sends it again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d, w) -> { repo.delete(c.uid); toast("Deleted: " + c.label()); showList(); })
                    .show()), margins(0, 18, 0, 0));
        }
        startClocks();
    }

    private void addTx(Casualty c, Tccc.March group, String code, String detail, Runnable redraw) {
        c.treatments.add(new Treatment(System.currentTimeMillis(), group, code, detail));
        redraw.run();
    }

    private static String[] codesFor(Tccc.March g) {
        switch (g) {
            case M: return new String[] { Treatment.TQ_EXTREMITY, Treatment.TQ_JUNCTIONAL, Treatment.TQ_TRUNCAL, Treatment.DRESSING_HEMOSTATIC, Treatment.DRESSING_PRESSURE, "Other..." };
            case A: return new String[] { Treatment.NPA, Treatment.CRIC, Treatment.ET_TUBE, Treatment.SGA, "Other..." };
            case R: return new String[] { Treatment.O2, Treatment.NEEDLE_D, Treatment.CHEST_TUBE, Treatment.CHEST_SEAL, "Other..." };
            case C: return new String[] { Treatment.FLUID, Treatment.BLOOD, Treatment.TXA, "Other..." };
            case H: return new String[] { Treatment.HYPOTHERMIA_PREVENTION, "Other..." };
            case MEDS: return new String[] { Treatment.ANALGESIC, Treatment.ANTIBIOTIC, Treatment.COMBAT_PILL_PACK, "Other..." };
            default: return new String[] { Treatment.SPLINT, Treatment.EYE_SHIELD, "Other..." };
        }
    }

    interface VitalsCb { void on(Vitals v); }

    /** One dialog for a full set of vitals; AVPU is a button that steps through the four levels. */
    private void vitalsDialog(final VitalsCb cb) {
        Context dc = dialogContext();
        LinearLayout l = new LinearLayout(dc);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(px(16), px(8), px(16), px(8));
        final String[] hints = { "Pulse", "Resp rate", "BP systolic", "BP diastolic", "SpO2 %", "Pain 0-10" };
        final EditText[] f = new EditText[hints.length];
        for (int i = 0; i < hints.length; i += 2) {
            LinearLayout r = new LinearLayout(dc);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < i + 2; j++) {
                f[j] = new EditText(dc);
                f[j].setHint(hints[j]); f[j].setInputType(NUM);
                r.addView(f[j], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            }
            l.addView(r);
        }
        final Tccc.Avpu[] avpu = { null };
        final Button avpuBtn = new Button(dc);
        avpuBtn.setText("AVPU: not assessed (tap to set)");
        avpuBtn.setOnClickListener(v -> {
            Tccc.Avpu[] all = Tccc.Avpu.values();
            avpu[0] = avpu[0] == null ? all[0] : avpu[0].ordinal() == all.length - 1 ? null : all[avpu[0].ordinal() + 1];
            avpuBtn.setText(avpu[0] == null ? "AVPU: not assessed (tap to set)" : "AVPU: " + avpu[0]);
        });
        l.addView(avpuBtn);
        ScrollView sv = new ScrollView(dc);
        sv.addView(l);
        new AlertDialog.Builder(dc).setTitle("Vital signs (leave blank if not taken)").setView(sv)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    Vitals v = new Vitals(System.currentTimeMillis());
                    v.pulse = num(str(f[0]), 0, 300); v.respRate = num(str(f[1]), 0, 99);
                    v.systolic = num(str(f[2]), 0, 300); v.diastolic = num(str(f[3]), 0, 250);
                    v.spo2 = num(str(f[4]), 0, 100); v.pain = num(str(f[5]), 0, 10);
                    v.avpu = avpu[0];
                    v.source = "manual";
                    if (v.summary().isEmpty()) { toast("No valid values entered"); return; }
                    cb.on(v);
                }).show();
    }

    // ------------------------------------------------------------------ 9-line

    void showNineLine() {
        final LinearLayout col = screen();
        col.addView(secondary("‹  BACK", this::showList), margins(0, 0, 0, 8));
        col.addView(header("9-LINE MEDEVAC", "Lines 3, 5 and 8 come from the selected cards"));

        final List<Casualty> candidates = new ArrayList<>();
        for (Casualty c : repo.store.sorted()) if (isOpen(c)) candidates.add(c);
        final List<CheckBox> boxes = new ArrayList<>();
        LinearLayout who = card("CASUALTIES TO EVACUATE");
        if (candidates.isEmpty()) who.addView(text("No active casualties. Add a casualty card first.", 14, false, Style.MUTED));
        for (Casualty c : candidates) {
            CheckBox cb = check(c.label() + "   " + c.precedence.letter + " " + c.precedence.name().replace('_', ' ') + "   " + c.patientType,
                    c.status == Tccc.Status.ACTIVE);
            boxes.add(cb); who.addView(cb);
        }
        col.addView(who);

        final boolean[] atSelf = { true };
        LinearLayout l12 = card("LINE 1-2: LOCATION, FREQUENCY, CALLSIGN");
        l12.addView(picker("Pickup site", new String[] { "MY POSITION", "MAP CENTRE" }, 0, i -> atSelf[0] = i == 0));
        SimpleDateFormat f = new SimpleDateFormat("HHmm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        final EditText titleEt = edit("Request title", "MED." + f.format(new Date()), CAPS);
        final EditText freq = edit("Frequency", "", TEXT);
        final EditText callsign = edit("Callsign", repo.callsign(), CAPS);
        l12.addView(titleEt); l12.addView(row(freq, callsign));
        col.addView(l12);

        LinearLayout l4 = card("LINE 4: SPECIAL EQUIPMENT");
        final CheckBox hoist = check("Hoist", false), extraction = check("Extraction", false), ventilator = check("Ventilator", false);
        l4.addView(row(hoist, extraction, ventilator));
        final EditText equipOther = edit("Other equipment", "", TEXT);
        l4.addView(equipOther);
        col.addView(l4);

        LinearLayout l67 = card("LINE 6-7: SECURITY, MARKING");
        final int[] sec = { 0 }, mark = { Tccc.Marking.NONE.ordinal() };
        l67.addView(picker("Security", names(Tccc.Security.values()), sec[0], i -> sec[0] = i));
        l67.addView(picker("Marking", names(Tccc.Marking.values()), mark[0], i -> mark[0] = i));
        final EditText markOther = edit("Marking detail (e.g. smoke colour)", "", TEXT);
        l67.addView(markOther);
        col.addView(l67);

        LinearLayout l9 = card("LINE 9: NBC / TERRAIN");
        final EditText nbc = edit("NBC contamination (N, B, C or blank)", "", CAPS);
        final CheckBox slope = check("Slope", false), rough = check("Rough", false), loose = check("Loose", false);
        l9.addView(nbc); l9.addView(row(slope, rough, loose));
        final EditText terrainOther = edit("Other terrain / obstacles", "", TEXT);
        final EditText remarks = edit("Remarks", "", TEXT);
        l9.addView(terrainOther); l9.addView(remarks);
        col.addView(l9);

        col.addView(primary("REVIEW AND SEND", () -> {
            final List<Casualty> chosen = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isChecked()) chosen.add(candidates.get(i));
            if (chosen.isEmpty()) { toast("Select at least one casualty"); return; }
            double[] p = repo.here(atSelf[0]);
            final NineLine n = NineLine.fromCasualties(str(titleEt), chosen, p[0], p[1]);
            n.locationText = repo.mgrs(n.lat, n.lon);
            n.freq = str(freq); n.callsign = str(callsign);
            n.hoist = hoist.isChecked(); n.extraction = extraction.isChecked(); n.ventilator = ventilator.isChecked();
            n.equipmentOther = str(equipOther);
            n.security = Tccc.Security.values()[sec[0]]; n.marking = Tccc.Marking.values()[mark[0]]; n.markingOther = str(markOther);
            n.nbc = str(nbc); n.terrainSlope = slope.isChecked(); n.terrainRough = rough.isChecked(); n.terrainLoose = loose.isChecked();
            n.terrainOther = str(terrainOther); n.remarks = str(remarks);
            new AlertDialog.Builder(dialogContext())
                    .setTitle("Send this 9-line to the team?")
                    .setMessage(Readout.nineLine(n))
                    .setNegativeButton("Edit", null)
                    .setPositiveButton("SEND", (d, w) -> {
                        if (!repo.send(n)) { toast("ATAK rejected the 9-line; nothing was sent"); return; }
                        for (Casualty c : chosen) {
                            Casualty e = copy(c);
                            e.status = Tccc.Status.EVAC_REQUESTED;
                            repo.save(e, true);
                        }
                        toast("9-line sent: " + n.title);
                        showList();
                    })
                    .show();
        }));
    }

    // ------------------------------------------------------------------ widgets

    private Context dialogContext() { return MapView.getMapView().getContext(); }

    private LinearLayout screen() {
        listVisible = false;
        clocks.clear();
        root.removeAllViews();
        ScrollView sv = new ScrollView(ctx);
        sv.setBackgroundColor(Style.BG);
        sv.setFillViewport(true);
        LinearLayout col = column();
        col.setPadding(px(10), px(10), px(10), px(28));
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return col;
    }

    private void startClocks() {
        if (ticking || clocks.isEmpty()) return;
        ticking = true;
        root.postDelayed(tick, TICK_MS);
    }

    private LinearLayout column() { LinearLayout l = new LinearLayout(ctx); l.setOrientation(LinearLayout.VERTICAL); return l; }

    private LinearLayout row(View... views) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setBaselineAligned(false);
        for (int i = 0; i < views.length; i++) {
            // Buttons in a row share one height even when a label wraps onto two lines.
            int h = views[i] instanceof Button && !(views[i] instanceof CheckBox) ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h, 1);
            lp.setMargins(i == 0 ? 0 : px(3), px(3), i == views.length - 1 ? 0 : px(3), px(3));
            l.addView(views[i], lp);
        }
        return l;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(px(l), px(t), px(r), px(b));
        return lp;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, px(6), 0);
        return lp;
    }

    private TextView text(String s, int sp, boolean bold, int colour) {
        TextView t = new TextView(ctx);
        t.setText(s); t.setTextSize(sp); t.setTextColor(colour);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(px(2), px(2), px(2), px(2));
        return t;
    }

    /** Screen title with a smaller line under it. */
    private View header(String title, String sub) {
        LinearLayout l = column();
        l.setPadding(px(2), 0, 0, px(8));
        l.addView(text(title, 20, true, Style.TEXT));
        if (sub != null && !sub.isEmpty()) l.addView(text(sub, 12, false, Style.MUTED));
        return l;
    }

    private LinearLayout card() {
        LinearLayout l = column();
        l.setBackground(Style.round(Style.CARD, Style.STROKE, px(10), px(1)));
        l.setPadding(px(10), px(10), px(10), px(10));
        l.setLayoutParams(margins(0, 0, 0, 10));
        return l;
    }

    private LinearLayout card(String title) {
        LinearLayout l = card();
        TextView t = text(title, 12, true, Style.GREEN);
        t.setLetterSpacing(0.08f);
        t.setPadding(px(2), 0, 0, px(6));
        l.addView(t);
        return l;
    }

    /** Small rounded label, e.g. "TQ on 23 min". */
    private TextView chip(String s, int colour) {
        TextView t = text(s, 11, true, Style.on(colour));
        t.setBackground(Style.round(colour, 0, px(6), 0));
        t.setPadding(px(7), px(3), px(7), px(3));
        return t;
    }

    private EditText edit(String hint, String value, int inputType) {
        EditText e = new EditText(ctx);
        e.setHint(hint); e.setText(value == null ? "" : value); e.setInputType(inputType);
        e.setTextColor(Style.TEXT); e.setHintTextColor(Style.MUTED); e.setTextSize(15);
        e.setBackground(Style.round(Style.FIELD, Style.STROKE, px(8), px(1)));
        e.setPadding(px(10), px(9), px(10), px(9));
        LinearLayout.LayoutParams lp = margins(0, 3, 0, 3);
        e.setLayoutParams(lp);
        return e;
    }

    private Button button(String label, int fill, int stroke, int textColour, final Runnable r) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(textColour);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(Style.pressable(fill, stroke, px(8), stroke == 0 ? 0 : px(1)));
        b.setMinHeight(px(46));
        b.setMinimumHeight(px(46));
        b.setPadding(px(8), px(6), px(8), px(6));
        b.setOnClickListener(v -> r.run());
        return b;
    }
    private Button primary(String label, Runnable r) { return button(label, Style.GREEN, 0, Style.on(Style.GREEN), r); }
    private Button secondary(String label, Runnable r) { return button(label, Style.CARD, Style.GREY, Style.TEXT, r); }
    private Button danger(String label, Runnable r) { return button(label, Style.BG, Style.RED, Style.RED, r); }
    private Button quick(String label, Runnable r) {
        Button b = button(label, Style.FIELD, Style.BLUE, Style.BLUE, r);
        b.setTextSize(12);
        return b;
    }

    private CheckBox check(String label, boolean on) {
        CheckBox c = new CheckBox(ctx);
        c.setText(label); c.setChecked(on); c.setTextColor(Style.TEXT); c.setTextSize(13);
        return c;
    }

    private View removable(String s, Runnable onRemove) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.addView(text(s, 14, false, Style.TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button x = button("X", Style.CARD, Style.GREY, Style.MUTED, onRemove);
        x.setMinHeight(px(38)); x.setMinimumHeight(px(38)); x.setMinWidth(0); x.setMinimumWidth(0);
        l.addView(x, new LinearLayout.LayoutParams(px(44), px(38)));
        return l;
    }

    /** A button showing "label: value" that opens a single-choice list. */
    private Button picker(final String label, final String[] items, int current, final Pick cb) {
        final Button b = secondary(label + ": " + items[current], () -> { });
        b.setOnClickListener(v -> choose(label, items, i -> { b.setText(label + ": " + items[i]); cb.on(i); }));
        return b;
    }

    private void choose(String title, String[] items, final Pick cb) {
        new AlertDialog.Builder(dialogContext()).setTitle(title)
                .setItems(items, (d, which) -> cb.on(which))
                .setNegativeButton("Cancel", null).show();
    }

    private void form(String title, String[] hints, int[] types, final Form cb) {
        Context dc = dialogContext();
        LinearLayout l = new LinearLayout(dc);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(px(16), px(8), px(16), px(8));
        final EditText[] fields = new EditText[hints.length];
        for (int i = 0; i < hints.length; i++) {
            fields[i] = new EditText(dc);
            fields[i].setHint(hints[i]); fields[i].setInputType(types[i]);
            l.addView(fields[i]);
        }
        ScrollView sv = new ScrollView(dc);
        sv.addView(l);
        new AlertDialog.Builder(dc).setTitle(title).setView(sv)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    String[] out = new String[fields.length];
                    for (int i = 0; i < fields.length; i++) out[i] = str(fields[i]);
                    cb.on(out);
                }).show();
    }

    private void report(String title, final String body) {
        new AlertDialog.Builder(dialogContext()).setTitle(title).setMessage(body)
                .setNegativeButton("Close", null)
                .setPositiveButton("Copy", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) dialogContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("MIST", body));
                    toast("Copied");
                }).show();
    }

    private void toast(String s) { Toast.makeText(dialogContext(), s, Toast.LENGTH_LONG).show(); }

    private int px(int dps) { return Math.round(dps * dp); }
    private static String str(EditText e) { return e.getText().toString().trim(); }
    private static Integer num(String s, int min, int max) {
        try { int v = Integer.parseInt(s.trim()); return v < min || v > max ? null : v; } catch (Exception e) { return null; }
    }
    private static String[] names(Enum<?>[] vals) {
        String[] out = new String[vals.length];
        for (int i = 0; i < vals.length; i++) out[i] = vals[i].name().replace('_', ' ');
        return out;
    }
    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) { if (sb.length() > 0) sb.append(", "); sb.append(s); }
        return sb.toString();
    }
}
