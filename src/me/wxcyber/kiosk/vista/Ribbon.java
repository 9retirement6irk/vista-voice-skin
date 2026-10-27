// SPDX-License-Identifier: Apache-2.0
package me.wxcyber.kiosk.vista;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The Vista Aero Home Assistant theme's wallpaper, drawn natively: the CSS
 * gradient stack for light or dark mode, with the animated ribbons SVG
 * (art/vista-ribbons.svg) over it. It covers the screen the way the theme's
 * "center / cover" does, and moves the way the SVG's CSS animations do: the
 * ribbons sway, light pulses travel along them and the rays and flares fade.
 * With animations turned off on the device it holds still, like the SVG
 * under prefers-reduced-motion.
 *
 * The timing and geometry helpers are plain Java so tests can check them.
 */
final class Ribbon {
    private Ribbon() {}

    // ------------------------------------------------------------ The data

    static final int STILL = 0, SWAY = 1, SWAY2 = 2;

    /** A skewed light ray: a rect filled top to bottom with gRay, fading in and out. */
    static final class Ray {
        final float x, y, w, h, skewDegrees, delay, duration;
        Ray(float x, float y, float w, float h, float skewDegrees, float delay, float duration) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.skewDegrees = skewDegrees; this.delay = delay; this.duration = duration;
        }
    }

    /** One ribbon line: M x y then cubic segments. A pulse draws one dash travelling along it. */
    static final class Stroke {
        final int group;
        /** A gradient id from the SVG, or null for a flat color. */
        final String gradient;
        final int color;
        final float width, opacity;
        final boolean pulse;
        final float delay, duration;
        final float[] d;
        Stroke(int group, String gradient, int color, float width, float opacity, boolean pulse, float delay, float duration, float[] d) {
            this.group = group; this.gradient = gradient; this.color = color; this.width = width; this.opacity = opacity;
            this.pulse = pulse; this.delay = delay; this.duration = duration; this.d = d;
        }
    }

    /** A bright elliptical flare, fading in and out. */
    static final class Flare {
        final float cx, cy, rx, ry, delay, duration;
        Flare(float cx, float cy, float rx, float ry, float delay, float duration) {
            this.cx = cx; this.cy = cy; this.rx = rx; this.ry = ry; this.delay = delay; this.duration = duration;
        }
    }

    // ------------------------------------------------------------ The CSS animations (from the SVG's <style>)

    static final int NORMAL = 0, ALTERNATE = 1, ALTERNATE_REVERSE = 2;

    /** .sway and .sway2: rotate(-0.6deg) translate(-6px, 4px) to rotate(0.6deg) translate(8px, -4px). */
    static final float[] SWAY_FROM = {-0.6f, -6, 4}, SWAY_TO = {0.6f, 8, -4};
    /** origin x, origin y, duration, delay, direction. */
    static final float[] SWAY_1 = {1300, 940, 34, 0, ALTERNATE};
    static final float[] SWAY_2 = {1300, 1000, 41, -9, ALTERNATE_REVERSE};
    /** .pulse: stroke-dasharray 100 900 on a pathLength of 1000, dashoffset 0 to -1000. */
    static final float PULSE_DASH = 100, PULSE_LENGTH = 1000;
    /** .ray and .flare: opacity 0.35 to 1. */
    static final float FADE_FROM = .35f, FADE_TO = 1;

    /**
     * Where a CSS animation is, from 0 to 1 before easing, at a time in
     * seconds. Negative delays start partway through, as in CSS.
     */
    static float progress(double seconds, float delay, float duration, int direction) {
        if (duration <= 0) return 0;
        double t = (seconds - delay) / duration;
        if (t < 0) return direction == ALTERNATE_REVERSE ? 1 : 0;
        long iteration = (long) Math.floor(t);
        float p = (float) (t - iteration);
        boolean odd = (iteration & 1) == 1;
        if (direction == ALTERNATE && odd) p = 1 - p;
        if (direction == ALTERNATE_REVERSE && !odd) p = 1 - p;
        return p;
    }

    /** CSS ease-in-out: cubic-bezier(0.42, 0, 0.58, 1). */
    static float easeInOut(float x) {
        return cubicBezier(.42f, 0, .58f, 1, x);
    }

    /** A CSS cubic-bezier timing function, solved for x by Newton's method with a bisection fallback. */
    static float cubicBezier(float x1, float y1, float x2, float y2, float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        float t = x;
        for (int i = 0; i < 8; i++) {
            float err = bezier(x1, x2, t) - x;
            if (Math.abs(err) < 1e-6f) return bezier(y1, y2, t);
            float slope = 3 * (1 - t) * (1 - t) * x1 + 6 * (1 - t) * t * (x2 - x1) + 3 * t * t * (1 - x2);
            if (Math.abs(slope) < 1e-6f) break;
            t -= err / slope;
        }
        float lo = 0, hi = 1;
        t = x;
        for (int i = 0; i < 40; i++) {
            float value = bezier(x1, x2, t);
            if (Math.abs(value - x) < 1e-6f) break;
            if (value < x) lo = t; else hi = t;
            t = (lo + hi) / 2;
        }
        return bezier(y1, y2, t);
    }

    private static float bezier(float p1, float p2, float t) {
        float u = 1 - t;
        return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t;
    }

    /** Opacity of a .ray or .flare at a time: ease-in-out, alternate. */
    static float fade(double seconds, float delay, float duration) {
        return FADE_FROM + (FADE_TO - FADE_FROM) * easeInOut(progress(seconds, delay, duration, ALTERNATE));
    }

    /** {degrees, tx, ty} of a sway group at a time. */
    static float[] sway(float[] spec, double seconds) {
        float e = easeInOut(progress(seconds, spec[3], spec[2], (int) spec[4]));
        return new float[] {
            SWAY_FROM[0] + (SWAY_TO[0] - SWAY_FROM[0]) * e,
            SWAY_FROM[1] + (SWAY_TO[1] - SWAY_FROM[1]) * e,
            SWAY_FROM[2] + (SWAY_TO[2] - SWAY_FROM[2]) * e,
        };
    }

    /**
     * The visible parts of a pulse's dash, in pathLength units (0 to 1000),
     * as {start, end} pairs; at most two when the dash wraps.
     */
    static float[] pulseSpans(double seconds, float delay, float duration) {
        float offset = -PULSE_LENGTH * progress(seconds, delay, duration, NORMAL);
        // Pattern position at distance s is (s + phase) mod 1000; the dash is visible below 100.
        float phase = ((offset % PULSE_LENGTH) + PULSE_LENGTH) % PULSE_LENGTH;
        float[] spans = new float[4];
        int n = 0;
        for (int k = 0; k <= 1; k++) {
            float a = k * PULSE_LENGTH - phase;
            float start = Math.max(a, 0), end = Math.min(a + PULSE_DASH, PULSE_LENGTH);
            if (end > start) { spans[n++] = start; spans[n++] = end; }
        }
        float[] out = new float[n];
        System.arraycopy(spans, 0, out, 0, n);
        return out;
    }

    /** {scale, dx, dy} that covers a w by h view with the SVG, centered: background-size cover. */
    static float[] cover(float w, float h) {
        float s = Math.max(w / RibbonData.VIEW_W, h / RibbonData.VIEW_H);
        return new float[] {s, (w - RibbonData.VIEW_W * s) / 2, (h - RibbonData.VIEW_H * s) / 2};
    }

    /** The start and end of a CSS linear-gradient(angle) line on a w by h box. */
    static float[] cssLine(float degrees, float w, float h) {
        double a = Math.toRadians(degrees);
        float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
        float half = (float) (Math.abs(w * Math.sin(a)) + Math.abs(h * Math.cos(a))) / 2;
        float cx = w / 2, cy = h / 2;
        return new float[] {cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half};
    }

    // ------------------------------------------------------------ The theme's CSS gradients

    /** A CSS radial-gradient(ellipse RX% RY% at CX% CY%, color, transparent 70%). */
    static final class Glow {
        final float cx, cy, rx, ry;
        final int color;
        Glow(float cx, float cy, float rx, float ry, int color) {
            this.cx = cx; this.cy = cy; this.rx = rx; this.ry = ry; this.color = color;
        }
    }

    /** The background-image under the SVG, bottom layer first. */
    static final class Ground {
        final float degrees;
        final float[] stops;
        final int[] colors;
        final Glow[] glows;
        Ground(float degrees, float[] stops, int[] colors, Glow... glows) {
            this.degrees = degrees; this.stops = stops; this.colors = colors; this.glows = glows;
        }
    }

    static final Ground LIGHT = new Ground(165,
        new float[] {0, .30f, .55f, .78f, 1},
        new int[] {0xFF0E5A8A, 0xFF1689A8, 0xFF1FA59A, 0xFF47B56A, 0xFFA8CF4A},
        new Glow(.15f, .10f, .60f, .50f, 0x8C1E5AAA),
        new Glow(.62f, .88f, .70f, .55f, 0x8CBEFFAA));

    static final Ground DARK = new Ground(170,
        new float[] {0, .45f, .75f, 1},
        new int[] {0xFF02070D, 0xFF041A26, 0xFF06303A, 0xFF0B4436},
        new Glow(.85f, .75f, .50f, .40f, 0x591E6EA0),
        new Glow(.62f, .90f, .65f, .45f, 0x7328A078));

    /** CSS "transparent" fades in premultiplied color: the same color at zero alpha, not black. */
    static int clear(int color) {
        return color & 0x00FFFFFF;
    }

    // ------------------------------------------------------------ Drawing

    /** Holds the shaders, paths and paints. Built on the main thread and reused every frame. */
    static final class Painter {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path[] paths = new Path[RibbonData.STROKES.length];
        private final PathMeasure[] measures = new PathMeasure[RibbonData.STROKES.length];
        private final float[] lengths = new float[RibbonData.STROKES.length];
        private final Shader[] strokeShaders = new Shader[RibbonData.STROKES.length];
        private final Shader[] rayShaders = new Shader[RibbonData.RAYS.length];
        private final Shader[] flareShaders = new Shader[RibbonData.FLARES.length];
        private final Path dash = new Path();
        private final RectF oval = new RectF();
        private Shader groundLine;
        private Shader[] groundGlows = new Shader[0];
        private int groundW = -1, groundH = -1;
        private Ground ground;

        Painter() {
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            for (int i = 0; i < paths.length; i++) {
                Stroke s = RibbonData.STROKES[i];
                Path p = new Path();
                p.moveTo(s.d[0], s.d[1]);
                for (int j = 2; j + 5 < s.d.length; j += 6) p.cubicTo(s.d[j], s.d[j + 1], s.d[j + 2], s.d[j + 3], s.d[j + 4], s.d[j + 5]);
                paths[i] = p;
                if (s.pulse) {
                    measures[i] = new PathMeasure(p, false);
                    lengths[i] = measures[i].getLength();
                }
                strokeShaders[i] = s.gradient == null ? null : strokeShader(s.gradient);
            }
            for (int i = 0; i < rayShaders.length; i++) {
                Ray r = RibbonData.RAYS[i];
                rayShaders[i] = new LinearGradient(0, r.y, 0, r.y + r.h, RibbonData.RAY_COLORS, RibbonData.RAY_STOPS, Shader.TileMode.CLAMP);
            }
            for (int i = 0; i < flareShaders.length; i++) {
                Flare f = RibbonData.FLARES[i];
                RadialGradient g = new RadialGradient(f.cx, f.cy, f.rx, RibbonData.FLARE_COLORS, RibbonData.FLARE_STOPS, Shader.TileMode.CLAMP);
                Matrix squash = new Matrix();
                squash.setScale(1, f.ry / f.rx, f.cx, f.cy);
                g.setLocalMatrix(squash);
                flareShaders[i] = g;
            }
        }

        private static Shader strokeShader(String id) {
            float[] l; float[] stops; int[] colors;
            switch (id) {
                case "gSheet": l = RibbonData.SHEET_LINE; stops = RibbonData.SHEET_STOPS; colors = RibbonData.SHEET_COLORS; break;
                case "gLime": l = RibbonData.LIME_LINE; stops = RibbonData.LIME_STOPS; colors = RibbonData.LIME_COLORS; break;
                case "gFeather": l = RibbonData.FEATHER_LINE; stops = RibbonData.FEATHER_STOPS; colors = RibbonData.FEATHER_COLORS; break;
                default: return null;
            }
            return new LinearGradient(l[0], l[1], l[2], l[3], colors, stops, Shader.TileMode.CLAMP);
        }

        private void groundFor(Ground g, int w, int h) {
            if (g == ground && w == groundW && h == groundH) return;
            ground = g; groundW = w; groundH = h;
            float[] l = cssLine(g.degrees, w, h);
            groundLine = new LinearGradient(l[0], l[1], l[2], l[3], g.colors, g.stops, Shader.TileMode.CLAMP);
            groundGlows = new Shader[g.glows.length];
            for (int i = 0; i < g.glows.length; i++) {
                Glow glow = g.glows[i];
                float cx = glow.cx * w, cy = glow.cy * h, rx = Math.max(1, glow.rx * w), ry = Math.max(1, glow.ry * h);
                RadialGradient r = new RadialGradient(cx, cy, rx, new int[] {glow.color, clear(glow.color)}, new float[] {0, .7f}, Shader.TileMode.CLAMP);
                Matrix squash = new Matrix();
                squash.setScale(1, ry / rx, cx, cy);
                r.setLocalMatrix(squash);
                groundGlows[i] = r;
            }
        }

        /** One frame. seconds is the animation clock; moving false draws the still picture. */
        void draw(Canvas canvas, int w, int h, boolean dark, double seconds, boolean moving) {
            groundFor(dark ? DARK : LIGHT, w, h);
            fill.setAlpha(255);
            fill.setShader(groundLine);
            canvas.drawRect(0, 0, w, h, fill);
            for (Shader glow : groundGlows) {
                fill.setShader(glow);
                canvas.drawRect(0, 0, w, h, fill);
            }
            fill.setShader(null);

            int save = canvas.save();
            float[] fit = cover(w, h);
            canvas.translate(fit[1], fit[2]);
            canvas.scale(fit[0], fit[0]);

            for (int i = 0; i < RibbonData.RAYS.length; i++) {
                Ray r = RibbonData.RAYS[i];
                fill.setShader(rayShaders[i]);
                fill.setAlpha(Math.round(255 * (moving ? fade(seconds, r.delay, r.duration) : 1)));
                int s = canvas.save();
                canvas.skew((float) Math.tan(Math.toRadians(r.skewDegrees)), 0);
                canvas.drawRect(r.x, r.y, r.x + r.w, r.y + r.h, fill);
                canvas.restoreToCount(s);
            }

            int current = -1;
            int groupSave = canvas.save();
            for (int i = 0; i < RibbonData.STROKES.length; i++) {
                Stroke s = RibbonData.STROKES[i];
                if (s.group != current) {
                    canvas.restoreToCount(groupSave);
                    groupSave = canvas.save();
                    current = s.group;
                    if (moving && s.group != STILL) {
                        float[] spec = s.group == SWAY ? SWAY_1 : SWAY_2;
                        float[] t = sway(spec, seconds);
                        canvas.translate(spec[0], spec[1]);
                        canvas.rotate(t[0]);
                        canvas.translate(t[1], t[2]);
                        canvas.translate(-spec[0], -spec[1]);
                    }
                }
                line.setStrokeWidth(s.width);
                if (strokeShaders[i] != null) {
                    line.setShader(strokeShaders[i]);
                    line.setColor(0xFF000000);
                } else {
                    line.setShader(null);
                    line.setColor(s.color);
                }
                line.setAlpha(Math.round(255 * s.opacity * (strokeShaders[i] != null ? 1 : (s.color >>> 24) / 255f)));
                if (!s.pulse) {
                    canvas.drawPath(paths[i], line);
                    continue;
                }
                // Still: the dash rests at the start of its line, as with the animation off.
                float[] spans = moving ? pulseSpans(seconds, s.delay, s.duration) : new float[] {0, PULSE_DASH};
                float unit = lengths[i] / PULSE_LENGTH;
                for (int k = 0; k + 1 < spans.length; k += 2) {
                    dash.rewind();
                    if (measures[i].getSegment(spans[k] * unit, spans[k + 1] * unit, dash, true)) canvas.drawPath(dash, line);
                }
            }
            canvas.restoreToCount(groupSave);
            line.setShader(null);

            for (int i = 0; i < RibbonData.FLARES.length; i++) {
                Flare f = RibbonData.FLARES[i];
                fill.setShader(flareShaders[i]);
                fill.setAlpha(Math.round(255 * (moving ? fade(seconds, f.delay, f.duration) : 1)));
                oval.set(f.cx - f.rx, f.cy - f.ry, f.cx + f.rx, f.cy + f.ry);
                canvas.drawOval(oval, fill);
            }
            fill.setShader(null);
            fill.setAlpha(255);
            canvas.restoreToCount(save);
        }
    }
}
