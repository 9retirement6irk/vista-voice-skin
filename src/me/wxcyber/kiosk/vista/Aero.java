// SPDX-License-Identifier: Apache-2.0
package me.wxcyber.kiosk.vista;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import java.util.Date;
import me.jxl.kiosk.plugins.KsTheme;

/** The Vista pieces: glass, gloss, wallpaper, microphone, progress bar and taskbar. */
final class Aero {
    private Aero() {}

    static final Shader.TileMode CLAMP = Shader.TileMode.CLAMP;

    static int argb(float alpha, int rgb) {
        return Color.argb(Math.round(255 * Math.max(0, Math.min(1, alpha))), Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    /** A vertical gradient with a hard step in the middle: Vista's two-tone gloss. */
    static Shader gloss(float top, float bottom, int[] colors, float[] stops) {
        return new LinearGradient(0, top, 0, bottom, colors, stops, CLAMP);
    }

    // ---------------------------------------------------------------- Gloss panel

    /** A glossy fill with a border: the dialog footer and dark command bars. */
    static final class GlossDrawable extends Drawable {
        private final int[] colors;
        private final float[] stops;
        private final int border;
        private final int highlight;
        private final float radius;
        private final boolean topEdgeOnly;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private int shadedHeight = -1;
        private int shadedTop;

        GlossDrawable(int[] colors, float[] stops, float radius, int border, int highlight, boolean topEdgeOnly) {
            this.colors = colors;
            this.stops = stops;
            this.radius = radius;
            this.border = border;
            this.highlight = highlight;
            this.topEdgeOnly = topEdgeOnly;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(1);
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            if (b.height() != shadedHeight || b.top != shadedTop) {
                fill.setShader(gloss(b.top, b.bottom, colors, stops));
                shadedHeight = b.height();
                shadedTop = b.top;
            }
            if (topEdgeOnly) {
                canvas.drawRect(b, fill);
                if (border != 0) { line.setColor(border); canvas.drawLine(b.left, b.top + .5f, b.right, b.top + .5f, line); }
                if (highlight != 0) { line.setColor(highlight); canvas.drawLine(b.left, b.top + 1.5f, b.right, b.top + 1.5f, line); }
                return;
            }
            box.set(b);
            canvas.drawRoundRect(box, radius, radius, fill);
            if (highlight != 0) {
                line.setColor(highlight);
                box.set(b.left + 1.5f, b.top + 1.5f, b.right - 1.5f, b.bottom - 1.5f);
                canvas.drawRoundRect(box, Math.max(0, radius - 1), Math.max(0, radius - 1), line);
            }
            if (border != 0) {
                line.setColor(border);
                box.set(b.left + .5f, b.top + .5f, b.right - .5f, b.bottom - .5f);
                canvas.drawRoundRect(box, radius, radius, line);
            }
        }

        @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---------------------------------------------------------------- Glass window

    /**
     * An Aero glass window. The frame, title text, icon and caption buttons
     * are drawn here; the client area is the child view.
     */
    static final class GlassWindow extends LinearLayout {
        private final Turn turn;
        private final boolean docked;
        private KsTheme theme;
        private boolean dark;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bitmap = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF box = new RectF();
        private final RectF cap = new RectF();
        private final Rect dst = new Rect();
        private final Path frame = new Path();

        GlassWindow(Context context, Turn turn, boolean docked) {
            super(context);
            this.turn = turn;
            this.docked = docked;
            setOrientation(VERTICAL);
            setWillNotDraw(false);
            stroke.setStyle(Paint.Style.STROKE);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius());
                }
            });
        }

        int titleHeight() { return theme.px(docked ? 28 : 38); }
        int frameWidth() { return theme.px(docked ? 6 : 8); }
        float radius() { return theme == null ? 0 : theme.px(docked ? 7 : 8); }

