package pt.relacoes.ui;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import pt.relacoes.R;
import pt.relacoes.core.Fmt;
import pt.relacoes.data.Backup;
import pt.relacoes.data.Db;
import pt.relacoes.data.Prefs;
import pt.relacoes.notify.Engine;
import pt.relacoes.notify.Notify;

/** Definições: conta e cópia de segurança, horários das notificações, metas, grupos, modelos e tema. */
public class SettingsActivity extends BaseActivity {
    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2;
    private static final int[] NAG = {0, 15, 30, 60, 120, 240, 1440};
    private static final String[] NAG_N = {"Não", "15 min", "30 min", "1 h", "2 h", "4 h", "1 dia"};
    private static final int[] SNOOZE = {15, 30, 60, 180, 1440};
    private static final String[] SNOOZE_N = {"15 min", "30 min", "1 h", "3 h", "1 dia"};
    private static final int[] GOALS = {0, 2, 3, 5, 7, 10};
    private static final String[] GOALS_N = {"Sem meta", "2", "3", "5", "7", "10"};
    private static final int[] BEFORE = {0, 1, 3, 7, 14};
    private static final String[] BEFORE_N = {"Não", "1 dia", "3 dias", "1 semana", "2 semanas"};
    private LinearLayout content;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Views.topBar(this, "Definições", true));
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

    private static int idx(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return -1;
    }

    private void render() {
        content.removeAllViews();

        // Conta e cópia de segurança.
        LinearLayout bk = Ui.card(this);
        bk.addView(Ui.cardHeader(this, R.drawable.ic_cloud, "Conta Google e cópia de segurança"));
        bk.addView(Ui.text(this, "A app não pede login. O Android guarda automaticamente uma cópia dos dados no Google Drive da conta Google "
                + "deste telemóvel (se a \"Cópia de segurança do Google\" estiver ligada nas definições do Android) e repõe-na ao "
                + "reinstalar a app ou num telemóvel novo.", 14, R.color.text2, false));
        bk.addView(Ui.spacer(this, 10));
        TextView sys = Ui.button(this, "Abrir definições de cópia do Android", false);
        sys.setOnClickListener(v -> openBackupSettings());
        bk.addView(sys, Ui.lpMW());
        bk.addView(Ui.divider(this));
        bk.addView(Ui.subLabel(this, "Cópia manual"));
        long le = Prefs.lastExport(this);
        bk.addView(Ui.text(this, "Exporte um ficheiro (pode escolher o Google Drive como destino) e restaure-o quando quiser. "
                + (le > 0 ? "Última exportação: " + Fmt.date(le, System.currentTimeMillis()) + "." : "Ainda não exportou nenhuma cópia."),
                14, R.color.text2, false));
        bk.addView(Ui.spacer(this, 10));
        TextView ex = Ui.button(this, "Exportar cópia", true);
        ex.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE,
                        "relacoes-" + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) + ".json"), REQ_EXPORT));
        bk.addView(ex, Ui.lpMW());
        bk.addView(Ui.spacer(this, 8));
        TextView im = Ui.button(this, "Restaurar cópia", false);
        im.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Restaurar cópia?")
                .setMessage("Todos os dados atuais serão substituídos pelos da cópia.")
                .setPositiveButton("Escolher ficheiro", (d, w) -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_IMPORT))
                .setNegativeButton("Cancelar", null).show());
        bk.addView(im, Ui.lpMW());
        content.addView(bk);

        // Fiabilidade.
        content.addView(reliability());

        // Despertador.
        LinearLayout al = Ui.card(this);
        al.addView(Ui.cardHeader(this, R.drawable.ic_bell, "Despertador"));
        al.addView(Views.hint(this, "Aniversários e lembretes criados à mão tocam como despertador: som de alarme mesmo em silêncio, "
                + "ecrã inteiro e vibração até marcar como feito ou adiar. Pode mudar isto em cada lembrete."));
        al.addView(Ui.spacer(this, 6));
        String snd = Prefs.alarmSound(this);
        String sndName = "Alarme do sistema";
        if (snd != null) {
            try {
                android.media.Ringtone rt = android.media.RingtoneManager.getRingtone(this, Uri.parse(snd));
                if (rt != null) sndName = rt.getTitle(this);
            } catch (Exception ignored) { }
        }
        al.addView(Ui.optionRow(this, "Som", sndName, v -> {
            Intent i = new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, snd == null ? null : Uri.parse(snd));
            try { startActivityForResult(i, REQ_SOUND); } catch (Exception e) { Toast.makeText(this, "Sem seletor de sons.", Toast.LENGTH_SHORT).show(); }
        }));
        al.addView(Ui.subLabel(this, "Volume do despertador: " + Prefs.alarmVolume(this) + "%"));
        android.widget.SeekBar vol = new android.widget.SeekBar(this);
        vol.setMax(90);
        vol.setProgress(Prefs.alarmVolume(this) - 10);
        final TextView volLabel = (TextView) al.getChildAt(al.getChildCount() - 1);
        vol.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar sb, int pr, boolean user) { volLabel.setText("Volume do despertador: " + (pr + 10) + "%"); }
            public void onStartTrackingTouch(android.widget.SeekBar sb) { }
            public void onStopTrackingTouch(android.widget.SeekBar sb) { Prefs.setAlarmVolume(SettingsActivity.this, sb.getProgress() + 10); }
        });
        al.addView(vol, Ui.lpMW());
        al.addView(Views.hint(this, "Usado só por esta app; o volume de alarme do sistema volta ao normal quando o despertador pára."));
        al.addView(toggle("Volume crescente (começa baixo)", Prefs.crescendo(this), on -> Prefs.setCrescendo(this, on)));
        al.addView(Ui.subLabel(this, "Botão \"+\" no alarme e no relógio"));
        al.addView(Views.chooser(this, QUICK_N, Math.max(0, idx(QUICK, Prefs.quickSnooze(this))), k -> Prefs.setQuickSnooze(this, QUICK[k])));
        al.addView(toggle("Marcar alarmes como feitos só com a impressão digital", Prefs.requireBiometric(this),
                on -> Prefs.setRequireBiometric(this, on)));
        al.addView(toggle("Abrir \"Por tratar\" ao desbloquear o telemóvel", Prefs.showPending(this), on -> {
            Prefs.setShowPending(this, on);
            pt.relacoes.notify.AlarmService.sync(this);
        }));
        al.addView(Ui.divider(this));
        al.addView(Ui.optionRow(this, "Pausa de alarmes", pauseText(), v -> pauseDialog()));
        al.addView(Ui.spacer(this, 8));
        TextView testA = Ui.button(this, "Testar despertador (10 s)", false);
        testA.setOnClickListener(v -> {
            Engine.scheduleTest(this, 10_000);
            Toast.makeText(this, "Ponha o telemóvel em silêncio, bloqueie o ecrã e aguarde 10 segundos", Toast.LENGTH_LONG).show();
        });
        al.addView(testA, Ui.lpMW());
        content.addView(al);

        // Horários.
        LinearLayout nf = Ui.card(this);
        nf.addView(Ui.cardHeader(this, R.drawable.ic_notif, "Notificações"));
        int mo = Prefs.morning(this), qs = Prefs.quietStart(this);
        nf.addView(Ui.optionRow(this, "Hora da manhã", Fmt.hm(mo) + " · sugestões do dia, aniversários e manter contacto",
                v -> time(mo, m -> { Prefs.setMorning(this, m); Engine.reschedule(this); })));
        nf.addView(toggle("Pausa noturna das insistências", Prefs.nightPause(this), on -> { Prefs.setNightPause(this, on); render(); }));
        if (Prefs.nightPause(this)) {
            nf.addView(Ui.optionRow(this, "Sem insistências a partir das", Fmt.hm(qs) + " (até à hora da manhã)",
                    v -> time(qs, m -> Prefs.setQuietStart(this, m))));
        } else {
            nf.addView(Views.hint(this, "Sem pausa noturna: os lembretes por fazer insistem a qualquer hora."));
        }
        nf.addView(Ui.subLabel(this, "Insistência por defeito"));
        nf.addView(Views.chooserWrap(this, NAG_N, Math.max(0, idx(NAG, Prefs.nagDefault(this))), 4, k -> Prefs.setNagDefault(this, NAG[k])));
        nf.addView(Ui.subLabel(this, "\"Adiar\" nas notificações normais"));
        nf.addView(Views.chooser(this, SNOOZE_N, Math.max(0, idx(SNOOZE, Prefs.snooze(this))), k -> Prefs.setSnooze(this, SNOOZE[k])));
        nf.addView(Ui.divider(this));
        nf.addView(toggle("Sugestões do dia de manhã", Prefs.suggestions(this), on -> Prefs.setSuggestions(this, on)));
        nf.addView(Ui.subLabel(this, "Pessoas sugeridas por dia"));
        nf.addView(Views.chooser(this, new String[]{"2", "3", "4"}, Math.max(0, Prefs.suggestCount(this) - 2), k -> Prefs.setSuggestCount(this, k + 2)));
        nf.addView(toggle("Resumo mensal (dia 1)", Prefs.monthly(this), on -> Prefs.setMonthly(this, on)));
        nf.addView(Ui.spacer(this, 10));
        TextView test = Ui.button(this, "Mostrar as sugestões de hoje agora", false);
        test.setOnClickListener(v -> new Thread(() -> Notify.suggestions(getApplicationContext())).start());
        nf.addView(test, Ui.lpMW());
        content.addView(nf);

        // Metas e pessoas novas.
        LinearLayout gc = Ui.card(this);
        gc.addView(Ui.cardHeader(this, R.drawable.ic_chart, "Metas"));
        gc.addView(Ui.subLabel(this, "Pessoas diferentes a contactar por semana"));
        gc.addView(Views.chooser(this, GOALS_N, Math.max(0, idx(GOALS, Prefs.weeklyGoal(this))), k -> Prefs.setWeeklyGoal(this, GOALS[k])));
        gc.addView(Ui.divider(this));
        gc.addView(Ui.subLabel(this, "Frequência para pessoas novas"));
        gc.addView(Views.chooserWrap(this, Fmt.EVERY_NAMES, Math.max(0, idx(Fmt.EVERY, Prefs.defaultEvery(this))), 3,
                k -> Prefs.setDefaultEvery(this, Fmt.EVERY[k])));
        gc.addView(Ui.subLabel(this, "Aviso de aniversário antes (pessoas novas)"));
        gc.addView(Views.chooserWrap(this, BEFORE_N, Math.max(0, idx(BEFORE, Prefs.defaultBdayBefore(this))), 3,
                k -> Prefs.setDefaultBdayBefore(this, BEFORE[k])));
        content.addView(gc);

        // Grupos, importação e modelos.
        LinearLayout oc = Ui.card(this);
        oc.addView(Ui.cardHeader(this, R.drawable.ic_people, "Pessoas e modelos"));
        oc.addView(Ui.optionRow(this, "Grupos", "Criar, mudar o nome, a cor e a ordem", v -> startActivity(new Intent(this, GroupsActivity.class))));
        oc.addView(Ui.optionRow(this, "Importar dos contactos", "Escolher pessoas da lista de contactos do telemóvel",
                v -> startActivity(new Intent(this, ImportActivity.class))));
        oc.addView(Ui.optionRow(this, "Repor modelos de origem", "Volta a acrescentar os modelos incluídos que tenha apagado", v -> {
            int n = Db.get(this).restoreBuiltinTemplates();
            Toast.makeText(this, n == 0 ? "Todos os modelos de origem já existem." : n + " modelos repostos.", Toast.LENGTH_SHORT).show();
        }));
        content.addView(oc);

        // Tema.
        LinearLayout look = Ui.card(this);
        look.addView(Ui.cardHeader(this, R.drawable.ic_settings, "Aparência"));
        look.addView(Views.chooser(this, new String[]{"Automático", "Claro", "Escuro"}, Prefs.theme(this), k -> { Prefs.setTheme(this, k); recreate(); }));
        content.addView(look);

        LinearLayout ab = Ui.card(this);
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        ab.addView(Ui.optionRow(this, getString(R.string.app_name) + " " + ver, "Sem publicidade. Os dados ficam no telemóvel.", null));
        content.addView(ab);
    }

    private static final int REQ_SOUND = 3;
    private static final int[] QUICK = {5, 10, 15, 30};
    private static final String[] QUICK_N = {"5 min", "10 min", "15 min", "30 min"};

    private String pauseText() {
        long s = Prefs.pauseStart(this), e = Prefs.pauseEnd(this), now = System.currentTimeMillis();
        if (s <= 0 || e <= now) return "Sem pausa. Os alarmes que vencerem durante uma pausa tocam em fila no fim.";
        return (Engine.pauseActive(this) ? "Em pausa até " : "De " + Fmt.when(s, now) + " até ") + Fmt.when(e, now);
    }

    /** Pausa única: data e hora de início e de fim. */
    private void pauseDialog() {
        final java.util.Calendar start = java.util.Calendar.getInstance(), end = java.util.Calendar.getInstance();
        long ps = Prefs.pauseStart(this), pe = Prefs.pauseEnd(this);
        if (ps > 0 && pe > System.currentTimeMillis()) { start.setTimeInMillis(ps); end.setTimeInMillis(pe); }
        else end.add(java.util.Calendar.HOUR_OF_DAY, 8);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), 0);
        TextView sb = Ui.button(this, "", false), eb = Ui.button(this, "", false);
        Runnable show = () -> {
            long now = System.currentTimeMillis();
            sb.setText("Início: " + Fmt.when(start.getTimeInMillis(), now));
            eb.setText("Fim: " + Fmt.when(end.getTimeInMillis(), now));
        };
        show.run();
        sb.setOnClickListener(v -> pickDateTime(start, show));
        eb.setOnClickListener(v -> pickDateTime(end, show));
        box.addView(sb, Ui.lpMW());
        box.addView(Ui.spacer(this, 8));
        box.addView(eb, Ui.lpMW());
        box.addView(Views.hint(this, "Os despertadores não tocam neste intervalo. Quando terminar, os alarmes atrasados tocam em fila."));
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Pausa de alarmes").setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    long s = start.getTimeInMillis(), e = end.getTimeInMillis();
                    if (e <= s || e <= System.currentTimeMillis()) {
                        Toast.makeText(this, "O fim tem de ser depois do início e no futuro.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Engine.setPause(this, s, e);
                    render();
                })
                .setNegativeButton("Cancelar", null);
        if (ps > 0) b.setNeutralButton("Remover pausa", (d, w) -> { Engine.setPause(this, 0, 0); render(); });
        b.show();
    }

    private void pickDateTime(java.util.Calendar cal, Runnable after) {
        new android.app.DatePickerDialog(this, (dp, y, m, d) -> {
            cal.set(y, m, d);
            new TimePickerDialog(this, (tp, h, mi) -> {
                cal.set(java.util.Calendar.HOUR_OF_DAY, h);
                cal.set(java.util.Calendar.MINUTE, mi);
                cal.set(java.util.Calendar.SECOND, 0);
                after.run();
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), DateFormat.is24HourFormat(this)).show();
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    /** Verificação de fiabilidade (igual ao Lembra). */
    private LinearLayout reliability() {
        LinearLayout rel = Ui.card(this);
        rel.addView(Ui.cardHeader(this, R.drawable.ic_check, "Verificação de fiabilidade"));
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
        boolean notif = nm.areNotificationsEnabled();
        boolean exact = Engine.canExact(this);
        boolean battery = pm.isIgnoringBatteryOptimizations(getPackageName());
        boolean overlay = Settings.canDrawOverlays(this);
        boolean fs = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent();
        int bad = (notif ? 0 : 1) + (exact ? 0 : 1) + (battery ? 0 : 1) + (overlay ? 0 : 1) + (fs ? 0 : 1);
        TextView score = Ui.text(this, bad == 0 ? "Tudo pronto: os alarmes vão tocar a horas."
                : bad + (bad == 1 ? " ponto precisa" : " pontos precisam") + " de atenção.", 15, bad == 0 ? R.color.accent : R.color.warn, true);
        score.setPadding(Ui.dp(this, 2), 0, 0, Ui.dp(this, 6));
        rel.addView(score);
        rel.addView(check("Notificações", notif, "Permitidas", "Bloqueadas — não verá os lembretes",
                () -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()))));
        rel.addView(check("Alarmes exatos", exact, "Permitidos — toca ao minuto", "Não permitidos — pode atrasar vários minutos", () -> {
            if (Build.VERSION.SDK_INT >= 31) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
        }));
        rel.addView(check("Otimização de bateria", battery, "Sem restrições", "Restrita — o sistema pode adiar ou bloquear alarmes", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }));
        if (Build.VERSION.SDK_INT >= 34) {
            rel.addView(check("Ecrã inteiro (despertador)", fs, "Permitido", "Não permitido — o despertador aparece só como notificação",
                    () -> startActivity(new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", Uri.parse("package:" + getPackageName())))));
        }
        rel.addView(check("Abrir por cima de outras apps", overlay, "Permitido — o alarme abre o ecrã com o telemóvel desbloqueado",
                "Não permitido — com o telemóvel desbloqueado só aparece a notificação",
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))));
        String diag = Prefs.str(this, "alarm_diag");
        if (diag != null) rel.addView(Views.hint(this, "Diagnóstico: " + diag));
        final String mf = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        if (mf.contains("xiaomi") || mf.contains("redmi") || mf.contains("poco")) {
            TextView xb = Ui.button(this, "Abrir permissões Xiaomi", false);
            xb.setOnClickListener(v -> {
                try {
                    startActivity(new Intent("miui.intent.action.APP_PERM_EDITOR")
                            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                            .putExtra("extra_pkgname", getPackageName()));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                }
            });
            LinearLayout.LayoutParams xlp = Ui.lpMW();
            xlp.topMargin = Ui.dp(this, 10);
            rel.addView(xb, xlp);
        }
        String tip = vendorTip(mf);
        if (tip != null) rel.addView(Views.hint(this, tip));
        return rel;
    }

    private LinearLayout check(String title, boolean ok, String good, String badText, Runnable fix) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        TextView mark = Ui.text(this, ok ? "✓" : "!", 16, ok ? R.color.accent : R.color.danger, true);
        mark.setGravity(android.view.Gravity.CENTER);
        mark.setBackground(Ui.round(Ui.col(this, ok ? R.color.accentSoft : R.color.dangerSoft), Ui.dp(this, 14)));
        row.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        t.addView(Ui.text(this, title, 15, R.color.text, true));
        t.addView(Ui.text(this, ok ? good : badText, 13, R.color.text2, false));
        row.addView(t, Ui.lpWeight(1));
        if (!ok && fix != null) {
            TextView b = Ui.text(this, "Corrigir", 14, R.color.accent, true);
            b.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
            b.setOnClickListener(v -> { try { fix.run(); } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); } });
            row.addView(b);
        }
        return row;
    }

    private static String vendorTip(String m) {
        if (m.contains("samsung"))
            return "Dica Samsung: em Definições › Bateria › Limites de utilização em segundo plano, adicione esta app a \"Aplicações que nunca entram em suspensão\".";
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco"))
            return "Xiaomi: em \"Abrir permissões Xiaomi\" (acima) ative \"Mostrar no ecrã de bloqueio\", \"Abrir novas janelas em segundo plano\" e \"Janelas pop-up\". "
                    + "Em Definições › Aplicações ative também \"Arranque automático\" e escolha \"Sem restrições\" na Poupança de bateria. Sem isto, o despertador pode só vibrar.";
        if (m.contains("huawei") || m.contains("honor"))
            return "Dica Huawei: em Bateria › Arranque de aplicações, desative a gestão automática desta app e permita todas as opções.";
        if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus") || m.contains("vivo"))
            return "Dica: nas definições de bateria da aplicação, permita a atividade em segundo plano e o arranque automático.";
        return null;
    }

    interface OnTime { void on(int minutes); }
    interface OnToggle { void on(boolean on); }

    private void time(int current, OnTime cb) {
        new TimePickerDialog(this, (p, h, m) -> { cb.on(h * 60 + m); render(); }, current / 60, current % 60,
                DateFormat.is24HourFormat(this)).show();
    }

    private Switch toggle(String label, boolean on, OnToggle cb) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(Ui.col(this, R.color.text));
        s.setTextSize(15);
        s.setChecked(on);
        s.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        s.setOnCheckedChangeListener((v, c) -> cb.on(c));
        s.setLayoutParams(Ui.lpMW());
        return s;
    }

    private void openBackupSettings() {
        String[] actions = {"android.settings.BACKUP_AND_RESET_SETTINGS", "android.settings.PRIVACY_SETTINGS", Settings.ACTION_SETTINGS};
        for (String a : actions) {
            try {
                startActivity(new Intent(a));
                return;
            } catch (Exception ignored) {}
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SOUND) {
            if (res == RESULT_OK && data != null) {
                Uri u = data.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                Prefs.setAlarmSound(this, u == null ? null : u.toString());
                render();
            }
            return;
        }
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_EXPORT) {
                try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                    os.write(Backup.export(this).getBytes(StandardCharsets.UTF_8));
                }
                Prefs.setLastExport(this, System.currentTimeMillis());
                Toast.makeText(this, "Cópia exportada.", Toast.LENGTH_SHORT).show();
            } else if (req == REQ_IMPORT) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                }
                int count = Backup.restore(this, new String(bo.toByteArray(), StandardCharsets.UTF_8));
                Engine.reschedule(this);
                Toast.makeText(this, count + " pessoas restauradas.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Não foi possível concluir").setMessage(String.valueOf(e.getMessage()))
                    .setPositiveButton("OK", null).show();
        }
        render();
    }
}
