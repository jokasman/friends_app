package pt.amigos.ui;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

import pt.amigos.R;
import pt.amigos.core.Days;
import pt.amigos.core.Friend;
import pt.amigos.core.Schedule;
import pt.amigos.data.Db;

/** Criar ou editar um amigo. */
public class EditActivity extends BaseActivity {
    private static final int PICK_CONTACT = 7;
    private Friend f;
    private EditText name, phone, email, notes;
    private TextView bday;
    private LinearLayout every;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        long id = getIntent().getLongExtra(FriendActivity.EXTRA_ID, 0);
        Friend existing = id > 0 ? Db.get(this).friend(id) : null;
        f = existing != null ? existing : new Friend();
        if (existing == null) f.everyDays = 30;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = Views.topBar(this, existing == null ? "Novo amigo" : "Editar", true);
        TextView save = Ui.button(this, "Guardar", true);
        save.setOnClickListener(v -> save());
        bar.addView(save);
        col.addView(bar);

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(this, 24));
        sv.addView(content);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout who = Ui.card(this);
        who.addView(Ui.cardHeader(this, R.drawable.ic_person, "Contacto"));
        name = Views.input(this, "Nome", f.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        phone = Views.input(this, "Telefone", f.phone, InputType.TYPE_CLASS_PHONE);
        email = Views.input(this, "Email", f.email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        who.addView(name);
        who.addView(phone);
        who.addView(email);
        TextView imp = Ui.button(this, "Importar dos contactos do telemóvel", false);
        imp.setOnClickListener(v -> {
            try {
                startActivityForResult(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), PICK_CONTACT);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Não foi possível abrir os contactos.", Toast.LENGTH_SHORT).show();
            }
        });
        who.addView(imp, Ui.lpMW());
        content.addView(who);

        LinearLayout bd = Ui.card(this);
        bd.addView(Ui.cardHeader(this, R.drawable.ic_cake, "Aniversário"));
        bday = Ui.button(this, "", false);
        bday.setOnClickListener(v -> pickBirthday());
        bd.addView(bday, Ui.lpMW());
        showBirthday();
        content.addView(bd);

        LinearLayout ev = Ui.card(this);
        ev.addView(Ui.cardHeader(this, R.drawable.ic_notif, "Lembrar de falar"));
        every = new LinearLayout(this);
        every.setOrientation(LinearLayout.VERTICAL);
        ev.addView(every);
        drawEvery();
        content.addView(ev);

        LinearLayout nt = Ui.card(this);
        nt.addView(Ui.cardHeader(this, R.drawable.ic_edit, "Notas"));
        notes = Views.input(this, "Gostos, família, assuntos para a próxima conversa…", f.notes,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        nt.addView(notes);
        content.addView(nt);

        setContentView(col);
    }

    private void drawEvery() {
        every.removeAllViews();
        LinearLayout line = null;
        for (int i = 0; i < Schedule.EVERY.length; i++) {
            if (i % 3 == 0) {
                line = new LinearLayout(this);
                line.setPadding(0, 0, 0, Ui.dp(this, 8));
                every.addView(line);
            }
            final int d = Schedule.EVERY[i];
            TextView c = Ui.chip(this, Schedule.EVERY_NAMES[i], f.everyDays == d, 0);
            c.setOnClickListener(v -> { f.everyDays = d; drawEvery(); });
            line.addView(c);
        }
        TextView hint = Ui.text(this, f.everyDays > 0
                ? "Recebe um aviso quando passarem " + f.everyDays + " dias sem registar um contacto."
                : "Sem avisos para este amigo (os aniversários são sempre avisados).", 13, R.color.text2, false);
        every.addView(hint);
    }

    private void showBirthday() {
        bday.setText(f.hasBirthday() ? Schedule.birthdayText(f) : "Escolher data de aniversário");
    }

    private void pickBirthday() {
        int curYear = Calendar.getInstance().get(Calendar.YEAR);
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);
        NumberPicker day = new NumberPicker(this), month = new NumberPicker(this), year = new NumberPicker(this);
        day.setMinValue(1); day.setMaxValue(31); day.setValue(f.hasBirthday() ? f.bDay : 1);
        String[] ms = new String[12];
        for (int i = 0; i < 12; i++) ms[i] = Schedule.MONTHS[i];
        month.setMinValue(1); month.setMaxValue(12); month.setDisplayedValues(ms); month.setValue(f.hasBirthday() ? f.bMonth : 1);
        int first = 1900;
        String[] ys = new String[curYear - first + 2];
        ys[0] = "Sem ano";
        for (int y = first; y <= curYear; y++) ys[y - first + 1] = "" + y;
        year.setMinValue(0); year.setMaxValue(ys.length - 1); year.setDisplayedValues(ys);
        year.setValue(f.bYear >= first && f.bYear <= curYear ? f.bYear - first + 1 : 0);
        year.setWrapSelectorWheel(false);
        NumberPicker.OnValueChangeListener clamp = (p, o, n) -> {
            int y = year.getValue() == 0 ? 2000 : first + year.getValue() - 1;
            int max = Days.monthLength(y, month.getValue());
            day.setMaxValue(max);
        };
        month.setOnValueChangedListener(clamp);
        year.setOnValueChangedListener(clamp);
        clamp.onValueChange(null, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        box.addView(day, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));
        box.addView(month, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));
        box.addView(year, lp);
        new AlertDialog.Builder(this).setTitle("Aniversário").setView(box)
                .setPositiveButton("OK", (d, w) -> {
                    f.bDay = day.getValue();
                    f.bMonth = month.getValue();
                    f.bYear = year.getValue() == 0 ? 0 : first + year.getValue() - 1;
                    showBirthday();
                })
                .setNeutralButton("Sem aniversário", (d, w) -> { f.bDay = 0; f.bMonth = 0; f.bYear = 0; showBirthday(); })
                .setNegativeButton("Cancelar", null).show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK_CONTACT || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String[] proj = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor c = getContentResolver().query(uri, proj, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0), p = c.getString(1);
                if (n != null) name.setText(n);
                if (p != null) phone.setText(p);
            }
        } catch (RuntimeException e) {
            Toast.makeText(this, "Não foi possível ler o contacto.", Toast.LENGTH_SHORT).show();
        }
    }

    private void save() {
        String n = name.getText().toString().trim();
        if (n.isEmpty()) {
            name.setError("Indique o nome");
            name.requestFocus();
            return;
        }
        f.name = n;
        f.phone = phone.getText().toString().trim();
        f.email = email.getText().toString().trim();
        f.notes = notes.getText().toString().trim();
        boolean isNew = f.id == 0;
        long id = Db.get(this).save(f);
        if (isNew) startActivity(new Intent(this, FriendActivity.class).putExtra(FriendActivity.EXTRA_ID, id));
        finish();
    }
}
