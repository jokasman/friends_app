package pt.amigos.core;

/** Um amigo: dados de contacto, aniversário e de quanto em quanto tempo quer falar com ele. */
public class Friend {
    public long id;
    public String name = "";
    public String phone = "";
    public String email = "";
    public String notes = "";
    /** Aniversário: mês 1..12 e dia 1..31 (0 = sem aniversário); ano 0 = desconhecido. */
    public int bMonth, bDay, bYear;
    /** Falar pelo menos a cada N dias (0 = sem lembrete). */
    public int everyDays;
    /** Último contacto registado (ms) ou 0 se nunca. */
    public long lastContact;
    public long created;

    public boolean hasBirthday() { return bMonth >= 1 && bMonth <= 12 && bDay >= 1 && bDay <= 31; }

    public String initials() {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "?";
        String[] p = n.split("\\s+");
        String a = p[0].substring(0, 1);
        String b = p.length > 1 ? p[p.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase();
    }

    public Friend copy() {
        Friend f = new Friend();
        f.id = id; f.name = name; f.phone = phone; f.email = email; f.notes = notes;
        f.bMonth = bMonth; f.bDay = bDay; f.bYear = bYear; f.everyDays = everyDays;
        f.lastContact = lastContact; f.created = created;
        return f;
    }
}
