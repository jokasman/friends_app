package pt.relacoes.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import pt.relacoes.R;
import pt.relacoes.core.Health;
import pt.relacoes.core.Person;
import pt.relacoes.data.Photos;

/** Peças de interface da app: avatar com saúde da relação, barra de topo, campos, linhas e chips. */
public final class Views {
    private Views() {}

    private static final int[] AVATAR = {0xFF3D5AFE, 0xFF00897B, 0xFFE65100, 0xFF8E24AA, 0xFFC62828, 0xFF2E7D32, 0xFF0277BD, 0xFFAD1457};

    static int avatarColor(String name) {
        int h = name == null ? 0 : name.trim().toLowerCase().hashCode();
        return AVATAR[Math.floorMod(h, AVATAR.length)];
    }

    /** Avatar redondo (foto ou iniciais). Se health >= 0, desenha um anel com a cor da saúde da relação. */
    static View avatar(Context c, Person p, int sizeDp, int health) {
        int size = Ui.dp(c, sizeDp);
        int ring = health > Health.NONE ? Math.max(2, Ui.dp(c, sizeDp >= 64 ? 4 : 3)) : 0;
        int gap = ring > 0 ? Ui.dp(c, 2) : 0;
        FrameLayout box = new FrameLayout(c);
        if (ring > 0) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(ring, Health.COLORS[health]);
            box.setBackground(g);
        }
        int inner = size - 2 * (ring + gap);
        Bitmap bm = Photos.load(c, p.photo);
        View face;
        if (bm != null) {
            ImageView iv = new ImageView(c);
            iv.setImageBitmap(bm);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
            });
            iv.setClipToOutline(true);
            face = iv;
        } else {
            TextView t = Ui.text(c, p.initials(), sizeDp * 0.36f, R.color.onAccent, true);
            t.setTextColor(0xFFFFFFFF);
            t.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(avatarColor(p.name));
            t.setBackground(g);
            face = t;
        }
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(inner, inner, Gravity.CENTER);
        box.addView(face, flp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        box.setLayoutParams(lp);
        return box;
    }

    /** Barra de topo: voltar (opcional), título e espaço para ações à direita. O título é o filho com tag "title". */
    static LinearLayout topBar(Activity a, String title, boolean back) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(a, back ? 4 : 18), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 4));
        if (back) bar.addView(Ui.iconButton(a, R.drawable.ic_back, "Voltar", v -> a.finish()));
        TextView t = Ui.text(a, title, back ? 20 : 28, R.color.text, true);
        t.setTypeface(Ui.font(a, 800));
        t.setTag("title");
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
        e.setTypeface(Ui.font(c, 400));
        e.setBackground(Ui.round(Ui.col(c, R.color.chip), Ui.dp(c, 14)));
        e.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        LinearLayout.LayoutParams lp = Ui.lpMW();
        lp.bottomMargin = Ui.dp(c, 10);
        e.setLayoutParams(lp);
        if ((type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
            e.setMinLines(2);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
        }
        return e;
    }

    static EditText multiline(Context c, String hint, String value) {
        return input(c, hint, value, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    }

    /** Linha de pessoa: avatar com cor, nome, subtítulo e destaque opcional à direita. */
    static LinearLayout personRow(Context c, Person p, int health, String sub, String badge, int badgeKind, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 2), Ui.dp(c, 7), Ui.dp(c, 2), Ui.dp(c, 7));
        row.setBackground(Ui.ripple(c, null, Ui.dp(c, 12)));
        row.addView(avatar(c, p, 46, health));
        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        TextView n = Ui.text(c, p.name, 16, R.color.text, true);
        Ui.ellipsize(n, 1);
        txt.addView(n);
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.text(c, sub, 13, R.color.text2, false);
            Ui.ellipsize(s, 2);
            txt.addView(s);
        }
        row.addView(txt, Ui.lpWeight(1));
        if (badge != null) row.addView(badge(c, badge, badgeKind));
        if (l != null) row.setOnClickListener(l);
        return row;
    }

    /**
     * Cartão de pessoa ao estilo do Círculo: avatar com anel de saúde, nome, subtítulo e, opcionalmente,
     * um destaque e um botão redondo de ação à direita.
     */
    static LinearLayout personCard(Context c, Person p, int health, String sub, String badge, int badgeKind,
                                   int actionIcon, String actionDesc, View.OnClickListener action, View.OnClickListener open) {
        LinearLayout row = Ui.itemCard(c, R.color.surface);
        row.addView(avatar(c, p, 52, health));
        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        TextView n = Ui.text(c, p.name, 16, R.color.text, true);
        n.setTypeface(Ui.font(c, 800));
        Ui.ellipsize(n, 1);
        txt.addView(n);
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.text(c, sub, 13, R.color.text2, false);
            Ui.ellipsize(s, 2);
            txt.addView(s);
        }
        row.addView(txt, Ui.lpWeight(1));
        if (badge != null) {
            TextView b = badge(c, badge, badgeKind);
            ((LinearLayout.LayoutParams) b.getLayoutParams()).setMarginEnd(Ui.dp(c, 8));
            row.addView(b);
        }
        if (action != null) row.addView(Ui.roundAction(c, actionIcon, actionDesc, action));
        if (open != null) row.setOnClickListener(open);
        return row;
    }

    static final int BADGE_ACCENT = 0, BADGE_WARN = 1, BADGE_DANGER = 2;

    static TextView badge(Context c, String text, int kind) {
        int fg = kind == BADGE_DANGER ? R.color.danger : kind == BADGE_WARN ? R.color.warn : R.color.accent;
        int bg = kind == BADGE_DANGER ? R.color.dangerSoft : kind == BADGE_WARN ? R.color.warnSoft : R.color.accentSoft;
        TextView b = Ui.text(c, text, 12, fg, true);
        b.setBackground(Ui.round(Ui.col(c, bg), Ui.dp(c, 10)));
        b.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
        b.setSingleLine(true);
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    /** Fila de chips com uma escolha. */
    public interface Pick { void on(int index); }

    static View chooser(Context c, String[] labels, int selected, Pick onPick) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        row.setPadding(0, 0, 0, Ui.dp(c, 8));
        hs.addView(row);
        final int[] sel = {selected};
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            row.removeAllViews();
            for (int i = 0; i < labels.length; i++) {
                final int k = i;
                TextView t = Ui.chip(c, labels[i], sel[0] == i, 0);
                t.setOnClickListener(v -> { sel[0] = k; draw[0].run(); onPick.on(k); });
                row.addView(t);
            }
        };
        draw[0].run();
        return hs;
    }

    /** Várias linhas de chips (quebra a cada "perLine"). */
    static LinearLayout chooserWrap(Context c, String[] labels, int selected, int perLine, Pick onPick) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        final int[] sel = {selected};
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            box.removeAllViews();
            LinearLayout line = null;
            for (int i = 0; i < labels.length; i++) {
                if (i % perLine == 0) {
                    line = new LinearLayout(c);
                    line.setPadding(0, 0, 0, Ui.dp(c, 8));
                    box.addView(line);
                }
                final int k = i;
                TextView t = Ui.chip(c, labels[i], sel[0] == i, 0);
                t.setOnClickListener(v -> { sel[0] = k; draw[0].run(); onPick.on(k); });
                line.addView(t);
            }
        };
        draw[0].run();
        return box;
    }

    static String joinNames(List<String> names) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.get(names.size() - 1);
    }

    /** Texto de ajuda pequeno. */
    static TextView hint(Context c, String s) {
        TextView t = Ui.text(c, s, 12, R.color.text2, false);
        t.setPadding(Ui.dp(c, 2), Ui.dp(c, 6), 0, 0);
        return t;
    }

    static TextView empty(Context c, String s) {
        return Ui.text(c, s, 15, R.color.text2, false);
    }

    /** Linha com rótulo à esquerda e valor/ação à direita. */
    static LinearLayout labelRow(Context c, String label, View right) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        TextView t = Ui.text(c, label, 15, R.color.text, false);
        r.addView(t, Ui.lpWeight(1));
        r.addView(right);
        return r;
    }
}
