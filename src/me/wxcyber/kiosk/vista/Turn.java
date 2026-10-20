// SPDX-License-Identifier: Apache-2.0
package me.wxcyber.kiosk.vista;

import android.graphics.Bitmap;

/**
 * Everything the views draw. The controller writes it on the plugin's
 * worker thread and the views read it on the main thread, so every field
 * is volatile and the views never write it.
 */
final class Turn {
    static final int LISTENING = 0;
    static final int THINKING = 1;
    static final int SPEAKING = 2;
    static final int DONE = 3;
    static final int ANNOUNCING = 4;

    /** Voice level from 0 to 1. */
    volatile float level;
    /** idle, listening, processing or responding. */
    volatile String state = "idle";
    volatile String command = "";
    volatile String answer = "";
    /** The latest tool line, or empty. */
    volatile String tool = "";
    volatile boolean announcement;

    /** Appearance settings. */
    volatile String theme = "auto";
    /** Wallpaper opacity, or null for the skin's own. */
    volatile Float background;
    volatile float textScale = 1;
    volatile boolean reactive = true;

    /** Plugin settings. */
    volatile String title = "Voice Assistant";
    volatile boolean showTaskbar = true;

    /** The chrome microphone, or null when the asset could not load. */
    volatile Bitmap mic;

    /** What the turn is doing, for the status line, the meter and the busy ring. */
    int phase() {
        if (announcement) return ANNOUNCING;
        String now = state;
        if ("listening".equals(now)) return LISTENING;
        if ("responding".equals(now)) return SPEAKING;
        if ("processing".equals(now)) return THINKING;
        boolean answered = !answer.isEmpty() || !tool.isEmpty();
        if (!command.isEmpty() && !answered) return THINKING;
        if (command.isEmpty() && !answered) return LISTENING;
        return DONE;
    }

    /** Theme picks the palette: Auto follows KS, Light and Dark force theirs. */
    boolean dark(boolean ksDark) {
        String pick = theme;
        return "dark".equals(pick) || (!"light".equals(pick) && ksDark);
    }

    /** The main instruction line: the answer, else a placeholder for the phase. */
    String headline() {
        String said = answer;
        if (!said.isEmpty()) return said;
        switch (phase()) {
            case LISTENING: return "Listening…";
            case THINKING: return "Working on it…";
            case ANNOUNCING: return "Announcement";
            default: return tool.isEmpty() ? "Done" : "";
        }
    }

    String status() {
        switch (phase()) {
            case LISTENING: return "Listening…";
            case THINKING: return "Asking Home Assistant";
            case SPEAKING: return "Speaking…";
            case ANNOUNCING: return "Announcement";
            default: return "Done";
        }
    }

    String meterLabel() {
        return phase() == THINKING ? "Thinking" : "Voice";
    }
}
