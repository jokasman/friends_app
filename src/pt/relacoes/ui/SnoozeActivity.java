package pt.relacoes.ui;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import pt.relacoes.R;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Reminder;
import pt.relacoes.data.Db;
import pt.relacoes.data.Prefs;
import pt.relacoes.notify.AlarmService;
import pt.relacoes.notify.Engine;
import pt.relacoes.notify.Notify;

/** Folha "Adiar": daqui a X minutos, mais tarde (esta noite, amanhã de manhã…) ou data e hora à escolha. */
public class SnoozeActivity extends BaseActivity {
    static final String EXTRA_FROM_ALARM = "fromAlarm";
    private static final String[] DAYS_SHORT = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
    private static final int MIN_STEP = 5;
    private Reminder r;
    private boolean fromAlarm, applied;
    private LinearLayout customPanel, dateChips;
    private LocalDate customDate;
    private NumberPicker hourP, minP;
    private TextView confirmBtn;

    private int dp(float v) { return Ui.dp(this, v); }

    private static ZoneId zone() { return ZoneId.systemDefault(); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        fromAlarm = getIntent().getBooleanExtra(EXTRA_FROM_ALARM, false);
        if (fromAlarm) {
            if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
            else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        r = Db.get(this).reminder(getIntent().getLongExtra(AlarmActivity.EXTRA_ID, -1));
        if (r == null || r.done) { finish(); return; }
        setFinishOnTouchOutside(!fromAlarm);

        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setGravity(Gravity.BOTTOM);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.55f);

        ScrollView scroll = new ScrollView(this);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.col(this, R.color.surface));
        float rad = dp(28);
        bg.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(dp(20), dp(10), dp(20), dp(20));
        scroll.addView(sheet, new ScrollView.LayoutParams(-1, -2));

        View handle = new View(this);
        handle.setBackground(Ui.round(Ui.col(this, R.color.divider), dp(3)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(40), dp(5));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = dp(14);
        sheet.addView(handle, hlp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = Ui.icon(this, R.drawable.ic_snooze, R.color.accent, 44, 10);
        ic.setBackground(Ui.round(Ui.col(this, R.color.accentSoft), dp(14)));
        LinearLayout.LayoutParams iclp = new LinearLayout.LayoutParams(dp(44), dp(44));
        iclp.setMarginEnd(dp(14));
        head.addView(ic, iclp);
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, Notify.texts(this, r)[0], 19, R.color.text, true);
        Ui.ellipsize(title, 2);
        ht.addView(title, Ui.lpMW());
        ht.addView(Ui.text(this, "Adiar · estava para " + Fmt.when(r.dueAt, System.currentTimeMillis()), 13, R.color.text2, false), Ui.lpMW());
        head.addView(ht, Ui.lpWeight(1));
        sheet.addView(head, Ui.lpMW());

        sheet.addView(label("Daqui a"));
        LinearLayout tiles = new LinearLayout(this);
        int[] mins = {10, 30, 60, 180};
        String[] big = {"10", "30", "1", "3"};
        String[] unit = {"min", "min", "hora", "horas"};
        for (int i = 0; i < mins.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(84), 1);
            if (i < mins.length - 1) lp.setMarginEnd(dp(8));
            tiles.addView(tile(big[i], unit[i], mins[i]), lp);
        }
        sheet.addView(tiles, Ui.lpMW());

