package io.github.vipersq.casualtykit.atak;

import android.os.Bundle;
import android.widget.Toast;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.vipersq.casualtykit.core.Casualty;
import io.github.vipersq.casualtykit.core.CasualtyStore;
import io.github.vipersq.casualtykit.core.CotCodec;
import io.github.vipersq.casualtykit.core.NineLine;
import io.github.vipersq.casualtykit.core.Vitals;
import io.github.vipersq.casualtykit.core.VitalsAlerts;

/** Glue between the platform-neutral core and ATAK: storage, map markers and the network. */
class Repo implements CotServiceRemote.CotEventListener {
    private static final String TAG = "CasualtyKit";
    /** Cards stay on other devices' maps for a day without a refresh. */
    static final long STALE_MS = 24L * 60 * 60 * 1000;

    final CasualtyStore store = new CasualtyStore();
    final VitalsAlerts alerts = new VitalsAlerts();
    /** Resolved in {@link #start()} so the class can be constructed without a running ATAK. */
    private File dir;
    /** Set by the UI; called on the UI thread when a record arrives from the network. */
    Runnable onRemoteChange;
    private boolean started;

    void start() {
        if (started) return;
        started = true;
        dir = FileSystemUtils.getItem("tools/casualtykit");
        try {
            if (!IOProviderFactory.exists(dir)) IOProviderFactory.mkdirs(dir);
            File[] files = IOProviderFactory.listFiles(dir);
            if (files != null) {
                for (File f : files) {
                    if (!f.getName().endsWith(".cot")) continue;
                    Casualty c = CotCodec.casualtyFromCot(read(f));
                    if (c != null) store.mergeRemote(c);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "could not load saved casualties", e);
        }
        CommsMapComponent.getInstance().addOnCotEventListener(this);
    }

    void stop() {
        if (!started) return;
        started = false;
        CommsMapComponent.getInstance().removeOnCotEventListener(this);
    }

    String callsign() {
        MapView mv = MapView.getMapView();
        String cs = mv == null ? null : mv.getDeviceCallsign();
        return cs == null ? "" : cs;
    }

    /** Own position if there is a fix, otherwise the centre of the map, as {lat, lon}. */
    double[] here(boolean preferSelf) {
        MapView mv = MapView.getMapView();
        GeoPoint p = null;
        if (preferSelf) {
            Marker self = mv.getSelfMarker();
            GeoPoint sp = self == null ? null : self.getPoint();
            if (sp != null && sp.isValid() && !(sp.getLatitude() == 0 && sp.getLongitude() == 0)) p = sp;
        }
        if (p == null) p = mv.getCenterPoint().get();
        return new double[] { p.getLatitude(), p.getLongitude() };
    }

    String mgrs(double lat, double lon) {
        try {
            return CoordinateFormatUtilities.formatToString(new GeoPoint(lat, lon), CoordinateFormat.MGRS);
        } catch (Exception e) {
            return "";
        }
    }

    /** If the user dragged the marker on the map, take the new position before saving. */
    private void pullPointFromMap(Casualty c) {
        MapItem mi = MapView.getMapView().getRootGroup().deepFindUID(c.uid);
        if (mi instanceof PointMapItem) {
            GeoPoint p = ((PointMapItem) mi).getPoint();
            if (p != null && p.isValid()) { c.lat = p.getLatitude(); c.lon = p.getLongitude(); }
        }
    }

    /** Save an edited card, update its marker and optionally send it to the team. Returns false if sending failed. */
    boolean save(Casualty c, boolean send) {
        if (store.get(c.uid) != null) pullPointFromMap(c);
        c.touch(System.currentTimeMillis(), callsign());
        store.putLocal(c);
        String xml = CotCodec.toCot(c, System.currentTimeMillis(), STALE_MS);
        write(new File(dir, safe(c.uid) + ".cot"), xml);
        return publish(xml, send);
    }

    /** Remove a card from this device and take its marker off the map. Other devices keep their copy. */
    void delete(String uid) {
        store.remove(uid);
        try {
            File f = new File(dir, safe(uid) + ".cot");
            if (IOProviderFactory.exists(f)) IOProviderFactory.delete(f);
        } catch (Exception e) {
            Log.e(TAG, "could not delete saved card", e);
        }
        MapItem mi = MapView.getMapView().getRootGroup().deepFindUID(uid);
        if (mi != null) mi.removeFromGroup();
    }

    boolean send(NineLine n) {
        n.updated = System.currentTimeMillis();
        return publish(CotCodec.toCot(n, System.currentTimeMillis(), STALE_MS), true);
    }

    private boolean publish(String xml, boolean external) {
        CotEvent ev = CotEvent.parse(xml);
        if (ev == null || !ev.isValid()) {
            Log.e(TAG, "generated CoT was rejected by ATAK");
            return false;
        }
        CotMapComponent.getInternalDispatcher().dispatch(ev);
        if (external) CotMapComponent.getExternalDispatcher().dispatch(ev);
        return true;
    }

    @Override
    public void onCotEvent(CotEvent ev, Bundle extras) {
        try {
            if (ev == null || !CotCodec.TYPE_CASUALTY.equals(ev.getType()) || ev.getDetail() == null
                    || ev.getDetail().getFirstChildByName(0, CotCodec.DETAIL_CASUALTY) == null) return;
            final Casualty c = CotCodec.casualtyFromCot(ev.toString());
            if (c == null) return;
            Casualty old = store.get(c.uid);
            Vitals before = old == null ? null : old.latestVitals();
            if (!store.mergeRemote(c)) return;
            write(new File(dir, safe(c.uid) + ".cot"), CotCodec.toCot(c, System.currentTimeMillis(), STALE_MS));
            final List<String> fresh = alerts.newAlerts(before, c.latestVitals());
            final MapView mv = MapView.getMapView();
            mv.post(new Runnable() {
                @Override public void run() {
                    if (!fresh.isEmpty())
                        Toast.makeText(mv.getContext(), "CASUALTY " + c.label() + ": " + fresh, Toast.LENGTH_LONG).show();
                    if (onRemoteChange != null) onRemoteChange.run();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "failed to handle incoming casualty", e);
        }
    }

    private static String safe(String uid) { return uid.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private static void write(File f, String s) {
        try (OutputStream os = IOProviderFactory.getOutputStream(f)) {
            os.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "could not save " + f.getName(), e);
        }
    }

    private static String read(File f) throws Exception {
        try (InputStream is = IOProviderFactory.getInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
