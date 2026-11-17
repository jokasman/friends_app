package pt.relacoes.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;

import pt.relacoes.R;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Person;
import pt.relacoes.core.Recur;
import pt.relacoes.data.Insights;

/** Resumo mensal: quem contactou, quem ficou atrasado, aniversários do mês seguinte e evolução das metas. */
public class SummaryActivity extends BaseActivity {
    private int year, month;
    private LinearLayout content;
    private TextView title;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Calendar c = Calendar.getInstance();
        year = getIntent().getIntExtra("y", c.get(Calendar.YEAR));
        month = getIntent().getIntExtra("m", c.get(Calendar.MONTH) + 1);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = Views.topBar(this, "", true);
        title = bar.findViewWithTag("title");
        bar.addView(Ui.iconButton(this, R.drawable.ic_prev, "Mês anterior", v -> shift(-1)));
        bar.addView(Ui.iconButton(this, R.drawable.ic_next, "Mês seguinte", v -> shift(1)));
        col.addView(bar);
        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(this, 24));
        sv.addView(content);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(col);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void shift(int d) {
        month += d;
        if (month < 1) { month = 12; year--; }
        if (month > 12) { month = 1; year++; }
        render();
    }

    private void render() {
        title.setText("Resumo de " + Fmt.MONTHS[month - 1] + " " + year);
        content.removeAllViews();
        Insights.Month m = Insights.month(this, year, month);
        long today = Insights.today();

        LinearLayout nums = Ui.card(this);
        LinearLayout row = new LinearLayout(this);
        row.addView(stat(String.valueOf(m.contacted.size()), "pessoas contactadas"), Ui.lpWeight(1));
        row.addView(stat(String.valueOf(m.interactions), "contactos registados"), Ui.lpWeight(1));
        row.addView(stat(String.valueOf(m.late.size()), "em atraso agora"), Ui.lpWeight(1));
        nums.addView(row);
        content.addView(nums);

        LinearLayout gc = Ui.card(this);
        gc.addView(Ui.cardHeader(this, R.drawable.ic_chart, "Metas semanais"));
        if (m.goal <= 0) gc.addView(Views.empty(this, "Sem meta semanal definida (Definições)."));
        else {
            gc.addView(Ui.text(this, m.weeksMet + " de " + m.weeks.size() + " semanas com a meta cumprida (" + m.goal + " pessoas por semana)",
                    14, R.color.text2, false));
            gc.addView(Ui.spacer(this, 10));
            int max = m.goal;
            for (long[] w : m.weeks) max = (int) Math.max(max, w[1]);
            for (long[] w : m.weeks) {
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
                TextView l = Ui.text(this, Fmt.date(Recur.at(w[0], 0), System.currentTimeMillis()), 13, R.color.text2, false);
                r.addView(l, new LinearLayout.LayoutParams(Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout track = new LinearLayout(this);
                track.setBackground(Ui.round(Ui.col(this, R.color.chip), Ui.dp(this, 6)));
                View fill = new View(this);
                fill.setBackground(Ui.round(Ui.col(this, w[1] >= m.goal ? R.color.accent : R.color.text2), Ui.dp(this, 6)));
                track.setWeightSum(max);
                track.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(this, 12), Math.max(0.0001f, w[1])));
                r.addView(track, new LinearLayout.LayoutParams(0, Ui.dp(this, 12), 1));
                TextView n = Ui.text(this, String.valueOf(w[1]), 13, R.color.text, true);
                n.setPadding(Ui.dp(this, 8), 0, 0, 0);
                r.addView(n);
                gc.addView(r);
            }
        }
        content.addView(gc);

        LinearLayout cc = Ui.card(this);
        cc.addView(Ui.cardHeader(this, R.drawable.ic_check, "Com quem falou"));
        if (m.contacted.isEmpty()) cc.addView(Views.empty(this, "Nenhum contacto registado neste mês."));
        for (Person p : m.contacted) cc.addView(row(p, null, today));
        content.addView(cc);

        LinearLayout lc = Ui.card(this);
        lc.addView(Ui.cardHeader(this, R.drawable.ic_warn, "Em atraso face à frequência"));
        if (m.late.isEmpty()) lc.addView(Views.empty(this, "Ninguém em atraso. 👏"));
        for (Person p : m.late) lc.addView(row(p, "Sem contacto " + Fmt.ago(Insights.daysSince(p, today)).replace("há ", "há "), today));
        content.addView(lc);

        int nm = month == 12 ? 1 : month + 1;
        LinearLayout bc = Ui.card(this);
        bc.addView(Ui.cardHeader(this, R.drawable.ic_cake, "Aniversários em " + Fmt.MONTHS[nm - 1]));
        if (m.nextMonthBirthdays.isEmpty()) bc.addView(Views.empty(this, "Nenhum aniversário."));
        for (Person p : m.nextMonthBirthdays) {
            int y = month == 12 ? year + 1 : year;
            int age = p.bYear > 0 ? y - p.bYear : 0;
            bc.addView(row(p, p.bDay + " de " + Fmt.MONTHS[nm - 1] + (age > 0 ? " · faz " + age + " anos" : ""), today));
        }
        content.addView(bc);
    }

    private View stat(String big, String label) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView b = Ui.text(this, big, 28, R.color.accent, true);
        b.setGravity(Gravity.CENTER);
        l.addView(b);
        TextView t = Ui.text(this, label, 12, R.color.text2, false);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        return l;
    }

    private View row(Person p, String sub, long today) {
        return Views.personRow(this, p, Insights.health(p, today), sub, null, 0,
                v -> startActivity(new Intent(this, PersonActivity.class).putExtra(PersonActivity.EXTRA_PERSON, p.id)));
    }
}
