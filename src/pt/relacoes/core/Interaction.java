package pt.relacoes.core;

/** Um contacto registado com uma pessoa. Se tiver mensagem, é uma mensagem enviada (ex.: de aniversário). */
public class Interaction {
    public static final int CALL = 0, SMS = 1, WHATSAPP = 2, FACEBOOK = 3, MESSENGER = 4, INSTAGRAM = 5, IN_PERSON = 6, EMAIL = 7, OTHER = 8;
    public static final String[] MEDIUMS = {"Chamada", "SMS", "WhatsApp", "Facebook", "Messenger", "Instagram", "Pessoalmente", "Email", "Outro"};

    public long id, personId, at;
    public int medium = OTHER;
    public String note = "";
    /** Texto da mensagem enviada ("" se não houve mensagem). */
    public String message = "";
    /** Modelo usado (0 = nenhum). */
    public long templateId;
    /** Quem tomou a iniciativa: true = eu, false = a outra pessoa. */
    public boolean byMe = true;

    public static String mediumName(int m) { return m >= 0 && m < MEDIUMS.length ? MEDIUMS[m] : "—"; }
}
