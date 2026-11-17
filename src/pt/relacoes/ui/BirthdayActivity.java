package pt.relacoes.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import pt.relacoes.R;
import pt.relacoes.core.Days;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Gift;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Person;
import pt.relacoes.core.Recur;
import pt.relacoes.core.Reminder;
import pt.relacoes.core.Template;
import pt.relacoes.data.Db;
import pt.relacoes.data.Insights;

/** Mensagem de aniversário: sugestões da biblioteca, edição e envio por WhatsApp ou partilha. */
public class BirthdayActivity extends BaseActivity {
    private Person p;
    private long reminderId;
    private EditText editor;
    private long chosenTemplate;
    private LinearLayout suggestionsBox;
    private List<Template> pool;
    private int shown;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Db.get(this).person(getIntent().getLongExtra(PersonActivity.EXTRA_PERSON, 0));
        if (p == null) { finish(); return; }
        reminderId = getIntent().getLongExtra(PersonActivity.EXTRA_REMINDER, 0);
        long today = Days.of(System.currentTimeMillis());
        if (reminderId <= 0) {
            for (Reminder r : Db.get(this).reminders(p.id)) {
                if (!r.done && r.kind == Reminder.BIRTHDAY && r.offsetDays == 0 && Days.of(r.dueAt) <= today) { reminderId = r.id; break; }
            }
        }
        long bday = p.hasBirthday() ? Recur.nextBirthday(p, today - 1) : today;
        int age = Insights.age(p, bday);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Views.topBar(this, "Mensagem de parabéns", true));
        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(this, 24));
        sv.addView(content);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout head = Ui.card(this);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.addView(Views.avatar(this, p, 76, -1));
        long d = bday - today;
        TextView t = Ui.text(this, "🎂 " + (d <= 0 ? "Hoje é o aniversário de " : d == 1 ? "Amanhã é o aniversário de " : "Aniversário de ") + p.firstName(),
                19, R.color.text, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 10), 0, 0);
        head.addView(t);
        if (age > 0) {
            TextView a = Ui.text(this, "Faz " + age + " anos", 15, R.color.text2, false);
            a.setGravity(Gravity.CENTER);
            head.addView(a);
        }
        content.addView(head);

        // Prendas e gostos.
        List<Gift> gifts = Contacting.openGifts(this, p.id);
        if (!gifts.isEmpty() || !p.likes.isEmpty() || !p.avoid.isEmpty()) {
            LinearLayout gc = Ui.card(this);
            gc.addView(Ui.cardHeader(this, R.drawable.ic_gift, "Ideias de prenda"));
            for (Gift g : gifts) gc.addView(Ui.text(this, "• " + g.text, 15, R.color.text, false));
            if (!p.likes.isEmpty()) gc.addView(Ui.optionRow(this, p.likes, "Gosta de", null));
            if (!p.avoid.isEmpty()) gc.addView(Ui.optionRow(this, p.avoid, "Evitar", null));
            content.addView(gc);
        }

        // Mensagens enviadas antes.
        List<Interaction> sent = new ArrayList<>();
        for (Interaction i : Db.get(this).interactions(p.id)) if (!i.message.isEmpty()) sent.add(i);
        if (!sent.isEmpty()) {
            LinearLayout hc = Ui.card(this);
            hc.addView(Ui.cardHeader(this, R.drawable.ic_history, "Mensagens enviadas antes"));
            for (int k = 0; k < Math.min(4, sent.size()); k++) {
                Interaction i = sent.get(k);
                hc.addView(Ui.optionRow(this, Fmt.date(i.at, System.currentTimeMillis()) + " · " + Interaction.mediumName(i.medium), i.message, null));
            }
            hc.addView(Views.hint(this, "As sugestões abaixo evitam os modelos que já usou com " + p.firstName() + "."));
            content.addView(hc);
        }

        // Sugestões.
        LinearLayout sc = Ui.card(this);
        sc.addView(Ui.cardHeader(this, R.drawable.ic_message, "Escolha uma mensagem"));
        suggestionsBox = new LinearLayout(this);
        suggestionsBox.setOrientation(LinearLayout.VERTICAL);
        sc.addView(suggestionsBox);
        pool = Contacting.suggestions(this, p, Template.BIRTHDAY, age);
        TextView more = Ui.button(this, "Outras sugestões", false);
        more.setOnClickListener(v -> showSuggestions(age));
        LinearLayout.LayoutParams mlp = Ui.lpMW();
        mlp.topMargin = Ui.dp(this, 6);
        sc.addView(more, mlp);
        content.addView(sc);

        LinearLayout ec = Ui.card(this);
        ec.addView(Ui.cardHeader(this, R.drawable.ic_edit, "A sua mensagem"));
        editor = Views.multiline(this, "Escreva ou escolha uma sugestão", "");
        editor.setMinLines(4);
        ec.addView(editor);
        boolean tel = !Contacting.digits(p.phone).isEmpty();
        TextView wa = Ui.button(this, "Enviar por WhatsApp", true);
        wa.setOnClickListener(v -> send(Interaction.WHATSAPP));
        ec.addView(wa, Ui.lpMW());
        if (tel) {
            TextView sms = Ui.button(this, "Enviar por SMS", false);
            sms.setOnClickListener(v -> send(Interaction.SMS));
            LinearLayout.LayoutParams lp = Ui.lpMW();
            lp.topMargin = Ui.dp(this, 8);
            ec.addView(sms, lp);
        }
        TextView share = Ui.button(this, "Partilhar noutra app…", false);
        share.setOnClickListener(v -> send(Interaction.OTHER));
        LinearLayout.LayoutParams lp = Ui.lpMW();
        lp.topMargin = Ui.dp(this, 8);
        ec.addView(share, lp);
        ec.addView(Views.hint(this, "Ao voltar, a app pergunta se quer registar a mensagem no histórico de " + p.firstName() + "."));
        content.addView(ec);

        setContentView(col);
        showSuggestions(age);
        if (!pool.isEmpty()) choose(pool.get(0), age);
    }

    private void showSuggestions(int age) {
        suggestionsBox.removeAllViews();
        if (pool.isEmpty()) {
            suggestionsBox.addView(Views.empty(this, "Não há modelos de aniversário. Crie-os no separador Modelos."));
            return;
        }
        int n = Math.min(4, pool.size());
        for (int k = 0; k < n; k++) {
            Template tp = pool.get((shown + k) % pool.size());
            TextView tv = Ui.text(this, Template.fill(tp.text, p, age), 15, R.color.text, false);
            tv.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.chip), Ui.dp(this, 14)), Ui.dp(this, 14)));
            tv.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            tv.setOnClickListener(v -> choose(tp, age));
            LinearLayout.LayoutParams lp = Ui.lpMW();
            lp.bottomMargin = Ui.dp(this, 8);
            suggestionsBox.addView(tv, lp);
        }
        shown += n;
    }

    private void choose(Template t, int age) {
        chosenTemplate = t.id;
        editor.setText(Template.fill(t.text, p, age));
        editor.setSelection(editor.getText().length());
    }

    private void send(int medium) {
        String msg = editor.getText().toString().trim();
        if (msg.isEmpty()) { editor.setError("Escreva ou escolha uma mensagem"); return; }
        if (Contacting.open(this, p, medium, msg)) expectContact(p.id, medium, msg, chosenTemplate, reminderId);
    }
}
