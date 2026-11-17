package pt.relacoes.core;

import java.util.Calendar;

/** Cálculo das datas dos lembretes (lógica pura, usa o fuso horário do sistema). */
public final class Recur {
    private Recur() {}

    /** Instante do dia (número de dia) à hora indicada (minutos desde a meia-noite). */
    public static long at(long day, int minutes) {
        int[] ymd = ymd(day);
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(ymd[0], ymd[1] - 1, ymd[2], minutes / 60, minutes % 60, 0);
        return c.getTimeInMillis();
    }

    /** Hora (minutos desde a meia-noite) de um instante, ou "fallback" se t = 0. */
    public static int minutesOf(long t, int fallback) {
        if (t <= 0) return fallback;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /** {ano, mês, dia} de um número de dia. */
    public static int[] ymd(long z) {
        z += 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp + (mp < 10 ? 3 : -9);
        return new int[]{(int) (y + (m <= 2 ? 1 : 0)), (int) m, (int) d};
    }

    /** Próximo dia de aniversário a partir de "fromDay" (inclusive). */
    public static long nextBirthday(Person p, long fromDay) {
        int y = ymd(fromDay)[0];
        long b = Days.birthdayIn(y, p.bMonth, p.bDay);
        if (b < fromDay) b = Days.birthdayIn(y + 1, p.bMonth, p.bDay);
        return b;
    }

    /**
     * Data de um lembrete de aniversário: próximo aniversário cujo aviso (aniversário - offset) ainda não passou
     * em relação a "fromDay".
     */
    public static long birthdayDue(Person p, int offsetDays, long fromDay, int minutes) {
        long b = nextBirthday(p, fromDay + offsetDays);
        return at(b - offsetDays, minutes);
    }

    /** Prazo de "manter contacto": dia do último contacto (ou da criação) + frequência, à hora indicada. */
    public static long keepDue(Person p, long lastDay, long createdDay, int minutes) {
        long base = p.lastAt > 0 ? lastDay : createdDay;
        return at(base + Math.max(1, p.everyDays), minutes);
    }

    /** Soma n unidades a um instante, mantendo a hora. */
    public static long add(long t, int unit, int n) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        switch (unit) {
            case Reminder.DAY: c.add(Calendar.DAY_OF_MONTH, n); break;
            case Reminder.WEEK: c.add(Calendar.DAY_OF_MONTH, 7 * n); break;
            case Reminder.YEAR: c.add(Calendar.YEAR, n); break;
            default: c.add(Calendar.MONTH, n); break;
        }
        return c.getTimeInMillis();
    }

    /** Próxima ocorrência de um lembrete recorrente depois de concluído (sempre no futuro em relação a "now"). */
    public static long nextRepeat(Reminder r, long now) {
        int n = Math.max(1, r.repeatN);
        long t = add(r.dueAt, r.repeatUnit, n);
        for (int guard = 0; t <= now && guard < 10000; guard++) t = add(t, r.repeatUnit, n);
        return t;
    }

    /**
     * Ajusta uma insistência para fora do período de silêncio [quietStart, morning) (minutos desde a meia-noite).
     */
    public static long outsideQuiet(long t, int quietStart, int morning) {
        if (quietStart == morning) return t;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        boolean quiet = quietStart > morning ? (m >= quietStart || m < morning) : (m >= quietStart && m < morning);
        if (!quiet) return t;
        if (quietStart > morning && m >= quietStart) c.add(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, morning / 60);
        c.set(Calendar.MINUTE, morning % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
