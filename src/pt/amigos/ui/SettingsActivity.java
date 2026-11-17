package pt.amigos.ui;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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

import pt.amigos.R;
import pt.amigos.data.Backup;
import pt.amigos.data.Prefs;
import pt.amigos.notify.Daily;

/** Definições: tema, avisos e cópia de segurança. */
public class SettingsActivity extends BaseActivity {
    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2;
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
        render();
    }

    private void render() {
        content.removeAllViews();

        LinearLayout look = Ui.card(this);
        look.addView(Ui.cardHeader(this, R.drawable.ic_settings, "Aparência"));
        look.addView(Ui.subLabel(this, "Tema"));
        String[] themes = {"Automático", "Claro", "Escuro"};
        LinearLayout tl = new LinearLayout(this);
        for (int i = 0; i < themes.length; i++) {
            final int t = i;
            TextView c = Ui.chip(this, themes[i], Prefs.theme(this) == i, 0);
            c.setOnClickListener(v -> { Prefs.setTheme(this, t); recreate(); });
            tl.addView(c);
        }
        look.addView(tl);
        content.addView(look);

        LinearLayout nf = Ui.card(this);
        nf.addView(Ui.cardHeader(this, R.drawable.ic_notif, "Avisos"));
        int t = Prefs.notifyTime(this);
        nf.addView(Ui.optionRow(this, "Hora do aviso diário", String.format(Locale.ROOT, "%02d:%02d", t / 60, t % 60), v ->
                new TimePickerDialog(this, (p, h, m) -> {
                    Prefs.setNotifyTime(this, h * 60 + m);
                    Daily.schedule(this);
                    render();
                }, t / 60, t % 60, DateFormat.is24HourFormat(this)).show()));
        nf.addView(Ui.divider(this));
        nf.addView(Ui.subLabel(this, "Avisar dos aniversários"));
        int[] adv = {0, 1, 3, 7};
        String[] advN = {"Só no dia", "1 dia antes", "3 dias antes", "1 semana antes"};
        LinearLayout al = new LinearLayout(this);
        al.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = null;
        for (int i = 0; i < adv.length; i++) {
            if (i % 2 == 0) {
                line = new LinearLayout(this);
                line.setPadding(0, 0, 0, Ui.dp(this, 8));
                al.addView(line);
            }
            final int a = adv[i];
            TextView c = Ui.chip(this, advN[i], Prefs.birthdayAdvance(this) == a, 0);
            c.setOnClickListener(v -> { Prefs.setBirthdayAdvance(this, a); render(); });
            line.addView(c);
        }
        nf.addView(al);
        nf.addView(Ui.divider(this));
        Switch sw = new Switch(this);
        sw.setText("Avisar quando estiver na hora de falar com alguém");
        sw.setTextColor(Ui.col(this, R.color.text));
        sw.setTextSize(16);
        sw.setChecked(Prefs.remindOverdue(this));
        sw.setOnCheckedChangeListener((v, on) -> Prefs.setRemindOverdue(this, on));
        nf.addView(sw, Ui.lpMW());
        nf.addView(Ui.spacer(this, 14));
        TextView test = Ui.button(this, "Mostrar os avisos de hoje agora", false);
        test.setOnClickListener(v -> new Thread(() -> Daily.run(getApplicationContext())).start());
        nf.addView(test, Ui.lpMW());
        content.addView(nf);

        LinearLayout bk = Ui.card(this);
        bk.addView(Ui.cardHeader(this, R.drawable.ic_history, "Cópia de segurança"));
        bk.addView(Ui.text(this, "Os dados ficam só neste telemóvel. Exporte uma cópia em JSON para guardar ou passar para outro telemóvel.",
                14, R.color.text2, false));
        bk.addView(Ui.spacer(this, 12));
        TextView ex = Ui.button(this, "Exportar", true);
        ex.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "amigos-" + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) + ".json");
            startActivityForResult(i, REQ_EXPORT);
        });
        bk.addView(ex, Ui.lpMW());
        bk.addView(Ui.spacer(this, 8));
        TextView im = Ui.button(this, "Restaurar", false);
        im.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Restaurar cópia?")
                .setMessage("Os amigos e o histórico atuais serão substituídos pelos da cópia.")
                .setPositiveButton("Escolher ficheiro", (d, w) -> startActivityForResult(
                        new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_IMPORT))
                .setNegativeButton("Cancelar", null).show());
        bk.addView(im, Ui.lpMW());
        content.addView(bk);

        LinearLayout ab = Ui.card(this);
        String ver = "";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}
        ab.addView(Ui.optionRow(this, getString(R.string.app_name) + " " + ver, "Sem contas, sem publicidade, sem internet.", null));
        content.addView(ab);
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
                Toast.makeText(this, "Cópia exportada.", Toast.LENGTH_SHORT).show();
            } else if (req == REQ_IMPORT) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                }
                int count = Backup.restore(this, new String(bo.toByteArray(), StandardCharsets.UTF_8));
                Toast.makeText(this, count + " amigos restaurados.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Não foi possível concluir").setMessage(String.valueOf(e.getMessage()))
                    .setPositiveButton("OK", null).show();
        }
    }
}
