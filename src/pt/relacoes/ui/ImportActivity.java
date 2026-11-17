package pt.relacoes.ui;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pt.relacoes.R;
import pt.relacoes.core.Person;
import pt.relacoes.data.Db;
import pt.relacoes.data.Photos;
import pt.relacoes.data.Prefs;
import pt.relacoes.notify.Engine;

/** Importação opcional de pessoas a partir dos contactos do telemóvel (nome, telefone, email, aniversário e foto). */
public class ImportActivity extends BaseActivity {
    private static final int REQ = 9;

    static final class C {
        long id;
        String name = "", phone = "", email = "";
        int bd, bm, by;
        boolean exists, checked;
    }

    private final List<C> contacts = new ArrayList<>();
    private LinearLayout list;
    private EditText search;
    private TextView importBtn;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = Views.topBar(this, "Importar contactos", true);
        importBtn = Ui.button(this, "Importar", true);
        importBtn.setOnClickListener(v -> doImport());
        bar.addView(importBtn);
        col.addView(bar);
        search = Views.input(this, "Procurar…", "", InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams slp = Ui.lpMW();
        slp.setMargins(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4));
        search.setLayoutParams(slp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable s) { render(); }
        });
        col.addView(search);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, Ui.dp(this, 24));
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(col);

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) load();
        else {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.cardHeader(this, R.drawable.ic_import, "Acesso aos contactos"));
            c.addView(Ui.text(this, "Para importar, a app precisa de ler os contactos do telemóvel. Só são importadas as pessoas que escolher, "
                    + "e os dados ficam apenas neste telemóvel.", 15, R.color.text2, false));
            c.addView(Ui.spacer(this, 12));
            TextView ok = Ui.button(this, "Permitir acesso", true);
            ok.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ));
            c.addView(ok, Ui.lpMW());
            list.addView(c);
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ);
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == REQ && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) load();
    }

    private void load() {
        list.removeAllViews();
        ProgressBar pb = new ProgressBar(this);
        list.addView(pb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 80)));
        new Thread(() -> {
            List<C> out = read();
            runOnUiThread(() -> {
                contacts.clear();
                contacts.addAll(out);
                render();
            });
        }).start();
    }

    private List<C> read() {
        ContentResolver cr = getContentResolver();
        Map<Long, C> map = new LinkedHashMap<>();
        Set<String> existing = new HashSet<>();
        for (Person p : Db.get(this).people()) existing.add(p.name.trim().toLowerCase());
        try (Cursor c = cr.query(ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                null, null, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC")) {
            while (c != null && c.moveToNext()) {
                String n = c.getString(1);
                if (n == null || n.trim().isEmpty()) continue;
                C k = new C();
                k.id = c.getLong(0);
                k.name = n.trim();
                k.exists = existing.contains(k.name.toLowerCase());
                map.put(k.id, k);
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        String[] proj = {ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2};
        String sel = ContactsContract.Data.MIMETYPE + " IN (?,?,?)";
        String[] args = {ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE};
        try (Cursor c = cr.query(ContactsContract.Data.CONTENT_URI, proj, sel, args, ContactsContract.Data.IS_PRIMARY + " DESC")) {
            while (c != null && c.moveToNext()) {
                C k = map.get(c.getLong(0));
                if (k == null) continue;
                String mime = c.getString(1), v = c.getString(2);
                if (v == null) continue;
                if (ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mime)) {
                    if (k.phone.isEmpty()) k.phone = v.trim();
                } else if (ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mime)) {
                    if (k.email.isEmpty()) k.email = v.trim();
                } else if (c.getInt(3) == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) {
                    parseBirthday(k, v.trim());
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(map.values());
    }

    /** Aceita "1990-05-12", "--05-12" e "12/05/1990". */
    static void parseBirthday(C k, String v) {
        try {
            if (v.startsWith("--")) {
                String[] p = v.substring(2).split("-");
                k.bm = Integer.parseInt(p[0]); k.bd = Integer.parseInt(p[1].substring(0, 2));
            } else if (v.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                k.by = Integer.parseInt(v.substring(0, 4)); k.bm = Integer.parseInt(v.substring(5, 7)); k.bd = Integer.parseInt(v.substring(8, 10));
            } else if (v.matches("\\d{1,2}[/.]\\d{1,2}[/.]\\d{4}")) {
                String[] p = v.split("[/.]");
                k.bd = Integer.parseInt(p[0]); k.bm = Integer.parseInt(p[1]); k.by = Integer.parseInt(p[2]);
            }
            if (k.bm < 1 || k.bm > 12 || k.bd < 1 || k.bd > 31) { k.bm = 0; k.bd = 0; k.by = 0; }
            if (k.by < 1900) k.by = 0;
        } catch (Exception e) {
            k.bm = 0; k.bd = 0; k.by = 0;
        }
    }

    private void render() {
        list.removeAllViews();
        int sel = 0;
        for (C k : contacts) if (k.checked) sel++;
        importBtn.setText(sel > 0 ? "Importar (" + sel + ")" : "Importar");
        if (contacts.isEmpty()) {
            LinearLayout c = Ui.card(this);
            c.addView(Views.empty(this, "Não foram encontrados contactos."));
            list.addView(c);
            return;
        }
        LinearLayout card = Ui.card(this);
        TextView all = Ui.button(this, "Selecionar todos os visíveis", false);
        String q = PeopleTab.fold(search.getText().toString().trim());
        all.setOnClickListener(v -> {
            for (C k : contacts) if (!k.exists && PeopleTab.fold(k.name).contains(q)) k.checked = true;
            render();
        });
        card.addView(all, Ui.lpMW());
        card.addView(Ui.spacer(this, 8));
        int shown = 0;
        for (C k : contacts) {
            if (!q.isEmpty() && !PeopleTab.fold(k.name).contains(q)) continue;
            if (++shown > 400) break;
            CheckBox cb = new CheckBox(this);
            String extra = k.phone;
            if (k.bm > 0) extra += (extra.isEmpty() ? "" : " · ") + "🎂 " + k.bd + "/" + k.bm;
            if (k.exists) extra += (extra.isEmpty() ? "" : " · ") + "já existe";
            cb.setText(k.name + (extra.isEmpty() ? "" : "\n" + extra));
            cb.setTextColor(Ui.col(this, k.exists ? R.color.text2 : R.color.text));
            cb.setTextSize(15);
            cb.setChecked(k.checked);
            cb.setGravity(Gravity.CENTER_VERTICAL);
            cb.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            cb.setOnCheckedChangeListener((v, on) -> {
                k.checked = on;
                int s = 0;
                for (C x : contacts) if (x.checked) s++;
                importBtn.setText(s > 0 ? "Importar (" + s + ")" : "Importar");
            });
            card.addView(cb);
        }
        list.addView(card);
    }

    private void doImport() {
        List<C> chosen = new ArrayList<>();
        for (C k : contacts) if (k.checked) chosen.add(k);
        if (chosen.isEmpty()) {
            Toast.makeText(this, "Escolha pelo menos um contacto.", Toast.LENGTH_SHORT).show();
            return;
        }
        importBtn.setEnabled(false);
        new Thread(() -> {
            Db db = Db.get(this);
            int every = Prefs.defaultEvery(this), before = Prefs.defaultBdayBefore(this);
            for (C k : chosen) {
                Person p = new Person();
                p.name = k.name; p.phone = k.phone; p.email = k.email;
                p.bDay = k.bd; p.bMonth = k.bm; p.bYear = k.by;
                p.everyDays = every; p.bdayBefore = before;
                String photo = photo(k.id);
                if (photo != null) p.photo = photo;
                db.save(p);
                Engine.syncPerson(this, p, true);
            }
            Engine.reschedule(this);
            runOnUiThread(() -> {
                Toast.makeText(this, chosen.size() + (chosen.size() == 1 ? " pessoa importada" : " pessoas importadas"), Toast.LENGTH_LONG).show();
                finish();
            });
        }).start();
    }

    private String photo(long contactId) {
        Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
        try (InputStream is = ContactsContract.Contacts.openContactPhotoInputStream(getContentResolver(), uri, true)) {
            if (is == null) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return Photos.save(this, bo.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

}
