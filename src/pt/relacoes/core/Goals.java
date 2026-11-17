package pt.relacoes.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Metas semanais (semanas de segunda a domingo) e séries de semanas cumpridas. */
public final class Goals {
    private Goals() {}

    /** Índice da semana (segunda-feira como início) de um número de dia. */
    public static long week(long day) { return Math.floorDiv(day - 4, 7); }

    /** Primeiro dia (segunda-feira) de uma semana. */
    public static long weekStart(long week) { return week * 7 + 4; }

    /** Pessoas diferentes contactadas por semana. "contacts" = pares {personId, dia}. */
    public static Map<Long, Integer> perWeek(List<long[]> contacts) {
        Map<Long, Set<Long>> m = new HashMap<>();
        for (long[] c : contacts) {
            Set<Long> s = m.get(week(c[1]));
            if (s == null) m.put(week(c[1]), s = new HashSet<>());
            s.add(c[0]);
        }
        Map<Long, Integer> out = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> e : m.entrySet()) out.put(e.getKey(), e.getValue().size());
        return out;
    }

    /** Semanas seguidas com a meta cumprida, terminando na semana atual (se já cumprida) ou na anterior. */
    public static int streak(Map<Long, Integer> perWeek, long today, int goal) {
        if (goal <= 0) return 0;
        long w = week(today);
        int s = 0;
        if (count(perWeek, w) >= goal) s++;
        for (long k = w - 1; count(perWeek, k) >= goal; k--) s++;
        return s;
    }

    public static int count(Map<Long, Integer> perWeek, long week) {
        Integer v = perWeek.get(week);
        return v == null ? 0 : v;
    }
}
