package com.bitwig.mods.quickinsert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import bml.api.Bml;
import bml.notifications.Notifications;
import bitwig.obf.FAu;
import bitwig.obf.IkS;
import bitwig.obf.iCC;
import bitwig.obf.j2e;
import bitwig.obf.jsd;
import bitwig.obf.uGG;
import com.bitwig.base.async.lBQ; // the listener Bitwig's insertion really hands its outcome to
import com.bitwig.base.io.z2y;
import com.bitwig.flt.app.CHR; // the application window controller, the fallback for a notification
import com.bitwig.flt.document.core.iface.RYd;
import com.bitwig.flt.document.core.iface.pHd;
import com.bitwig.flt.document.core.iface.clipboard.device.IntelligentDeviceInsertionPoint;

/**
 * The slots themselves: what each one holds, where that comes from, and how it is inserted.
 * Shared by the actions (one per slot) and the panel (a grid of them).
 *
 * <p>Which slot holds what lives in this mod's settings, not in a folder, so a slot keeps its
 * place when a file is renamed or another file is added, and editing a slot never needs a build.
 * Three keys per slot:
 *
 * <pre>
 * slot.17.path=/some/where/Pro-Q 4.vst3    the file Bitwig loads; empty for an empty slot
 * slot.17.path.alt=/some/where/Pro-Q 4.vst3/wrapper   only when the browser named a bundle
 * slot.17.name=Pro-Q 4                     what the panel shows; the file name when unset
 * </pre>
 *
 * <p>An empty-looking path falls back to the older {@code slotN.preset} key, so settings written by
 * an earlier version keep working.
 *
 * <p>The settings are read once and kept: a menu with sixty-four rows asks for every one of them,
 * and reading a file that often stalls the menu. Every write goes through {@link #changed()}, so
 * the copy and the panel follow at once.
 */
final class Slots {
    /** How many slots the mod offers. */
    static final int COUNT = 64;

    /** The mod, for its settings, its folder and its log. */
    static final Bml.Mod MOD = Bml.mod("quick-insert-slots");

    /** Slots with nothing of their own fall back to these: none, so a fresh install starts empty. */
    private static final String[] DEFAULT_PRESETS = {};

    /** The settings key that says the slots were filled for the first time. */
    private static final String SEEDED = "slots.seeded";

    /** The settings key that says whether a right-click on a cell empties it. */
    private static final String CLEAR_ON_RIGHT_CLICK = "clear.right.click";

    /** The settings key that says whether a slot goes into every selected track. */
    private static final String INSERT_IN_ALL = "insert.all.selected";

    /** The settings key that says whether using a slot says so. */
    private static final String NOTIFY = "notify.enabled";

    /** The settings key that says how many slots are on show. */
    private static final String SHOWN = "slots.count";

    /** How many slots a fresh install shows. */
    static final int DEFAULT_SHOWN = 4;

    /** How many slots the panel and the menus offer, kept here so a menu does not read settings. */
    private static volatile int shown;

    /** The slots as last read; null until the first read and after every write. */
    private static volatile Slot[] cache;

    /** What wants to know when a slot changes: the panel, so it can redraw. */
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private Slots() {
    }

    // ------------------------------------------------------------------ counts and switches

    /** How many slots the panel and the menus offer. */
    static int shown() {
        int count = shown;
        if (count != 0) return count;
        synchronized (Slots.class) {
            if (shown == 0) shown = clamp(MOD.settings().getInt(SHOWN, DEFAULT_SHOWN));
            return shown;
        }
    }

    /**
     * Shows more or fewer slots. Only what the menus and the panel draw changes: every slot keeps
     * its settings and its action, so a slot hidden now comes back with its content and its
     * shortcut when it is shown again.
     */
    static void show(int count) {
        int clamped = clamp(count);
        MOD.settings().putInt(SHOWN, clamped);
        MOD.settings().flush();
        shown = clamped;
        changed();
        probe("SLOTS_SHOWN: " + clamped);
    }

    private static int clamp(int count) {
        return Math.max(1, Math.min(COUNT, count));
    }

    /** Whether a right-click on a cell empties it: on, until turned off in the settings. */
    static boolean clearsOnRightClick() {
        return MOD.settings().getBoolean(CLEAR_ON_RIGHT_CLICK, true);
    }

    /** Turns right-click clearing on or off, for a slot that is too easy to hit by accident. */
    static void clearsOnRightClick(boolean on) {
        MOD.settings().putBoolean(CLEAR_ON_RIGHT_CLICK, on);
        MOD.settings().flush();
        probe("CLEAR_ON_RIGHT_CLICK: " + on);
    }

    /** Whether one click puts the slot into every selected track instead of the first one. */
    static boolean insertsIntoAllSelected() {
        return MOD.settings().getBoolean(INSERT_IN_ALL, false);
    }

