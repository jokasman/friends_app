package pt.amigos.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import pt.amigos.R;
import pt.amigos.core.Contact;
import pt.amigos.core.Days;
import pt.amigos.core.Friend;
import pt.amigos.core.Schedule;
import pt.amigos.data.Db;
import pt.amigos.notify.Daily;

/** Ecrã principal: aniversários próximos, amigos com quem falar e lista completa. */
public class MainActivity extends BaseActivity {
    private LinearLayout content;
    private EditText search;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Daily.channels(this);
        Daily.schedule(this);

        FrameLayout root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = Views.topBar(this, getString(R.string.app_name), false);
        bar.addView(Ui.iconButton(this, R.drawable.ic_search, "Procurar", v -> toggleSearch()));
        bar.addView(Ui.iconButton(this, R.drawable.ic_settings, "Definições",
                v -> startActivity(new Intent(this, SettingsActivity.class))));
        col.addView(bar);

        search = Views.input(this, "Procurar amigo…", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout.LayoutParams slp = Ui.lpMW();
        slp.setMargins(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4));
        search.setLayoutParams(slp);
        search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            public void afterTextChanged(Editable s) { render(); }
        });
        col.addView(search);

        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 96));
        sv.addView(content);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ImageView fab = Ui.icon(this, R.drawable.ic_add, R.color.onAccent, 60, 16);
        fab.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.accent), Ui.dp(this, 20)), Ui.dp(this, 20)));
        fab.setElevation(Ui.dp(this, 6));
        fab.setContentDescription("Novo amigo");
        fab.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 60), Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, Ui.dp(this, 20), Ui.dp(this, 24));
        root.addView(fab, flp);

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    @Override public void onBackPressed() {
        if (search.getVisibility() == View.VISIBLE) toggleSearch();
        else super.onBackPressed();
    }

    private void toggleSearch() {
        InputMethodManager im = getSystemService(InputMethodManager.class);
        if (search.getVisibility() == View.VISIBLE) {
            search.setText("");
            search.setVisibility(View.GONE);
            im.hideSoftInputFromWindow(search.getWindowToken(), 0);
        } else {
            search.setVisibility(View.VISIBLE);
            search.requestFocus();
            im.showSoftInput(search, 0);
        }
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT);
    }

    private void render() {
        content.removeAllViews();
        List<Friend> all = Db.get(this).all();
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        long today = Days.of(now.getTimeInMillis());

        if (all.isEmpty()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.cardHeader(this, R.drawable.ic_person, "Bem-vindo"));
            TextView t = Ui.text(this, "Junte os seus amigos, os aniversários e de quanto em quanto tempo quer falar com cada um. "
                    + "A app avisa quando estiver na hora de dar notícias.", 15, R.color.text2, false);
            card.addView(t);
            card.addView(Ui.spacer(this, 14));
            TextView btn = Ui.button(this, "Adicionar o primeiro amigo", true);
            btn.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)));
            card.addView(btn);
            content.addView(card);
            return;
        }

        String q = fold(search.getText().toString().trim());
        if (!q.isEmpty()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.sectionLabel(this, "Resultados"));
            int n = 0;
            for (Friend f : all) {
                if (!fold(f.name).contains(q) && !fold(f.notes).contains(q) && !f.phone.replace(" ", "").contains(q)) continue;
                card.addView(row(f, year, today, lastText(f, today), null, false));
                n++;
            }
            if (n == 0) card.addView(Ui.text(this, "Nenhum amigo encontrado.", 15, R.color.text2, false));
            content.addView(card);
            return;
        }

        // Aniversários nos próximos 30 dias.
        List<Friend> bdays = new ArrayList<>();
        for (Friend f : all) {
            int d = Schedule.daysToBirthday(f, year, today);
            if (d >= 0 && d <= 30) bdays.add(f);
        }
        Collections.sort(bdays, (a, b) -> Schedule.daysToBirthday(a, year, today) - Schedule.daysToBirthday(b, year, today));
        if (!bdays.isEmpty()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.cardHeader(this, R.drawable.ic_cake, "Aniversários próximos"));
            for (Friend f : bdays) {
                int d = Schedule.daysToBirthday(f, year, today);
                int age = Schedule.turning(f, year, today);
                String sub = f.bDay + " de " + Schedule.MONTHS[f.bMonth - 1] + (age > 0 ? " · faz " + age + " anos" : "");
                String badge = d == 0 ? "Hoje" : d == 1 ? "Amanhã" : "Em " + d + " dias";
                card.addView(row(f, year, today, sub, badge, d <= 1));
            }
            content.addView(card);
        }

        // Amigos com o contacto em atraso ou a vencer nos próximos 3 dias.
        List<Friend> due = new ArrayList<>();
        for (Friend f : all) if (overdue(f, today) >= -3) due.add(f);
        Collections.sort(due, (a, b) -> Long.compare(overdue(b, today), overdue(a, today)));
        if (!due.isEmpty()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.cardHeader(this, R.drawable.ic_chat, "Falar em breve"));
            for (Friend f : due) {
                long od = overdue(f, today);
                String badge = od > 0 ? "Atraso " + od + " d" : od == 0 ? "Hoje" : "Em " + (-od) + " d";
                card.addView(row(f, year, today, lastText(f, today), badge, od >= 0));
            }
            TextView hint = Ui.text(this, "Toque sem largar num amigo para registar que falou hoje.", 12, R.color.text2, false);
            hint.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), 0, 0);
            card.addView(hint);
            content.addView(card);
        }

        LinearLayout card = Ui.card(this);
        card.addView(Ui.cardHeader(this, R.drawable.ic_person, "Todos (" + all.size() + ")"));
        for (Friend f : all) card.addView(row(f, year, today, lastText(f, today), null, false));
        content.addView(card);
    }

    static long overdue(Friend f, long today) {
        return Schedule.overdue(f, Days.of(f.lastContact), Days.of(f.created), today);
    }

    static String lastText(Friend f, long today) {
        if (f.lastContact <= 0) return "Ainda sem contactos registados";
        return "Último contacto " + Schedule.ago(today - Days.of(f.lastContact));
    }

    private View row(Friend f, int year, long today, String sub, String badge, boolean warn) {
        LinearLayout r = Views.friendRow(this, f, sub, badge, warn,
                v -> startActivity(new Intent(this, FriendActivity.class).putExtra(FriendActivity.EXTRA_ID, f.id)));
        r.setOnLongClickListener(v -> {
            Contact k = new Contact();
            k.friendId = f.id;
            k.at = System.currentTimeMillis();
            k.kind = Contact.TALK;
            Db.get(this).addContact(k);
            Toast.makeText(this, "Registado: falou hoje com " + f.name, Toast.LENGTH_SHORT).show();
            render();
            return true;
        });
        return r;
    }
}
