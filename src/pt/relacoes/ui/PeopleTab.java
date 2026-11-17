package pt.relacoes.ui;

import android.content.Intent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pt.relacoes.R;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Group;
import pt.relacoes.core.Health;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Person;
import pt.relacoes.data.Db;
import pt.relacoes.data.Insights;

/** Pessoas: lista com a cor da saúde da relação, filtro por grupo, pesquisa e ordenação. */
final class PeopleTab extends MainActivity.Tab {
    private static long groupFilter = 0;
    private static int sort = 0; // 0 = tempo sem contacto, 1 = nome
    private LinearLayout list, chips;
    private EditText search;

    PeopleTab(MainActivity a) { super(a); }

    @Override View.OnClickListener fab() { return v -> a.startActivity(new Intent(a, EditPersonActivity.class)); }
    @Override String fabLabel() { return "Nova pessoa"; }

    @Override View build() {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        search = Views.input(a, "Procurar por nome, notas ou telefone…", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout.LayoutParams slp = Ui.lpMW();
        slp.setMargins(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 14), Ui.dp(a, 6));
        search.setLayoutParams(slp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable s) { refresh(); }
        });
        col.addView(search);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(a);
        chips.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 14), Ui.dp(a, 4));
        hs.addView(chips);
        col.addView(hs);
        ScrollView sv = new ScrollView(a);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, Ui.dp(a, 90));
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        refresh();
        return col;
    }

    static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    @Override void refresh() {
        Db db = Db.get(a);
        List<Group> groups = db.groups();
        boolean found = groupFilter == 0;
        for (Group g : groups) if (g.id == groupFilter) found = true;
        if (!found) groupFilter = 0;
        chips.removeAllViews();
        TextView all = Ui.chip(a, "Todos", groupFilter == 0, 0);
        all.setOnClickListener(v -> { groupFilter = 0; refresh(); });
        chips.addView(all);
        for (Group g : groups) {
            TextView c = Ui.chip(a, g.name, groupFilter == g.id, g.color);
            c.setOnClickListener(v -> { groupFilter = g.id; refresh(); });
            chips.addView(c);
        }
        TextView sortChip = Ui.chip(a, sort == 0 ? "↓ Tempo sem contacto" : "↓ Nome", false, 0);
        sortChip.setOnClickListener(v -> { sort = 1 - sort; refresh(); });
        chips.addView(sortChip);

        list.removeAllViews();
        long today = Insights.today();
        Map<Long, List<Long>> member = db.allMemberships();
        String q = fold(search.getText().toString().trim());
        List<Person> ps = new ArrayList<>();
        for (Person p : db.people()) {
            if (groupFilter != 0) {
                List<Long> gs = member.get(p.id);
                if (gs == null || !gs.contains(groupFilter)) continue;
            }
            if (!q.isEmpty() && !fold(p.name).contains(q) && !fold(p.notes).contains(q) && !p.phone.replace(" ", "").contains(q)) continue;
            ps.add(p);
        }
        if (sort == 0) {
            Collections.sort(ps, (x, y) -> {
                long dx = Insights.daysSince(x, today), dy = Insights.daysSince(y, today);
                if (dx != dy) return Long.compare(dy, dx);
                return x.name.compareToIgnoreCase(y.name);
            });
        }
        LinearLayout card = Ui.card(a);
        card.addView(Ui.sectionLabel(a, ps.size() + (ps.size() == 1 ? " pessoa" : " pessoas")));
        if (ps.isEmpty()) {
            card.addView(Views.empty(a, db.people().isEmpty() ? "Ainda não adicionou ninguém." : "Ninguém corresponde ao filtro."));
            if (db.people().isEmpty()) {
                card.addView(Ui.spacer(a, 12));
                TextView imp = Ui.button(a, "Importar dos contactos do telemóvel", false);
                imp.setOnClickListener(v -> a.startActivity(new Intent(a, ImportActivity.class)));
                card.addView(imp, Ui.lpMW());
            }
        }
        for (Person p : ps) {
            int h = Insights.health(p, today);
            String sub = p.lastAt > 0 ? Fmt.date(p.lastAt, System.currentTimeMillis()) + " (" + Fmt.ago(today - pt.relacoes.core.Days.of(p.lastAt)) + ")"
                    + (p.lastMedium >= 0 ? " · " + Interaction.mediumName(p.lastMedium) : "") : "Sem contactos registados";
            String badge = h == Health.RED ? "Em atraso" : h == Health.YELLOW ? "Em breve" : null;
            card.addView(Views.personRow(a, p, h, sub, badge, h == Health.RED ? Views.BADGE_DANGER : Views.BADGE_WARN,
                    v -> a.startActivity(new Intent(a, PersonActivity.class).putExtra(PersonActivity.EXTRA_PERSON, p.id))));
        }
        list.addView(card);
    }
}
