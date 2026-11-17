package pt.amigos.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import pt.amigos.R;
import pt.amigos.core.Friend;

/** Peças de interface próprias da app (avatar, barra de topo, campos de texto, linhas de amigo). */
final class Views {
    private Views() {}

    private static final int[] AVATAR = {0xFF3D5AFE, 0xFF00897B, 0xFFE65100, 0xFF8E24AA, 0xFFC62828, 0xFF2E7D32, 0xFF0277BD, 0xFFAD1457};

    static int avatarColor(String name) {
        int h = name == null ? 0 : name.trim().toLowerCase().hashCode();
        return AVATAR[Math.floorMod(h, AVATAR.length)];
    }

    static TextView avatar(Context c, Friend f, int sizeDp) {
        TextView t = Ui.text(c, f.initials(), sizeDp * 0.38f, R.color.onAccent, true);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(avatarColor(f.name));
        t.setBackground(g);
        t.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp)));
        return t;
    }

    /** Barra de topo: voltar (opcional), título e ações à direita. */
    static LinearLayout topBar(BaseActivity a, String title, boolean back) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(a, back ? 4 : 18), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 4));
        if (back) bar.addView(Ui.iconButton(a, R.drawable.ic_back, "Voltar", v -> a.finish()));
        TextView t = Ui.text(a, title, back ? 20 : 26, R.color.text, true);
        Ui.ellipsize(t, 1);
        t.setPadding(Ui.dp(a, back ? 6 : 0), 0, 0, 0);
        bar.addView(t, Ui.lpWeight(1));
        return bar;
    }

    static EditText input(Context c, String hint, String value, int type) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(type);
        e.setTextColor(Ui.col(c, R.color.text));
        e.setHintTextColor(Ui.col(c, R.color.text2));
        e.setTextSize(16);
        e.setBackground(Ui.round(Ui.col(c, R.color.chip), Ui.dp(c, 14)));
        e.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        LinearLayout.LayoutParams lp = Ui.lpMW();
        lp.bottomMargin = Ui.dp(c, 10);
        e.setLayoutParams(lp);
        if ((type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        return e;
    }

    /** Linha de lista: avatar, nome e subtítulo, com destaque opcional à direita. */
    static LinearLayout friendRow(Context c, Friend f, String sub, String badge, boolean badgeWarn, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8));
        row.setBackground(Ui.ripple(c, null, Ui.dp(c, 12)));
        row.addView(avatar(c, f, 42));
        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        TextView n = Ui.text(c, f.name, 16, R.color.text, true);
        Ui.ellipsize(n, 1);
        txt.addView(n);
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.text(c, sub, 13, R.color.text2, false);
            Ui.ellipsize(s, 1);
            txt.addView(s);
        }
        row.addView(txt, Ui.lpWeight(1));
        if (badge != null) {
            TextView b = Ui.text(c, badge, 12, badgeWarn ? R.color.warn : R.color.accent, true);
            b.setBackground(Ui.round(Ui.col(c, badgeWarn ? R.color.warnSoft : R.color.accentSoft), Ui.dp(c, 10)));
            b.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
            row.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        row.setOnClickListener(l);
        return row;
    }
}
