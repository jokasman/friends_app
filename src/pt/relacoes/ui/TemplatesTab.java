package pt.relacoes.ui;

import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import pt.relacoes.R;
import pt.relacoes.core.Template;
import pt.relacoes.data.Db;

/** Modelos: biblioteca de mensagens por tipo e tamanho. */
final class TemplatesTab extends MainActivity.Tab {
    private static int type = Template.BIRTHDAY;
    private LinearLayout content;

    TemplatesTab(MainActivity a) { super(a); }

    @Override View.OnClickListener fab() { return v -> edit(a, null, type, this::refresh); }
    @Override String fabLabel() { return "Novo modelo"; }

    @Override View build() {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        View ch = Views.chooser(a, Template.TYPES, type, k -> { type = k; refresh(); });
        ch.setPadding(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 14), 0);
        col.addView(ch);
        ScrollView sv = new ScrollView(a);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(a, 90));
        sv.addView(content);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        refresh();
        return col;
    }

    @Override void refresh() {
        content.removeAllViews();
        List<Template> all = Db.get(a).templates();
        for (int size = 0; size < 2; size++) {
            LinearLayout card = Ui.card(a);
            card.addView(Ui.sectionLabel(a, "Mensagens " + Template.SIZES[size].toLowerCase() + "s"));
            int n = 0;
            for (Template t : all) {
                if (t.type != type || t.size != size) continue;
                if (n++ > 0) card.addView(Ui.divider(a));
                TextView tv = Ui.text(a, t.text, 15, R.color.text, false);
                tv.setBackground(Ui.ripple(a, null, Ui.dp(a, 8)));
                tv.setOnClickListener(v -> edit(a, t, type, this::refresh));
                card.addView(tv);
            }
            if (n == 0) card.addView(Views.empty(a, "Nenhum modelo. Toque em + para criar."));
            content.addView(card);
        }
        content.addView(Views.hint(a, "    Variáveis: {nome} (nome completo), {primeiro_nome} (como lhe chama, ou o primeiro nome) e {idade}. Toque num modelo para o editar ou apagar."));
    }

    static void edit(BaseActivity a, Template t, int defType, Runnable after) {
        final Template m = t != null ? t : new Template();
        if (t == null) m.type = defType;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(a, 22), Ui.dp(a, 8), Ui.dp(a, 22), 0);
        box.addView(Ui.subLabel(a, "Tipo"));
        box.addView(Views.chooser(a, Template.TYPES, m.type, k -> m.type = k));
        box.addView(Ui.subLabel(a, "Tamanho"));
        box.addView(Views.chooser(a, Template.SIZES, m.size, k -> m.size = k));
        EditText text = Views.multiline(a, "Texto da mensagem. Ex.: Parabéns, {primeiro_nome}!", m.text);
        text.setMinLines(4);
        box.addView(text);
        box.addView(Views.hint(a, "Variáveis: {nome}, {primeiro_nome} (como lhe chama), {idade}"));
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(t == null ? "Novo modelo" : "Editar modelo").setView(sv)
                .setPositiveButton("Guardar", (d, w) -> {
                    String s = text.getText().toString().trim();
                    if (s.isEmpty()) return;
                    m.text = s;
                    Db.get(a).save(m);
                    after.run();
                })
                .setNegativeButton("Cancelar", null);
        if (t != null) b.setNeutralButton("Apagar", (d, w) -> { Db.get(a).deleteTemplate(t.id); after.run(); });
        b.show();
    }
}
