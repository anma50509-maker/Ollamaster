package com.ollamaster;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.Window;

public class Theme {
    public final int bg, surface, surfaceAlt, border, textPri, textSec, accent, accentDeep, danger, ok, light;

    private Theme(int bg, int surface, int surfaceAlt, int border, int textPri, int textSec,
                  int accent, int danger, boolean light) {
        this.bg = bg;
        this.surface = surface;
        this.surfaceAlt = surfaceAlt;
        this.border = border;
        this.textPri = textPri;
        this.textSec = textSec;
        this.accent = accent;
        this.accentDeep = mix(accent, bg, 0.72f);
        this.danger = danger;
        this.ok = 0xFF7BAE7F;
        this.light = light ? 1 : 0;
    }

    public static Theme of(Activity a) {
        Prefs p = Prefs.get(a);
        if (p.customTheme()) {
            int bg = p.cBg(), tx = p.cText(), ac = p.cAccent();
            boolean light = Color.luminance(bg) > 0.5;
            return new Theme(bg, mix(bg, tx, 0.06f), mix(bg, tx, 0.11f),
                    (ac & 0x00FFFFFF) | 0x33000000, tx, mix(tx, bg, 0.42f), ac, 0xFFC0574F, light);
        }
        switch (p.themeName()) {
            case "rosewood":
                return new Theme(0xFF160F11, 0xFF201619, 0xFF2A1D21, 0x33C87F8E, 0xFFF4E9EC, 0xFFA08E94, 0xFFC87F8E, 0xFFB85C50, false);
            case "celadon":
                return new Theme(0xFF0E1418, 0xFF15202A, 0xFF1C2A36, 0x337FB3D5, 0xFFE8EFF5, 0xFF8CA0B0, 0xFF7FB3D5, 0xFFC0574F, false);
            case "pine":
                return new Theme(0xFF0F1412, 0xFF16201C, 0xFF1E2A25, 0x336FAE9B, 0xFFE9F1ED, 0xFF93A69E, 0xFF6FAE9B, 0xFFBF6B5B, false);
            case "obsidian":
                return new Theme(0xFF000000, 0xFF0D0D10, 0xFF15151A, 0x33B39DDB, 0xFFEDEAF5, 0xFF94909F, 0xFFB39DDB, 0xFFC0574F, false);
            case "moon":
                return new Theme(0xFFF7F5F0, 0xFFFFFFFF, 0xFFF0EDE5, 0x228A6D3B, 0xFF26241F, 0xFF7C776B, 0xFF8A6D3B, 0xFFB04A42, true);
            default:
                return new Theme(0xFF101216, 0xFF171A21, 0xFF20242E, 0x26D4AF6A, 0xFFF2EDE2, 0xFF9AA0AC, 0xFFD4AF6A, 0xFFC0574F, false);
        }
    }

    public static int mix(int a, int b, float t) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bgc = Color.green(b), bb = Color.blue(b);
        return Color.rgb((int) (ar + (br - ar) * t), (int) (ag + (bgc - ag) * t), (int) (ab + (bb - ab) * t));
    }

    public int alpha(int base, float a) {
        return (base & 0x00FFFFFF) | ((int) (a * 255) << 24);
    }

    public boolean isDark(int c) { return android.graphics.Color.luminance(c) <= 0.5; }

    public int mixTextOn(Theme t) {
        return isDark(accent) ? 0xFF15161A : 0xFF191919;
    }

    public void applyWindow(Activity act) {
        Window w = act.getWindow();
        w.setStatusBarColor(light == 1 ? alpha(bg, 0.98f) : bg);
        w.setNavigationBarColor(bg);
        int flags = w.getDecorView().getSystemUiVisibility();
        if (light == 1) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        w.getDecorView().setSystemUiVisibility(flags);
    }
}
