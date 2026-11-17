package pt.relacoes.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import pt.relacoes.R;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Person;
import pt.relacoes.core.Reminder;
import pt.relacoes.data.Db;
import pt.relacoes.data.Prefs;
import pt.relacoes.notify.AlarmService;
import pt.relacoes.notify.Engine;
import pt.relacoes.notify.Notify;

/** Ecrã inteiro do despertador, por cima do ecrã de bloqueio. Só fecha com uma decisão. */
public class AlarmActivity extends BaseActivity {
    public static final String EXTRA_ID = "id", EXTRA_DONE = "done";
    private static final List<AlarmActivity> open = new ArrayList<>();
    private long id;

    public static void closeAll() {
        for (AlarmActivity a : new ArrayList<>(open)) a.finish();
    }

    private int dp(float v) { return Ui.dp(this, v); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        open.add(this);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        build();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); build(); }

    @Override protected void onDestroy() { open.remove(this); super.onDestroy(); }

    @Override public void onBackPressed() { /* não fecha sem uma decisão */ }

    private void build() {
        id = getIntent().getLongExtra(EXTRA_ID, Long.MIN_VALUE);
        final boolean test = id == AlarmService.TEST_ID;
        final Reminder r = test ? null : Db.get(this).reminder(id);
        if (!test && (r == null || r.done)) { finish(); return; }
        final Person p = test ? null : Db.get(this).person(r.personId);
        if (!test && p == null) { finish(); return; }
        String[] t = test ? new String[]{"Teste do despertador", "Se ouviu o som com o telemóvel em silêncio, está tudo a funcionar.",
                "Se ouviu o som com o telemóvel em silêncio, está tudo a funcionar."} : Notify.texts(this, r);
        int color = Ui.col(this, R.color.accent);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{darken(color), 0xFF0E1016}));
        root.setPadding(dp(28), dp(40), dp(28), dp(20));

        // O conteúdo pode deslizar; os botões ficam sempre visíveis.
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));

        long now = System.currentTimeMillis();
        TextView clock = white(Fmt.time(now), 72);
        clock.setTypeface(Ui.font(this, 400));
        content.addView(clock);
        Calendar cal = Calendar.getInstance();
        String[] wd = {"domingo", "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira", "sábado"};
        TextView day = white(wd[cal.get(Calendar.DAY_OF_WEEK) - 1] + ", " + cal.get(Calendar.DAY_OF_MONTH) + " de " + Fmt.MONTHS[cal.get(Calendar.MONTH)], 16);
        day.setAlpha(0.8f);
        content.addView(day);

        if (p != null) {
            View av = Views.avatar(this, p, 88, -1);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(88), dp(88));
            alp.topMargin = dp(28);
            content.addView(av, alp);
        }
        TextView title = white(t[0], 26);
        title.setTypeface(Ui.font(this, 800));
        title.setPadding(0, dp(18), 0, dp(8));
        content.addView(title);
        TextView sub = white(t[2], 16);
        sub.setAlpha(0.85f);
        content.addView(sub);
        if (r != null && r.kind == Reminder.REPEAT) {
            TextView rep = white("↻ " + r.repeatText(), 14);
            rep.setAlpha(0.7f);
            rep.setPadding(0, dp(8), 0, 0);
            content.addView(rep);
        }

        if (test) {
            root.addView(bigButton("Parar", 0xFFFFFFFF, 0xFF0E1016, v -> { AlarmService.stop(null); finish(); }));
        } else {
            boolean bio = r.alarm && Prefs.requireBiometric(this);
            root.addView(bigButton(bio ? "Feito  ·  impressão digital" : "Feito", 0xFFFFFFFF, 0xFF0E1016, v -> done()));
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(12), 0, 0);
            final int quick = Prefs.quickSnooze(this);
            LinearLayout.LayoutParams lp = Ui.lpWeight(1);
            lp.setMarginEnd(dp(10));
            row.addView(bigButton("+" + Fmt.duration(quick), 0x33FFFFFF, 0xFFFFFFFF, v -> {
                Engine.snooze(this, id, quick);
                finish();
            }), lp);
            row.addView(bigButton("Adiar…", 0x33FFFFFF, 0xFFFFFFFF, v -> {
                // Silencia enquanto escolhe; se cancelar, o alarme volta a tocar.
                AlarmService.pause();
                startActivity(new Intent(this, SnoozeActivity.class).putExtra(EXTRA_ID, id).putExtra(SnoozeActivity.EXTRA_FROM_ALARM, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            }), Ui.lpWeight(1));
            root.addView(row, Ui.lpMW());
            TextView talked = bigButton("Já falei com " + p.firstName(), 0x33FFFFFF, 0xFFFFFFFF, v -> {
                AlarmService.pause();
                startActivity(LogActivity.intent(this, p.id, id, -1, "", 0).putExtra(LogActivity.EXTRA_FROM_ALARM, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            });
            LinearLayout.LayoutParams tlp = Ui.lpMW();
            tlp.topMargin = dp(12);
            root.addView(talked, tlp);
        }
        setContentView(root);
        if (!test && getIntent().getBooleanExtra(EXTRA_DONE, false)) {
            getIntent().removeExtra(EXTRA_DONE);
            root.post(this::done);
        }
    }

    private void done() {
        Reminder fresh = Db.get(this).reminder(id);
        if (fresh == null || fresh.done) { AlarmService.stop(id); finish(); return; }
        Auth.confirmDone(this, fresh, () -> {
            Engine.done(this, id);
            AlarmService.stop(id);
            finish();
        });
    }

    private TextView white(String s, float sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Ui.font(this, 600));
        return t;
    }

    private TextView bigButton(String label, int bg, int fg, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(18);
        b.setTextColor(fg);
        b.setTypeface(Ui.font(this, 800));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(18), dp(16), dp(18));
        b.setBackground(Ui.ripple(this, Ui.round(bg, dp(32)), dp(32)));
        b.setOnClickListener(l);
        b.setLayoutParams(Ui.lpMW());
        return b;
    }

    private static int darken(int c) {
        int r = ((c >> 16) & 0xFF) * 6 / 10, g = ((c >> 8) & 0xFF) * 6 / 10, b = (c & 0xFF) * 6 / 10;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
