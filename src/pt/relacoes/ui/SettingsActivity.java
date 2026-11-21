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

        // Deteção automática de contactos.
        LinearLayout dc = Ui.card(this);
        dc.addView(Ui.cardHeader(this, R.drawable.ic_people, "Detetar contactos recebidos"));
        boolean detOn = pt.relacoes.notify.Detect.enabled(this);
        dc.addView(Ui.text(this, "Com acesso às notificações, a app reconhece mensagens e chamadas recebidas de pessoas do seu círculo "
                + "(WhatsApp, SMS, chamadas, Messenger, Instagram, Telegram, email…). Compara o nome completo ou o número de telefone; "
                + "o conteúdo das mensagens não é lido nem guardado. O que envia não é detetado.", 14, R.color.text2, false));
        dc.addView(Ui.spacer(this, 10));
        dc.addView(check("Acesso às notificações", detOn, "Ativo", "Desligado — toque em Corrigir para ativar",
                () -> startActivity(pt.relacoes.notify.Detect.settingsIntent())));
        dc.addView(Ui.subLabel(this, "Quando deteta um contacto"));
        dc.addView(Views.chooser(this, new String[]{"Perguntar", "Registar logo"}, Prefs.detectMode(this), k -> Prefs.setDetectMode(this, k)));
        long conn = pt.relacoes.notify.Detect.connectedAt(this);
        String disc = Prefs.str(this, "detect_disconnected");
        boolean linked = detOn && conn > 0 && (disc == null || disc.isEmpty());
        dc.addView(check("Serviço de leitura", linked, "Ligado desde " + Fmt.when(conn, System.currentTimeMillis()),
                detOn ? "O Android ainda não o ligou — veja a nota abaixo e reinicie o telemóvel se persistir"
                        : "Desligado (precisa do acesso às notificações)",
                () -> { pt.relacoes.notify.Detect.rebind(this); render(); }));
        if (!detOn || !linked) {
            dc.addView(Views.hint(this, "Android 13 ou mais recente: em apps instaladas fora da Play Store o acesso às notificações "
                    + "fica bloqueado (\"definição restrita\"). Abra Informações da app › menu ⋮ › \"Permitir definições restritas\" "
                    + "e depois volte a ativar o acesso às notificações."));
            TextView info = Ui.button(this, "Abrir informações da app", false);
            info.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
            LinearLayout.LayoutParams ilp = Ui.lpMW();
            ilp.topMargin = Ui.dp(this, 8);
            dc.addView(info, ilp);
        }
        dc.addView(Ui.divider(this));
        dc.addView(Ui.subLabel(this, "Últimas notificações vistas"));
        java.util.List<String[]> log = pt.relacoes.notify.Detect.recent(this);
        if (log.isEmpty()) dc.addView(Views.hint(this, detOn ? "Ainda nenhuma. Peça a alguém do círculo para lhe enviar uma mensagem."
                : "Nenhuma — o acesso às notificações está desligado."));
        for (String[] e : log) {
            dc.addView(Ui.optionRow(this, e[1] + (e[2].isEmpty() ? "" : " · " + e[2]), e[0] + " — " + e[3], null));
        }
        dc.addView(Views.hint(this, "Para ser reconhecida, a pessoa tem de ter na ficha o mesmo nome completo que aparece na notificação "
                + "(ex.: no WhatsApp) ou o mesmo número de telefone."));
        TextView refresh = Ui.button(this, "Atualizar", false);
        refresh.setOnClickListener(v -> render());
        LinearLayout.LayoutParams rlp2 = Ui.lpMW();
        rlp2.topMargin = Ui.dp(this, 8);
        dc.addView(refresh, rlp2);
        content.addView(dc);

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
        nf.addView(Ui.subLabel(this, "Repetir a notificação até ser feito (lembretes novos)"));
        nf.addView(Views.nagChooser(this, Prefs.nagDefault(this), m -> Prefs.setNagDefault(this, m)));
        TextView applyAll = Ui.button(this, "Aplicar aos lembretes existentes", false);
        applyAll.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Todos os lembretes por fazer passam a repetir a notificação "
                        + (Prefs.nagDefault(this) > 0 ? "a cada " + Prefs.nagDefault(this) + " min" : "nunca (não repetir)") + ". Continuar?")
                .setPositiveButton("Aplicar", (d, w) -> {
                    int n = Db.get(this).setAllNag(Prefs.nagDefault(this));
                    Engine.reschedule(this);
                    Toast.makeText(this, n + " lembretes atualizados.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null).show());
        nf.addView(applyAll, Ui.lpMW());
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

    /** Verificação de fiabilidade: notificações, alarmes exatos e bateria. */
    private LinearLayout reliability() {
        LinearLayout rel = Ui.card(this);
        rel.addView(Ui.cardHeader(this, R.drawable.ic_check, "Verificação de fiabilidade"));
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
        boolean notif = nm.areNotificationsEnabled();
        boolean exact = Engine.canExact(this);
        boolean battery = pm.isIgnoringBatteryOptimizations(getPackageName());
        int bad = (notif ? 0 : 1) + (exact ? 0 : 1) + (battery ? 0 : 1);
        TextView score = Ui.text(this, bad == 0 ? "Tudo pronto: as notificações vão chegar a horas."
                : bad + (bad == 1 ? " ponto precisa" : " pontos precisam") + " de atenção.", 15, bad == 0 ? R.color.accent : R.color.warn, true);
        score.setPadding(Ui.dp(this, 2), 0, 0, Ui.dp(this, 6));
        rel.addView(score);
        rel.addView(check("Notificações", notif, "Permitidas", "Bloqueadas — não verá os lembretes",
                () -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()))));
        rel.addView(check("Alarmes exatos", exact, "Permitidos — toca ao minuto", "Não permitidos — as notificações podem atrasar vários minutos", () -> {
            if (Build.VERSION.SDK_INT >= 31) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
        }));
        rel.addView(check("Otimização de bateria", battery, "Sem restrições", "Restrita — o sistema pode adiar ou bloquear as notificações", () -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }));
        String lc = Prefs.str(this, "last_catchup");
        long lct = 0;
        try { lct = lc == null ? 0 : Long.parseLong(lc); } catch (NumberFormatException ignored) { }
        rel.addView(Views.hint(this, lct > 0 ? "Última verificação automática em segundo plano: " + Fmt.when(lct, System.currentTimeMillis())
                + ". Deve acontecer de ~15 em 15 minutos; se for muito antiga, o sistema está a suspender a app."
                : "Ainda não houve verificação automática em segundo plano (acontece de ~15 em 15 minutos)."));
        rel.addView(Views.hint(this, "Dica: ponha o widget Círculo no ecrã principal — ajuda o Android a manter a app ativa."));
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
