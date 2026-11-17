package pt.amigos.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferências da app. */
public final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("amigos", Context.MODE_PRIVATE); }

    /** 0 automático, 1 claro, 2 escuro. */
    public static int theme(Context c) { return sp(c).getInt("theme", 0); }
    public static void setTheme(Context c, int v) { sp(c).edit().putInt("theme", v).apply(); }

    /** Hora do aviso diário (minutos desde a meia-noite). */
    public static int notifyTime(Context c) { return sp(c).getInt("notify_time", 9 * 60); }
    public static void setNotifyTime(Context c, int v) { sp(c).edit().putInt("notify_time", v).apply(); }

    /** Avisar do aniversário N dias antes (0 = só no dia). */
    public static int birthdayAdvance(Context c) { return sp(c).getInt("bday_adv", 1); }
    public static void setBirthdayAdvance(Context c, int v) { sp(c).edit().putInt("bday_adv", v).apply(); }

    public static boolean remindOverdue(Context c) { return sp(c).getBoolean("remind_overdue", true); }
    public static void setRemindOverdue(Context c, boolean v) { sp(c).edit().putBoolean("remind_overdue", v).apply(); }
}
