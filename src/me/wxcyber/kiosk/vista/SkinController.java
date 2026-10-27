// SPDX-License-Identifier: Apache-2.0
package me.wxcyber.kiosk.vista;

import android.content.Context;
import android.view.View;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import me.wxcyber.kiosk.vista.SkinViews.SkinView;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Follows Voice Satellite and shows the skin while it is picked and the
 * overlay is up: the full screen look or the docked window, whichever the
 * Overlay mode names. Events arrive on the plugin's worker thread; views
 * hear about changes through post().
 */
final class SkinController {
    static final String KEY = "vista_skin";
    /** Where the docked window sits above the bottom edge, in dp. */
    static final int DOCK_INSET = 24;
    static final String[] EVENTS = {"voice.skin", "voice.overlay", "voice.level", "voice.text", "voice.appearance", "voice.state"};

    final Turn turn = new Turn();
    private PluginHost host;
    private int generation;
    private boolean selected;
    /** Events are newer than the reads, so a heard event wins. */
    private boolean skinHeard;
    private boolean stateHeard;
    private boolean appearanceHeard;
    private boolean textHeard;
    private String overlay = "hidden";
    /** null, "full" or "docked". */
    private String drawn;
    /** The showing view. Written on the main thread. */
    private volatile SkinView view;

    synchronized void start(PluginHost host) {
        this.host = host;
        int current = ++generation;
        try {
            for (String event : EVENTS) host.subscribe(event);
        } catch (IllegalArgumentException | UnsupportedOperationException olderHost) {
            // Kiosk Satellite versions without voice skins never list this one.
            return;
        }
        try {
            read(current);
        } catch (UnsupportedOperationException olderHost) {
            // Events alone still bring the skin up.
        }
    }

