package pt.relacoes.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

import pt.relacoes.R;
import pt.relacoes.core.Group;
import pt.relacoes.data.Db;

/** Gestão dos grupos (Família, Amigos, Trabalho…). */
public class GroupsActivity extends BaseActivity {
    private LinearLayout content;

    interface Saved { void on(Group g); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = Views.topBar(this, "Grupos", true);
        bar.addView(Ui.iconButton(this, R.drawable.ic_add, "Novo grupo", v -> editGroup(this, null, g -> render())));
        col.addView(bar);
        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sv.addView(content);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(col);
        render();
    }

    private void render() {
        content.removeAllViews();
        LinearLayout card = Ui.card(this);
        List<Group> gs = Db.get(this).groups();
        Map<Long, List<Long>> mem = Db.get(this).allMemberships();
        if (gs.isEmpty()) card.addView(Views.empty(this, "Sem grupos. Toque em + para criar."));
        for (int i = 0; i < gs.size(); i++) {
            Group g = gs.get(i);
            int n = 0;
            for (List<Long> l : mem.values()) if (l.contains(g.id)) n++;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView chip = Ui.chip(this, g.name, false, g.color);
            row.addView(chip);
            TextView cnt = Ui.text(this, n + (n == 1 ? " pessoa" : " pessoas"), 13, R.color.text2, false);
            row.addView(cnt, Ui.lpWeight(1));
            final int idx = i;
            if (i > 0) row.addView(Ui.iconButton(this, R.drawable.ic_up, "Subir", v -> move(gs, idx, -1)));
            row.addView(Ui.iconButton(this, R.drawable.ic_edit, "Editar", v -> editGroup(this, g, x -> render())));
            row.addView(Ui.iconButton(this, R.drawable.ic_delete, "Apagar", v -> new AlertDialog.Builder(this)
                    .setMessage("Apagar o grupo \"" + g.name + "\"? As pessoas não são apagadas.")
                    .setPositiveButton("Apagar", (d, w) -> { Db.get(this).deleteGroup(g.id); render(); })
                    .setNegativeButton("Cancelar", null).show()));
            row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            card.addView(row);
        }
        content.addView(card);
    }

    private void move(List<Group> gs, int idx, int dir) {
        Group a = gs.get(idx), b = gs.get(idx + dir);
        gs.set(idx, b);
        gs.set(idx + dir, a);
        for (int i = 0; i < gs.size(); i++) { gs.get(i).sort = i; Db.get(this).save(gs.get(i)); }
        render();
    }

    /** Diálogo para criar ou editar um grupo (nome e cor). */
    static void editGroup(BaseActivity a, Group existing, Saved after) {
        final Group g = existing != null ? existing : new Group();
        if (existing == null) {
            List<Group> all = Db.get(a).groups();
            g.color = Group.COLORS[all.size() % Group.COLORS.length];
            g.sort = all.size();
        }
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(a, 22), Ui.dp(a, 8), Ui.dp(a, 22), 0);
        EditText name = Views.input(a, "Nome do grupo", g.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(name);
        box.addView(Ui.subLabel(a, "Cor"));
        LinearLayout colors = new LinearLayout(a);
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            colors.removeAllViews();
            for (int c : Group.COLORS) {
                android.view.View dot = new android.view.View(a);
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                gd.setColor(c);
                if (c == g.color) gd.setStroke(Ui.dp(a, 3), Ui.col(a, R.color.text));
                dot.setBackground(gd);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 26), Ui.dp(a, 26));
                lp.setMarginEnd(Ui.dp(a, 6));
                dot.setOnClickListener(v -> { g.color = c; draw[0].run(); });
                colors.addView(dot, lp);
            }
        };
        draw[0].run();
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.addView(colors);
        box.addView(hs);
        new AlertDialog.Builder(a).setTitle(existing == null ? "Novo grupo" : "Editar grupo").setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) return;
                    g.name = n;
                    Db.get(a).save(g);
                    after.on(g);
                })
                .setNegativeButton("Cancelar", null).show();
    }
}
