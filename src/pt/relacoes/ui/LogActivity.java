package pt.relacoes.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Calendar;

import pt.relacoes.R;
import pt.relacoes.core.Days;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Person;
import pt.relacoes.data.Db;
import pt.relacoes.notify.Engine;

/** Registar um contacto ("Já falei"): data (pode ser passada), meio e nota. Em janela de diálogo. */
public class LogActivity extends BaseActivity {
    static final String EXTRA_MEDIUM = "medium", EXTRA_MESSAGE = "message", EXTRA_TEMPLATE = "template", EXTRA_ASK = "ask", EXTRA_BY_THEM = "by_them";
    private static final int[] FOLLOW = {0, 1, 2, 3, 7};
    private static final String[] FOLLOW_N = {"Não", "1 dia", "2 dias", "3 dias", "1 semana"};

    static Intent intent(Context c, long person, long reminder, int medium, String message, long template) {
        return new Intent(c, LogActivity.class).putExtra(PersonActivity.EXTRA_PERSON, person)
                .putExtra(PersonActivity.EXTRA_REMINDER, reminder).putExtra(EXTRA_MEDIUM, medium)
                .putExtra(EXTRA_MESSAGE, message).putExtra(EXTRA_TEMPLATE, template);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setFinishOnTouchOutside(false);
        Intent in = getIntent();
        long personId = in.getLongExtra(PersonActivity.EXTRA_PERSON, 0);
        long reminderId = in.getLongExtra(PersonActivity.EXTRA_REMINDER, 0);
        Person p = Db.get(this).person(personId);
        if (p == null) { finish(); return; }
        final int[] medium = {in.getIntExtra(EXTRA_MEDIUM, -1)};
        if (medium[0] < 0) medium[0] = p.lastMedium >= 0 ? p.lastMedium : Interaction.CALL;
        String message = in.getStringExtra(EXTRA_MESSAGE);
        if (message == null) message = "";
        long template = in.getLongExtra(EXTRA_TEMPLATE, 0);
        boolean ask = in.getBooleanExtra(EXTRA_ASK, false);
        final Calendar when = Calendar.getInstance();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 16));
        box.setBackground(Ui.round(Ui.col(this, R.color.surface), Ui.dp(this, 24)));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.addView(Views.avatar(this, p, 44, -1));
        TextView title = Ui.text(this, (ask ? "Falou com " + p.firstName() + "?" : "Registar contacto com " + p.firstName()), 18, R.color.text, true);
        title.setPadding(Ui.dp(this, 12), 0, 0, 0);
        head.addView(title, Ui.lpWeight(1));
        box.addView(head);
        box.addView(Ui.spacer(this, 14));

        box.addView(Ui.subLabel(this, "Data"));
        TextView date = Ui.button(this, "", false);
        DateFormat df = DateFormat.getDateInstance(DateFormat.FULL);
        Runnable show = () -> date.setText(Days.of(when.getTimeInMillis()) == Days.of(System.currentTimeMillis()) ? "Hoje"
                : df.format(when.getTime()));
        show.run();
        date.setOnClickListener(v -> {
            DatePickerDialog dp = new DatePickerDialog(this, (pk, y, m, d) -> { when.set(y, m, d); show.run(); },
                    when.get(Calendar.YEAR), when.get(Calendar.MONTH), when.get(Calendar.DAY_OF_MONTH));
            dp.getDatePicker().setMaxDate(System.currentTimeMillis());
            dp.show();
        });
        box.addView(date, Ui.lpMW());
        box.addView(Ui.spacer(this, 14));

        box.addView(Ui.subLabel(this, "Meio"));
        box.addView(Views.chooserWrap(this, Interaction.MEDIUMS, medium[0], 3, k -> medium[0] = k));
        box.addView(Ui.spacer(this, 6));

        // Quem tomou a iniciativa e follow-up se não houver resposta.
        final boolean[] byMe = {!in.getBooleanExtra(EXTRA_BY_THEM, false)};
        final int[] follow = {0};
        final LinearLayout followBox = new LinearLayout(this);
        followBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.subLabel(this, "Quem tomou a iniciativa"));
        box.addView(Views.chooser(this, new String[]{"Fui eu", "Foi " + p.firstName()}, byMe[0] ? 0 : 1, k -> {
            byMe[0] = k == 0;
            followBox.setVisibility(byMe[0] ? android.view.View.VISIBLE : android.view.View.GONE);
        }));
        followBox.addView(Ui.subLabel(this, "Follow-up se não houver resposta"));
        followBox.addView(Views.chooserWrap(this, FOLLOW_N, 0, 3, k -> follow[0] = FOLLOW[k]));
        followBox.addView(Views.hint(this, "Cria um lembrete que fecha sozinho quando registar o próximo contacto com "
                + p.firstName() + " (por exemplo, a resposta)."));
        followBox.setVisibility(byMe[0] ? android.view.View.VISIBLE : android.view.View.GONE);
        box.addView(followBox);
        box.addView(Ui.spacer(this, 10));

        if (!message.isEmpty()) {
            box.addView(Ui.subLabel(this, "Mensagem enviada"));
            TextView mt = Ui.text(this, message, 14, R.color.text, false);
            mt.setBackground(Ui.round(Ui.col(this, R.color.chip), Ui.dp(this, 12)));
            mt.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            Ui.ellipsize(mt, 5);
            box.addView(mt, Ui.lpMW());
            box.addView(Ui.spacer(this, 10));
        }
        EditText note = Views.multiline(this, "Nota (opcional): sobre o que falaram", "");
        box.addView(note);

        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(android.view.Gravity.END);
        TextView cancel = Ui.button(this, ask ? "Não" : "Cancelar", false);
        cancel.setOnClickListener(v -> finish());
        TextView ok = Ui.button(this, "Registar", true);
        final String msg = message;
        ok.setOnClickListener(v -> {
            Interaction i = new Interaction();
            i.personId = p.id;
            long now = System.currentTimeMillis();
            i.at = Days.of(when.getTimeInMillis()) == Days.of(now) ? now : when.getTimeInMillis();
            i.medium = medium[0];
            i.note = note.getText().toString().trim();
            i.message = msg;
            i.templateId = template;
            i.byMe = byMe[0];
            Engine.contacted(this, i, reminderId, byMe[0] ? follow[0] : 0);
            Toast.makeText(this, "Contacto registado", Toast.LENGTH_SHORT).show();
            finish();
        });
        btns.addView(cancel);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(Ui.dp(this, 10));
        btns.addView(ok, lp);
        box.addView(btns, Ui.lpMW());

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        setContentView(sv);
    }
}
