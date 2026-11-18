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

    /** Insistência por defeito (min; 0 = não insiste). */
    public static int nagDefault(Context c) { return sp(c).getInt("nag", 120); }
    public static void setNagDefault(Context c, int v) { sp(c).edit().putInt("nag", v).apply(); }

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

    // ---------------------------------------------------------------- Despertador

    /** "+N min" no ecrã do alarme e na notificação. */
    public static int quickSnooze(Context c) { return sp(c).getInt("quick_snooze", 10); }
    public static void setQuickSnooze(Context c, int v) { sp(c).edit().putInt("quick_snooze", v).apply(); }

    /** Som do despertador (URI) ou null para o alarme do sistema. */
    public static String alarmSound(Context c) { return sp(c).getString("alarm_sound", null); }
    public static void setAlarmSound(Context c, String v) { sp(c).edit().putString("alarm_sound", v).apply(); }

    /** Volume próprio do despertador em % (10–100), independente do volume de alarme do sistema. */
    public static int alarmVolume(Context c) { return sp(c).getInt("alarm_volume", 80); }
    public static void setAlarmVolume(Context c, int v) { sp(c).edit().putInt("alarm_volume", Math.max(10, Math.min(100, v))).apply(); }

    /** Começar baixo e aumentar até ao volume escolhido. */
    public static boolean crescendo(Context c) { return sp(c).getBoolean("crescendo", true); }
    public static void setCrescendo(Context c, boolean v) { sp(c).edit().putBoolean("crescendo", v).apply(); }

    /** Pausa de alarmes entre duas datas (ms; 0 = sem pausa). */
    public static long pauseStart(Context c) { return sp(c).getLong("pause_start", 0L); }
    public static long pauseEnd(Context c) { return sp(c).getLong("pause_end", 0L); }
    public static void setPause(Context c, long start, long end) { sp(c).edit().putLong("pause_start", start).putLong("pause_end", end).apply(); }

    /** Marcar alarmes como feitos só com impressão digital. */
    public static boolean requireBiometric(Context c) { return sp(c).getBoolean("biometric", true); }
    public static void setRequireBiometric(Context c, boolean v) { sp(c).edit().putBoolean("biometric", v).apply(); }

    /** Abrir "Por tratar" ao desbloquear o telemóvel. */
    public static boolean showPending(Context c) { return sp(c).getBoolean("show_pending", true); }
    public static void setShowPending(Context c, boolean v) { sp(c).edit().putBoolean("show_pending", v).apply(); }

    /** Contactos detetados pelas notificações: 0 = perguntar, 1 = registar logo. */
    public static int detectMode(Context c) { return sp(c).getInt("detect_mode", 0); }
    public static void setDetectMode(Context c, int v) { sp(c).edit().putInt("detect_mode", v).apply(); }

    public static String str(Context c, String key) { return sp(c).getString(key, null); }
    public static void setStr(Context c, String key, String v) { sp(c).edit().putString(key, v).apply(); }

    public static boolean onboarded(Context c) { return sp(c).getBoolean("onboarded", false); }
    public static void setOnboarded(Context c) { sp(c).edit().putBoolean("onboarded", true).apply(); }
}
