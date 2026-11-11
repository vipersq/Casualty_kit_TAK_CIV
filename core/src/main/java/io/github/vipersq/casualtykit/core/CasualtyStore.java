package io.github.vipersq.casualtykit.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory set of casualties with last-writer-wins merge for records arriving from the network. */
public class CasualtyStore {
    public interface Listener { void onChanged(Casualty c, boolean fromNetwork); }

    private final Map<String, Casualty> byUid = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    public synchronized void addListener(Listener l) { listeners.add(l); }
    public synchronized Casualty get(String uid) { return byUid.get(uid); }
    public synchronized int size() { return byUid.size(); }

    /** Store a locally edited record (caller has already called {@link Casualty#touch}). */
    public synchronized void putLocal(Casualty c) { byUid.put(c.uid, c); fire(c, false); }

    /** Merge a record received from another device. Returns true if it replaced or added a record. */
    public synchronized boolean mergeRemote(Casualty incoming) {
        Casualty mine = byUid.get(incoming.uid);
        if (mine != null && !mine.isSupersededBy(incoming)) return false;
        byUid.put(incoming.uid, incoming);
        fire(incoming, true);
        return true;
    }

    /** Remove a record from this device only. Returns false if it was not present. */
    public synchronized boolean remove(String uid) { return byUid.remove(uid) != null; }

    /** On-scene casualties first, then the evacuated and returned to duty, the dead last. */
    private static int group(Casualty c) { return c.status.isOpen() ? 0 : c.status.isDead() ? 2 : 1; }

    /** Casualties still on scene first; within a group most urgent first, then longest-waiting first. */
    public synchronized List<Casualty> sorted() {
        List<Casualty> out = new ArrayList<>(byUid.values());
        Collections.sort(out, new Comparator<Casualty>() {
            @Override public int compare(Casualty a, Casualty b) {
                int g = Integer.compare(group(a), group(b));
                if (g != 0) return g;
                int p = a.precedence.compareTo(b.precedence);
                return p != 0 ? p : Long.compare(a.timeOfInjury, b.timeOfInjury);
            }
        });
        return out;
    }

    private void fire(Casualty c, boolean net) { for (Listener l : listeners) l.onChanged(c, net); }
}
