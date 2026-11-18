package pt.relacoes.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import pt.relacoes.R;
import pt.relacoes.core.Days;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Group;
import pt.relacoes.core.Person;
import pt.relacoes.data.Db;
import pt.relacoes.data.Photos;
import pt.relacoes.data.Prefs;
import pt.relacoes.notify.Engine;

/** Criar ou editar uma pessoa. */
public class EditPersonActivity extends BaseActivity {
    private static final int PICK_PHOTO = 3;
    private static final int[] BEFORE = {0, 1, 3, 7, 14};
    private static final String[] BEFORE_N = {"Não", "1 dia", "3 dias", "1 semana", "2 semanas"};

    private Person p;
    private int oldM, oldD, oldY;
    private String oldPhoto;
    private EditText name, nickname, phone, email, notes, likes, avoid;
    private TextView bday;
    private FrameLayout photoBox;
    private LinearLayout groupsBox, bdayBox;
    private final List<Long> groups = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        long id = getIntent().getLongExtra(PersonActivity.EXTRA_PERSON, 0);
        Person existing = id > 0 ? Db.get(this).person(id) : null;
        p = existing != null ? existing : new Person();
        if (existing == null) {
            p.everyDays = Prefs.defaultEvery(this);
            p.bdayBefore = Prefs.defaultBdayBefore(this);
        } else {
            groups.addAll(Db.get(this).groupsOf(p.id));
        }
        oldM = p.bMonth; oldD = p.bDay; oldY = p.bYear; oldPhoto = p.photo;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = Views.topBar(this, existing == null ? "Nova pessoa" : "Editar", true);
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

        // Contacto e foto.
        LinearLayout who = Ui.card(this);
        photoBox = new FrameLayout(this);
        photoBox.setOnClickListener(v -> photoMenu());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        plp.bottomMargin = Ui.dp(this, 4);
        who.addView(photoBox, plp);
        TextView ph = Views.hint(this, "Toque na imagem para escolher uma foto");
        ph.setGravity(Gravity.CENTER);
        who.addView(ph, Ui.lpMW());
        who.addView(Ui.spacer(this, 10));
        name = Views.input(this, "Nome", p.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        phone = Views.input(this, "Telefone", p.phone, InputType.TYPE_CLASS_PHONE);
        email = Views.input(this, "Email", p.email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        who.addView(name);
        nickname = Views.input(this, "Como lhe chama (ex.: Zé, Mãe, Dr. Silva)", p.nickname,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        who.addView(nickname);
        TextView nh = Views.hint(this, "Usado nas mensagens sugeridas. Se ficar vazio, usa o primeiro nome.");
        nh.setPadding(Ui.dp(this, 2), 0, 0, Ui.dp(this, 10));
        who.addView(nh);
        who.addView(phone);
        who.addView(email);
        content.addView(who);
        drawPhoto();

        // Grupos.
        LinearLayout gc = Ui.card(this);
        gc.addView(Ui.cardHeader(this, R.drawable.ic_people, "Grupos"));
        groupsBox = new LinearLayout(this);
        groupsBox.setOrientation(LinearLayout.VERTICAL);
        gc.addView(groupsBox);
        content.addView(gc);
        drawGroups();

        // Manter contacto.
        LinearLayout ev = Ui.card(this);
        ev.addView(Ui.cardHeader(this, R.drawable.ic_chat, "Manter contacto"));
        int sel = 0;
        for (int i = 0; i < Fmt.EVERY.length; i++) if (Fmt.EVERY[i] == p.everyDays) sel = i;
        ev.addView(Views.chooserWrap(this, Fmt.EVERY_NAMES, sel, 3, k -> p.everyDays = Fmt.EVERY[k]));
        ev.addView(Views.hint(this, "Falar pelo menos com esta frequência. O prazo conta a partir do último contacto registado."));
        content.addView(ev);

        // Aniversário.
        LinearLayout bd = Ui.card(this);
        bd.addView(Ui.cardHeader(this, R.drawable.ic_cake, "Aniversário"));
        bday = Ui.button(this, "", false);
        bday.setOnClickListener(v -> pickBirthday());
        bd.addView(bday, Ui.lpMW());
        bdayBox = new LinearLayout(this);
        bdayBox.setOrientation(LinearLayout.VERTICAL);
        bd.addView(bdayBox);
        content.addView(bd);
        showBirthday();

        // Mensagens.
        LinearLayout mc = Ui.card(this);
        mc.addView(Ui.cardHeader(this, R.drawable.ic_message, "Mensagens sugeridas"));
        mc.addView(Ui.subLabel(this, "Tom"));
        mc.addView(Views.chooser(this, new String[]{"Pessoal", "Profissional"}, p.tone, k -> p.tone = k));
        mc.addView(Ui.subLabel(this, "Tamanho"));
        mc.addView(Views.chooser(this, new String[]{"Curtas", "Longas"}, p.longMessages ? 1 : 0, k -> p.longMessages = k == 1));
        content.addView(mc);

        // Notas e preferências.
        LinearLayout nt = Ui.card(this);
        nt.addView(Ui.cardHeader(this, R.drawable.ic_edit, "Notas e preferências"));
        notes = Views.multiline(this, "Notas: assuntos para a próxima conversa, família, trabalho…", p.notes);
        likes = Views.multiline(this, "Gosta de…", p.likes);
        avoid = Views.multiline(this, "Coisas a evitar…", p.avoid);
        nt.addView(notes);
        nt.addView(likes);
        nt.addView(avoid);
        content.addView(nt);

        setContentView(col);
    }

    private void drawPhoto() {
        photoBox.removeAllViews();
        Person tmp = new Person();
        tmp.name = name == null || name.getText().toString().trim().isEmpty() ? "?" : name.getText().toString();
        tmp.photo = p.photo;
        tmp.id = p.id;
        photoBox.addView(Views.avatar(this, tmp, 92, -1));
        android.widget.ImageView cam = Ui.icon(this, R.drawable.ic_camera, R.color.onAccent, 30, 6);
        cam.setBackground(Ui.round(Ui.col(this, R.color.accent), Ui.dp(this, 15)));
        photoBox.addView(cam, new FrameLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30), Gravity.BOTTOM | Gravity.END));
    }