        void restyle(KsTheme theme, boolean dark) {
            this.theme = theme;
            this.dark = dark;
            int edge = frameWidth();
            setPadding(edge, titleHeight(), edge, edge);
            setElevation(theme.px(docked ? 10 : 18));
            text.setTypeface(theme.typeface(400));
            invalidateOutline();
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (theme == null) return;
            float w = getWidth(), h = getHeight(), r = radius();
            frame.reset();
            box.set(0, 0, w, h);
            frame.addRoundRect(box, r, r, Path.Direction.CW);

            // The glass: lighter at the top, more see-through below. Docked it
            // sits over a busy dashboard, so it is more opaque.
            int top, mid, bottom;
            if (dark) {
                float a = docked ? .9f : .72f;
                top = argb(a, 0x46505C); mid = argb(a - .02f, 0x1E242C); bottom = argb(a, 0x10141A);
            } else if (docked) {
                top = argb(.94f, 0xB4D4EE); mid = argb(.9f, 0x98BCDE); bottom = argb(.9f, 0x7AA6CF);
            } else {
                top = argb(.62f, 0xBEDAF0); mid = argb(.5f, 0x8CB4D6); bottom = argb(.45f, 0x6E9BC3);
            }
            paint.setShader(new LinearGradient(0, 0, 0, h, new int[] {top, mid, bottom}, new float[] {0, .3f, 1}, CLAMP));
            canvas.drawPath(frame, paint);
            paint.setShader(null);

            // Glass streaks across the title bar.
            int save = canvas.save();
            canvas.clipPath(frame);
            canvas.clipRect(0, 0, w, titleHeight() + frameWidth());
            float step = theme.px(docked ? 140 : 170);
            float rad = (float) Math.toRadians(118 - 90);
            float dx = (float) Math.cos(rad) * step, dy = (float) Math.sin(rad) * step;
            int streak = argb(dark ? .07f : .14f, Color.WHITE);
            paint.setShader(new LinearGradient(0, 0, dx, dy, new int[] {0, 0, streak, 0, 0}, new float[] {0, .35f, .38f, .53f, 1}, Shader.TileMode.REPEAT));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            canvas.restoreToCount(save);

            // Borders: dark outside, a light hairline inside.
            stroke.setStrokeWidth(1);
            stroke.setColor(dark ? 0x48FFFFFF : 0x80FFFFFF);
            box.set(1.5f, 1.5f, w - 1.5f, h - 1.5f);
            canvas.drawRoundRect(box, r - 1, r - 1, stroke);
            stroke.setColor(dark ? 0xFF000000 : 0xB3000000);
            box.set(.5f, .5f, w - .5f, h - .5f);
            canvas.drawRoundRect(box, r, r, stroke);

            drawTitle(canvas, w);
            drawCaptions(canvas, w);
        }

        private void drawTitle(Canvas canvas, float w) {
            int th = titleHeight();
            float x = frameWidth() - theme.px(2);
            int icon = theme.px(docked ? 16 : 20);
            int iconTop = Math.round((th - icon) / 2f);
            drawMicIcon(canvas, turn.mic, Math.round(x), iconTop, icon, bitmap, paint);
            x += icon + theme.px(docked ? 6 : 8);
            float size = theme.px(docked ? 13 : 15) * Math.max(.75f, Math.min(1.4f, turn.textScale));
            text.setTextSize(size);
            Paint.FontMetrics m = text.getFontMetrics();
            float baseline = th / 2f - (m.ascent + m.descent) / 2f;
            float limit = w - x - theme.px(docked ? 52 : 112);
            String title = ellipsize(turn.title, limit);
            // Vista's title text: black with a white glow on light glass.
            text.setColor(dark ? Color.WHITE : Color.BLACK);
            text.setShadowLayer(theme.px(6), 0, 0, dark ? Color.BLACK : Color.WHITE);
            canvas.drawText(title, x, baseline, text);
            canvas.drawText(title, x, baseline, text);
            text.clearShadowLayer();
        }

        private String ellipsize(String value, float limit) {
            if (limit <= 0) return "";
            if (text.measureText(value) <= limit) return value;
            int count = text.breakText(value, true, limit - text.measureText("…"), null);
            return value.substring(0, Math.max(0, count)) + "…";
        }

