package pt.relacoes.core;

/** Saúde da relação: verde, amarelo ou vermelho conforme o tempo desde o último contacto face à frequência. */
public final class Health {
    private Health() {}

    public static final int NONE = 0, GREEN = 1, YELLOW = 2, RED = 3;
    public static final String[] NAMES = {"Sem frequência", "Em dia", "Quase na hora", "Em atraso"};

    /** Dias desde o último contacto (ou desde a criação, se nunca houve contacto). */
    public static long daysSince(Person p, long lastDay, long createdDay, long today) {
        return today - (p.lastAt > 0 ? lastDay : createdDay);
    }

    public static int of(Person p, long lastDay, long createdDay, long today) {
        if (p.everyDays <= 0) return NONE;
        long d = daysSince(p, lastDay, createdDay, today);
        if (d >= p.everyDays) return RED;
        if (d >= Math.ceil(p.everyDays * 0.75)) return YELLOW;
        return GREEN;
    }

    /** Urgência para ordenar sugestões (maior = mais urgente). */
    public static double urgency(Person p, long lastDay, long createdDay, long today) {
        if (p.everyDays <= 0) return -1;
        return (double) daysSince(p, lastDay, createdDay, today) / p.everyDays;
    }

    public static final int[] COLORS = {0xFF9AA0AC, 0xFF2E7D32, 0xFFF9A825, 0xFFD32F2F};
}
