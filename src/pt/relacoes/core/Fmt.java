package pt.relacoes.core;

import java.util.Calendar;
import java.util.Locale;

/** Textos de datas em português. */
public final class Fmt {
    private Fmt() {}

    public static final String[] MONTHS = {"janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"};
    private static final String[] WEEKDAYS = {"domingo", "segunda", "terça", "quarta", "quinta", "sexta", "sábado"};

    public static String ago(long days) {
        if (days <= 0) return "hoje";
        if (days == 1) return "ontem";
        if (days < 14) return "há " + days + " dias";
        if (days < 60) return "há " + (days / 7) + " semanas";
        if (days < 365) return "há " + (days / 30) + " meses";
        long y = days / 365;
        return y == 1 ? "há 1 ano" : "há " + y + " anos";
    }

    public static String inDays(long days) {
        if (days < 0) return days == -1 ? "ontem" : "há " + (-days) + " dias";
        if (days == 0) return "hoje";
        if (days == 1) return "amanhã";
        return "daqui a " + days + " dias";
    }

    public static String birthday(Person p) {
        if (!p.hasBirthday()) return "";
        String s = p.bDay + " de " + MONTHS[p.bMonth - 1];
        return p.bYear > 0 ? s + " de " + p.bYear : s;
    }

    public static String time(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    public static String hm(int minutes) { return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60); }

    /** "12 mar", "12 mar 2025" (se de outro ano). */
    public static String date(long t, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        int y = c.get(Calendar.YEAR);
        c.setTimeInMillis(t);
        String s = c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[c.get(Calendar.MONTH)].substring(0, 3);
        return c.get(Calendar.YEAR) == y ? s : s + " " + c.get(Calendar.YEAR);
    }

    /** "hoje às 09:00", "amanhã às 09:00", "sexta, 12 mar às 09:00". */
    public static String when(long t, long now) {
        long d = Days.of(t) - Days.of(now);
        String day;
        if (d == 0) day = "hoje";
        else if (d == 1) day = "amanhã";
        else if (d == -1) day = "ontem";
        else {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(t);
            day = (d > 0 && d < 7 ? WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " : "") + date(t, now);
        }
        return day + " às " + time(t);
    }

    public static String everyName(int days) {
        for (int i = 0; i < EVERY.length; i++) if (EVERY[i] == days) return EVERY_NAMES[i];
        return days <= 0 ? EVERY_NAMES[0] : "A cada " + days + " dias";
    }

    public static final int[] EVERY = {0, 7, 14, 30, 60, 90, 180, 365};
    public static final String[] EVERY_NAMES = {"Sem frequência", "Semanal", "Quinzenal", "Mensal", "2 em 2 meses", "Trimestral", "Semestral", "Anual"};
}
