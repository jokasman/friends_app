package pt.relacoes.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferências da app. Horas em minutos desde a meia-noite. */
public final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("relacoes", Context.MODE_PRIVATE); }

    public static int theme(Context c) { return sp(c).getInt("theme", 0); }
    public static void setTheme(Context c, int v) { sp(c).edit().putInt("theme", v).apply(); }

    /** Hora da manhã: sugestões do dia, avisos de aniversário e de manter contacto. */
    public static int morning(Context c) { return sp(c).getInt("morning", 9 * 60); }
    public static void setMorning(Context c, int v) { sp(c).edit().putInt("morning", v).apply(); }

    /** Início do período sem insistências (até à hora da manhã). */
    public static int quietStart(Context c) { return sp(c).getInt("quiet", 22 * 60); }
    public static void setQuietStart(Context c, int v) { sp(c).edit().putInt("quiet", v).apply(); }

    /** Repetir a notificação de N em N minutos até ser feito (lembretes novos; 0 = não repete). */
    public static int nagDefault(Context c) { return sp(c).getInt("nag_min", 5); }
    public static void setNagDefault(Context c, int v) { sp(c).edit().putInt("nag_min", v).apply(); }

    /** Adiar pela notificação (min). */
    public static int snooze(Context c) { return sp(c).getInt("snooze", 60); }
    public static void setSnooze(Context c, int v) { sp(c).edit().putInt("snooze", v).apply(); }

    public static boolean suggestions(Context c) { return sp(c).getBoolean("suggest", true); }
    public static void setSuggestions(Context c, boolean v) { sp(c).edit().putBoolean("suggest", v).apply(); }

    public static int suggestCount(Context c) { return sp(c).getInt("suggest_n", 3); }
    public static void setSuggestCount(Context c, int v) { sp(c).edit().putInt("suggest_n", v).apply(); }

    public static boolean monthly(Context c) { return sp(c).getBoolean("monthly", true); }
    public static void setMonthly(Context c, boolean v) { sp(c).edit().putBoolean("monthly", v).apply(); }

    /** Meta semanal: pessoas diferentes contactadas por semana (0 = sem meta). */
    public static int weeklyGoal(Context c) { return sp(c).getInt("goal", 5); }
    public static void setWeeklyGoal(Context c, int v) { sp(c).edit().putInt("goal", v).apply(); }

    /** Valores por defeito para pessoas novas. */
    public static int defaultEvery(Context c) { return sp(c).getInt("def_every", 30); }
    public static void setDefaultEvery(Context c, int v) { sp(c).edit().putInt("def_every", v).apply(); }
    public static int defaultBdayBefore(Context c) { return sp(c).getInt("def_bday_before", 3); }
    public static void setDefaultBdayBefore(Context c, int v) { sp(c).edit().putInt("def_bday_before", v).apply(); }

    /** Data da última cópia exportada (ms). */
    public static long lastExport(Context c) { return sp(c).getLong("last_export", 0); }
    public static void setLastExport(Context c, long v) { sp(c).edit().putLong("last_export", v).apply(); }

    /** Pausa noturna das insistências (das quietStart à hora da manhã). */
    public static boolean nightPause(Context c) { return sp(c).getBoolean("night_pause", true); }
    public static void setNightPause(Context c, boolean v) { sp(c).edit().putBoolean("night_pause", v).apply(); }

    /** Contactos detetados pelas notificações: 0 = perguntar, 1 = registar logo. */
    public static int detectMode(Context c) { return sp(c).getInt("detect_mode", 0); }
    public static void setDetectMode(Context c, int v) { sp(c).edit().putInt("detect_mode", v).apply(); }

    public static String str(Context c, String key) { return sp(c).getString(key, null); }
    public static void setStr(Context c, String key, String v) { sp(c).edit().putString(key, v).apply(); }

    public static boolean onboarded(Context c) { return sp(c).getBoolean("onboarded", false); }
    public static void setOnboarded(Context c) { sp(c).edit().putBoolean("onboarded", true).apply(); }
}
