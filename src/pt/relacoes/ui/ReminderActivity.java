package pt.relacoes.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import pt.relacoes.R;
import pt.relacoes.core.Days;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Person;
import pt.relacoes.core.Recur;
import pt.relacoes.core.Reminder;
import pt.relacoes.data.Db;
import pt.relacoes.data.Prefs;
import pt.relacoes.notify.Engine;

/** Criar ou editar um lembrete (único, recorrente ou de aniversário). */
public class ReminderActivity extends BaseActivity {
    private static final int[] NAG = {0, 15, 30, 60, 120, 240, 1440};
    private static final String[] NAG_N = {"Não insistir", "15 min", "30 min", "1 hora", "2 horas", "4 horas", "1 dia"};
    private static final int[] OFFS = {0, 1, 3, 7, 14, 30};
    private static final String[] OFFS_N = {"No dia", "1 dia antes", "3 dias antes", "1 semana antes", "2 semanas antes", "1 mês antes"};
    private static final String[] UNITS = {"Dias", "Semanas", "Meses", "Anos"};

    private Reminder r;
    private Person person;
    private boolean isNew;
    private LinearLayout content;
    private EditText text, every;
    private final Calendar when = Calendar.getInstance();
    private int minutes;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        long id = getIntent().getLongExtra(PersonActivity.EXTRA_REMINDER, 0);
        Db db = Db.get(this);
        r = id > 0 ? db.reminder(id) : null;
        isNew = r == null;
        if (isNew) {
            r = new Reminder();
            r.kind = Reminder.ONCE;
            r.nagMin = Prefs.nagDefault(this);
            r.alarm = true;
            r.personId = getIntent().getLongExtra(PersonActivity.EXTRA_PERSON, 0);
            when.add(Calendar.DAY_OF_MONTH, 1);
            int m = Prefs.morning(this);
            when.set(Calendar.HOUR_OF_DAY, m / 60);
            when.set(Calendar.MINUTE, m % 60);
        } else {
            when.setTimeInMillis(r.dueAt);
        }
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        minutes = when.get(Calendar.HOUR_OF_DAY) * 60 + when.get(Calendar.MINUTE);
        person = r.personId > 0 ? db.person(r.personId) : null;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = Views.topBar(this, isNew ? "Novo lembrete" : "Lembrete", true);
        if (!isNew && !r.isManaged()) bar.addView(Ui.iconButton(this, R.drawable.ic_delete, "Apagar", v -> delete()));
        TextView save = Ui.button(this, "Guardar", true);
        save.setOnClickListener(v -> save());
        bar.addView(save);
        col.addView(bar);
        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(this, 24));
        sv.addView(content);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(col);
        text = Views.input(this, "O quê? Ex.: comprar prenda, agendar jantar, devolver o livro", r.text,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        every = Views.input(this, "1", String.valueOf(Math.max(1, r.repeatN)), InputType.TYPE_CLASS_NUMBER);
        render();
    }

    private void render() {
        content.removeAllViews();
        boolean auto = r.isManaged();

        LinearLayout pc = Ui.card(this);
        pc.addView(Ui.cardHeader(this, R.drawable.ic_person, "Pessoa"));
        if (person != null) {
            pc.addView(Views.personRow(this, person, -1, isNew ? "Toque para mudar" : null, null, 0, isNew ? v -> pickPerson() : null));
        } else {
            TextView pick = Ui.button(this, "Escolher pessoa", false);
            pick.setOnClickListener(v -> pickPerson());
            pc.addView(pick, Ui.lpMW());
        }
        content.addView(pc);

        if (auto) {
            LinearLayout ac = Ui.card(this);
            ac.addView(Ui.cardHeader(this, r.kind == Reminder.BIRTHDAY ? R.drawable.ic_cake : R.drawable.ic_chat, r.text));
            String info = r.kind == Reminder.KEEP
                    ? "Lembrete automático de manter contacto (" + (person != null ? Fmt.everyName(person.everyDays).toLowerCase() : "") + "). "
                      + "É recalculado sempre que regista um contacto."
                    : "Lembrete automático de aniversário. Altere os avisos na ficha da pessoa (Editar → Aniversário).";
            ac.addView(Ui.text(this, info, 14, R.color.text2, false));
            ac.addView(Ui.spacer(this, 8));
            ac.addView(Ui.optionRow(this, Fmt.when(r.dueAt, System.currentTimeMillis()), "Próximo aviso", null));
            content.addView(ac);
        } else {
            LinearLayout wc = Ui.card(this);
            wc.addView(Ui.cardHeader(this, R.drawable.ic_notif, "Lembrete"));
            if (text.getParent() != null) ((ViewGroup) text.getParent()).removeView(text);
            wc.addView(text);
            wc.addView(Ui.subLabel(this, "Tipo"));
            List<String> kinds = new ArrayList<>();
            kinds.add("Único");
            kinds.add("Recorrente");
            boolean bdayOk = person != null && person.hasBirthday();
            if (bdayOk || r.kind == Reminder.BIRTHDAY) kinds.add("Aniversário");
            int sel = r.kind == Reminder.REPEAT ? 1 : r.kind == Reminder.BIRTHDAY ? 2 : 0;
            wc.addView(Views.chooser(this, kinds.toArray(new String[0]), sel, k -> {
                r.kind = k == 1 ? Reminder.REPEAT : k == 2 ? Reminder.BIRTHDAY : Reminder.ONCE;
                render();
            }));
            if (!bdayOk && person != null) wc.addView(Views.hint(this, "Para lembretes de aniversário, indique primeiro a data de aniversário na ficha."));
            content.addView(wc);

            LinearLayout dc = Ui.card(this);
            dc.addView(Ui.cardHeader(this, R.drawable.ic_calendar, "Quando"));
            if (r.kind == Reminder.BIRTHDAY) {
                int os = 0;
                for (int i = 0; i < OFFS.length; i++) if (OFFS[i] == r.offsetDays) os = i;
                dc.addView(Views.chooserWrap(this, OFFS_N, os, 3, k -> r.offsetDays = OFFS[k]));
                if (person != null && person.hasBirthday()) dc.addView(Views.hint(this, "Aniversário: " + Fmt.birthday(person)));
            } else {
                TextView date = Ui.button(this, android.text.format.DateFormat.getLongDateFormat(this).format(when.getTime()), false);
                date.setOnClickListener(v -> new DatePickerDialog(this, (pk, y, m, d) -> { when.set(y, m, d); render(); },
                        when.get(Calendar.YEAR), when.get(Calendar.MONTH), when.get(Calendar.DAY_OF_MONTH)).show());
                dc.addView(date, Ui.lpMW());
                dc.addView(Ui.spacer(this, 8));
            }
            TextView time = Ui.button(this, "Às " + Fmt.hm(minutes), false);
            time.setOnClickListener(v -> new TimePickerDialog(this, (pk, h, m) -> { minutes = h * 60 + m; render(); },
                    minutes / 60, minutes % 60, DateFormat.is24HourFormat(this)).show());
            if (r.kind == Reminder.BIRTHDAY) dc.addView(Ui.spacer(this, 4));
            dc.addView(time, Ui.lpMW());
            if (r.kind == Reminder.REPEAT) {
                dc.addView(Ui.divider(this));
                dc.addView(Ui.subLabel(this, "Repetir a cada"));
                if (every.getParent() != null) ((ViewGroup) every.getParent()).removeView(every);
                dc.addView(every);
                dc.addView(Views.chooser(this, UNITS, Math.max(0, r.repeatUnit - 1), k -> r.repeatUnit = k + 1));
            }
            content.addView(dc);
        }

        LinearLayout al = Ui.card(this);
        al.addView(Ui.cardHeader(this, R.drawable.ic_bell, "Despertador"));
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setText("Tocar como despertador");
        sw.setTextColor(Ui.col(this, R.color.text));
        sw.setTextSize(15);
        sw.setChecked(r.alarm);
        sw.setOnCheckedChangeListener((v, on) -> r.alarm = on);
        al.addView(sw, Ui.lpMW());
        al.addView(Views.hint(this, "Toca com som de alarme mesmo em silêncio, em ecrã inteiro, até marcar como feito"
                + (Prefs.requireBiometric(this) ? " (com a impressão digital)" : "") + " ou adiar."));
        content.addView(al);

        LinearLayout nc = Ui.card(this);
        nc.addView(Ui.cardHeader(this, R.drawable.ic_repeat, "Insistência"));
        int ns = 0;
        for (int i = 0; i < NAG.length; i++) if (NAG[i] == r.nagMin) ns = i;
        nc.addView(Views.chooserWrap(this, NAG_N, ns, 3, k -> r.nagMin = NAG[k]));
        nc.addView(Views.hint(this, "Enquanto não for marcado como feito, volta a avisar com esta periodicidade "
                + "(sem insistir entre as " + Fmt.hm(Prefs.quietStart(this)) + " e as " + Fmt.hm(Prefs.morning(this)) + ")."));
        content.addView(nc);

        if (r.auto == Reminder.AUTO_FOLLOWUP) {
            LinearLayout fc = Ui.card(this);
            fc.addView(Views.hint(this, "Follow-up automático: fecha sozinho quando registar o próximo contacto com esta pessoa."));
            content.addView(fc, 1);
        }
        if (!isNew) {
            TextView done = Ui.button(this, "Marcar como feito", true);
            done.setOnClickListener(v -> Auth.confirmDone(this, Db.get(this).reminder(r.id), () -> { Engine.done(this, r.id); finish(); }));
            LinearLayout.LayoutParams lp = Ui.lpMW();
            lp.setMargins(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), 0);
            content.addView(done, lp);
        }
    }

    private void pickPerson() {
        List<Person> all = Db.get(this).people();
        if (all.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("Adicione primeiro uma pessoa.").setPositiveButton("OK", null).show();
            return;
        }
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) names[i] = all.get(i).name;
        new AlertDialog.Builder(this).setTitle("Pessoa").setItems(names, (d, w) -> {
            person = all.get(w);
            r.personId = person.id;
            if (r.kind == Reminder.BIRTHDAY && !person.hasBirthday()) r.kind = Reminder.ONCE;
            render();
        }).show();
    }

    private void save() {
        if (person == null) { pickPerson(); return; }
        if (!r.isManaged()) {
            String t = text.getText().toString().trim();
            if (t.isEmpty()) { text.setError("Escreva o lembrete"); text.requestFocus(); return; }
            r.text = t;
            if (r.kind == Reminder.BIRTHDAY) {
                if (!person.hasBirthday()) return;
                r.dueAt = Recur.birthdayDue(person, r.offsetDays, Days.of(System.currentTimeMillis()), minutes);
            } else {
                when.set(Calendar.HOUR_OF_DAY, minutes / 60);
                when.set(Calendar.MINUTE, minutes % 60);
                r.dueAt = when.getTimeInMillis();
            }
            if (r.kind == Reminder.REPEAT) {
                try { r.repeatN = Math.max(1, Integer.parseInt(every.getText().toString().trim())); } catch (NumberFormatException e) { r.repeatN = 1; }
                if (r.repeatUnit < 1) r.repeatUnit = Reminder.MONTH;
            }
        }
        Db.get(this).save(r);
        Engine.saved(this, r);
        finish();
    }

    private void delete() {
        new AlertDialog.Builder(this).setMessage("Apagar este lembrete?")
                .setPositiveButton("Apagar", (d, w) -> {
                    Engine.deleted(this, r.id);
                    finish();
                })
                .setNegativeButton("Cancelar", null).show();
    }
}