        private void drawCaptions(Canvas canvas, float w) {
            float hgt = theme.px(docked ? 17 : 19);
            float right = w - theme.px(docked ? 1 : 2);
            float closeW = theme.px(docked ? 40 : 46);
            cap.set(right - closeW, -1, right, hgt);
            drawCaption(canvas, cap, true, docked ? 3 : 1);
            drawCross(canvas, cap);
            if (docked) return;
            float glassW = theme.px(27);
            cap.set(right - closeW - glassW, -1, right - closeW, hgt);
            drawCaption(canvas, cap, false, 0);
            drawMaximize(canvas, cap);
            cap.set(right - closeW - glassW * 2, -1, right - closeW - glassW, hgt);
            drawCaption(canvas, cap, false, 2);
            drawMinimize(canvas, cap);
        }

        /** corners: 1 bottom-right, 2 bottom-left, 3 both, 0 none. */
        private void drawCaption(Canvas canvas, RectF r, boolean close, int corners) {
            int[] colors = close
                ? new int[] {0xFFEAA99A, 0xFFD47A68, 0xFFC0432F, 0xFFD86A4F}
                : dark ? new int[] {0x4DFFFFFF, 0x1FFFFFFF, 0x00FFFFFF, 0x1FFFFFFF}
                : new int[] {0x8CFFFFFF, 0x40FFFFFF, 0x0DFFFFFF, 0x4DFFFFFF};
            paint.setShader(gloss(r.top, r.bottom, colors, new float[] {0, .45f, .5f, 1}));
            float rad = theme.px(5);
            Path shape = captionPath(r, corners, rad);
            canvas.drawPath(shape, paint);
            paint.setShader(null);
            stroke.setColor(argb(dark ? .3f : .45f, Color.WHITE));
            RectF inner = new RectF(r.left + 1.5f, r.top + 1.5f, r.right - 1.5f, r.bottom - 1.5f);
            canvas.drawPath(captionPath(inner, corners, rad - 1), stroke);
            stroke.setColor(dark ? 0xCC000000 : 0x8C000000);
            RectF outer = new RectF(r.left + .5f, r.top + .5f, r.right - .5f, r.bottom - .5f);
            canvas.drawPath(captionPath(outer, corners, rad), stroke);
        }

        private Path captionPath(RectF r, int corners, float rad) {
            float bl = (corners & 2) != 0 ? rad : 0, br = (corners & 1) != 0 ? rad : 0;
            Path path = new Path();
            path.addRoundRect(r, new float[] {0, 0, 0, 0, br, br, bl, bl}, Path.Direction.CW);
            return path;
        }

        private void drawCross(Canvas canvas, RectF r) {
            float cx = r.centerX(), cy = r.centerY(), s = theme.px(docked ? 3.5f : 4.5f);
            stroke.setStrokeCap(Paint.Cap.SQUARE);
            stroke.setColor(0xFF3A0A00);
            stroke.setStrokeWidth(theme.px(docked ? 3.4f : 4));
            canvas.drawLine(cx - s, cy - s, cx + s, cy + s, stroke);
            canvas.drawLine(cx + s, cy - s, cx - s, cy + s, stroke);
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(theme.px(docked ? 1.8f : 2));
            canvas.drawLine(cx - s, cy - s, cx + s, cy + s, stroke);
            canvas.drawLine(cx + s, cy - s, cx - s, cy + s, stroke);
            stroke.setStrokeWidth(1);
            stroke.setStrokeCap(Paint.Cap.BUTT);
        }

        private void drawMinimize(Canvas canvas, RectF r) {
            float w = theme.px(9), h = theme.px(3);
            float left = r.centerX() - w / 2, top = r.centerY() + theme.px(2);
            paint.setColor(0xFF333333);
            canvas.drawRect(left - 1, top - 1, left + w + 1, top + h + 1, paint);
            paint.setColor(Color.WHITE);
            canvas.drawRect(left, top, left + w, top + h, paint);
        }