    /** Turns "insert into all selected tracks" on or off. */
    static void insertsIntoAllSelected(boolean on) {
        MOD.settings().putBoolean(INSERT_IN_ALL, on);
        MOD.settings().flush();
        probe("INSERT_IN_ALL: " + on);
    }

    /** Whether using a slot says so on screen: on, until turned off in the settings. */
    static boolean notifies() {
        return MOD.settings().getBoolean(NOTIFY, true);
    }

    /** Turns the notices on or off: someone who has learned the mod may not want to be told. */
    static void notifies(boolean on) {
        MOD.settings().putBoolean(NOTIFY, on);
        MOD.settings().flush();
        probe("NOTIFY: " + on);
        if (on) notify("Quick Insert: notices are on");
    }

    // ------------------------------------------------------------------ reading

    /** A slot: where it is, what to call it, and the bundle the browser named, if it named one. */
    static final class Slot {
        final int number;
        final String path;
        /** The exact path the browser named, when {@link #path} is the module inside it. */
        final String altPath;
        final String name;

        Slot(int number, String path, String altPath, String name) {
            this.number = number;
            this.path = path;
            this.altPath = altPath;
            this.name = name;
        }

        boolean filled() {
            return !path.isEmpty();
        }
    }

    /** The slots, in order; a slot with nothing in it has an empty path. */
    static Slot[] slots() {
        Slot[] read = cache;
        if (read != null) return read;
        synchronized (Slots.class) {
            if (cache == null) cache = readAll();
            return cache;
        }
    }

    /** One slot. */
    static Slot slot(int number) {
        if (number < 1 || number > COUNT) return new Slot(number, "", "", "");
        return slots()[number - 1];
    }

    private static Slot[] readAll() {
        seedOnce();
        Slot[] slots = new Slot[COUNT];
        for (int number = 1; number <= COUNT; number++) slots[number - 1] = read(number);
        return slots;
    }

    private static Slot read(int number) {
        String path = MOD.settings().get(key(number, "path"), "").trim();
        if (path.isEmpty()) path = MOD.settings().get("slot" + number + ".preset", "").trim(); // older key
        String name = cleanName(MOD.settings().get(key(number, "name"), "").trim());
        String altPath = MOD.settings().get(key(number, "path.alt"), "").trim();
        // A slot saved earlier may point at the .vst3 bundle instead of the module inside it, which
        // Bitwig cannot load: keep the module, and the bundle as the thing it came from.
        String module = moduleOf(path);
        if (!module.equals(path)) {
            if (altPath.isEmpty()) altPath = path;
            path = module;
        }
        if (name.isEmpty()) name = nameOf(path);
        return new Slot(number, path, altPath, name);
    }

