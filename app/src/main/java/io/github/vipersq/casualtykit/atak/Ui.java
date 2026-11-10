package io.github.vipersq.casualtykit.atak;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
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
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import io.github.vipersq.casualtykit.core.Casualty;
import io.github.vipersq.casualtykit.core.CotCodec;
import io.github.vipersq.casualtykit.core.Injury;
import io.github.vipersq.casualtykit.core.NineLine;
import io.github.vipersq.casualtykit.core.Readout;
import io.github.vipersq.casualtykit.core.Tccc;
import io.github.vipersq.casualtykit.core.Treatment;
import io.github.vipersq.casualtykit.core.Vitals;

/** All screens of the pane, built in code: casualty list, TCCC card editor and 9-line form. */
class Ui {
    interface Pick { void on(int index); }
    interface Form { void on(String[] values); }

    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    private static final int CAPS = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
    private static final int NUM = InputType.TYPE_CLASS_NUMBER;

    private final Context ctx;
    private final FrameLayout root;
    private final Repo repo;
    private final float dp;
    private boolean listVisible;

    Ui(Context pluginContext, FrameLayout root, Repo repo) {
        this.ctx = pluginContext;
        this.root = root;
        this.repo = repo;
        this.dp = pluginContext.getResources().getDisplayMetrics().density;
        repo.onRemoteChange = new Runnable() {
            @Override public void run() { if (listVisible) showList(); }
        };
    }

    // ------------------------------------------------------------------ list

    void showList() {
        LinearLayout col = screen();
        listVisible = true;
        List<Casualty> all = repo.store.sorted();
        col.addView(title("CASUALTIES (" + all.size() + ")"));
        col.addView(row(
                btn("+ NEW AT MY POSITION", new Runnable() { @Override public void run() { newCasualty(true); } }),
                btn("+ NEW AT MAP CENTRE", new Runnable() { @Override public void run() { newCasualty(false); } })));
        col.addView(btn("9-LINE MEDEVAC REQUEST", new Runnable() { @Override public void run() { showNineLine(); } }));
        if (all.isEmpty()) col.addView(text("No casualties recorded.", 14, false));
        for (final Casualty c : all) {
            Vitals v = c.latestVitals();
            List<String> al = repo.alerts.check(v);
            String line = c.label() + "   " + c.precedence.letter + " " + c.precedence.name().replace('_', ' ') + "   " + c.patientType
                    + (c.status == Tccc.Status.ACTIVE ? "" : "   [" + c.status.name().replace('_', ' ') + "]")
                    + "\n" + (v == null ? "no vitals recorded" : v.summary())
                    + (al.isEmpty() ? "" : "\nALERT: " + join(al));
            Button b = btn(line, new Runnable() { @Override public void run() { showCard(copy(c), false); } });
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            b.setAllCaps(false);
            View bar = new View(ctx);
            bar.setBackgroundColor(c.status == Tccc.Status.DECEASED ? Color.DKGRAY : c.precedence.argb);
            LinearLayout r = new LinearLayout(ctx);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.addView(bar, new LinearLayout.LayoutParams(px(10), ViewGroup.LayoutParams.MATCH_PARENT));
            r.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            col.addView(r);
        }
        col.addView(text("Unvalidated software. Not a medical device.", 11, false));
    }

    private void newCasualty(boolean atSelf) {
        Casualty c = new Casualty();
        GeoPoint p = repo.here(atSelf);
        c.lat = p.getLatitude(); c.lon = p.getLongitude();
        c.timeOfInjury = System.currentTimeMillis();
        c.firstResponder = repo.callsign();
        c.rev = 0;
        showCard(c, true);
    }

    private static Casualty copy(Casualty c) {
        return CotCodec.casualtyFromCot(CotCodec.toCot(c, System.currentTimeMillis(), Repo.STALE_MS));
    }

    // ------------------------------------------------------------------ card

