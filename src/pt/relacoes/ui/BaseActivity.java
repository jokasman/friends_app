package pt.relacoes.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;

import pt.relacoes.data.Prefs;

/**
 * Base de todos os ecrãs: aplica o tema escolhido e, ao voltar de uma app de contacto aberta pela app
 * (telefone, WhatsApp, SMS…), propõe registar o contacto.
 */
public abstract class BaseActivity extends Activity {
    public static final int THEME_AUTO = 0, THEME_LIGHT = 1, THEME_DARK = 2;
    private int appliedTheme = -1;

    /** Contacto iniciado a partir da app, à espera de ser registado ao voltar. */
    long pendingPerson;
    int pendingMedium;
    String pendingMessage = "";
    long pendingTemplate, pendingReminder;

    static Context themed(Context base) {
        int t = Prefs.theme(base);
        if (t == THEME_AUTO) return base;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (t == THEME_DARK ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(c);
    }

    @Override protected void attachBaseContext(Context base) {
        appliedTheme = Prefs.theme(base);
        super.attachBaseContext(themed(base));
    }

    @Override protected void onResume() {
        super.onResume();
        if (appliedTheme != Prefs.theme(this)) recreate();
    }

    void expectContact(long person, int medium, String message, long template, long reminder) {
        pendingPerson = person;
        pendingMedium = medium;
        pendingMessage = message == null ? "" : message;
        pendingTemplate = template;
        pendingReminder = reminder;
    }

    @Override protected void onRestart() {
        super.onRestart();
        if (pendingPerson > 0) {
            Intent i = LogActivity.intent(this, pendingPerson, pendingReminder, pendingMedium, pendingMessage, pendingTemplate)
                    .putExtra(LogActivity.EXTRA_ASK, true);
            pendingPerson = 0;
            startActivity(i);
        }
    }
}