    /**
     * A name without the file extension Bitwig's browser appends to some results: a built-in device
     * is called {@code 8Macro Random.bwdevice} there, and a slot should say {@code 8Macro Random}.
     */
    static String cleanName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        for (String suffix : new String[] {".bwdevice", ".bwpreset", ".bwclip", ".vst3", ".clap", ".dll", ".au"}) {
            if (lower.endsWith(suffix)) return trimmed.substring(0, trimmed.length() - suffix.length()).trim();
        }
        return trimmed;
    }

    /** A slot's name: its own name, else its file's name without the folder or the extension. */
    static String nameOf(String path) {
        if (path == null || path.isEmpty()) return "";
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** What a slot is called: its own name, or nothing when the slot is empty. */
    static String nameOfSlot(int number) {
        Slot slot = slot(number);
        return slot.filled() ? slot.name : "";
    }

    /** The file Bitwig can load for a plug-in path. A VST3 on Windows is usually a folder named */
    /** {@code X.vst3} with the loadable module in {@code Contents\<arch>\X.vst3}. */
    static String moduleOf(String path) {
        if (path == null || path.isEmpty()) return path;
        File file = new File(path);
        if (!file.isDirectory() || !path.toLowerCase(java.util.Locale.ROOT).endsWith(".vst3")) return path;
        File contents = new File(file, "Contents");
        File[] architectures = contents.listFiles();
        if (architectures == null) return path;
        for (File architecture : architectures) {
            File[] modules = architecture.listFiles();
            if (modules == null) continue;
            for (File module : modules) {
                if (module.isFile() && module.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".vst3")) {
                    return module.getPath();
                }
            }
        }
        return path;
    }

    private static String key(int number, String field) {
        return "slot." + number + "." + field;
    }

    /**
     * Fills the slots once: the `.bwpreset` files in the mod's `slots` folder in file-name order,
     * then the built-in defaults. Later runs leave the settings alone, so a slot the user changed
     * stays changed.
     */
    private static void seedOnce() {
        try {
            if (!MOD.settings().get(SEEDED, "").isEmpty()) return;
            List<String> files = folderPresets();
            for (int number = 1; number <= COUNT; number++) {
                String path = "";
                if (number <= files.size()) path = files.get(number - 1);
                else if (number <= DEFAULT_PRESETS.length) path = DEFAULT_PRESETS[number - 1];
                if (path.isEmpty()) continue;
                if (!MOD.settings().get(key(number, "path"), "").isEmpty()) continue;
                MOD.settings().put(key(number, "path"), path);
            }
            MOD.settings().put(SEEDED, "1");
            MOD.settings().flush();
            probe("SLOTS_SEEDED");
        } catch (Throwable problem) {
            probe("SLOTS_SEED_ERROR " + problem.getClass().getName() + ": " + problem.getMessage());
        }
    }

    /** The files in the mod's `slots` folder that can go into a device chain, in file-name order. */
    static List<String> folderPresets() {
        List<String> files = new ArrayList<>();
        try {
            java.nio.file.Path readme = MOD.resource("readme");
            java.nio.file.Path folder = readme != null ? readme.getParent().resolve("slots") : null;
            if (folder == null || !java.nio.file.Files.isDirectory(folder)) return files;
            List<java.nio.file.Path> sorted = new ArrayList<>();
            try (var stream = java.nio.file.Files.list(folder)) {
                stream.filter(p -> {
                    String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return name.endsWith(".bwpreset") || name.endsWith(".vst3") || name.endsWith(".clap");
                }).forEach(sorted::add);
            }
            sorted.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            for (java.nio.file.Path file : sorted) files.add(file.toAbsolutePath().toString());
        } catch (Throwable problem) {
            probe("SLOTS_FOLDER_ERROR " + problem.getClass().getName() + ": " + problem.getMessage());
        }
        return files;
    }

    // ------------------------------------------------------------------ writing

    /**
     * Puts an item into a slot: the file Bitwig loads, and the path the browser named when that is
     * a bundle around it. One write, one flush, one redraw.
     */
    static void store(int number, String path, String altPath, String name) {
        try {
            String module = moduleOf(path);
            MOD.settings().put(key(number, "path"), module);
            if (altPath == null || altPath.isEmpty() || altPath.equals(module)) {
                MOD.settings().remove(key(number, "path.alt"));
            } else {
                MOD.settings().put(key(number, "path.alt"), altPath);
            }
            MOD.settings().put(key(number, "name"), cleanName(name));
            MOD.settings().remove(key(number, "kind")); // a key an earlier version wrote
            MOD.settings().remove("slot" + number + ".preset"); // the key before slots were numbered
            MOD.settings().flush();
            changed();
        } catch (Throwable problem) {
            probe("SLOT_STORE_ERROR: " + number + " " + problem);
        }
    }

    /** Empties a slot: its path, its name and what it was, so nothing of the old content stays. */
    static void clear(int number) {
        try {
            for (String field : new String[] {"path", "path.alt", "name", "kind", "source"}) {
                MOD.settings().remove(key(number, field));
            }
            MOD.settings().remove("slot" + number + ".preset"); // the key before slots were numbered
            MOD.settings().flush();
            changed();
            probe("SLOT_CLEARED: slot " + number);
        } catch (Throwable problem) {
            probe("SLOT_CLEAR_ERROR: " + number + " " + problem);
        }
    }

    /** Wants to hear about a change: the panel redraws itself. */
    static void onChanged(Runnable listener) {
        LISTENERS.add(listener);
    }

    /** Stops a listener hearing about changes: called when a panel closes. */
    static void removeChanged(Runnable listener) {
        LISTENERS.remove(listener);
    }

    /** Forgets the copy of the settings and tells the panel to draw them again. */
    static void changed() {
        cache = null;
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (Throwable problem) {
                probe("SLOTS_LISTENER_ERROR: " + problem);
            }
        }
    }

    // ------------------------------------------------------------------ inserting

    /** Inserts a slot into a window's project: one track, or every selected one. */
    static void insertInto(bitwig.obf.aBW window, int number) {
        Slot slot = slot(number);
        if (!slot.filled()) {
            probe("SLOT_EMPTY: slot " + number + " has nothing configured");
            notify("Quick Insert: slot " + number + " is empty");
            return;
        }
        pHd project = window != null ? window.mtS() : null;
        List<RYd> tracks = selectedTracks(project);
        if (tracks.isEmpty()) {
            probe("SLOT_SKIP: no track selected");
            notify("Quick Insert: select a track first");
            return;
        }
        if (insertsIntoAllSelected()) {
            probe("SLOT_ALL: slot " + number + " into " + tracks.size() + " tracks");
            for (RYd track : tracks) insert(track.Ucl(), slot);
        } else {
            insert(tracks.get(0).Ucl(), slot);
        }
    }

    /** The selected tracks that have a device chain, in the order Bitwig has them. */
    static List<RYd> selectedTracks(pHd project) {
        List<RYd> tracks = new ArrayList<>();
        if (project == null) return tracks;
        iCC selection = project.wIw().E3(); // wIw(): the project's view settings; E3(): the track selection
        if (selection == null) return tracks;
        for (Object item : selection.FzB()) {
            if (item instanceof RYd channel && channel.Ucl() != null) tracks.add(channel);
        }
        return tracks;
    }

    /**
     * Inserts what a slot points at.
     *
     * <p>The file Bitwig could not open is the one case where the path the browser named is tried
     * as well: a bundle is a path Bitwig will refuse, and its module is not. A refusal is not
     * retried with a different path, because the second attempt would only be refused again (or,
     * for a bundle, fail with an error of its own).
     */
    static void insert(FAu chain, Slot slot) {
        if (chain == null) {
            probe("SLOT_SKIP: the track has no device chain");
            return;
        }
        Outcome outcome = tryInsert(chain, slot.path);
        if (outcome == Outcome.MISSING && !slot.altPath.isEmpty() && !slot.altPath.equals(slot.path)) {
            probe("SLOT_FALLBACK: " + slot.altPath);
            tryInsert(chain, slot.altPath);
        }
    }

    /** What trying one path came to. */
    private enum Outcome { QUEUED, MISSING, REFUSED }

    /** One attempt: check the file, ask Bitwig to take it, and report when Bitwig is done with it. */
    private static Outcome tryInsert(FAu chain, String path) {
        File file = new File(moduleOf(path));
        String name = nameOf(path);
        if (!file.exists()) {
            probe("SLOT_MISSING: " + path);
            notify("Quick Insert: " + name + " is missing");
            return Outcome.MISSING;
        }
        IkS data = new IkS(List.of(new z2y(file)), java.util.Map.of());
        // The intelligent point: an instrument goes to the start of the chain, an effect to its end.
        // Placing by index was tried and taken out again: an index taken from the selected device
        // put an instrument among the effects, and Bitwig's own placement is the better judge.
        uGG point = new IntelligentDeviceInsertionPoint(chain);
        j2e insertion = point.cfM(data, point.UNj(data));
        if (insertion == null) {
            probe("SLOT_REFUSED: " + path);
            notify("Quick Insert: Bitwig would not insert " + name);
            return Outcome.REFUSED;
        }
        probe("SLOT_QUEUED: " + name + " from " + path);
        // The insertion runs after this call returns, and Bitwig can still fail it: a plug-in that
        // cannot be loaded fails here, after a "done" would already have been shown. So the report
        // is the insertion's own. The listener is an lBQ: Bitwig's method takes an oRV but casts it
        // to lBQ inside, so a plain oRV makes it throw a ClassCastException and nothing is inserted.
        try {
            insertion.MRY(new lBQ() {
                @Override
                public void taskCompleted() {
                    probe("SLOT_DONE: inserted " + name);
                    Slots.notify("Quick Insert: inserted " + name);
                }

                @Override
                public void cfM(Exception failure) {
                    probe("SLOT_FAILED: " + name + " " + failure);
                    String detail = failure != null && failure.getMessage() != null ? ": " + failure.getMessage() : "";
                    Slots.notify("Quick Insert: could not insert " + name + detail);
                }
            });
        } catch (Throwable problem) {
            probe("SLOT_LISTENER_ERROR: " + name + " " + problem);
        }
        return Outcome.QUEUED;
    }

    /** The device chain of a device, or null when it has none. */
    static FAu chainOf(jsd device) {
        return device != null ? device.xL() : null;
    }

    // ------------------------------------------------------------------ reporting

    /** A line to the mod log, for the loader's report. */
    static void probe(String message) {
        try {
            MOD.log(message);
        } catch (Throwable ignored) {
            // Nothing left to try.
        }
    }

    /**
     * A short notice for the user, from Framework: Notifications: it is the framework's job to say
     * what happened, and it knows where the user wants to see it (a toast, or the notification
     * centre when they have turned toasts off). Bitwig's own notification is the fallback.
     */
    static void notify(String text) {
        if (!notifies()) {
            probe("NOTICE_OFF: " + text);
            return;
        }
        try {
            Notifications.toast(text);
            return;
        } catch (Throwable problem) {
            probe("NOTIFY_FALLBACK: " + problem);
        }
        try {
            CHR app = CHR.Grk(); // the application window controller
            if (app != null) app.hUR(text); // show the on-screen notification
        } catch (Throwable ignored) {
            // The mod log above already has the line.
        }
    }
}
