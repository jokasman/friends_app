package pt.relacoes.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import pt.relacoes.R;
import pt.relacoes.core.Days;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Gift;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Person;
import pt.relacoes.core.Recur;
import pt.relacoes.core.Reminder;
import pt.relacoes.data.Db;
import pt.relacoes.data.Insights;
import pt.relacoes.data.Prefs;
import pt.relacoes.notify.Engine;

/** Hoje: topo coral com a meta da semana, sugestões do dia, lembretes pendentes e aniversários próximos. */
final class TodayTab extends MainActivity.Tab {
    private LinearLayout content;

    TodayTab(MainActivity a) { super(a); }

    @Override View build() {
        ScrollView sv = new ScrollView(a);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(a, 24));
        sv.addView(content);
        refresh();
        return sv;
    }

    private int dp(float v) { return Ui.dp(a, v); }

    @Override void refresh() {
        content.removeAllViews();
        Db db = Db.get(a);
        Map<Long, Person> people = db.peopleById();
        long now = System.currentTimeMillis();
        long today = Days.of(now);

        content.addView(hero());

        if (people.isEmpty()) {
            content.addView(Ui.heading(a, "Comece pelo seu círculo"));
            LinearLayout card = Ui.card(a);
            card.addView(Ui.text(a, "Junte as pessoas que lhe importam, defina de quanto em quanto tempo quer falar com cada uma e "
                    + "a app ajuda a não deixar passar o tempo nem os aniversários.", 15, R.color.text2, false));
            card.addView(Ui.spacer(a, 14));
            card.addView(darkButton("Adicionar uma pessoa", v -> a.startActivity(new Intent(a, EditPersonActivity.class))), Ui.lpMW());
            card.addView(Ui.spacer(a, 8));
            TextView imp = Ui.button(a, "Importar dos contactos do telemóvel", false);
            imp.setOnClickListener(v -> a.startActivity(new Intent(a, ImportActivity.class)));
            card.addView(imp, Ui.lpMW());
            content.addView(card);
            return;
        }

        // Contactos detetados pelas notificações, por confirmar.
        List<Db.Detection> det = db.pendingDetections();
        if (!det.isEmpty()) {
            content.addView(Ui.heading(a, "Contactos detetados"));
            for (Db.Detection d : det) {
                Person p = people.get(d.personId);
                if (p == null) continue;
                String sub = (d.medium == Interaction.CALL ? "Chamada" : "Mensagem por " + Interaction.mediumName(d.medium))
                        + " · " + Fmt.when(d.at, now);
                LinearLayout card = Views.personCard(a, p, -1, sub, null, 0, R.drawable.ic_check, "Registar contacto",
                        v -> { pt.relacoes.notify.Detect.accept(a, d.id); refresh(); }, v -> openPerson(p.id, 0));
                android.widget.ImageView no = Ui.iconButton(a, R.drawable.ic_close, "Ignorar", v -> {
                    pt.relacoes.notify.Detect.dismiss(a, d.id);
                    refresh();
                });
                card.addView(no, card.getChildCount() - 1);
                content.addView(card);
            }
            TextView hint = Views.hint(a, "Toque em ✓ para registar no histórico (contacto recebido) ou em ✕ para ignorar.");
            hint.setPadding(dp(22), dp(2), dp(22), 0);
            content.addView(hint);
        }

        // Sugestões do dia: um cartão por pessoa, com botão de contacto direto.
        content.addView(Ui.heading(a, "Sugestões para hoje"));
        List<Person> sug = Insights.suggestions(a, Math.max(2, Prefs.suggestCount(a)));
        if (sug.isEmpty()) {
            LinearLayout card = Ui.itemCard(a, R.color.surface);
            card.addView(Ui.text(a, "Está tudo em dia. Ninguém à espera de notícias.", 15, R.color.text2, false), Ui.lpWeight(1));
            content.addView(card);
        }
        for (Person p : sug) {
            String sub = p.lastAt > 0 ? Fmt.ago(Insights.daysSince(p, today)).replaceFirst("^h", "H")
                    + (p.lastMedium >= 0 ? " · " + Interaction.mediumName(p.lastMedium) : "") : "Ainda sem contactos";
            int medium = quickMedium(p);
            content.addView(Views.personCard(a, p, Insights.health(p, today), sub, null, 0, mediumIcon(medium),
                    "Contactar " + p.firstName(), v -> Contacting.start(a, p, medium, 0), v -> openPerson(p.id, 0)));
        }

        // Lembretes pendentes (até ao fim do dia).
        long endOfDay = Recur.at(today + 1, 0);
        List<Reminder> pend = new ArrayList<>();
        for (Reminder r : db.reminders()) if (!r.done && r.dueAt < endOfDay && people.containsKey(r.personId)) pend.add(r);
        if (!pend.isEmpty()) {
            content.addView(Ui.heading(a, "Por fazer hoje"));
            for (Reminder r : pend) content.addView(RemindersTab.card(a, r, people.get(r.personId), now, this::refresh));
        }

        // Aniversários próximos: cartões amarelos com ideias de prenda.
        List<Person> bd = Insights.upcomingBirthdays(a, 30);
        if (!bd.isEmpty()) {
            content.addView(Ui.heading(a, "Aniversários"));
            int n = 0;
            for (Person p : bd) {
                if (n++ >= 5) break;
                content.addView(birthdayCard(p, today));
            }
        }

        if (!Engine.canExact(a)) {
            content.addView(Ui.heading(a, "Atenção"));
            LinearLayout w = Ui.card(a);
            w.addView(Ui.text(a, "Sem a permissão de alarmes exatos, os avisos podem chegar atrasados. Veja em Definições › Verificação de fiabilidade.",
                    14, R.color.text2, false));
            content.addView(w);
        }
    }

    // ------------------------------------------------------------------ Topo coral

    private View hero() {
        int[] g = Insights.goal(a);
        LinearLayout hero = new LinearLayout(a);
        hero.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.col(a, R.color.hero));
        float r = dp(36);
        bg.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        hero.setBackground(bg);
        hero.setPadding(dp(20), dp(12), dp(16), dp(26));

        // Logótipo, nome e botões.
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(logo(), new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView name = Ui.text(a, a.getString(R.string.app_name), 21, R.color.heroText, true);
        name.setTypeface(Ui.font(a, 800));
        name.setPadding(dp(10), 0, 0, 0);
        top.addView(name, Ui.lpWeight(1));
        top.addView(heroButton(R.drawable.ic_chart, "Resumo mensal", v -> a.startActivity(new Intent(a, SummaryActivity.class))));
        View gap = new View(a);
        top.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));
        top.addView(heroButton(R.drawable.ic_settings, "Definições", v -> a.startActivity(new Intent(a, SettingsActivity.class))));
        hero.addView(top, Ui.lpMW());

        // Saudação, título e anel da meta.
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(18), 0, 0);
        LinearLayout ht = new LinearLayout(a);
        ht.setOrientation(LinearLayout.VERTICAL);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        TextView hi = Ui.text(a, hour < 13 ? "Bom dia!" : hour < 20 ? "Boa tarde!" : "Boa noite!", 15, R.color.heroText, true);
        ht.addView(hi);
        String headline = g[0] == 0 ? "Com quem vai falar hoje?"
                : "Já falou com " + g[0] + (g[0] == 1 ? " pessoa" : " pessoas") + " esta semana";
        TextView big = Ui.text(a, headline, 26, R.color.heroText, true);
        big.setTypeface(Ui.font(a, 800));
        big.setLineSpacing(0, 0.95f);
        big.setPadding(0, dp(2), 0, 0);
        ht.addView(big);
        if (g[1] > 0 && (g[0] >= g[1] || g[2] > 0)) {
            String sub = g[0] >= g[1] ? "Meta da semana cumprida!" : g[2] + (g[2] == 1 ? " semana seguida" : " semanas seguidas") + " com a meta cumprida";
            TextView st = Ui.text(a, sub, 14, R.color.heroText, false);
            st.setAlpha(0.85f);
            st.setPadding(0, dp(6), 0, 0);
            ht.addView(st);
        }
        row.addView(ht, Ui.lpWeight(1));
        if (g[1] > 0) {
            Ring ring = new Ring(a, Ui.col(a, R.color.heroTrack), Ui.col(a, R.color.heroRing), Ui.col(a, R.color.heroText), 9);
            ring.set(g[0] / (float) g[1], g[0] + "/" + g[1]);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(78), dp(78));
            rlp.setMarginStart(dp(14));
            row.addView(ring, rlp);
        }
        hero.addView(row, Ui.lpMW());
        LinearLayout.LayoutParams lp = Ui.lpMW();
        lp.bottomMargin = dp(4);
        hero.setLayoutParams(lp);
        return hero;
    }

    /** Logótipo: sol amarelo dentro de um círculo creme. */
    private View logo() {
        FrameLayout f = new FrameLayout(a);
        View outer = new View(a);
        GradientDrawable o = new GradientDrawable();
        o.setShape(GradientDrawable.OVAL);
        o.setColor(0xFFFFF8F0);
        outer.setBackground(o);
        f.addView(outer, new FrameLayout.LayoutParams(dp(32), dp(32)));
        View sun = new View(a);
        GradientDrawable s = new GradientDrawable();
        s.setShape(GradientDrawable.OVAL);
        s.setColor(Ui.col(a, R.color.sun));
        sun.setBackground(s);
        f.addView(sun, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        return f;
    }

    private View heroButton(int icon, String desc, View.OnClickListener l) {
        android.widget.ImageView iv = Ui.icon(a, icon, R.color.heroText, 44, 11);
        iv.setBackground(Ui.ripple(a, Ui.round(Ui.col(a, R.color.heroTrack), dp(22)), dp(22)));
        iv.setContentDescription(desc);
        iv.setOnClickListener(l);
        return iv;
    }

    // ------------------------------------------------------------------ Cartões

    private View birthdayCard(Person p, long today) {
        long day = Recur.nextBirthday(p, today);
        long d = day - today;
        int age = Insights.age(p, day);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.ripple(a, Ui.round(Ui.col(a, R.color.warnSoft), dp(24)), dp(24)));
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        LinearLayout.LayoutParams lp = Ui.lpMW();
        lp.setMargins(dp(16), dp(5), dp(16), dp(5));
        card.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView ic = Ui.icon(a, R.drawable.ic_gift, R.color.accent, 40, 9);
        ic.setBackground(Ui.round(Ui.col(a, R.color.surface), dp(20)));
        top.addView(ic);
        String when = d == 0 ? "hoje" : d == 1 ? "amanhã" : Fmt.inDays(d);
        String title = d == 0 ? "Hoje é o aniversário de " + p.firstName()
                : p.firstName() + (age > 0 ? " faz " + age + " anos " : " faz anos ") + when;
        if (d == 0 && age > 0) title += " (" + age + " anos)";
        TextView t = Ui.text(a, title, 16, R.color.text, true);
        t.setTypeface(Ui.font(a, 800));
        t.setPadding(dp(12), 0, 0, 0);
        top.addView(t, Ui.lpWeight(1));
        card.addView(top);

        List<String> ideas = new ArrayList<>();
        for (Gift g : Db.get(a).gifts(p.id)) if (!g.done) ideas.add(g.text);
        String sub = !ideas.isEmpty() ? (ideas.size() == 1 ? "Ideia: " : "Ideias: ") + String.join(" · ", ideas)
                : Fmt.birthday(p).replaceAll(" de \\d{4}$", "") + (p.likes.isEmpty() ? "" : " · Gosta de " + p.likes);
        TextView st = Ui.text(a, sub, 14, R.color.warn, false);
        st.setPadding(0, dp(10), 0, 0);
        Ui.ellipsize(st, 3);
        card.addView(st);
        if (d <= 1) {
            TextView b = darkButton("Preparar mensagem de parabéns", v -> a.startActivity(new Intent(a, BirthdayActivity.class)
                    .putExtra(PersonActivity.EXTRA_PERSON, p.id)));
            LinearLayout.LayoutParams blp = Ui.lpMW();
            blp.topMargin = dp(12);
            card.addView(b, blp);
        }
        card.setOnClickListener(v -> openPerson(p.id, 0));
        return card;
    }

    /** Botão escuro do desenho (texto claro sobre a cor do texto). */
    private TextView darkButton(String label, View.OnClickListener l) {
        TextView b = Ui.text(a, label, 15, R.color.bg, true);
        b.setTypeface(Ui.font(a, 800));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(13), dp(16), dp(13));
        b.setBackground(Ui.ripple(a, Ui.round(Ui.col(a, R.color.text), dp(20)), dp(20)));
        b.setOnClickListener(l);
        return b;
    }

    /** Meio para o botão de contacto direto: o último usado, senão WhatsApp, email ou partilha. */
    static int quickMedium(Person p) {
        boolean tel = !Contacting.digits(p.phone).isEmpty();
        if (tel && (p.lastMedium == Interaction.CALL || p.lastMedium == Interaction.SMS)) return p.lastMedium;
        if (tel) return Interaction.WHATSAPP;
        if (!p.email.trim().isEmpty()) return Interaction.EMAIL;
        return Interaction.OTHER;
    }

    static int mediumIcon(int medium) {
        switch (medium) {
            case Interaction.CALL: return R.drawable.ic_phone;
            case Interaction.EMAIL: return R.drawable.ic_mail;
            case Interaction.OTHER: return R.drawable.ic_share;
            case Interaction.SMS: return R.drawable.ic_send;
            default: return R.drawable.ic_chat;
        }
    }

    private void openPerson(long id, long reminder) {
        a.startActivity(new Intent(a, PersonActivity.class).putExtra(PersonActivity.EXTRA_PERSON, id)
                .putExtra(PersonActivity.EXTRA_REMINDER, reminder));
    }
}
