package pt.relacoes.core;

/** Uma pessoa com quem o utilizador quer manter contacto. */
public class Person {
    public static final int TONE_PERSONAL = 0, TONE_PRO = 1;

    public long id;
    public String name = "", phone = "", email = "", notes = "";
    /** Como o utilizador lhe chama (ex.: "Zé", "Mãe"); usado nas mensagens. Vazio = primeiro nome. */
    public String nickname = "";
    /** Nome do ficheiro da foto (pasta privada da app) ou "" sem foto. */
    public String photo = "";
    /** Aniversário: mês 1..12, dia 1..31 (0 = sem); ano 0 = desconhecido. */
    public int bMonth, bDay, bYear;
    /** Falar pelo menos a cada N dias (0 = sem frequência). */
    public int everyDays;
    /** Avisos automáticos do aniversário: no próprio dia e/ou N dias antes (0 = não). */
    public boolean bdayOnDay = true;
    public int bdayBefore;
    /** Mensagens de aniversário longas (senão curtas) e tom pessoal ou profissional. */
    public boolean longMessages;
    public int tone;
    /** Gostos e coisas a evitar (texto livre). */
    public String likes = "", avoid = "";
    public long created;
    /** Último contacto (ms, 0 = nunca) e meio usado (-1 = nenhum). */
    public long lastAt;
    public int lastMedium = -1;

    public boolean hasBirthday() { return bMonth >= 1 && bMonth <= 12 && bDay >= 1 && bDay <= 31; }

    public String firstName() {
        String n = name == null ? "" : name.trim();
        int i = n.indexOf(' ');
        return i > 0 ? n.substring(0, i) : n;
    }

    /** Nome a usar nas mensagens: como lhe chama, ou o primeiro nome. */
    public String callName() {
        String n = nickname == null ? "" : nickname.trim();
        return n.isEmpty() ? firstName() : n;
    }

    public String initials() {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "?";
        String[] p = n.split("\\s+");
        String a = p[0].substring(0, 1);
        String b = p.length > 1 ? p[p.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase();
    }
}
