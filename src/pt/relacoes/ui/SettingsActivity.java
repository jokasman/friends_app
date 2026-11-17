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

        // Horários.
        LinearLayout nf = Ui.card(this);
        nf.addView(Ui.cardHeader(this, R.drawable.ic_notif, "Notificações"));
        int mo = Prefs.morning(this), qs = Prefs.quietStart(this);
        nf.addView(Ui.optionRow(this, "Hora da manhã", Fmt.hm(mo) + " · sugestões do dia, aniversários e manter contacto",
                v -> time(mo, m -> { Prefs.setMorning(this, m); Engine.reschedule(this); })));
        nf.addView(Ui.optionRow(this, "Sem insistências a partir das", Fmt.hm(qs) + " (até à hora da manhã)",
                v -> time(qs, m -> Prefs.setQuietStart(this, m))));
        nf.addView(Ui.subLabel(this, "Insistência por defeito"));
        nf.addView(Views.chooserWrap(this, NAG_N, Math.max(0, idx(NAG, Prefs.nagDefault(this))), 4, k -> Prefs.setNagDefault(this, NAG[k])));
        nf.addView(Ui.subLabel(this, "\"Adiar\" na notificação"));
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
        if (!Engine.canExact(this) && Build.VERSION.SDK_INT >= 31) {
            nf.addView(Ui.spacer(this, 8));
            TextView ex2 = Ui.button(this, "Permitir alarmes exatos", true);
            ex2.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                }
            });
            nf.addView(ex2, Ui.lpMW());
        }
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
