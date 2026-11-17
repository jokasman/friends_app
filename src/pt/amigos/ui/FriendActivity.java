package pt.amigos.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import pt.amigos.R;
import pt.amigos.core.Contact;
import pt.amigos.core.Days;
import pt.amigos.core.Friend;
import pt.amigos.core.Schedule;
import pt.amigos.data.Db;

/** Ficha de um amigo: atalhos para contactar, registo de contactos e histórico. */
public class FriendActivity extends BaseActivity {
    public static final String EXTRA_ID = "id";
    private long id;
    private LinearLayout content;
    private TextView title;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        id = getIntent().getLongExtra(EXTRA_ID, 0);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = Views.topBar(this, "", true);
        title = (TextView) bar.getChildAt(1);
        bar.addView(Ui.iconButton(this, R.drawable.ic_edit, "Editar",
                v -> startActivity(new Intent(this, EditActivity.class).putExtra(EXTRA_ID, id))));
        bar.addView(Ui.iconButton(this, R.drawable.ic_delete, "Apagar", v -> confirmDelete()));
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

    private void render() {
        Friend f = Db.get(this).friend(id);
        if (f == null) { finish(); return; }
        title.setText(f.name);
        content.removeAllViews();
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        long today = Days.of(now.getTimeInMillis());

        // Cabeçalho com avatar e resumo.
        LinearLayout head = Ui.card(this);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.addView(Views.avatar(this, f, 76));
        TextView name = Ui.text(this, f.name, 22, R.color.text, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        head.addView(name);
        TextView last = Ui.text(this, MainActivity.lastText(f, today), 14, R.color.text2, false);
        last.setGravity(Gravity.CENTER);
        head.addView(last);
        long od = MainActivity.overdue(f, today);
        if (f.everyDays > 0) {
            String s = Schedule.everyName(f.everyDays) + " · " + (od > 0 ? "atrasado " + od + " dias"
                    : od == 0 ? "falar hoje" : "próximo " + Schedule.inDays((int) -od));
            TextView t = Ui.text(this, s, 13, od >= 0 ? R.color.warn : R.color.accent, true);
            t.setBackground(Ui.round(Ui.col(this, od >= 0 ? R.color.warnSoft : R.color.accentSoft), Ui.dp(this, 10)));
            t.setPadding(Ui.dp(this, 12), Ui.dp(this, 5), Ui.dp(this, 12), Ui.dp(this, 5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 10);
            head.addView(t, lp);
        }

        // Atalhos de contacto.
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout acts = new LinearLayout(this);
        acts.setPadding(0, Ui.dp(this, 16), 0, 0);
        String tel = f.phone.replaceAll("[^0-9+]", "");
        if (!tel.isEmpty()) {
            acts.addView(action(R.drawable.ic_phone, "Ligar", () -> open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + tel)), f, Contact.CALL)));
            acts.addView(action(R.drawable.ic_send, "SMS", () -> open(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + tel)), f, Contact.MESSAGE)));
            String wa = tel.startsWith("+") ? tel.substring(1) : tel.startsWith("00") ? tel.substring(2) : (tel.length() == 9 ? "351" + tel : tel);
            acts.addView(action(R.drawable.ic_chat, "WhatsApp", () -> open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + wa)), f, Contact.MESSAGE)));
        }
        if (!f.email.trim().isEmpty()) {
            acts.addView(action(R.drawable.ic_mail, "Email", () -> open(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + f.email.trim())), f, Contact.MESSAGE)));
        }
        if (acts.getChildCount() > 0) {
            hs.addView(acts);
            head.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView reg = Ui.button(this, "Registar contacto", true);
        LinearLayout.LayoutParams rlp = Ui.lpMW();
        rlp.topMargin = Ui.dp(this, 16);
        reg.setOnClickListener(v -> registerDialog(f, Contact.TALK));
        head.addView(reg, rlp);
        content.addView(head);

        // Detalhes.
        LinearLayout info = Ui.card(this);
        info.addView(Ui.cardHeader(this, R.drawable.ic_person, "Detalhes"));
        if (f.hasBirthday()) {
            int d = Schedule.daysToBirthday(f, year, today);
            int age = Schedule.turning(f, year, today);
            String sub = (d == 0 ? "É hoje!" : "Faltam " + d + " dias") + (age > 0 ? " · faz " + age + " anos" : "");
            info.addView(Ui.optionRow(this, "Aniversário: " + Schedule.birthdayText(f), sub, null));
        }
        if (!f.phone.isEmpty()) info.addView(Ui.optionRow(this, f.phone, "Telefone", null));
        if (!f.email.isEmpty()) info.addView(Ui.optionRow(this, f.email, "Email", null));
        info.addView(Ui.optionRow(this, Schedule.everyName(f.everyDays), "Lembrete para falar", null));
        if (!f.notes.trim().isEmpty()) {
            info.addView(Ui.divider(this));
            info.addView(Ui.subLabel(this, "Notas"));
            info.addView(Ui.text(this, f.notes.trim(), 15, R.color.text, false));
        }
        content.addView(info);

        // Histórico.
        LinearLayout hist = Ui.card(this);
        hist.addView(Ui.cardHeader(this, R.drawable.ic_history, "Histórico"));
        List<Contact> ks = Db.get(this).contacts(id);
        if (ks.isEmpty()) hist.addView(Ui.text(this, "Ainda não há contactos registados.", 15, R.color.text2, false));
        DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
        for (Contact k : ks) {
            String t = Contact.kindName(k.kind) + " · " + df.format(new Date(k.at)) + " (" + Schedule.ago(today - Days.of(k.at)) + ")";
            LinearLayout r = Ui.optionRow(this, t, k.note.isEmpty() ? null : k.note, null);
            r.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage("Apagar este registo?")
                        .setPositiveButton("Apagar", (d, w) -> { Db.get(this).deleteContact(k); render(); })
                        .setNegativeButton("Cancelar", null).show();
                return true;
            });
            hist.addView(r);
        }
        if (!ks.isEmpty()) {
            TextView hint = Ui.text(this, "Toque sem largar num registo para o apagar.", 12, R.color.text2, false);
            hint.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), 0, 0);
            hist.addView(hint);
        }
        content.addView(hist);
    }

    private View action(int icon, String label, Runnable r) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        b.setBackground(Ui.ripple(this, null, Ui.dp(this, 14)));
        android.widget.ImageView ic = Ui.icon(this, icon, R.color.accent, 46, 11);
        ic.setBackground(Ui.round(Ui.col(this, R.color.accentSoft), Ui.dp(this, 23)));
        b.addView(ic);
        TextView t = Ui.text(this, label, 12, R.color.text, false);
        t.setPadding(0, Ui.dp(this, 4), 0, 0);
        b.addView(t);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    /** Abre a app de contacto e, ao voltar, pergunta se quer registar o contacto. */
    private void open(Intent i, Friend f, int kind) {
        try {
            startActivity(i);
            pendingKind = kind;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nenhuma app disponível para esta ação.", Toast.LENGTH_SHORT).show();
        }
    }

    private int pendingKind = -1;

    @Override protected void onRestart() {
        super.onRestart();
        if (pendingKind >= 0) {
            int k = pendingKind;
            pendingKind = -1;
            Friend f = Db.get(this).friend(id);
            if (f != null) registerDialog(f, k);
        }
    }

    private void registerDialog(Friend f, int kind) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        final int[] sel = {kind};
        final Calendar when = Calendar.getInstance();

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        hs.addView(chips);
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            chips.removeAllViews();
            for (int i = 0; i < Contact.KIND_NAMES.length; i++) {
                final int k = i;
                TextView c = Ui.chip(this, Contact.KIND_NAMES[i], sel[0] == i, 0);
                c.setOnClickListener(v -> { sel[0] = k; draw[0].run(); });
                chips.addView(c);
            }
        };
        draw[0].run();
        box.addView(hs);

        TextView date = Ui.button(this, "", false);
        DateFormat df = DateFormat.getDateInstance(DateFormat.FULL);
        Runnable showDate = () -> date.setText("Data: " + (Days.of(when.getTimeInMillis()) == Days.of(System.currentTimeMillis())
                ? "hoje" : df.format(when.getTime())));
        showDate.run();
        date.setOnClickListener(v -> {
            DatePickerDialog dp = new DatePickerDialog(this, (p, y, m, d) -> {
                when.set(y, m, d);
                showDate.run();
            }, when.get(Calendar.YEAR), when.get(Calendar.MONTH), when.get(Calendar.DAY_OF_MONTH));
            dp.getDatePicker().setMaxDate(System.currentTimeMillis());
            dp.show();
        });
        LinearLayout.LayoutParams dlp = Ui.lpMW();
        dlp.setMargins(0, Ui.dp(this, 14), 0, Ui.dp(this, 12));
        box.addView(date, dlp);

        EditText note = Views.input(this, "Nota (opcional): sobre o que falaram", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(note);

        new AlertDialog.Builder(this).setTitle("Contacto com " + f.name).setView(box)
                .setPositiveButton("Registar", (d, w) -> {
                    Contact k = new Contact();
                    k.friendId = f.id;
                    k.kind = sel[0];
                    k.note = note.getText().toString().trim();
                    long nowMs = System.currentTimeMillis();
                    k.at = Days.of(when.getTimeInMillis()) == Days.of(nowMs) ? nowMs : when.getTimeInMillis();
                    Db.get(this).addContact(k);
                    render();
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void confirmDelete() {
        Friend f = Db.get(this).friend(id);
        if (f == null) return;
        new AlertDialog.Builder(this).setTitle("Apagar " + f.name + "?")
                .setMessage("O amigo e todo o histórico de contactos serão apagados.")
                .setPositiveButton("Apagar", (d, w) -> { Db.get(this).delete(id); finish(); })
                .setNegativeButton("Cancelar", null).show();
    }
}