        private void drawMaximize(Canvas canvas, RectF r) {
            float w = theme.px(10), h = theme.px(8);
            float left = r.centerX() - w / 2, top = r.centerY() - h / 2;
            paint.setColor(0xFF333333);
            canvas.drawRect(left - 1, top - 1, left + w + 1, top + h + 1, paint);
            paint.setColor(Color.WHITE);
            canvas.drawRect(left, top, left + w, top + h, paint);
            // A window outline: thick white top, thin white sides, the glass showing through.
            float side = Math.max(1, theme.px(1.5f)), lid = theme.px(3);
            paint.setColor(0xFF333333);
            canvas.drawRect(left + side - 1, top + lid - 1, left + w - side + 1, top + h - side + 1, paint);
            paint.setColor(dark ? 0xFF3A4450 : 0xFF8FB2D2);
            canvas.drawRect(left + side, top + lid, left + w - side, top + h - side, paint);
        }
    }

    /** The microphone image, or a plain drawn one without it. */
    static void drawMicIcon(Canvas canvas, Bitmap mic, int left, int top, int size, Paint bitmapPaint, Paint scratch) {
        if (mic != null) {
            Rect dst = new Rect(left, top, left + size, top + size);
            canvas.drawBitmap(mic, null, dst, bitmapPaint);
            return;
        }
        float w = size * .42f, h = size * .62f;
        float cx = left + size / 2f;
        RectF head = new RectF(cx - w / 2, top + size * .04f, cx + w / 2, top + size * .04f + h);
        scratch.setShader(new LinearGradient(head.left, 0, head.right, 0, new int[] {0xFF9AA3AD, 0xFFFFFFFF, 0xFFB8C0C8, 0xFF6E7781}, new float[] {0, .35f, .7f, 1}, CLAMP));
        canvas.drawRoundRect(head, w / 2, w / 2, scratch);
        scratch.setShader(null);
        scratch.setColor(0xFF6E7781);
        canvas.drawRect(cx - size * .04f, head.bottom, cx + size * .04f, top + size * .9f, scratch);
        canvas.drawRect(cx - size * .18f, top + size * .88f, cx + size * .18f, top + size * .96f, scratch);
    }

    // ---------------------------------------------------------------- Microphone

    /**
     * The chrome microphone with a blue glow that swells with the voice.
     * While the assistant thinks it shrinks a little inside a turning
     * Vista busy ring.
     */
    static final class MicView extends View {
        private final Turn turn;
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint scratch = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix spin = new Matrix();
        private final RectF arc = new RectF();
        private boolean dark;
        private float shown;
        private float thinking;

        MicView(Context context, Turn turn) {
            super(context);
            this.turn = turn;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
            shadow.setColorFilter(new PorterDuffColorFilter(0x40001E50, PorterDuff.Mode.SRC_IN));
        }

        void restyle(boolean dark) {
            this.dark = dark;
            image.setColorFilter(dark ? new LightingColorFilter(0xFFD4D4D4, 0) : null);
            onSizeChanged(getWidth(), getHeight(), 0, 0);
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            if (w <= 0 || h <= 0) return;
            float r = Math.min(w, h) / 2f;
            int inner = dark ? argb(.3f, 0x3CAAFF) : argb(.5f, 0x50C8FF);
            glow.setShader(new RadialGradient(w / 2f, h / 2f, r, new int[] {inner, inner, 0}, new float[] {0, .45f, 1}, CLAMP));
            ring.setShader(new SweepGradient(w / 2f, h / 2f, new int[] {0x003FB0FF, 0x003FB0FF, 0xFF3FB0FF, 0xFFE6F7FF, 0x003FB0FF}, new float[] {0, .42f, .78f, .97f, 1}));
        }

        @Override protected void onDraw(Canvas canvas) {
            int phase = turn.phase();
            boolean busy = phase == Turn.THINKING;
            float target = turn.reactive ? turn.level : (phase == Turn.LISTENING || phase == Turn.SPEAKING ? .5f : 0);
            shown += (target - shown) * .25f;
            thinking += ((busy ? 1 : 0) - thinking) * .15f;
            float w = getWidth(), h = getHeight(), size = Math.min(w, h), cx = w / 2f, cy = h / 2f;
            long now = SystemClock.uptimeMillis();

            // The glow: breathes at rest, swells with the voice.
            float breath = (float) (.5 - .5 * Math.cos((now % 3200) / 3200.0 * Math.PI * 2));
            float scale = .78f + .06f * breath + .32f * shown;
            int save = canvas.save();
            canvas.scale(scale, scale, cx, cy);
            canvas.drawCircle(cx, cy, size / 2f, glow);
            canvas.restoreToCount(save);

            // The busy ring.
            if (thinking > .02f) {
                float r = size * .46f;
                ring.setStrokeWidth(size * .045f);
                ring.setAlpha(Math.round(255 * thinking));
                spin.setRotate((now % 1100) / 1100f * 360, cx, cy);
                ring.getShader().setLocalMatrix(spin);
                arc.set(cx - r, cy - r, cx + r, cy + r);
                canvas.drawOval(arc, ring);
            }

            // The microphone, a soft shadow under it.
            float micSize = size * (.92f - .16f * thinking);
            int left = Math.round(cx - micSize / 2), top = Math.round(cy - micSize / 2), s = Math.round(micSize);
            Bitmap mic = turn.mic;
            if (mic != null) {
                canvas.drawBitmap(mic, null, new Rect(left, top + Math.round(size * .025f), left + s, top + s + Math.round(size * .025f)), shadow);
                canvas.drawBitmap(mic, null, new Rect(left, top, left + s, top + s), image);
            } else {
                drawMicIcon(canvas, null, left, top, s, image, scratch);
            }
            postInvalidateOnAnimation();
        }
    }

    // ---------------------------------------------------------------- Progress bar

    /**
     * The Vista progress bar: a green gloss fill with a light sweeping
     * across it. It follows the voice, runs a marquee while the assistant
     * thinks and fills up when the turn is done.
     */
    static final class MeterView extends View {
        private final Turn turn;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sweep = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Path clip = new Path();
        private boolean dark;
        private float shown;
        private float density = 1;

        MeterView(Context context, Turn turn) {
            super(context);
            this.turn = turn;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(1);
            shine.setColor(0x1FFFFFFF);
        }

        void restyle(KsTheme theme, boolean dark) {
            this.dark = dark;
            this.density = theme.px(100) / 100f;
            onSizeChanged(getWidth(), getHeight(), 0, 0);
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            if (w <= 0 || h <= 0) return;
            float[] stops = {0, .5f, .51f, 1};
            track.setShader(gloss(0, h, dark ? new int[] {0xFF3A3F46, 0xFF22262B, 0xFF16191D, 0xFF2A2E34} : new int[] {0xFFF3F3F3, 0xFFDCDCDC, 0xFFCFCFCF, 0xFFE6E6E6}, stops));
            fill.setShader(gloss(0, h, new int[] {0xFFD9FBD0, 0xFF79DD5F, 0xFF3EC52A, 0xFF5FD848, 0xFF8DE57A}, new float[] {0, .45f, .5f, .8f, 1}));
            float r = Math.min(3 * density, h / 2f);
            clip.reset();
            box.set(1, 1, w - 1, h - 1);
            clip.addRoundRect(box, r, r, Path.Direction.CW);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            int phase = turn.phase();
            long now = SystemClock.uptimeMillis();
            float r = Math.min(3 * density, h / 2f);

            box.set(.5f, .5f, w - .5f, h - .5f);
            canvas.drawRoundRect(box, r, r, track);
            int save = canvas.save();
            canvas.clipPath(clip);

            if (phase == Turn.THINKING) {
                // Marquee: a green block glides across, Vista's "please wait".
                float block = w * .3f;
                float x = -block + (now % 1800) / 1800f * (w + block);
                sweep.setShader(new LinearGradient(x, 0, x + block, 0, new int[] {0x003EC52A, 0xFF5FD848, 0xFF3EC52A, 0xFF5FD848, 0x003EC52A}, new float[] {0, .3f, .5f, .7f, 1}, CLAMP));
                canvas.drawRect(x, 0, x + block, h, sweep);
                shown = 0;
            } else {
                float target;
                if (phase == Turn.DONE || phase == Turn.ANNOUNCING) target = 1;
                else if (!turn.reactive) target = .5f;
                else target = Math.min(1, turn.level * 1.25f);
                shown += (target - shown) * .25f;
                float edge = Math.max(0, Math.min(w, w * shown));
                canvas.drawRect(0, 0, edge, h, fill);
                if (edge > 2) {
                    // The light that sweeps along a Vista bar.
                    float band = 70 * density;
                    float x = -band + (now % 2600) / 2600f * (edge + band * 2);
                    sweep.setShader(new LinearGradient(x, 0, x + band, 0, new int[] {0x00FFFFFF, 0x8CFFFFFF, 0x00FFFFFF}, null, CLAMP));
                    canvas.save();
                    canvas.clipRect(0, 0, edge, h);
                    canvas.drawRect(x, 0, x + band, h, sweep);
                    canvas.restore();
                    line.setColor(0xFF2E9A1C);
                    canvas.drawLine(edge - .5f, 0, edge - .5f, h, line);
                }
            }
            if (dark) canvas.drawRect(0, 0, w, h / 2f, shine);
            canvas.restoreToCount(save);

            line.setColor(dark ? 0xFF000000 : 0xFFA8A8A8);
            canvas.drawRoundRect(box, r, r, line);
            postInvalidateOnAnimation();
        }
    }

    // ---------------------------------------------------------------- Check mark

    /** A green round check, shown beside the action the assistant took. */
    static final class CheckView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tick = new Path();

        CheckView(Context context) { super(context); }

        @Override protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight()), r = s / 2f - 1, c = s / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(c, s * .3f, s * .8f, new int[] {0xFFD8FFC4, 0xFF4FBF2A, 0xFF22790F}, new float[] {0, .55f, 1}, CLAMP));
            canvas.drawCircle(c, c, r, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(0xFF1B5E0B);
            canvas.drawCircle(c, c, r, paint);
            tick.reset();
            tick.moveTo(s * .27f, s * .52f);
            tick.lineTo(s * .42f, s * .67f);
            tick.lineTo(s * .73f, s * .35f);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(s * .12f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawPath(tick, paint);
        }
    }

    // ---------------------------------------------------------------- Wallpaper

    /**
     * An aurora: soft green and cyan ribbons of light over deep teal. It is
     * painted once into a half size bitmap whenever the size or theme
     * changes, so the blur costs nothing per frame.
     */
    static final class Wallpaper extends View {
        private final Turn turn;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect dst = new Rect();
        private Bitmap painted;
        private boolean dark;
        private boolean paintedDark;

        Wallpaper(Context context, Turn turn) {
            super(context);
            this.turn = turn;
        }

        void restyle(boolean dark) {
            this.dark = dark;
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            painted = null;
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            painted = null;
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (painted == null || paintedDark != dark) {
                try {
                    painted = paint(Math.max(1, w / 2), Math.max(1, h / 2), dark);
                    paintedDark = dark;
                } catch (OutOfMemoryError tight) {
                    painted = null;
                    canvas.drawColor(dark ? 0xFF061624 : 0xFF0B3150);
                    return;
                }
            }
            Float background = turn.background;
            paint.setAlpha(Math.round(255 * (background != null ? background : 1f)));
            dst.set(0, 0, w, h);
            canvas.drawBitmap(painted, null, dst, paint);
        }

        static Bitmap paint(int w, int h, boolean dark) {
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            // The deep teal ground, an ellipse of light off center.
            float cx = (dark ? .65f : .35f) * w, cy = (dark ? .45f : .55f) * h;
            float rx = .9f * w, ry = .8f * h;
            RadialGradient ground = dark
                ? new RadialGradient(cx, cy, rx, new int[] {0xFF0F3B4A, 0xFF08202F, 0xFF030B14}, new float[] {0, .4f, 1}, CLAMP)
                : new RadialGradient(cx, cy, rx, new int[] {0xFF1F8B78, 0xFF12566A, 0xFF0B3150, 0xFF061A2E}, new float[] {0, .32f, .62f, 1}, CLAMP);
            Matrix stretch = new Matrix();
            stretch.setScale(1, ry / rx, cx, cy);
            ground.setLocalMatrix(stretch);
            paint.setShader(ground);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            // Ribbons, laid out on a 1280 by 800 sketch and stretched to fit.
            float sx = w / 1280f, sy = h / 800f, sk = Math.min(sx, sy);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (dark) {
                ribbon(canvas, paint, sx, sy, sk, new float[] {-80, 120, 240, 200, 460, 470, 760, 480, 1160, 560, 1380, 760}, 110, 16, 1, new int[] {0x003EF2A0, 0x8C3EF2A0, 0x002FB7FF});
                ribbon(canvas, paint, sx, sy, sk, new float[] {-80, 140, 250, 220, 470, 490, 770, 500, 1170, 580, 1380, 780}, 2.5f, 0, 1, new int[] {0x00FFFFFF, 0x99C8FFF0, 0x00FFFFFF});
            } else {
                ribbon(canvas, paint, sx, sy, sk, new float[] {-80, 700, 200, 640, 380, 380, 640, 330, 1080, 260, 1380, 60}, 120, 14, .75f, new int[] {0x00B6FF5C, 0xE6B6FF5C, 0xB33EF2D0, 0x003EF2D0});
                ribbon(canvas, paint, sx, sy, sk, new float[] {-80, 720, 220, 660, 400, 420, 660, 360, 1100, 290, 1380, 100}, 6, 3, 1, new int[] {0x00FFFFFF, 0xD9E9FFF2, 0x007FF7FF});
                ribbon(canvas, paint, sx, sy, sk, new float[] {-80, 760, 260, 700, 460, 470, 700, 410, 1120, 330, 1380, 160}, 70, 14, .8f, new int[] {0x002FB7FF, 0x994FD2FF, 0x002FB7FF});
                ribbon(canvas, paint, sx, sy, sk, new float[] {-80, 770, 270, 715, 470, 490, 710, 430, 1130, 350, 1380, 180}, 2.5f, 3, .8f, new int[] {0x00FFFFFF, 0xD9E9FFF2, 0x007FF7FF});
                ribbon(canvas, paint, sx, sy, sk, new float[] {-80, 640, 160, 600, 360, 330, 600, 280, 1040, 220, 1380, 10}, 2, 0, .55f, new int[] {0x00FFFFFF, 0xD9E9FFF2, 0x007FF7FF});
            }
            return bitmap;
        }

        /**
         * One ribbon: M p0 C c1 c2 p1 S c3 p2, the S reflecting c2 about p1.
         * Width and blur are in sketch pixels.
         */
        private static void ribbon(Canvas canvas, Paint paint, float sx, float sy, float sk, float[] p, float width, float blur, float alpha, int[] colors) {
            Path path = new Path();
            path.moveTo(p[0] * sx, p[1] * sy);
            path.cubicTo(p[2] * sx, p[3] * sy, p[4] * sx, p[5] * sy, p[6] * sx, p[7] * sy);
            float rx = 2 * p[6] - p[4], ry = 2 * p[7] - p[5];
            path.cubicTo(rx * sx, ry * sy, p[8] * sx, p[9] * sy, p[10] * sx, p[11] * sy);
            float[] stops = new float[colors.length];
            for (int i = 0; i < colors.length; i++) stops[i] = colors.length == 1 ? 0 : i / (float) (colors.length - 1);
            paint.setShader(new LinearGradient(0, 0, 1280 * sx, 0, colors, stops, CLAMP));
            paint.setStrokeWidth(Math.max(1, width * sk));
            paint.setMaskFilter(blur > 0 ? new BlurMaskFilter(Math.max(1, blur * sk * 2), BlurMaskFilter.Blur.NORMAL) : null);
            paint.setAlpha(Math.round(255 * alpha));
            canvas.drawPath(path, paint);
            paint.setMaskFilter(null);
            paint.setShader(null);
            paint.setAlpha(255);
        }
    }

    // ---------------------------------------------------------------- Taskbar

    /** The black glass taskbar: an orb, a button for the assistant and a clock. */
    static final class Taskbar extends View {
        private final Turn turn;
        private KsTheme theme;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bitmap = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF box = new RectF();
        private final Runnable tick = this::invalidate;
        private String clock = "";
        private long clockMinute = -1;

        Taskbar(Context context, Turn turn) {
            super(context);
            this.turn = turn;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(1);
            text.setColor(Color.WHITE);
        }

        /** The bar's own height, without the orb that rises above it. */
        static int barHeight(KsTheme theme) { return theme.px(44); }
        static int rise(KsTheme theme) { return theme.px(6); }

        void restyle(KsTheme theme) {
            this.theme = theme;
            text.setTypeface(theme.typeface(400));
            invalidate();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(tick);
        }

        @Override protected void onDraw(Canvas canvas) {
            if (theme == null) return;
            float w = getWidth(), h = getHeight(), top = rise(theme);
            paint.setShader(gloss(top, h, new int[] {0xE03C4650, 0xEB14181E, 0xF2000000, 0xF20A0C10}, new float[] {0, .48f, .5f, 1}));
            canvas.drawRect(0, top, w, h, paint);
            paint.setShader(null);
            line.setColor(0xCC000000);
            canvas.drawLine(0, top - .5f, w, top - .5f, line);
            line.setColor(0x47FFFFFF);
            canvas.drawLine(0, top + .5f, w, top + .5f, line);

            // The orb, rising out of the bar.
            float orb = theme.px(42), ox = theme.px(6), oy = h - theme.px(1) - orb;
            float ocx = ox + orb / 2, ocy = oy + orb / 2;
            paint.setShader(new RadialGradient(ocx, ocy, orb * .75f, new int[] {0x996ED2FF, 0x006ED2FF}, null, CLAMP));
            canvas.drawCircle(ocx, ocy, orb * .75f, paint);
            paint.setShader(new RadialGradient(ocx, oy + orb * .92f, orb, new int[] {0xFF6FF0FF, 0xFF1584D8, 0xFF0A3F85, 0xFF021530}, new float[] {0, .38f, .7f, 1}, CLAMP));
            canvas.drawCircle(ocx, ocy, orb / 2, paint);
            paint.setShader(new LinearGradient(0, oy + orb * .05f, 0, oy + orb * .48f, 0xD9FFFFFF, 0x1AFFFFFF, CLAMP));
            box.set(ocx - orb * .33f, oy + orb * .05f, ocx + orb * .33f, oy + orb * .48f);
            canvas.drawOval(box, paint);
            paint.setShader(null);
            line.setColor(0xFF000000);
            canvas.drawCircle(ocx, ocy, orb / 2, line);
            line.setColor(0x66FFFFFF);
            canvas.drawCircle(ocx, ocy, orb / 2 - 1.5f, line);

            // The assistant's button.
            float barTop = top + theme.px(6), barBottom = h - theme.px(6);
            float bx = ox + orb + theme.px(10);
            text.setTextSize(theme.px(13));
            String title = turn.title;
            float tw = Math.min(text.measureText(title), w * .4f);
            float bw = theme.px(16) + theme.px(22) + theme.px(8) + tw + theme.px(16);
            box.set(bx, barTop, bx + bw, barBottom);
            paint.setShader(gloss(barTop, barBottom, new int[] {0x52FFFFFF, 0x1FFFFFFF, 0x05FFFFFF, 0x24FFFFFF}, new float[] {0, .48f, .5f, 1}));
            float r = theme.px(3);
            canvas.drawRoundRect(box, r, r, paint);
            paint.setShader(null);
            line.setColor(0x40FFFFFF);
            box.inset(1.5f, 1.5f);
            canvas.drawRoundRect(box, r - 1, r - 1, line);
            box.inset(-1, -1);
            line.setColor(0xCC000000);
            canvas.drawRoundRect(box, r, r, line);
            int icon = theme.px(22);
            float midY = (barTop + barBottom) / 2;
            drawMicIcon(canvas, turn.mic, Math.round(bx + theme.px(14)), Math.round(midY - icon / 2f), icon, bitmap, paint);
            Paint.FontMetrics m = text.getFontMetrics();
            float baseline = midY - (m.ascent + m.descent) / 2;
            canvas.save();
            canvas.clipRect(bx, barTop, bx + bw - theme.px(10), barBottom);
            canvas.drawText(title, bx + theme.px(14) + icon + theme.px(8), baseline, text);
            canvas.restore();

            // The clock.
            long minute = System.currentTimeMillis() / 60000;
            if (minute != clockMinute) {
                clockMinute = minute;
                clock = DateFormat.getTimeFormat(getContext()).format(new Date());
            }
            float cw = text.measureText(clock);
            float cx = w - theme.px(14) - cw;
            line.setColor(0x26FFFFFF);
            canvas.drawLine(cx - theme.px(14), barTop, cx - theme.px(14), barBottom, line);
            canvas.drawText(clock, cx, baseline, text);
            removeCallbacks(tick);
            postDelayed(tick, 15000);
        }
    }

    /** The KS typeface, or the platform's. */
    static Typeface face(KsTheme theme, int weight) {
        return theme.typeface(weight);
    }
}
