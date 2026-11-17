package pt.relacoes.data;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pt.relacoes.core.Days;
import pt.relacoes.core.Goals;
import pt.relacoes.core.Health;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Person;
import pt.relacoes.core.Recur;

/** Cálculos para os ecrãs Hoje e Resumo, o widget e as notificações. */
public final class Insights {
    private Insights() {}

    public static long today() { return Days.of(System.currentTimeMillis()); }

    public static int health(Person p, long today) { return Health.of(p, Days.of(p.lastAt), Days.of(p.created), today); }

    public static double urgency(Person p, long today) { return Health.urgency(p, Days.of(p.lastAt), Days.of(p.created), today); }

    public static long daysSince(Person p, long today) { return Health.daysSince(p, Days.of(p.lastAt), Days.of(p.created), today); }

    /** Sugestões do dia: pessoas mais urgentes (pelo menos 75% do prazo), sem contacto hoje. */
    public static List<Person> suggestions(Context c, int max) {
        long today = today();
        List<Person> out = new ArrayList<>();
        for (Person p : Db.get(c).people()) {
            if (p.everyDays <= 0) continue;
            if (p.lastAt > 0 && Days.of(p.lastAt) == today) continue;
            if (urgency(p, today) >= 0.75) out.add(p);
        }
        Collections.sort(out, (a, b) -> Double.compare(urgency(b, today), urgency(a, today)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Pessoas há mais tempo sem contacto (com frequência definida primeiro, por urgência). */
    public static List<Person> longestWithoutContact(Context c, int max) {
        long today = today();
        List<Person> out = new ArrayList<>(Db.get(c).people());
        Collections.sort(out, (a, b) -> {
            double ua = urgency(a, today), ub = urgency(b, today);
            if (ua != ub) return Double.compare(ub, ua);
            return Long.compare(daysSince(b, today), daysSince(a, today));
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Aniversários nos próximos N dias, ordenados. */
    public static List<Person> upcomingBirthdays(Context c, int days) {
        long today = today();
        List<Person> out = new ArrayList<>();
        for (Person p : Db.get(c).people()) if (p.hasBirthday() && Recur.nextBirthday(p, today) - today <= days) out.add(p);
        Collections.sort(out, (a, b) -> Long.compare(Recur.nextBirthday(a, today), Recur.nextBirthday(b, today)));
        return out;
    }

    public static int age(Person p, long day) {
        if (!p.hasBirthday() || p.bYear <= 0) return 0;
        return Recur.ymd(day)[0] - p.bYear;
    }

    /** Idade que faz no próximo aniversário (0 se desconhecida). */
    public static int turning(Person p, long today) {
        return p.hasBirthday() ? age(p, Recur.nextBirthday(p, today)) : 0;
    }

    /** {contactadas esta semana, meta, série de semanas}. */
    public static int[] goal(Context c) {
        int goal = Prefs.weeklyGoal(c);
        long today = today();
        long from = Recur.at(today - 7 * 120, 0);
        List<long[]> pairs = new ArrayList<>();
        for (Interaction i : Db.get(c).interactionsBetween(from, Long.MAX_VALUE)) pairs.add(new long[]{i.personId, Days.of(i.at)});
        Map<Long, Integer> pw = Goals.perWeek(pairs);
        return new int[]{Goals.count(pw, Goals.week(today)), goal, Goals.streak(pw, today, goal)};
    }

    /** Resumo de um mês. */
    public static final class Month {
        public int year, month;
        public List<Person> contacted = new ArrayList<>();
        public int interactions;
        public List<Person> late = new ArrayList<>();
        public List<Person> nextMonthBirthdays = new ArrayList<>();
        /** Por semana do mês: {dia de início, contactadas}. */
        public List<long[]> weeks = new ArrayList<>();
        public int goal, weeksMet;
    }

    public static Month month(Context c, int year, int month) {
        Month m = new Month();
        m.year = year; m.month = month;
        Db db = Db.get(c);
        Map<Long, Person> byId = db.peopleById();
        long first = Days.of(year, month, 1);
        long last = Days.of(year, month, Days.monthLength(year, month));
        List<Interaction> list = db.interactionsBetween(Recur.at(first, 0), Recur.at(last + 1, 0));
        m.interactions = list.size();
        Set<Long> seen = new HashSet<>();
        List<long[]> pairs = new ArrayList<>();
        for (Interaction i : list) {
            pairs.add(new long[]{i.personId, Days.of(i.at)});
            if (seen.add(i.personId) && byId.containsKey(i.personId)) m.contacted.add(byId.get(i.personId));
        }
        long today = today();
        for (Person p : byId.values()) if (health(p, today) == pt.relacoes.core.Health.RED) m.late.add(p);
        Collections.sort(m.late, (a, b) -> Double.compare(urgency(b, today), urgency(a, today)));
        int ny = month == 12 ? year + 1 : year, nm = month == 12 ? 1 : month + 1;
        for (Person p : byId.values()) if (p.bMonth == nm) m.nextMonthBirthdays.add(p);
        Collections.sort(m.nextMonthBirthdays, (a, b) -> a.bDay - b.bDay);
        Map<Long, Integer> pw = Goals.perWeek(pairs);
        m.goal = Prefs.weeklyGoal(c);
        for (long w = Goals.week(first); w <= Goals.week(last); w++) {
            int n = Goals.count(pw, w);
            m.weeks.add(new long[]{Goals.weekStart(w), n});
            if (m.goal > 0 && n >= m.goal) m.weeksMet++;
        }
        return m;
    }

    public static int[] previousMonth() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, -1);
        return new int[]{c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1};
    }
}
