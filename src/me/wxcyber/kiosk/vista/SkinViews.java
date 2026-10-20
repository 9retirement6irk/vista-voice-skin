// SPDX-License-Identifier: Apache-2.0
package me.wxcyber.kiosk.vista;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import me.jxl.kiosk.plugins.KsTheme;

/** The full screen look and the docked look. Built and touched on the main thread only. */
final class SkinViews {
    private SkinViews() {}

    interface SkinView {
        View asView();
        /** New words or a new phase. */
        void refresh();
        /** New appearance or plugin settings. */
        void restyle();
        /** A new KS theme. */
        void restyle(KsTheme theme);
    }

    static final int LIGHT_SAID = 0xFF5A5A5A;
    static final int LIGHT_ANSWER = 0xFF003399;
    static final int LIGHT_TEXT = 0xFF1E1E1E;
    static final int DARK_SAID = 0xFFA9B4C0;
    static final int DARK_ANSWER = 0xFF8FD0FF;
    static final int DARK_TEXT = 0xFFE6E9EE;

    static String quoted(String said) {
        return said.isEmpty() ? "" : "You said: “" + said + "”";
    }

    /** The client area's fill: plain white, or Media Player black when dark. */
    static GradientDrawable client(KsTheme theme, boolean dark) {
        GradientDrawable fill = dark
            ? new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF1C2128, 0xFF0B0D10, 0xFF000000})
            : new GradientDrawable();
        if (!dark) fill.setColor(0xFFFFFFFF);
        fill.setStroke(Math.max(1, theme.px(1) / 2 + 1), dark ? 0xFF000000 : 0x8C000000);
        return fill;
    }

    /** The command area under the content, like a Vista task dialog's footer. */
    static Aero.GlossDrawable footer(boolean dark) {
        return dark
            ? new Aero.GlossDrawable(new int[] {0xFF2A3038, 0xFF15191E, 0xFF0A0C0F, 0xFF14181D}, new float[] {0, .5f, .51f, 1}, 0, 0xFF000000, 0x24FFFFFF, true)
            : new Aero.GlossDrawable(new int[] {0xFFF0F0F0, 0xFFF0F0F0}, new float[] {0, 1}, 0, 0xFFDFDFDF, 0, true);
    }

    // ---------------------------------------------------------------- Full screen

    static final class Full extends FrameLayout implements SkinView {
        private final Turn turn;
        private KsTheme theme;
        private final Aero.Wallpaper wallpaper;
        private final FrameLayout area;
        private final Aero.GlassWindow window;
        private final LinearLayout clientArea;
        private final LinearLayout content;
        private final Aero.MicView mic;
        private final LinearLayout words;
        private final TextView said;
        private final TextView answered;
        private final LinearLayout toolRow;
        private final Aero.CheckView check;
        private final TextView tool;
        private final LinearLayout footerRow;
        private final TextView label;
        private final Aero.MeterView meter;
        private final TextView status;
        private final Aero.Taskbar taskbar;
        private boolean narrow;

        Full(Context context, KsTheme theme, Turn turn) {
            super(context);
            this.turn = turn;
            this.theme = theme;

            wallpaper = new Aero.Wallpaper(context, turn);
            addView(wallpaper, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            area = new FrameLayout(context);
            addView(area, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            window = new Aero.GlassWindow(context, turn, false);
            area.addView(window, new LayoutParams(theme.px(900), LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            clientArea = new LinearLayout(context);
            clientArea.setOrientation(LinearLayout.VERTICAL);
            window.addView(clientArea, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            content = new LinearLayout(context);
            clientArea.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            mic = new Aero.MicView(context, turn);
            words = new LinearLayout(context);
            words.setOrientation(LinearLayout.VERTICAL);
            said = new TextView(context);
            answered = new TextView(context);
            answered.setMaxLines(9);
            answered.setEllipsize(TextUtils.TruncateAt.END);
            toolRow = new LinearLayout(context);
            toolRow.setOrientation(LinearLayout.HORIZONTAL);
            toolRow.setGravity(Gravity.CENTER_VERTICAL);
            check = new Aero.CheckView(context);
            tool = new TextView(context);
            tool.setMaxLines(2);
            tool.setEllipsize(TextUtils.TruncateAt.END);
            toolRow.addView(check, new LinearLayout.LayoutParams(theme.px(22), theme.px(22)));
            LinearLayout.LayoutParams toolText = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            toolText.leftMargin = theme.px(10);
            toolRow.addView(tool, toolText);
            words.addView(said);
            LinearLayout.LayoutParams answerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            answerParams.topMargin = theme.px(12);
            words.addView(answered, answerParams);
            LinearLayout.LayoutParams toolParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            toolParams.topMargin = theme.px(16);
            words.addView(toolRow, toolParams);

            footerRow = new LinearLayout(context);
            footerRow.setOrientation(LinearLayout.HORIZONTAL);
            footerRow.setGravity(Gravity.CENTER_VERTICAL);
            label = new TextView(context);
            meter = new Aero.MeterView(context, turn);
            status = new TextView(context);
            status.setSingleLine(true);
            status.setEllipsize(TextUtils.TruncateAt.END);
            status.setGravity(Gravity.END);
            footerRow.addView(label);
            LinearLayout.LayoutParams meterParams = new LinearLayout.LayoutParams(theme.px(300), theme.px(18));
            meterParams.leftMargin = theme.px(16);
            footerRow.addView(meter, meterParams);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            statusParams.leftMargin = theme.px(16);
            footerRow.addView(status, statusParams);
            clientArea.addView(footerRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            taskbar = new Aero.Taskbar(context, turn);
            addView(taskbar, new LayoutParams(LayoutParams.MATCH_PARENT, Aero.Taskbar.barHeight(theme) + Aero.Taskbar.rise(theme), Gravity.BOTTOM));

            int screen = context.getResources().getDisplayMetrics().widthPixels;
            arrange(screen);
            restyle();
            refresh();
        }

        @Override public View asView() { return this; }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w > 0 && w != oldw) post(() -> { arrange(w); restyle(); });
        }

        /** Window width, and side by side or stacked, for this screen width. */
        private void arrange(int screen) {
            int width = Math.min(theme.px(900), screen - theme.px(48));
            LayoutParams params = (LayoutParams) window.getLayoutParams();
            params.width = width;
            window.setLayoutParams(params);
            narrow = width < theme.px(600);
            content.removeAllViews();
            int micSize = theme.px(narrow ? 120 : 168);
            if (narrow) {
                content.setOrientation(LinearLayout.VERTICAL);
                content.setGravity(Gravity.CENTER_HORIZONTAL);
                content.addView(mic, new LinearLayout.LayoutParams(micSize, micSize));
                LinearLayout.LayoutParams column = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                column.topMargin = theme.px(16);
                content.addView(words, column);
            } else {
                content.setOrientation(LinearLayout.HORIZONTAL);
                content.setGravity(Gravity.TOP);
                content.addView(mic, new LinearLayout.LayoutParams(micSize, micSize));
                LinearLayout.LayoutParams column = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                column.leftMargin = theme.px(36);
                column.topMargin = theme.px(6);
                content.addView(words, column);
            }
            int side = theme.px(narrow ? 24 : 40);
            content.setPadding(side, theme.px(narrow ? 24 : 34), side, theme.px(narrow ? 22 : 30));
            footerRow.setPadding(side, theme.px(14), side, theme.px(14));
            LinearLayout.LayoutParams meterParams = (LinearLayout.LayoutParams) meter.getLayoutParams();
            meterParams.width = Math.min(theme.px(300), Math.max(theme.px(80), width / 3));
            meter.setLayoutParams(meterParams);
            int gravity = narrow ? Gravity.CENTER_HORIZONTAL : Gravity.START;
            said.setGravity(gravity);
            answered.setGravity(gravity);
            toolRow.setGravity(Gravity.CENTER_VERTICAL | gravity);
        }

        @Override public void restyle(KsTheme next) {
            theme = next;
            restyle();
        }

        @Override public void restyle() {
            boolean dark = turn.dark(theme.isDark());
            float scale = turn.textScale;
            wallpaper.restyle(dark);
            window.restyle(theme, dark);
            clientArea.setBackground(client(theme, dark));
            footerRow.setBackground(footer(dark));
            mic.restyle(dark);
            meter.restyle(theme, dark);
            theme.styleText(said, 16 * scale, 400, "onSurface");
            theme.styleText(answered, (narrow ? 24 : 30) * scale, 400, "onSurface");
            answered.setLineSpacing(0, 1.12f);
            theme.styleText(tool, 16 * scale, 400, "onSurface");
            theme.styleText(label, 15 * scale, 400, "onSurface");
            theme.styleText(status, 15 * scale, 400, "onSurface");
            said.setTextColor(dark ? DARK_SAID : LIGHT_SAID);
            answered.setTextColor(dark ? DARK_ANSWER : LIGHT_ANSWER);
            tool.setTextColor(dark ? DARK_TEXT : LIGHT_TEXT);
            label.setTextColor(dark ? DARK_TEXT : LIGHT_TEXT);
            status.setTextColor(dark ? DARK_SAID : LIGHT_SAID);
            taskbar.restyle(theme);
            taskbar.setVisibility(turn.showTaskbar ? VISIBLE : GONE);
            LayoutParams areaParams = (LayoutParams) area.getLayoutParams();
            areaParams.bottomMargin = turn.showTaskbar ? Aero.Taskbar.barHeight(theme) : 0;
            area.setLayoutParams(areaParams);
            window.invalidate();
        }

        @Override public void refresh() {
            String command = turn.command;
            said.setText(quoted(command));
            said.setVisibility(command.isEmpty() || turn.announcement ? GONE : VISIBLE);
            String headline = turn.headline();
            answered.setText(headline);
            answered.setVisibility(headline.isEmpty() ? GONE : VISIBLE);
            String action = turn.tool;
            tool.setText(action);
            toolRow.setVisibility(action.isEmpty() ? GONE : VISIBLE);
            label.setText(turn.meterLabel());
            status.setText(turn.status());
            window.invalidate();
            taskbar.invalidate();
        }
    }

    // ---------------------------------------------------------------- Docked

    static final class Docked extends FrameLayout implements SkinView {
        private final Turn turn;
        private KsTheme theme;
        private final Aero.GlassWindow window;
        private final LinearLayout clientArea;
        private final LinearLayout row;
        private final Aero.MicView mic;
        private final TextView said;
        private final TextView answered;
        private final FrameLayout footerRow;
        private final Aero.MeterView meter;

        Docked(Context context, KsTheme theme, Turn turn) {
            super(context);
            this.turn = turn;
            this.theme = theme;
            int screen = context.getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(theme.px(580), screen - theme.px(48));

            window = new Aero.GlassWindow(context, turn, true);
            addView(window, new LayoutParams(width, LayoutParams.WRAP_CONTENT));

            clientArea = new LinearLayout(context);
            clientArea.setOrientation(LinearLayout.VERTICAL);
            window.addView(clientArea, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(theme.px(14), theme.px(12), theme.px(18), theme.px(12));
            mic = new Aero.MicView(context, turn);
            row.addView(mic, new LinearLayout.LayoutParams(theme.px(64), theme.px(64)));
            LinearLayout words = new LinearLayout(context);
            words.setOrientation(LinearLayout.VERTICAL);
            said = new TextView(context);
            said.setSingleLine(true);
            said.setEllipsize(TextUtils.TruncateAt.END);
            answered = new TextView(context);
            answered.setMaxLines(3);
            answered.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(said);
            LinearLayout.LayoutParams answerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            answerParams.topMargin = theme.px(3);
            words.addView(answered, answerParams);
            LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            wordsParams.leftMargin = theme.px(16);
            row.addView(words, wordsParams);
            clientArea.addView(row);

            footerRow = new FrameLayout(context);
            footerRow.setPadding(theme.px(18), theme.px(8), theme.px(18), theme.px(8));
            meter = new Aero.MeterView(context, turn);
            footerRow.addView(meter, new LayoutParams(LayoutParams.MATCH_PARENT, theme.px(10)));
            clientArea.addView(footerRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            restyle();
            refresh();
        }

        @Override public View asView() { return this; }

        @Override public void restyle(KsTheme next) {
            theme = next;
            restyle();
        }

        @Override public void restyle() {
            boolean dark = turn.dark(theme.isDark());
            float scale = turn.textScale;
            window.restyle(theme, dark);
            clientArea.setBackground(client(theme, dark));
            footerRow.setBackground(footer(dark));
            mic.restyle(dark);
            meter.restyle(theme, dark);
            theme.styleText(said, 13 * scale, 400, "onSurface");
            theme.styleText(answered, 18 * scale, 400, "onSurface");
            said.setTextColor(dark ? DARK_SAID : LIGHT_SAID);
            answered.setTextColor(dark ? DARK_ANSWER : LIGHT_ANSWER);
        }

        @Override public void refresh() {
            String command = turn.command;
            said.setText(quoted(command));
            said.setVisibility(command.isEmpty() || turn.announcement ? GONE : VISIBLE);
            String headline = turn.answer.isEmpty() && !turn.tool.isEmpty() ? turn.tool : turn.headline();
            answered.setText(headline);
            answered.setVisibility(headline.isEmpty() ? GONE : VISIBLE);
            window.invalidate();
        }
    }
}
