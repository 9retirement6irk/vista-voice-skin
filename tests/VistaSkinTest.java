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
        /** Package assets; null is a host without the asset API. */
        Map<String, byte[]> assets;
        public java.io.InputStream openAsset(String name) throws java.io.IOException {
            if (assets == null) throw new UnsupportedOperationException("Plugin assets are unavailable");
            byte[] data = assets.get(name);
            if (data == null) throw new java.io.FileNotFoundException(name);
            return new java.io.ByteArrayInputStream(data);
        }
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

        // The Home Assistant theme's wallpapers load from the assets; light and dark are kept apart.
        assert turn.wallpaperLight == null && turn.wallpaperDark == null;
        Host withAssets = new Host();
        withAssets.assets = new HashMap<>();
        withAssets.assets.put("wallpaper-light.jpg", new byte[] {1, 2, 3});
        withAssets.assets.put("wallpaper-dark.jpg", new byte[] {4, 5});
        VistaSkinPlugin themed = new VistaSkinPlugin();
        themed.start(withAssets, map());
        Turn walls = themed.skin().turn;
        assert Arrays.equals(walls.wallpaper(false), new byte[] {1, 2, 3});
        assert Arrays.equals(walls.wallpaper(true), new byte[] {4, 5});
        assert walls.mic == null;

        // The photo is decoded no smaller than the screen and cropped to cover it, like CSS cover.
        assert Aero.Wallpaper.sampleSize(1920, 1200, 1920, 1200) == 1;
        assert Aero.Wallpaper.sampleSize(1920, 1200, 960, 600) == 2;
        assert Aero.Wallpaper.sampleSize(1920, 1200, 959, 400) == 2;
        assert Aero.Wallpaper.sampleSize(1920, 1200, 400, 250) == 4;
        assert Aero.Wallpaper.sampleSize(1920, 1200, 2560, 1600) == 1;
        assert Aero.Wallpaper.sampleSize(1920, 1200, 0, 0) == 1;
        // A 16:10 picture on a 16:9 screen loses a little top and bottom.
        assert Arrays.equals(Aero.Wallpaper.coverCrop(1920, 1200, 1920, 1080), new int[] {0, 60, 1920, 1140});
        // On a portrait phone it keeps the middle.
        assert Arrays.equals(Aero.Wallpaper.coverCrop(1920, 1200, 1080, 1920), new int[] {622, 0, 1297, 1200});
        assert Arrays.equals(Aero.Wallpaper.coverCrop(1920, 1200, 1920, 1200), new int[] {0, 0, 1920, 1200});

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
