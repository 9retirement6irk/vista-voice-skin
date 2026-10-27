// SPDX-License-Identifier: Apache-2.0
package me.wxcyber.kiosk.vista;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.util.Map;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Vista Voice Skin. Pick it under Voice Satellite > Appearance > Skin and
 * it draws each voice turn as an Aero glass window. It has no actions of
 * its own and sends nothing anywhere.
 */
public final class VistaSkinPlugin implements KioskPlugin {
    private final SkinController skin = new SkinController();

    @Override public void start(PluginHost host, Map<String, Object> settings) {
        configure(settings);
        skin.turn.mic = loadMic(host);
        skin.turn.wallpaperLight = loadBytes(host, "wallpaper-light.jpg", "Light wallpaper");
        skin.turn.wallpaperDark = loadBytes(host, "wallpaper-dark.jpg", "Dark wallpaper");
        skin.start(host);
    }

    /** An asset's raw bytes, or null so the skin falls back to drawing its own. */
    static byte[] loadBytes(PluginHost host, String name, String what) {
        try (InputStream in = host.openAsset(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            for (int read; (read = in.read(buffer)) != -1; ) out.write(buffer, 0, read);
            return out.size() == 0 ? null : out.toByteArray();
        } catch (Exception unavailable) {
            host.log(what + " unavailable: " + unavailable.getMessage());
            return null;
        }
    }

    /** The chrome microphone. Without it the skin draws a plain one. */
    static Bitmap loadMic(PluginHost host) {
        try (InputStream in = host.openAsset("mic.png")) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception unavailable) {
            host.log("Microphone image unavailable: " + unavailable.getMessage());
            return null;
        }
    }

    @Override public void configure(Map<String, Object> settings) {
        Object title = settings == null ? null : settings.get("title");
        Object taskbar = settings == null ? null : settings.get("showTaskbar");
        skin.configure(title instanceof String ? (String) title : null, !Boolean.FALSE.equals(taskbar));
    }

    @Override public void execute(String command, Map<String, Object> arguments) {
        throw new IllegalArgumentException("Vista Voice Skin has no actions");
    }

    @Override public void onEvent(String event, Map<String, Object> payload) {
        skin.onEvent(event, payload);
    }

    @Override public void stop() {
        skin.stop();
    }

    /** For tests. */
    SkinController skin() { return skin; }
}
