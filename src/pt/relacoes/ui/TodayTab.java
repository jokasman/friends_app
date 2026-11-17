package pt.relacoes.ui;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pt.relacoes.R;
import pt.relacoes.core.Days;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Person;
import pt.relacoes.core.Recur;
import pt.relacoes.core.Reminder;
import pt.relacoes.data.Db;
import pt.relacoes.data.Insights;
import pt.relacoes.data.Prefs;
import pt.relacoes.notify.Engine;

/** Hoje: sugestões do dia, lembretes pendentes, aniversários próximos e progresso da meta. */
final class TodayTab extends MainActivity.Tab {
    private LinearLayout content;

    TodayTab(MainActivity a) { super(a); }

    @Override View build() {
        ScrollView sv = new ScrollView(a);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 90));
        sv.addView(content);
        refresh();
        return sv;
    }

    @Override View.OnClickListener fab() { return v -> a.startActivity(new Intent(a, EditPersonActivity.class)); }
    @Override String fabLabel() { return "Nova pessoa"; }

    @Override void refresh() {
        content.removeAllViews();
        Db db = Db.get(a);
        Map<Long, Person> people = db.peopleById();
        long now = System.currentTimeMillis();
        long today = Days.of(now);

        if (people.isEmpty()) {
            LinearLayout card = Ui.card(a);
            card.addView(Ui.cardHeader(a, R.drawable.ic_people, "Bem-vindo"));
            card.addView(Ui.text(a, "Junte as pessoas que lhe importam, defina de quanto em quanto tempo quer falar com cada uma e "
                    + "a app ajuda a não deixar passar o tempo nem os aniversários.", 15, R.color.text2, false));
            card.addView(Ui.spacer(a, 14));
            TextView add = Ui.button(a, "Adicionar uma pessoa", true);
            add.setOnClickListener(v -> a.startActivity(new Intent(a, EditPersonActivity.class)));
            card.addView(add, Ui.lpMW());
            card.addView(Ui.spacer(a, 8));
            TextView imp = Ui.button(a, "Importar dos contactos do telemóvel", false);
            imp.setOnClickListener(v -> a.startActivity(new Intent(a, ImportActivity.class)));
            card.addView(imp, Ui.lpMW());
            content.addView(card);
            return;
        }

        // Topo coral: saudação e meta da semana com anel de progresso.
        int[] g = Insights.goal(a);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        LinearLayout hero = new LinearLayout(a);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setBackground(Ui.round(Ui.col(a, R.color.hero), Ui.dp(a, 32)));
        hero.setPadding(Ui.dp(a, 22), Ui.dp(a, 20), Ui.dp(a, 18), Ui.dp(a, 20));
        LinearLayout ht = new LinearLayout(a);
        ht.setOrientation(LinearLayout.VERTICAL);
        TextView hi = Ui.text(a, hour < 13 ? "Bom dia!" : hour < 20 ? "Boa tarde!" : "Boa noite!", 15, R.color.heroText, true);
        hi.setTypeface(Ui.font(a, 700));
        ht.addView(hi);
        String headline = g[0] == 0 ? "Com quem vai falar hoje?"
                : g[0] + (g[0] == 1 ? " pessoa contactada" : " pessoas contactadas") + " esta semana";
        TextView big = Ui.text(a, headline, 24, R.color.heroText, true);
        big.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 4));
        ht.addView(big);
        if (g[1] > 0) {
            String sub = g[0] >= g[1] ? "Meta cumprida!" : "Meta: " + g[1] + " por semana";
            if (g[2] > 0) sub += " · " + g[2] + (g[2] == 1 ? " semana seguida" : " semanas seguidas");
            TextView st = Ui.text(a, sub, 14, R.color.heroText, false);
            st.setAlpha(0.85f);
            ht.addView(st);
        }
        hero.addView(ht, Ui.lpWeight(1));
        if (g[1] > 0) {
            Ring ring = new Ring(a, Ui.col(a, R.color.heroTrack), Ui.col(a, R.color.heroRing), Ui.col(a, R.color.heroText), 8);
            ring.set(g[0] / (float) g[1], g[0] + "/" + g[1]);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Ui.dp(a, 82), Ui.dp(a, 82));
            rlp.setMarginStart(Ui.dp(a, 12));
            hero.addView(ring, rlp);
        }
        LinearLayout.LayoutParams hlp = Ui.lpMW();
        hlp.setMargins(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 14), Ui.dp(a, 8));
        content.addView(hero, hlp);

        // Sugestões do dia.
        List<Person> sug = Insights.suggestions(a, Math.max(2, Prefs.suggestCount(a)));
        LinearLayout sc = Ui.card(a);
        sc.addView(Ui.cardHeader(a, R.drawable.ic_chat, "Sugestões de hoje"));
        if (sug.isEmpty()) sc.addView(Views.empty(a, "Está tudo em dia. Ninguém à espera de notícias."));
        for (Person p : sug) {
            long d = Insights.daysSince(p, today);
            String sub = (p.lastAt > 0 ? "Último contacto " + Fmt.ago(d) + (p.lastMedium >= 0 ? " · " + Interaction.mediumName(p.lastMedium) : "")
                    : "Ainda sem contactos") + (p.notes.isEmpty() ? "" : "\n" + p.notes.replace('\n', ' '));
            LinearLayout row = Views.personRow(a, p, Insights.health(p, today), sub, null, 0, v -> openPerson(p.id, 0));
            row.addView(Ui.iconButton(a, R.drawable.ic_check, "Já falei", v -> a.startActivity(LogActivity.intent(a, p.id, 0, -1, "", 0))));
            sc.addView(row);
        }
        content.addView(sc);

        // Lembretes pendentes (até ao fim do dia).
        long endOfDay = Recur.at(today + 1, 0);
        List<Reminder> pend = new ArrayList<>();
        for (Reminder r : db.reminders()) if (!r.done && r.dueAt < endOfDay && people.containsKey(r.personId)) pend.add(r);
        if (!pend.isEmpty()) {
            LinearLayout rc = Ui.card(a);
            rc.addView(Ui.cardHeader(a, R.drawable.ic_notif, "Lembretes pendentes"));
            for (Reminder r : pend) rc.addView(RemindersTab.row(a, r, people.get(r.personId), now, this::refresh));
            content.addView(rc);
        }

        // Aniversários próximos.
        List<Person> bd = Insights.upcomingBirthdays(a, 30);
        if (!bd.isEmpty()) {
            LinearLayout bc = Ui.card(a);
            bc.addView(Ui.cardHeader(a, R.drawable.ic_cake, "Aniversários próximos"));
            for (Person p : bd) {
                long day = Recur.nextBirthday(p, today);
                long d = day - today;
                int age = Insights.age(p, day);
                String sub = Fmt.birthday(p).replaceAll(" de \\d{4}$", "") + (age > 0 ? " · faz " + age + " anos" : "");
                String badge = d == 0 ? "Hoje 🎉" : d == 1 ? "Amanhã" : "Em " + d + " dias";
                bc.addView(Views.personRow(a, p, -1, sub, badge, d <= 1 ? Views.BADGE_WARN : Views.BADGE_ACCENT, v -> {
                    if (d == 0) a.startActivity(new Intent(a, BirthdayActivity.class).putExtra(PersonActivity.EXTRA_PERSON, p.id));
                    else openPerson(p.id, 0);
                }));
            }
            content.addView(bc);
        }

        if (!Engine.canExact(a)) {
            LinearLayout w = Ui.card(a);
            w.addView(Ui.cardHeader(a, R.drawable.ic_notif, "Avisos pontuais desligados"));
            w.addView(Ui.text(a, "Sem a permissão de alarmes exatos, os avisos podem chegar atrasados. Ative-a nas definições do Android.",
                    14, R.color.text2, false));
            content.addView(w);
        }
    }

    private void openPerson(long id, long reminder) {
        a.startActivity(new Intent(a, PersonActivity.class).putExtra(PersonActivity.EXTRA_PERSON, id)
                .putExtra(PersonActivity.EXTRA_REMINDER, reminder));
    }
}
