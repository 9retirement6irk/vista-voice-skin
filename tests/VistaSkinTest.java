// SPDX-License-Identifier: Apache-2.0
package me.wxcyber.kiosk.vista;

import java.util.*;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Exercises the skin's logic without a device. No view is ever built. */
public final class VistaSkinTest {
    static final class Host implements PluginHost {
        final Set<String> subscriptions = new HashSet<>();
        final Map<String, CommandCallback> reads = new HashMap<>();
        final Map<String, OverlaySpec> overlays = new HashMap<>();
        final List<String> logs = new ArrayList<>();
        boolean older;
        public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) {
            assert arguments.isEmpty();
            reads.put(command, callback);
        }
        public void subscribe(String event) { if (older) throw new IllegalArgumentException("Unknown KS event"); subscriptions.add(event); }
        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) { assert key.matches("[a-z][a-z0-9_]{0,39}") : key; overlays.put(key, spec); }
        public void hideOverlay(String key) { overlays.remove(key); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) { logs.add(message); }
        public void status(String message, boolean error) {}
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> value = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) value.put((String) pairs[i], pairs[i + 1]);
        return value;
    }

    public static void main(String[] args) throws Exception {
        Host host = new Host();
        VistaSkinPlugin plugin = new VistaSkinPlugin();
        plugin.start(host, map("title", "  Kitchen  ", "showTaskbar", false));
        SkinController skin = plugin.skin();
        Turn turn = skin.turn;

        // Settings: trimmed title, taskbar off. No asset API on this host: a plain mic.
        assert "Kitchen".equals(turn.title) && !turn.showTaskbar;
        assert turn.mic == null && !host.logs.isEmpty();
        plugin.configure(map("title", " ", "showTaskbar", true));
        assert "Voice Assistant".equals(turn.title) && turn.showTaskbar;

        assert host.subscriptions.containsAll(Arrays.asList(SkinController.EVENTS));
        assert host.reads.keySet().containsAll(Arrays.asList("getVoiceState", "getVoiceText", "getVoiceAppearance"));

        // Not picked: KS draws the turn.
        host.reads.get("getVoiceState").onResult(true, map("enabled", true, "state", "listening", "skinSelected", false, "overlay", "hidden"), null);
        assert "listening".equals(turn.state);
        plugin.onEvent("ks.voice.overlay", map("overlay", "full"));
        assert host.overlays.isEmpty() && skin.drawn() == null;

        // Picked: full screen, on top, touches pass through.
        plugin.onEvent("ks.voice.skin", map("selected", true));
        OverlaySpec full = host.overlays.get(SkinController.KEY);
        assert full != null && full.onTop() && !full.touchable() && !full.closeOnBack();
        assert full.width() == OverlaySpec.FILL && full.height() == OverlaySpec.FILL;
        assert "full".equals(skin.drawn());

        // Phases follow voice.state and the words.
        assert turn.phase() == Turn.LISTENING && "Listening…".equals(turn.headline());
        plugin.onEvent("ks.voice.level", map("level", 1.7));
        assert turn.level == 1f;
        plugin.onEvent("ks.voice.level", map("level", "loud"));
        assert turn.level == 0f;
        plugin.onEvent("ks.voice.text", map("command", "What's the weather tomorrow?", "answer", "", "tools", Collections.emptyList(), "streaming", false, "announcement", false));
        plugin.onEvent("ks.voice.state", map("state", "processing"));
        assert turn.phase() == Turn.THINKING && "Thinking".equals(turn.meterLabel()) && "Working on it…".equals(turn.headline());
        plugin.onEvent("ks.voice.text", map("command", "What's the weather tomorrow?", "answer", "Mostly sunny.", "tools", Arrays.asList("Got the forecast", "Read the sensor"), "streaming", true));
        plugin.onEvent("ks.voice.state", map("state", "responding"));
        assert turn.phase() == Turn.SPEAKING && "Read the sensor".equals(turn.tool) && "Mostly sunny.".equals(turn.headline());
        plugin.onEvent("ks.voice.state", map("state", "idle"));
        assert turn.phase() == Turn.DONE && "Done".equals(turn.status());
        // The answer setting is off and a tool ran: no placeholder over the tool line.
        plugin.onEvent("ks.voice.text", map("command", "Lights on", "answer", "", "tools", Arrays.asList("Turned on the lights")));
        assert turn.phase() == Turn.DONE && turn.headline().isEmpty();
        plugin.onEvent("ks.voice.text", map("command", "", "answer", "Dinner is ready", "announcement", true));
        assert turn.phase() == Turn.ANNOUNCING;

        // A late read loses to events it is older than.
        host.reads.get("getVoiceText").onResult(true, map("command", "stale", "answer", "stale"), null);
        assert "Dinner is ready".equals(turn.answer);

        // Appearance, clamped.
        host.reads.get("getVoiceAppearance").onResult(true, map("theme", "dark", "background", 0.4, "textScale", 1.5, "reactive", true), null);
        assert "dark".equals(turn.theme) && turn.background == 0.4f && turn.textScale == 1.5f;
        assert turn.dark(false) && !new Turn().dark(false) && new Turn().dark(true);
        plugin.onEvent("ks.voice.appearance", map("theme", "light", "background", null, "textScale", 9, "reactive", false));
        assert "light".equals(turn.theme) && turn.background == null && turn.textScale == 2f && !turn.reactive;
        assert !turn.dark(true);
        // An older appearance read never overrides an event.
        host.reads.get("getVoiceAppearance").onResult(true, map("theme", "dark"), null);
        assert "light".equals(turn.theme);

        // Docked replaces full screen with a window at the bottom.
        plugin.onEvent("ks.voice.overlay", map("overlay", "docked"));
        OverlaySpec docked = host.overlays.get(SkinController.KEY);
        assert docked != null && docked != full && docked.onTop() && !docked.touchable();
        assert OverlaySpec.BOTTOM.equals(docked.anchor()) && "docked".equals(skin.drawn());

        // Hidden removes it, and the level drops.
        plugin.onEvent("ks.voice.level", map("level", 0.5));
        plugin.onEvent("ks.voice.overlay", map("overlay", "hidden"));
        assert host.overlays.isEmpty() && skin.drawn() == null && turn.level == 0;

        // Another skin picked while showing: removed.
        plugin.onEvent("ks.voice.overlay", map("overlay", "full"));
        assert host.overlays.containsKey(SkinController.KEY);
        plugin.onEvent("ks.voice.skin", map("selected", false));
        assert host.overlays.isEmpty();

        // Unknown events and null payloads are ignored.
        plugin.onEvent("ks.screen.state", map("on", true));
        plugin.onEvent("ks.voice.overlay", null);

        // Actions: none.
        try {
            plugin.execute("anything", map());
            assert false;
        } catch (IllegalArgumentException expected) {
        }

        // Stop resets everything, and a stale read after it does nothing.
        PluginHost.CommandCallback staleState = host.reads.get("getVoiceState");
        plugin.stop();
        assert skin.drawn() == null && "idle".equals(turn.state) && turn.answer.isEmpty() && "auto".equals(turn.theme);
        staleState.onResult(true, map("skinSelected", true, "overlay", "full"), null);
        assert host.overlays.isEmpty();

        // Theme: Light and Dark force theirs, anything else follows KS's light or dark mode.
        Turn follow = new Turn();
        for (String pick : new String[] {"auto", "Auto", "system", "", null, "something new"}) {
            follow.theme = pick;
            assert follow.dark(true) && !follow.dark(false) : pick;
        }
        follow.theme = " Dark ";
        assert follow.dark(false);
        follow.theme = "LIGHT";
        assert !follow.dark(true);

        // The wallpaper: the Vista Aero theme's ribbons SVG, ported whole.
        assert RibbonData.RAYS.length == 6 && RibbonData.STROKES.length == 41 && RibbonData.FLARES.length == 2;
        int pulses = 0;
        for (Ribbon.Stroke s : RibbonData.STROKES) {
            if (s.pulse) pulses++;
            assert (s.d.length - 2) % 6 == 0 && s.d.length >= 8 : "M then whole cubic segments";
            assert s.gradient == null || Arrays.asList("gSheet", "gLime", "gFeather").contains(s.gradient) : s.gradient;
        }
        assert pulses == 7;
        assert RibbonData.STROKES[0].group == Ribbon.SWAY && RibbonData.STROKES[RibbonData.STROKES.length - 1].group == Ribbon.SWAY;
        assert RibbonData.SHEET_COLORS[0] == 0xE67DFF5C && RibbonData.FLARES[1].delay == -4f;

        // CSS timing: ease-in-out is symmetric and pinned at its ends.
        assert Ribbon.easeInOut(0) == 0 && Ribbon.easeInOut(1) == 1 && Math.abs(Ribbon.easeInOut(.5f) - .5f) < 1e-4;
        assert Math.abs(Ribbon.easeInOut(.25f) + Ribbon.easeInOut(.75f) - 1) < 1e-4 && Ribbon.easeInOut(.25f) < .25f;
        // alternate runs forward then back; a negative delay starts partway through.
        assert Ribbon.progress(0, 0, 10, Ribbon.ALTERNATE) == 0;
        assert Math.abs(Ribbon.progress(13, 0, 10, Ribbon.ALTERNATE) - .7f) < 1e-5;
        assert Math.abs(Ribbon.progress(0, -9, 41, Ribbon.ALTERNATE_REVERSE) - (1 - 9 / 41f)) < 1e-5;
        assert Math.abs(Ribbon.progress(4, 0, 16, Ribbon.NORMAL) - .25f) < 1e-6;
        // Rays and flares fade between 0.35 and 1.
        assert Math.abs(Ribbon.fade(0, 0, 12) - .35f) < 1e-5 && Math.abs(Ribbon.fade(12, 0, 12) - 1) < 1e-5;
        // Sway rests at its "from" pose at time 0 and swings to "to".
        float[] pose = Ribbon.sway(Ribbon.SWAY_1, 0);
        assert pose[0] == -0.6f && pose[1] == -6 && pose[2] == 4;
        pose = Ribbon.sway(Ribbon.SWAY_1, 34);
        assert Math.abs(pose[0] - .6f) < 1e-5 && Math.abs(pose[1] - 8) < 1e-4;
        // A pulse is one dash of 100 in 1000, sliding forward; it wraps at the end.
        assert Arrays.equals(Ribbon.pulseSpans(0, 0, 16), new float[] {0, 100});
        assert Arrays.equals(Ribbon.pulseSpans(4, 0, 16), new float[] {250, 350});
        float[] wrap = Ribbon.pulseSpans(15.2, 0, 16);
        assert wrap.length == 4 && wrap[0] == 0 && Math.abs(wrap[1] - 50) < .01 && Math.abs(wrap[2] - 950) < .01 && wrap[3] == 1000;

        // Geometry: cover fills the screen, centered, like "center / cover".
        float[] fit = Ribbon.cover(1280, 800);
        assert fit[0] == .64f && fit[1] == 0 && fit[2] == 0;
        fit = Ribbon.cover(1080, 1920);
        assert Math.abs(fit[0] - 1.536f) < 1e-5 && Math.abs(fit[1] - (1080 - 2000 * 1.536f) / 2) < 1e-2 && fit[2] == 0;
        // CSS linear-gradient angles: 180deg runs top to bottom, 90deg left to right.
        float[] down = Ribbon.cssLine(180, 100, 50);
        assert Math.abs(down[0] - 50) < 1e-3 && Math.abs(down[1]) < 1e-3 && Math.abs(down[3] - 50) < 1e-3;
        float[] right = Ribbon.cssLine(90, 100, 50);
        assert Math.abs(right[0]) < 1e-3 && Math.abs(right[2] - 100) < 1e-3 && Math.abs(right[1] - 25) < 1e-3;
        // "transparent" keeps the color and drops the alpha.
        assert Ribbon.clear(0x8CBEFFAA) == 0x00BEFFAA;
        assert Ribbon.LIGHT.glows.length == 2 && Ribbon.DARK.colors[0] == 0xFF02070D;

        // The start orb shows KS's Home Assistant icon, and its plain house on a KS without it.
        assert "mdi:home-assistant".equals(Aero.Taskbar.ORB_ICONS[0]) && "mdi:home".equals(Aero.Taskbar.ORB_ICONS[1]);

        // Older Kiosk Satellite: no voice skin events, nothing happens.
        Host older = new Host();
        older.older = true;
        VistaSkinPlugin old = new VistaSkinPlugin();
        old.start(older, map());
        assert older.reads.isEmpty() && older.overlays.isEmpty();

        System.out.println("VistaSkinTest passed");
    }
}