    private void photoMenu() {
        if (p.photo.isEmpty()) { pickPhoto(); return; }
        new AlertDialog.Builder(this).setItems(new String[]{"Escolher outra foto", "Remover foto"}, (d, w) -> {
            if (w == 0) pickPhoto();
            else { p.photo = ""; drawPhoto(); }
        }).show();
    }

    private void pickPhoto() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), PICK_PHOTO);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Não há app para escolher imagens.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_PHOTO && res == RESULT_OK && data != null && data.getData() != null) {
            String f = Photos.save(this, data.getData());
            if (f == null) Toast.makeText(this, "Não foi possível ler a imagem.", Toast.LENGTH_SHORT).show();
            else {
                if (!p.photo.isEmpty() && !p.photo.equals(oldPhoto)) Photos.delete(this, p.photo);
                p.photo = f;
                drawPhoto();
            }
        }
    }

    private void drawGroups() {
        groupsBox.removeAllViews();
        List<Group> all = Db.get(this).groups();
        LinearLayout line = null;
        int i = 0;
        for (Group g : all) {
            if (i++ % 3 == 0) {
                line = new LinearLayout(this);
                line.setPadding(0, 0, 0, Ui.dp(this, 8));
                groupsBox.addView(line);
            }
            boolean on = groups.contains(g.id);
            TextView c = Ui.chip(this, (on ? "✓ " : "") + g.name, on, g.color);
            c.setOnClickListener(v -> {
                if (groups.contains(g.id)) groups.remove(g.id); else groups.add(g.id);
                drawGroups();
            });
            line.addView(c);
        }
        TextView add = Ui.chip(this, "+ Novo grupo", false, 0);
        add.setOnClickListener(v -> GroupsActivity.editGroup(this, null, g -> { groups.add(g.id); drawGroups(); }));
        if (line == null || line.getChildCount() >= 3) {
            line = new LinearLayout(this);
            groupsBox.addView(line);
        }
        line.addView(add);
    }

    private void showBirthday() {
        bday.setText(p.hasBirthday() ? Fmt.birthday(p) : "Escolher data de aniversário");
        bdayBox.removeAllViews();
        if (!p.hasBirthday()) return;
        bdayBox.addView(Ui.spacer(this, 12));
        CheckBox on = new CheckBox(this);
        on.setText("Avisar no próprio dia");
        on.setTextColor(Ui.col(this, R.color.text));
        on.setTextSize(15);
        on.setChecked(p.bdayOnDay);
        on.setOnCheckedChangeListener((v, c) -> p.bdayOnDay = c);
        bdayBox.addView(on);
        bdayBox.addView(Ui.subLabel(this, "Avisar também antes"));
        int sel = 0;
        for (int i = 0; i < BEFORE.length; i++) if (BEFORE[i] == p.bdayBefore) sel = i;
        if (sel == 0 && p.bdayBefore > 0) { p.bdayBefore = 0; }
        bdayBox.addView(Views.chooserWrap(this, BEFORE_N, sel, 3, k -> p.bdayBefore = BEFORE[k]));
        bdayBox.addView(Views.hint(this, "Os avisos de aniversário insistem até serem marcados como feitos. "
                + "Na ficha da pessoa pode juntar mais lembretes (ex.: comprar prenda 7 dias antes)."));
    }

    private void pickBirthday() {
        int curYear = Calendar.getInstance().get(Calendar.YEAR);
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);
        NumberPicker day = new NumberPicker(this), month = new NumberPicker(this), year = new NumberPicker(this);
        day.setMinValue(1); day.setMaxValue(31); day.setValue(p.hasBirthday() ? p.bDay : 1);
        month.setMinValue(1); month.setMaxValue(12); month.setDisplayedValues(Fmt.MONTHS.clone()); month.setValue(p.hasBirthday() ? p.bMonth : 1);
        final int first = 1900;
        String[] ys = new String[curYear - first + 2];
        ys[0] = "Sem ano";
        for (int y = first; y <= curYear; y++) ys[y - first + 1] = String.valueOf(y);
        year.setMinValue(0); year.setMaxValue(ys.length - 1); year.setDisplayedValues(ys);
        year.setValue(p.bYear >= first && p.bYear <= curYear ? p.bYear - first + 1 : 0);
        year.setWrapSelectorWheel(false);
        NumberPicker.OnValueChangeListener clamp = (pk, o, n) -> {
            int y = year.getValue() == 0 ? 2000 : first + year.getValue() - 1;
            day.setMaxValue(Days.monthLength(y, month.getValue()));
        };
        month.setOnValueChangedListener(clamp);
        year.setOnValueChangedListener(clamp);
        clamp.onValueChange(null, 0, 0);
        box.addView(day, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));
        box.addView(month, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        box.addView(year, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        new AlertDialog.Builder(this).setTitle("Aniversário").setView(box)
                .setPositiveButton("OK", (d, w) -> {
                    p.bDay = day.getValue();
                    p.bMonth = month.getValue();
                    p.bYear = year.getValue() == 0 ? 0 : first + year.getValue() - 1;
                    showBirthday();
                })
                .setNeutralButton("Sem aniversário", (d, w) -> { p.bDay = 0; p.bMonth = 0; p.bYear = 0; showBirthday(); })
                .setNegativeButton("Cancelar", null).show();
    }

    private void save() {
        String n = name.getText().toString().trim();
        if (n.isEmpty()) {
            name.setError("Indique o nome");
            name.requestFocus();
            return;
        }
        p.name = n;
        p.nickname = nickname.getText().toString().trim();
        p.phone = phone.getText().toString().trim();
        p.email = email.getText().toString().trim();
        p.notes = notes.getText().toString().trim();
        p.likes = likes.getText().toString().trim();
        p.avoid = avoid.getText().toString().trim();
        boolean isNew = p.id == 0;
        boolean bChanged = isNew || oldM != p.bMonth || oldD != p.bDay || oldY != p.bYear;
        Db db = Db.get(this);
        db.save(p);
        db.setGroups(p.id, groups);
        if (oldPhoto != null && !oldPhoto.isEmpty() && !oldPhoto.equals(p.photo)) Photos.delete(this, oldPhoto);
        Engine.syncPerson(this, p, bChanged);
        Engine.reschedule(this);
        if (isNew) startActivity(new Intent(this, PersonActivity.class).putExtra(PersonActivity.EXTRA_PERSON, p.id));
        finish();
    }
}