        sheet.addView(label("Mais tarde"));
        LinearLayout later = new LinearLayout(this);
        later.setOrientation(LinearLayout.VERTICAL);
        later.setBackground(Ui.round(Ui.col(this, R.color.chip), dp(18)));
        LocalDateTime n = LocalDateTime.now(zone()).withSecond(0).withNano(0);
        LocalDate today = n.toLocalDate();
        int morning = Prefs.morning(this);
        LocalTime mt = LocalTime.of(morning / 60, morning % 60);
        boolean first = true;
        if (n.getHour() < 19) { addLater(later, "Esta noite", today.atTime(20, 0), first); first = false; }
        addLater(later, "Amanhã de manhã", today.plusDays(1).atTime(mt), first);
        if (n.getDayOfWeek().getValue() < 5) addLater(later, "No fim de semana", today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).atTime(10, 0), false);
        addLater(later, "Próxima segunda", today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(mt), false);
        sheet.addView(later, Ui.lpMW());

        final TextView custom = Ui.button(this, "Escolher data e hora", false);
        LinearLayout.LayoutParams clp = Ui.lpMW();
        clp.topMargin = dp(16);
        sheet.addView(custom, clp);
        customPanel = buildCustomPanel();
        customPanel.setVisibility(View.GONE);
        sheet.addView(customPanel, Ui.lpMW());
        custom.setOnClickListener(v -> {
            boolean show = customPanel.getVisibility() != View.VISIBLE;
            customPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            custom.setText(show ? "Fechar seletor" : "Escolher data e hora");
        });

        TextView cancel = Ui.text(this, fromAlarm ? "Voltar ao alarme" : "Cancelar", 15, R.color.text2, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(16), 0, dp(4));
        cancel.setOnClickListener(v -> finish());
        sheet.addView(cancel, Ui.lpMW());

        setContentView(scroll);
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView label(String s) {
        TextView t = Ui.text(this, s.toUpperCase(), 12, R.color.text2, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(18), 0, dp(8));
        t.setLayoutParams(Ui.lpMW());
        return t;
    }

    private View tile(String big, String unit, final int minutes) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.ripple(this, Ui.round(Ui.col(this, R.color.accentSoft), dp(18)), dp(18)));
        TextView b = Ui.text(this, big, 24, R.color.accent, true);
        b.setGravity(Gravity.CENTER);
        t.addView(b, new LinearLayout.LayoutParams(-2, -2));
        t.addView(Ui.text(this, unit, 12, R.color.accent, false), new LinearLayout.LayoutParams(-2, -2));
        TextView at = Ui.text(this, Fmt.time(System.currentTimeMillis() + minutes * 60000L), 11, R.color.text2, false);
        at.setPadding(0, dp(2), 0, 0);
        t.addView(at, new LinearLayout.LayoutParams(-2, -2));
        t.setContentDescription("Adiar " + big + " " + unit);
        t.setOnClickListener(v -> apply(System.currentTimeMillis() + minutes * 60000L));
        return t;
    }

    private void addLater(LinearLayout group, String label, LocalDateTime t, boolean first) {
        final long ms = t.atZone(zone()).toInstant().toEpochMilli();
        if (!first) {
            View div = new View(this);
            div.setBackgroundColor(Ui.col(this, R.color.divider));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, 1);
            dlp.setMargins(dp(16), 0, dp(16), 0);
            group.addView(div, dlp);
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setBackground(Ui.ripple(this, null, dp(18)));
        row.addView(Ui.text(this, label, 16, R.color.text, false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(Ui.text(this, Fmt.when(ms, System.currentTimeMillis()), 14, R.color.text2, false), new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(x -> apply(ms));
        group.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
    }

    private LinearLayout buildCustomPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(0, dp(12), 0, 0);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        dateChips = new LinearLayout(this);
        hs.addView(dateChips);
        p.addView(hs, Ui.lpMW());
        LocalDateTime base = LocalDateTime.now(zone()).plusHours(1);
        customDate = base.toLocalDate();
        renderDateChips();

        LinearLayout time = new LinearLayout(this);
        time.setGravity(Gravity.CENTER);
        time.setPadding(0, dp(8), 0, 0);
        hourP = new NumberPicker(this);
        hourP.setMinValue(0);
        hourP.setMaxValue(23);
        hourP.setFormatter(v -> v < 10 ? "0" + v : String.valueOf(v));
        hourP.setValue(base.getHour());
        minP = new NumberPicker(this);
        String[] mins = new String[60 / MIN_STEP];
        for (int i = 0; i < mins.length; i++) { int m = i * MIN_STEP; mins[i] = m < 10 ? "0" + m : String.valueOf(m); }
        minP.setMinValue(0);
        minP.setMaxValue(mins.length - 1);
        minP.setDisplayedValues(mins);
        minP.setValue(0);
        hourP.setOnValueChangedListener((pk, o, nv) -> updateConfirm());
        minP.setOnValueChangedListener((pk, o, nv) -> updateConfirm());
        time.addView(hourP, new LinearLayout.LayoutParams(dp(72), -2));
        TextView colon = Ui.text(this, ":", 28, R.color.text, true);
        colon.setPadding(dp(10), 0, dp(10), 0);
        time.addView(colon);
        time.addView(minP, new LinearLayout.LayoutParams(dp(72), -2));
        p.addView(time, Ui.lpMW());

        confirmBtn = Ui.button(this, "", true);
        LinearLayout.LayoutParams lp = Ui.lpMW();
        lp.topMargin = dp(10);
        p.addView(confirmBtn, lp);
        confirmBtn.setOnClickListener(v -> {
            long ms = customMs();
            if (ms <= System.currentTimeMillis()) {
                Toast.makeText(this, "Escolha uma hora no futuro", Toast.LENGTH_SHORT).show();
                return;
            }
            apply(ms);
        });
        updateConfirm();
        return p;
    }

    private void renderDateChips() {
        dateChips.removeAllViews();
        LocalDate today = LocalDate.now(zone());
        boolean inRange = false;
        for (int i = 0; i < 7; i++) {
            final LocalDate d = today.plusDays(i);
            String label = i == 0 ? "Hoje" : i == 1 ? "Amanhã" : DAYS_SHORT[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth();
            boolean sel = d.equals(customDate);
            if (sel) inRange = true;
            TextView c = Ui.chip(this, label, sel, 0);
            c.setOnClickListener(v -> { customDate = d; renderDateChips(); updateConfirm(); });
            dateChips.addView(c);
        }
        String other = inRange ? "Outra data…" : customDate.getDayOfMonth() + " " + Fmt.MONTHS[customDate.getMonthValue() - 1].substring(0, 3);
        TextView o = Ui.chip(this, other, !inRange, 0);
        o.setOnClickListener(v -> {
            DatePickerDialog dd = new DatePickerDialog(this, (dp, y, m, d) -> {
                customDate = LocalDate.of(y, m + 1, d);
                renderDateChips();
                updateConfirm();
            }, customDate.getYear(), customDate.getMonthValue() - 1, customDate.getDayOfMonth());
            dd.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            dd.show();
        });
        dateChips.addView(o);
    }

    private long customMs() {
        return customDate.atTime(LocalTime.of(hourP.getValue(), minP.getValue() * MIN_STEP)).atZone(zone()).toInstant().toEpochMilli();
    }

    private void updateConfirm() {
        if (confirmBtn == null || hourP == null) return;
        long ms = customMs();
        boolean past = ms <= System.currentTimeMillis();
        confirmBtn.setText(past ? "Essa hora já passou" : "Adiar para " + Fmt.when(ms, System.currentTimeMillis()));
        confirmBtn.setAlpha(past ? 0.5f : 1f);
    }

    @Override protected void onDestroy() {
        // Cancelou a escolha vindo do alarme: o alarme volta a tocar (só pára com uma decisão).
        if (fromAlarm && !applied && isFinishing() && r != null) {
            Reminder cur = Db.get(this).reminder(r.id);
            if (cur != null && !cur.done) AlarmService.resume(); else AlarmService.stop(null);
        }
        super.onDestroy();
    }

    private void apply(long until) {
        applied = true;
        Reminder fresh = Db.get(this).reminder(r.id);
        if (fresh != null) {
            Engine.snoozeUntil(this, fresh, until);
            Toast.makeText(this, "Adiado para " + Fmt.when(until, System.currentTimeMillis()), Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
