package pt.relacoes.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import pt.relacoes.R;
import pt.relacoes.data.Prefs;
import pt.relacoes.notify.Engine;
import pt.relacoes.notify.Notify;

/** Ecrã principal com navegação inferior: Hoje, Pessoas, Lembretes e Modelos. */
public class MainActivity extends BaseActivity {
    static final String EXTRA_TAB = "tab";
    static final int TAB_TODAY = 0, TAB_PEOPLE = 1, TAB_REMINDERS = 2, TAB_TEMPLATES = 3;
    private static final String[] TITLES = {"Hoje", "Pessoas", "Lembretes", "Modelos"};
    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_people, R.drawable.ic_notif, R.drawable.ic_message};

    private int tab = TAB_TODAY;
    private TextView title;
    private FrameLayout body;
    private LinearLayout nav, bar;
    private ImageView fab;
    private Tab current;

    /** Um separador: constrói o conteúdo e, opcionalmente, a ação do botão flutuante. */
    abstract static class Tab {
        final MainActivity a;
        Tab(MainActivity a) { this.a = a; }
        abstract View build();
        void refresh() {}
        View.OnClickListener fab() { return null; }
        String fabLabel() { return ""; }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Notify.channels(this);
        tab = b != null ? b.getInt(EXTRA_TAB, TAB_TODAY) : getIntent().getIntExtra(EXTRA_TAB, TAB_TODAY);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        bar = Views.topBar(this, "", false);
        title = bar.findViewWithTag("title");
        bar.addView(Ui.iconButton(this, R.drawable.ic_chart, "Resumo mensal", v -> startActivity(new Intent(this, SummaryActivity.class))));
        bar.addView(Ui.iconButton(this, R.drawable.ic_settings, "Definições", v -> startActivity(new Intent(this, SettingsActivity.class))));
        col.addView(bar);

        FrameLayout mid = new FrameLayout(this);
        body = new FrameLayout(this);
        mid.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fab = Ui.icon(this, R.drawable.ic_add, R.color.onAccent, 56, 15);
        fab.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.accent), Ui.dp(this, 28)), Ui.dp(this, 28)));
        fab.setElevation(Ui.dp(this, 3));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56), Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, Ui.dp(this, 18), Ui.dp(this, 18));
        mid.addView(fab, flp);
        col.addView(mid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        View line = new View(this);
        line.setBackgroundColor(Ui.col(this, R.color.cardStroke));
        col.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1.5f))));
        nav = new LinearLayout(this);
        nav.setBackgroundColor(Ui.col(this, R.color.surface));
        nav.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        col.addView(nav, Ui.lpMW());
        setContentView(col);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        new Thread(() -> Engine.reschedule(getApplicationContext())).start();
        if (!Prefs.onboarded(this)) Prefs.setOnboarded(this);
        show(tab);
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i.hasExtra(EXTRA_TAB)) show(i.getIntExtra(EXTRA_TAB, TAB_TODAY));
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_TAB, tab);
    }

    private boolean firstResume = true;

    @Override protected void onResume() {
        super.onResume();
        if (firstResume) { firstResume = false; return; }
        if (current != null) current.refresh();
    }

    @Override public void onBackPressed() {
        if (tab != TAB_TODAY) show(TAB_TODAY);
        else super.onBackPressed();
    }

    void show(int t) {
        tab = t;
        title.setText(TITLES[t]);
        // No Hoje, o topo coral (com logótipo e definições) substitui a barra e pinta a barra de estado.
        bar.setVisibility(t == TAB_TODAY ? View.GONE : View.VISIBLE);
        getWindow().setStatusBarColor(Ui.col(this, t == TAB_TODAY ? R.color.hero : R.color.bg));
        switch (t) {
            case TAB_PEOPLE: current = new PeopleTab(this); break;
            case TAB_REMINDERS: current = new RemindersTab(this); break;
            case TAB_TEMPLATES: current = new TemplatesTab(this); break;
            default: current = new TodayTab(this);
        }
        body.removeAllViews();
        body.addView(current.build(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View.OnClickListener f = current.fab();
        fab.setVisibility(f == null ? View.GONE : View.VISIBLE);
        fab.setOnClickListener(f);
        fab.setContentDescription(current.fabLabel());
        drawNav();
    }

    private void drawNav() {
        nav.removeAllViews();
        for (int i = 0; i < TITLES.length; i++) {
            final int k = i;
            boolean sel = i == tab;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setBackground(Ui.ripple(this, null, Ui.dp(this, 16)));
            ImageView ic = Ui.icon(this, ICONS[i], sel ? R.color.accent : R.color.text2, 30, 4);
            if (sel) ic.setBackground(Ui.round(Ui.col(this, R.color.accentSoft), Ui.dp(this, 15)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 30));
            ilp.gravity = Gravity.CENTER_HORIZONTAL;
            ic.setLayoutParams(ilp);
            item.addView(ic);
            TextView l = Ui.text(this, TITLES[i], 12, sel ? R.color.accent : R.color.text2, sel);
            l.setPadding(0, Ui.dp(this, 2), 0, 0);
            l.setGravity(Gravity.CENTER_HORIZONTAL);
            l.setSingleLine(true);
            item.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            item.setOnClickListener(v -> { if (k != tab) show(k); });
            nav.addView(item, Ui.lpWeight(1));
        }
    }
}
