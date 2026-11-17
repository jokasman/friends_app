package pt.amigos.core;

/** Aniversários e contactos em atraso. Todas as datas são números de dia ({@link Days}). */
public final class Schedule {
    private Schedule() {}

    /** Dias até ao próximo aniversário (0 = hoje) ou -1 se não tiver aniversário. */
    public static int daysToBirthday(Friend f, int year, long today) {
        if (!f.hasBirthday()) return -1;
        long b = Days.birthdayIn(year, f.bMonth, f.bDay);
        if (b < today) b = Days.birthdayIn(year + 1, f.bMonth, f.bDay);
        return (int) (b - today);
    }

    /** Idade que faz no próximo aniversário, ou 0 se o ano for desconhecido. */
    public static int turning(Friend f, int year, long today) {
        if (!f.hasBirthday() || f.bYear <= 0) return 0;
        long b = Days.birthdayIn(year, f.bMonth, f.bDay);
        int y = b < today ? year + 1 : year;
        return y - f.bYear;
    }

    /** Dia em que deve voltar a falar (último contacto, ou criação, + intervalo); Long.MAX_VALUE sem lembrete. */
    public static long dueDay(Friend f, long lastDay, long createdDay) {
        if (f.everyDays <= 0) return Long.MAX_VALUE;
        long base = f.lastContact > 0 ? lastDay : createdDay;
        return base + f.everyDays;
    }

    /** Dias de atraso (0 = é hoje, negativo = ainda falta). */
    public static long overdue(Friend f, long lastDay, long createdDay, long today) {
        long due = dueDay(f, lastDay, createdDay);
        return due == Long.MAX_VALUE ? Long.MIN_VALUE : today - due;
    }

    /** "hoje", "ontem", "há 5 dias", "há 3 semanas", "há 2 meses", "há 1 ano". */
    public static String ago(long days) {
        if (days <= 0) return "hoje";
        if (days == 1) return "ontem";
        if (days < 14) return "há " + days + " dias";
        if (days < 60) return "há " + (days / 7) + " semanas";
        if (days < 365) return "há " + (days / 30) + " meses";
        long y = days / 365;
        return y == 1 ? "há 1 ano" : "há " + y + " anos";
    }

    /** "hoje", "amanhã", "daqui a 5 dias". */
    public static String inDays(int days) {
        if (days <= 0) return "hoje";
        if (days == 1) return "amanhã";
        return "daqui a " + days + " dias";
    }

    public static final int[] EVERY = {0, 7, 14, 30, 60, 90, 180, 365};
    public static final String[] EVERY_NAMES = {"Sem lembrete", "Semanal", "Quinzenal", "Mensal", "2 em 2 meses", "Trimestral", "Semestral", "Anual"};

    public static String everyName(int days) {
        for (int i = 0; i < EVERY.length; i++) if (EVERY[i] == days) return EVERY_NAMES[i];
        return days <= 0 ? EVERY_NAMES[0] : "A cada " + days + " dias";
    }

    public static final String[] MONTHS = {"janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"};

    /** "12 de março" ou "12 de março de 1990". */
    public static String birthdayText(Friend f) {
        if (!f.hasBirthday()) return "";
        String s = f.bDay + " de " + MONTHS[f.bMonth - 1];
        return f.bYear > 0 ? s + " de " + f.bYear : s;
    }

    /** Lê "12/3" ou "12/03/1990". Devolve {dia, mês, ano} ou null se for inválido. */
    public static int[] parseBirthday(String s) {
        if (s == null) return null;
        String[] p = s.trim().split("[/.\\-]");
        if (p.length < 2 || p.length > 3) return null;
        try {
            int d = Integer.parseInt(p[0].trim()), m = Integer.parseInt(p[1].trim());
            int y = p.length == 3 && !p[2].trim().isEmpty() ? Integer.parseInt(p[2].trim()) : 0;
            if (y > 0 && y < 100) y += y > 30 ? 1900 : 2000;
            if (m < 1 || m > 12 || d < 1 || d > Days.monthLength(y > 0 ? y : 2000, m)) return null;
            if (y != 0 && (y < 1900 || y > 2100)) return null;
            return new int[]{d, m, y};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