    private void showCard(final Casualty c, final boolean isNew) {
        final LinearLayout col = screen();
        col.addView(btn("< BACK (discard changes)", new Runnable() { @Override public void run() { showList(); } }));
        col.addView(title(isNew ? "NEW CASUALTY" : "TCCC CARD  " + c.label()));
        col.addView(text("Location: " + Repo.mgrs(c.lat, c.lon), 13, false));

        final EditText roster = edit("Battle roster no. (e.g. JS1234)", c.rosterId, CAPS);
        final EditText name = edit("Name", c.name, TEXT);
        final EditText unit = edit("Unit", c.unit, TEXT);
        final EditText allergies = edit("Allergies", c.allergies, TEXT);
        final EditText blood = edit("Blood type", c.bloodType, CAPS);
        col.addView(roster); col.addView(row(name, unit)); col.addView(row(allergies, blood));

        col.addView(row(
                picker("Evac", names(Tccc.Precedence.values()), c.precedence.ordinal(), new Pick() {
                    @Override public void on(int i) { c.precedence = Tccc.Precedence.values()[i]; } }),
                picker("Type", names(Tccc.PatientType.values()), c.patientType.ordinal(), new Pick() {
                    @Override public void on(int i) { c.patientType = Tccc.PatientType.values()[i]; } })));
        col.addView(row(
                picker("Nat", names(Tccc.Nationality.values()), c.nationality.ordinal(), new Pick() {
                    @Override public void on(int i) { c.nationality = Tccc.Nationality.values()[i]; } }),
                picker("Status", names(Tccc.Status.values()), c.status.ordinal(), new Pick() {
                    @Override public void on(int i) { c.status = Tccc.Status.values()[i]; } })));

        col.addView(section("MECHANISM OF INJURY"));
        final TextView toi = text("", 13, false);
        final Runnable showToi = new Runnable() { @Override public void run() { toi.setText("Time of injury: " + Readout.dtg(c.timeOfInjury)); } };
        showToi.run();
        col.addView(toi);
        col.addView(row(
                btn("NOW", new Runnable() { @Override public void run() { c.timeOfInjury = System.currentTimeMillis(); showToi.run(); } }),
                btn("-5 MIN", new Runnable() { @Override public void run() { c.timeOfInjury -= 5 * 60000L; showToi.run(); } }),
                btn("+5 MIN", new Runnable() { @Override public void run() { c.timeOfInjury += 5 * 60000L; showToi.run(); } })));
        LinearLayout moiRow = null;
        for (final Tccc.Mechanism m : Tccc.Mechanism.values()) {
            if (moiRow == null || moiRow.getChildCount() == 3) { moiRow = row(); col.addView(moiRow); }
            final CheckBox cb = check(m.name(), c.mechanisms.contains(m));
            cb.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (cb.isChecked()) c.mechanisms.add(m); else c.mechanisms.remove(m); } });
            moiRow.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        final EditText moiOther = edit("Other mechanism (detail)", c.mechanismOther, TEXT);
        col.addView(moiOther);

        // --- injuries
        col.addView(section("INJURIES"));
        final LinearLayout injBox = column();
        col.addView(injBox);
        final Runnable[] drawInj = new Runnable[1];
        drawInj[0] = new Runnable() { @Override public void run() {
            injBox.removeAllViews();
            for (final Injury i : c.injuries)
                injBox.addView(removable(i.summary(), new Runnable() { @Override public void run() { c.injuries.remove(i); drawInj[0].run(); } }));
        } };
        drawInj[0].run();
        col.addView(btn("+ INJURY", new Runnable() { @Override public void run() {
            form("Add injury", new String[] { "Location (e.g. L thigh)", "Type (e.g. GSW, burn, fracture)" }, new int[] { TEXT, TEXT }, new Form() {
                @Override public void on(String[] v) {
                    if (v[0].isEmpty() && v[1].isEmpty()) return;
                    c.injuries.add(new Injury(v[0], v[1])); drawInj[0].run();
                } });
        } }));

        // --- vitals
        col.addView(section("SIGNS & SYMPTOMS"));
        final LinearLayout vitBox = column();
        col.addView(vitBox);
        final Runnable[] drawVit = new Runnable[1];
        drawVit[0] = new Runnable() { @Override public void run() {
            vitBox.removeAllViews();
            for (final Vitals v : c.vitals) {
                List<String> al = repo.alerts.check(v);
                vitBox.addView(removable(Readout.dtg(v.time) + "  " + v.summary() + (al.isEmpty() ? "" : "  ALERT: " + join(al)),
                        new Runnable() { @Override public void run() { c.vitals.remove(v); drawVit[0].run(); } }));
            }
        } };
        drawVit[0].run();
        col.addView(btn("+ VITALS", new Runnable() { @Override public void run() {
            form("Add vital signs (leave blank if not taken)",
                    new String[] { "Pulse", "BP systolic", "BP diastolic", "Resp rate", "SpO2 %", "Pain 0-10", "AVPU (A, V, P or U)" },
                    new int[] { NUM, NUM, NUM, NUM, NUM, NUM, CAPS }, new Form() {
                @Override public void on(String[] s) {
                    Vitals v = new Vitals(System.currentTimeMillis());
                    v.pulse = num(s[0], 0, 300); v.systolic = num(s[1], 0, 300); v.diastolic = num(s[2], 0, 250);
                    v.respRate = num(s[3], 0, 99); v.spo2 = num(s[4], 0, 100); v.pain = num(s[5], 0, 10);
                    v.source = "manual";
                    if (!s[6].isEmpty()) for (Tccc.Avpu a : Tccc.Avpu.values()) if (a.name().charAt(0) == Character.toUpperCase(s[6].charAt(0))) v.avpu = a;
                    if (v.summary().isEmpty()) { toast("No valid values entered"); return; }
                    c.vitals.add(v); drawVit[0].run();
                    List<String> al = repo.alerts.check(v);
                    if (!al.isEmpty()) toast("ALERT: " + join(al));
                } });
        } }));

        // --- treatments
        col.addView(section("TREATMENTS (MARCH)"));
        final LinearLayout txBox = column();
        col.addView(txBox);
        final Runnable[] drawTx = new Runnable[1];
        drawTx[0] = new Runnable() { @Override public void run() {
            txBox.removeAllViews();
            for (final Treatment t : c.treatments)
                txBox.addView(removable(Readout.dtg(t.time) + "  " + t.group + ": " + t.summary(),
                        new Runnable() { @Override public void run() { c.treatments.remove(t); drawTx[0].run(); } }));
        } };
        drawTx[0].run();
        col.addView(btn("+ TREATMENT", new Runnable() { @Override public void run() {
            final Tccc.March[] groups = Tccc.March.values();
            String[] labels = new String[groups.length];
            for (int i = 0; i < groups.length; i++) labels[i] = groups[i].name() + " - " + groups[i].label;
            choose("Treatment group", labels, new Pick() { @Override public void on(int g) {
                final Tccc.March group = groups[g];
                final String[] codes = codesFor(group);
                choose(group.label, codes, new Pick() { @Override public void on(final int k) {
                    final boolean other = k == codes.length - 1;
                    form(other ? "Other treatment" : codes[k],
                            other ? new String[] { "Treatment", "Detail (dose, route, site)" } : new String[] { "Detail (dose, route, site)" },
                            other ? new int[] { CAPS, TEXT } : new int[] { TEXT }, new Form() {
                        @Override public void on(String[] v) {
                            String code = other ? v[0] : codes[k];
                            if (code.isEmpty()) return;
                            c.treatments.add(new Treatment(System.currentTimeMillis(), group, code, other ? v[1] : v[0]));
                            drawTx[0].run();
                        } });
                } });
            } });
        } }));

        col.addView(section("NOTES"));
        final EditText notes = edit("Notes", c.notes, TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notes.setMinLines(2);
        final EditText responder = edit("First responder", c.firstResponder, TEXT);
        col.addView(notes); col.addView(responder);

        final Runnable apply = new Runnable() { @Override public void run() {
            c.rosterId = str(roster); c.name = str(name); c.unit = str(unit); c.allergies = str(allergies); c.bloodType = str(blood);
            c.mechanismOther = str(moiOther); c.notes = str(notes); c.firstResponder = str(responder);
        } };
        col.addView(row(
                btn("SAVE + SEND", new Runnable() { @Override public void run() {
                    apply.run();
                    toast(repo.save(c, true) ? "Saved and sent: " + c.label() : "Saved, but ATAK rejected the message");
                    showList();
                } }),
                btn("SAVE ONLY", new Runnable() { @Override public void run() {
                    apply.run(); repo.save(c, false); toast("Saved locally: " + c.label()); showList();
                } })));
        col.addView(row(
                btn("MIST REPORT", new Runnable() { @Override public void run() { apply.run(); report("MIST", Readout.mist(c)); } }),
                btn("SHOW ON MAP", new Runnable() { @Override public void run() {
                    MapItem mi = MapView.getMapView().getRootGroup().deepFindUID(c.uid);
                    if (mi != null) ATAKUtilities.scaleToFit(mi); else toast("Save the card first");
                } })));
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

    // ------------------------------------------------------------------ 9-line

    private void showNineLine() {
        final LinearLayout col = screen();
        col.addView(btn("< BACK", new Runnable() { @Override public void run() { showList(); } }));
        col.addView(title("9-LINE MEDEVAC REQUEST"));

        final List<Casualty> candidates = new ArrayList<>();
        for (Casualty c : repo.store.sorted()) if (c.status == Tccc.Status.ACTIVE || c.status == Tccc.Status.EVAC_REQUESTED) candidates.add(c);
        final List<CheckBox> boxes = new ArrayList<>();
        col.addView(section("CASUALTIES TO EVACUATE (lines 3, 5, 8)"));
        if (candidates.isEmpty()) col.addView(text("No active casualties. Add a casualty card first.", 14, false));
        for (Casualty c : candidates) {
            CheckBox cb = check(c.label() + "  " + c.precedence.letter + " " + c.precedence + "  " + c.patientType, c.status == Tccc.Status.ACTIVE);
            boxes.add(cb); col.addView(cb);
        }

        final boolean[] atSelf = { true };
        col.addView(section("LINE 1-2"));
        col.addView(picker("Pickup site", new String[] { "MY POSITION", "MAP CENTRE" }, 0, new Pick() { @Override public void on(int i) { atSelf[0] = i == 0; } }));
        SimpleDateFormat f = new SimpleDateFormat("HHmm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        final EditText titleEt = edit("Request title", "MED." + f.format(new Date()), CAPS);
        final EditText freq = edit("Line 2: frequency", "", TEXT);
        final EditText callsign = edit("Line 2: callsign", repo.callsign(), CAPS);
        col.addView(titleEt); col.addView(row(freq, callsign));

        col.addView(section("LINE 4: SPECIAL EQUIPMENT"));
        final CheckBox hoist = check("Hoist", false), extraction = check("Extraction", false), ventilator = check("Ventilator", false);
        col.addView(row3(hoist, extraction, ventilator));
        final EditText equipOther = edit("Other equipment", "", TEXT);
        col.addView(equipOther);

        col.addView(section("LINE 6-7"));
        final int[] sec = { 0 }, mark = { Tccc.Marking.NONE.ordinal() };
        col.addView(picker("Security", names(Tccc.Security.values()), sec[0], new Pick() { @Override public void on(int i) { sec[0] = i; } }));
        col.addView(picker("Marking", names(Tccc.Marking.values()), mark[0], new Pick() { @Override public void on(int i) { mark[0] = i; } }));
        final EditText markOther = edit("Marking detail (e.g. smoke colour)", "", TEXT);
        col.addView(markOther);

        col.addView(section("LINE 9: NBC / TERRAIN"));
        final EditText nbc = edit("NBC contamination (N, B, C or blank)", "", CAPS);
        final CheckBox slope = check("Slope", false), rough = check("Rough", false), loose = check("Loose", false);
        col.addView(nbc); col.addView(row3(slope, rough, loose));
        final EditText terrainOther = edit("Other terrain / obstacles", "", TEXT);
        final EditText remarks = edit("Remarks", "", TEXT);
        col.addView(terrainOther); col.addView(remarks);

        col.addView(btn("REVIEW AND SEND", new Runnable() { @Override public void run() {
            final List<Casualty> chosen = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isChecked()) chosen.add(candidates.get(i));
            if (chosen.isEmpty()) { toast("Select at least one casualty"); return; }
            GeoPoint p = repo.here(atSelf[0]);
            final NineLine n = NineLine.fromCasualties(str(titleEt), chosen, p.getLatitude(), p.getLongitude());
            n.locationText = Repo.mgrs(n.lat, n.lon);
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
                    .setPositiveButton("SEND", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            if (!repo.send(n)) { toast("ATAK rejected the 9-line; nothing was sent"); return; }
                            for (Casualty c : chosen) {
                                Casualty e = copy(c);
                                e.status = Tccc.Status.EVAC_REQUESTED;
                                repo.save(e, true);
                            }
                            toast("9-line sent: " + n.title);
                            showList();
                        } })
                    .show();
        } }));
    }

    // ------------------------------------------------------------------ widgets

    private Context dialogContext() { return MapView.getMapView().getContext(); }

    private LinearLayout screen() {
        listVisible = false;
        root.removeAllViews();
        ScrollView sv = new ScrollView(ctx);
        sv.setBackgroundColor(0xFF1E1E1E);
        LinearLayout col = column();
        col.setPadding(px(8), px(8), px(8), px(24));
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return col;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(ctx); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(View... views) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : views) l.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return l;
    }
    private LinearLayout row3(View a, View b, View c) { return row(a, b, c); }
    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s); t.setTextSize(sp); t.setTextColor(Color.WHITE);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(px(4), px(4), px(4), px(4));
        return t;
    }
    private TextView title(String s) { return text(s, 18, true); }
    private TextView section(String s) {
        TextView t = text(s, 13, true);
        t.setTextColor(0xFF9AD0FF);
        t.setPadding(px(4), px(14), px(4), px(2));
        return t;
    }
    private EditText edit(String hint, String value, int inputType) { return edit(ctx, hint, value, inputType); }
    private static EditText edit(Context c, String hint, String value, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint); e.setText(value == null ? "" : value); e.setInputType(inputType);
        e.setTextColor(Color.WHITE); e.setHintTextColor(0xFF9E9E9E); e.setTextSize(15);
        return e;
    }
    private Button btn(String label, final Runnable r) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        return b;
    }
    private CheckBox check(String label, boolean on) {
        CheckBox c = new CheckBox(ctx);
        c.setText(label); c.setChecked(on); c.setTextColor(Color.WHITE); c.setTextSize(13);
        return c;
    }
    private View removable(String s, Runnable onRemove) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.addView(text(s, 14, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        l.addView(btn("X", onRemove), new LinearLayout.LayoutParams(px(48), ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }
    /** A button showing "label: value" that opens a single-choice list. */
    private Button picker(final String label, final String[] items, int current, final Pick cb) {
        final Button b = new Button(ctx);
        b.setText(label + ": " + items[current]);
        b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            choose(label, items, new Pick() { @Override public void on(int i) { b.setText(label + ": " + items[i]); cb.on(i); } });
        } });
        return b;
    }
    private void choose(String title, String[] items, final Pick cb) {
        new AlertDialog.Builder(dialogContext()).setTitle(title)
                .setItems(items, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int which) { cb.on(which); } })
                .setNegativeButton("Cancel", null).show();
    }
    private void form(String title, String[] hints, int[] types, final Form cb) {
        Context dc = dialogContext();
        LinearLayout l = new LinearLayout(dc);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(px(16), px(8), px(16), px(8));
        final EditText[] fields = new EditText[hints.length];
        for (int i = 0; i < hints.length; i++) { fields[i] = edit(dc, hints[i], "", types[i]); l.addView(fields[i]); }
        ScrollView sv = new ScrollView(dc);
        sv.addView(l);
        new AlertDialog.Builder(dc).setTitle(title).setView(sv)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) {
                    String[] out = new String[fields.length];
                    for (int i = 0; i < fields.length; i++) out[i] = str(fields[i]);
                    cb.on(out);
                } }).show();
    }
    private void report(String title, final String body) {
        new AlertDialog.Builder(dialogContext()).setTitle(title).setMessage(body)
                .setNegativeButton("Close", null)
                .setPositiveButton("Copy", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) {
                    ClipboardManager cm = (ClipboardManager) dialogContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("MIST", body));
                    toast("Copied");
                } }).show();
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
