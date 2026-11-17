package pt.relacoes.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import pt.relacoes.R;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Person;
import pt.relacoes.core.Reminder;
import pt.relacoes.data.Db;
import pt.relacoes.data.Prefs;
import pt.relacoes.notify.AlarmService;
import pt.relacoes.notify.Engine;
import pt.relacoes.notify.Notify;

/** "Por tratar": aparece ao desbloquear o telemóvel enquanto houver lembretes notificados sem decisão. */
public class PendingActivity extends BaseActivity {
    private LinearLayout list;
    private TextView header;

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(24), 0, 0);
        header = Ui.text(this, "", 24, R.color.text, true);
        header.setPadding(dp(20), dp(8), dp(20), dp(2));
        root.addView(header);
        TextView sub = Ui.text(this, "Marque como feito ou adie para outra hora, para nada ficar esquecido.", 14, R.color.text2, false);
        sub.setPadding(dp(20), 0, dp(20), dp(10));
        root.addView(sub);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(16));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(20));
        TextView later = Ui.button(this, "Decidir mais tarde", false);
        later.setOnClickListener(v -> finish());
        bottom.addView(later, Ui.lpWeight(1));
        root.addView(bottom);
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void load() {
        List<Reminder> p = AlarmService.pending(this);
        if (p.isEmpty()) { finish(); return; }
        Collections.sort(p, (a, b) -> Long.compare(a.dueAt, b.dueAt));
        header.setText(p.size() == 1 ? "1 lembrete por tratar" : p.size() + " lembretes por tratar");
        list.removeAllViews();
        Map<Long, Person> people = Db.get(this).peopleById();
        long now = System.currentTimeMillis();
        final int quick = Prefs.quickSnooze(this);
        for (final Reminder r : p) {
            Person person = people.get(r.personId);
            LinearLayout card = Ui.card(this);
            String[] t = Notify.texts(this, r);
            LinearLayout top = Views.personRow(this, person, -1, Fmt.when(r.dueAt, now) + (r.alarm ? " · ⏰" : ""), null, 0,
                    v -> startActivity(new Intent(this, PersonActivity.class).putExtra(PersonActivity.EXTRA_PERSON, r.personId)
                            .putExtra(PersonActivity.EXTRA_REMINDER, r.id)));
            ((TextView) ((LinearLayout) top.getChildAt(1)).getChildAt(0)).setText(t[0]);
            card.addView(top);
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding(0, dp(12), 0, 0);
            TextView done = Ui.button(this, "Feito", true);
            done.setPadding(dp(8), dp(10), dp(8), dp(10));
            done.setOnClickListener(v -> Auth.confirmDone(this, r, () -> { Engine.done(this, r.id); load(); }));
            LinearLayout.LayoutParams lp1 = Ui.lpWeight(1);
            lp1.setMarginEnd(dp(8));
            actions.addView(done, lp1);
            TextView plus = Ui.button(this, "+" + Fmt.duration(quick), false);
            plus.setPadding(dp(8), dp(10), dp(8), dp(10));
            plus.setOnClickListener(v -> { Engine.snooze(this, r.id, quick); load(); });
            LinearLayout.LayoutParams lp2 = Ui.lpWeight(1);
            lp2.setMarginEnd(dp(8));
            actions.addView(plus, lp2);
            TextView snooze = Ui.button(this, "Adiar…", false);
            snooze.setPadding(dp(8), dp(10), dp(8), dp(10));
            snooze.setOnClickListener(v -> startActivity(new Intent(this, SnoozeActivity.class).putExtra(AlarmActivity.EXTRA_ID, r.id)));
            actions.addView(snooze, Ui.lpWeight(1));
            card.addView(actions);
            list.addView(card);
        }
    }
}
