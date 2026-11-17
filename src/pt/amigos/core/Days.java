package pt.amigos.core;

import java.util.Calendar;

/** Contas de datas em dias inteiros, sem depender do fuso horário (lógica pura e testável). */
public final class Days {
    private Days() {}

    public static final long DAY_MS = 86_400_000L;

    /** Número do dia (dias desde 1970-01-01) de uma data civil. */
    public static long of(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    /** Número do dia local de um instante. */
    public static long of(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static boolean leap(int y) { return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0; }

    public static int monthLength(int y, int m) {
        switch (m) {
            case 2: return leap(y) ? 29 : 28;
            case 4: case 6: case 9: case 11: return 30;
            default: return 31;
        }
    }

    /** Dia do aniversário num ano (29/02 passa a 28/02 em anos comuns). */
    public static long birthdayIn(int year, int m, int d) {
        return of(year, m, Math.min(d, monthLength(year, m)));
    }
}
