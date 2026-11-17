package pt.amigos.core;

/** Um contacto registado com um amigo (conversa, chamada, mensagem ou encontro). */
public class Contact {
    public static final int TALK = 0, CALL = 1, MESSAGE = 2, MEET = 3;
    public static final String[] KIND_NAMES = {"Conversa", "Chamada", "Mensagem", "Encontro"};

    public long id, friendId, at;
    public int kind;
    public String note = "";

    public static String kindName(int k) { return k >= 0 && k < KIND_NAMES.length ? KIND_NAMES[k] : KIND_NAMES[0]; }
}