    private void read(int current) {
        host.executeCommand("getVoiceState", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (SkinController.this) {
                if (current != generation || !ok || !(data instanceof Map)) return;
                Map<?, ?> state = (Map<?, ?>) data;
                if (!skinHeard) selected = Boolean.TRUE.equals(state.get("skinSelected"));
                if (!stateHeard && state.get("state") instanceof String) turn.state = (String) state.get("state");
                if ("hidden".equals(overlay) && state.get("overlay") instanceof String) overlay = (String) state.get("overlay");
                update();
                refresh();
            }
        });
        host.executeCommand("getVoiceAppearance", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (SkinController.this) {
                if (current == generation && ok && data instanceof Map && !appearanceHeard) appearance((Map<?, ?>) data);
            }
        });
        host.executeCommand("getVoiceText", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (SkinController.this) {
                if (current == generation && ok && data instanceof Map && !textHeard) words((Map<?, ?>) data);
            }
        });
    }

    synchronized void onEvent(String event, Map<String, Object> payload) {
        if (host == null || payload == null) return;
        switch (event) {
            case "ks.voice.skin":
                selected = Boolean.TRUE.equals(payload.get("selected"));
                skinHeard = true;
                update();
                break;
            case "ks.voice.overlay":
                overlay = String.valueOf(payload.get("overlay"));
                update();
                break;
            case "ks.voice.level":
                Object value = payload.get("level");
                turn.level = value instanceof Number ? clamp(((Number) value).floatValue()) : 0;
                break;
            case "ks.voice.state":
                if (payload.get("state") instanceof String) turn.state = (String) payload.get("state");
                stateHeard = true;
                refresh();
                break;
            case "ks.voice.text":
                textHeard = true;
                words(payload);
                break;
            case "ks.voice.appearance":
                appearanceHeard = true;
                appearance(payload);
                break;
            default:
                break;
        }
    }

    /** The plugin's own settings. */
    void configure(String title, boolean showTaskbar) {
        turn.title = title == null || title.trim().isEmpty() ? "Voice Assistant" : title.trim();
        turn.showTaskbar = showTaskbar;
        restyle();
    }

    synchronized String drawn() { return drawn; }

    private static float clamp(float value) {
        return Float.isNaN(value) ? 0 : Math.max(0, Math.min(1, value));
    }

    private void appearance(Map<?, ?> settings) {
        turn.theme = settings.get("theme") instanceof String ? (String) settings.get("theme") : "auto";
        turn.background = settings.get("background") instanceof Number ? clamp(((Number) settings.get("background")).floatValue()) : null;
        float scale = settings.get("textScale") instanceof Number ? ((Number) settings.get("textScale")).floatValue() : 1;
        turn.textScale = Float.isNaN(scale) ? 1 : Math.max(0.5f, Math.min(2, scale));
        turn.reactive = !Boolean.FALSE.equals(settings.get("reactive"));
        restyle();
    }

    private void words(Map<?, ?> text) {
        turn.command = text.get("command") instanceof String ? (String) text.get("command") : "";
        turn.answer = text.get("answer") instanceof String ? (String) text.get("answer") : "";
        Object tools = text.get("tools");
        turn.tool = tools instanceof List && !((List<?>) tools).isEmpty() ? String.valueOf(((List<?>) tools).get(((List<?>) tools).size() - 1)) : "";
        turn.announcement = Boolean.TRUE.equals(text.get("announcement"));
        refresh();
    }

    private void refresh() {
        SkinView current = view;
        if (current != null) current.asView().post(current::refresh);
    }

    private void restyle() {
        SkinView current = view;
        if (current != null) current.asView().post(current::restyle);
    }

    /** Shows the look the overlay mode names while the skin is picked, and nothing otherwise. */
    private void update() {
        String want = selected && ("full".equals(overlay) || "docked".equals(overlay)) ? overlay : null;
        if (want == null ? drawn == null : want.equals(drawn)) return;
        drawn = want;
        if (want == null) {
            host.hideOverlay(KEY);
            turn.level = 0;
        } else if ("full".equals(want)) {
            host.showOverlay(KEY, animated(OverlaySpec.fullScreen().onTop(true).touchable(false).closeOnBack(false), OverlaySpec.FADE, 300), new Factory(false));
        } else {
            host.showOverlay(KEY, animated(OverlaySpec.at(OverlaySpec.BOTTOM).inset(DOCK_INSET).onTop(true).touchable(false).closeOnBack(false), OverlaySpec.SLIDE, 250), new Factory(true));
        }
    }

    private static OverlaySpec animated(OverlaySpec spec, String transition, int ms) {
        try {
            return spec.enter(transition).exit(transition).transitionDuration(ms);
        } catch (LinkageError olderHost) {
            return spec;
        }
    }

    synchronized void stop() {
        host = null;
        generation++;
        selected = skinHeard = stateHeard = appearanceHeard = textHeard = false;
        overlay = "hidden";
        drawn = null;
        turn.level = 0;
        turn.state = "idle";
        turn.command = turn.answer = turn.tool = "";
        turn.announcement = false;
        turn.theme = "auto";
        turn.background = null;
        turn.textScale = 1;
        turn.reactive = true;
    }

    private final class Factory implements OverlayFactory {
        private final boolean docked;

        Factory(boolean docked) { this.docked = docked; }

        @Override public View create(Context context, KsTheme theme) {
            SkinView skin = docked ? new SkinViews.Docked(context, theme, turn) : new SkinViews.Full(context, theme, turn);
            view = skin;
            return skin.asView();
        }

        /**
         * KS switched between light and dark, or changed its colors. In Auto
         * the skin follows it: glass, client area, text, meter and wallpaper
         * all swap. KS may hand back the view it wraps ours in, so fall back
         * to the one this factory built.
         */
        @Override public void onThemeChanged(View changed, KsTheme theme) {
            SkinView target = changed instanceof SkinView ? (SkinView) changed : view;
            if (target != null && theme != null) target.restyle(theme);
        }

        @Override public void onDestroy(View destroyed) {
            if (view != null && view.asView() == destroyed) view = null;
        }
    }
}
