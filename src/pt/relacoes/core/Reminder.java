package pt.relacoes.core;

/**
 * Lembrete associado a uma pessoa. Modelo unificado para:
 * único, recorrente, manter contacto (automático) e aniversário (N dias antes do aniversário).
 */
public class Reminder {
    public static final int ONCE = 0, REPEAT = 1, KEEP = 2, BIRTHDAY = 3;
    public static final int DAY = 1, WEEK = 2, MONTH = 3, YEAR = 4;
    public static final String[] UNITS = {"", "dia(s)", "semana(s)", "mês(es)", "ano(s)"};
    /** Lembretes criados automaticamente a partir da pessoa. */
    public static final int AUTO_NONE = 0, AUTO_KEEP = 1, AUTO_BDAY_DAY = 2, AUTO_BDAY_BEFORE = 3, AUTO_FOLLOWUP = 4;

    public long id, personId;
    public String text = "";
    public int kind = ONCE;
    public int auto = AUTO_NONE;
    /** Data/hora prevista. */
    public long dueAt;
    /** Repetição (REPEAT): a cada repeatN unidades. */
    public int repeatUnit = MONTH, repeatN = 1;
    /** Aniversário: dias antes (0 = no dia). */
    public int offsetDays;
    /** Insistência: volta a notificar a cada N minutos até ser feito (0 = não insiste). */
    public int nagMin = 60;
    /** Próxima notificação (Long.MAX_VALUE = nenhuma). */
    public long nextAt = Long.MAX_VALUE;
    public boolean done;
    public long doneAt;
    /** Já notificado pelo menos uma vez para a data atual. */
    public boolean notified;
    /** Toca como despertador (som de alarme em ecrã inteiro até uma decisão). */
    public boolean alarm;

    /** Gerido automaticamente a partir da pessoa (manter contacto e avisos de aniversário): não se edita à mão. */
    public boolean isManaged() { return auto == AUTO_KEEP || auto == AUTO_BDAY_DAY || auto == AUTO_BDAY_BEFORE; }

    public boolean isPending(long now) { return !done && dueAt <= now; }

    public static String kindName(int k) {
        switch (k) {
            case REPEAT: return "Recorrente";
            case KEEP: return "Manter contacto";
            case BIRTHDAY: return "Aniversário";
            default: return "Único";
        }
    }

    public String repeatText() {
        if (kind != REPEAT) return "";
        String[] one = {"", "Todos os dias", "Todas as semanas", "Todos os meses", "Todos os anos"};
        String[] many = {"", "dias", "semanas", "meses", "anos"};
        int u = repeatUnit < 1 || repeatUnit > 4 ? MONTH : repeatUnit;
        return repeatN <= 1 ? one[u] : "A cada " + repeatN + " " + many[u];
    }
}
