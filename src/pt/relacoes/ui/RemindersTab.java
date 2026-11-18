package pt.relacoes.ui;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pt.relacoes.R;
import pt.relacoes.core.Days;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Person;
import pt.relacoes.core.Recur;
import pt.relacoes.core.Reminder;
import pt.relacoes.data.Db;
import pt.relacoes.notify.Engine;

/** Lembretes: em atraso/hoje, próximos 7 dias e mais tarde. */
final class RemindersTab extends MainActivity.Tab {
    private LinearLayout content;

    RemindersTab(MainActivity a) { super(a); }

    @Override View.OnClickListener fab() { return v -> a.startActivity(new Intent(a, ReminderActivity.class)); }
    @Override String fabLabel() { return "Novo lembrete"; }

    @Override View build() {
        ScrollView sv = new ScrollView(a);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 90));
        sv.addView(content);
        refresh();
        return sv;
    }

    @Override void refresh() {
        content.removeAllViews();
        Db db = Db.get(a);
        Map<Long, Person> people = db.peopleById();
        long now = System.currentTimeMillis();
        long today = Days.of(now);
        long endToday = Recur.at(today + 1, 0), endWeek = Recur.at(today + 8, 0);
        List<Reminder> late = new ArrayList<>(), week = new ArrayList<>(), later = new ArrayList<>();
        for (Reminder r : db.reminders()) {
            if (r.done || !people.containsKey(r.personId)) continue;
            if (r.dueAt < endToday) late.add(r);
            else if (r.dueAt < endWeek) week.add(r);
            else later.add(r);
        }
        if (late.isEmpty() && week.isEmpty() && later.isEmpty()) {
            content.addView(Ui.heading(a, "Sem lembretes"));
            LinearLayout c = Ui.card(a);
            c.addView(Views.empty(a, "Os lembretes de aniversário e de manter contacto aparecem aqui automaticamente. "
                    + "Também pode criar os seus: \"comprar prenda\", \"agendar jantar\", \"devolver o livro\"…"));
            content.addView(c);
            return;
        }
        section("Em atraso e hoje", R.drawable.ic_warn, late, people, now);
        section("Próximos 7 dias", R.drawable.ic_notif, week, people, now);
        section("Mais tarde", R.drawable.ic_calendar, later, people, now);
    }

    private void section(String title, int icon, List<Reminder> rs, Map<Long, Person> people, long now) {
        if (rs.isEmpty()) return;
        content.addView(Ui.heading(a, title + " (" + rs.size() + ")"));
        for (Reminder r : rs) content.addView(card(a, r, people.get(r.personId), now, this::refresh));
    }

    /** Lembrete num cartão próprio, com botão redondo "Feito". */
    static View card(BaseActivity a, Reminder r, Person p, long now, Runnable after) {
        String title = r.kind == Reminder.KEEP ? "Falar com " + p.name : p.firstName() + " · " + r.text;
        if (r.kind == Reminder.BIRTHDAY && r.isManaged()) title = r.text;
        String sub = Fmt.when(r.dueAt, now);
        if (r.kind == Reminder.REPEAT) sub += " · " + r.repeatText();
        if (r.kind == Reminder.BIRTHDAY && r.offsetDays > 0) sub += " · " + r.offsetDays + " dias antes";
        if (r.alarm) sub += " · despertador";
        boolean overdue = r.dueAt <= now;
        LinearLayout row = Views.personCard(a, p, -1, sub, overdue ? "Atrasado" : null, Views.BADGE_DANGER,
                R.drawable.ic_check, "Feito", v -> Auth.confirmDone(a, r, () -> { Engine.done(a, r.id); after.run(); }),
                v -> a.startActivity(new Intent(a, ReminderActivity.class).putExtra(PersonActivity.EXTRA_REMINDER, r.id)));
        ((android.widget.TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(0)).setText(title);
        return row;
    }

    /** Linha de lembrete com botão "Feito". */
    static View row(BaseActivity a, Reminder r, Person p, long now, Runnable after) {
        String title = r.kind == Reminder.KEEP ? "Falar com " + p.name : p.firstName() + " · " + r.text;
        if (r.kind == Reminder.BIRTHDAY && r.isManaged()) title = "🎂 " + r.text;
        String sub = Reminder.kindName(r.kind) + " · " + Fmt.when(r.dueAt, now);
        if (r.kind == Reminder.REPEAT) sub += " · " + r.repeatText();
        if (r.kind == Reminder.BIRTHDAY && r.offsetDays > 0) sub += " · " + r.offsetDays + " dias antes";
        boolean overdue = r.dueAt <= now;
        LinearLayout row = Views.personRow(a, p, -1, sub, null, 0, v -> a.startActivity(new Intent(a, ReminderActivity.class)
                .putExtra(PersonActivity.EXTRA_REMINDER, r.id)));
        ((android.widget.TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(0)).setText(title);
        if (overdue) row.addView(Views.badge(a, "!", Views.BADGE_DANGER));
        if (r.alarm) row.addView(Views.badge(a, "⏰", Views.BADGE_ACCENT));
        row.addView(Ui.iconButton(a, R.drawable.ic_check, "Feito", v -> Auth.confirmDone(a, r, () -> {
            Engine.done(a, r.id);
            after.run();
        })));
        return row;
    }
}
