package pt.relacoes.core;

/** Modelo de mensagem com variáveis {nome}, {primeiro_nome} e {idade}. */
public class Template {
    public static final int BIRTHDAY = 0, MISS = 1, PRO = 2;
    public static final String[] TYPES = {"Aniversário", "Saudade", "Profissional"};
    public static final int SHORT = 0, LONG = 1;
    public static final String[] SIZES = {"Curta", "Longa"};

    public long id;
    public int type, size;
    public String text = "";
    public boolean builtin;

    public static String typeName(int t) { return t >= 0 && t < TYPES.length ? TYPES[t] : TYPES[0]; }

    /** Substitui as variáveis pelos dados da pessoa. */
    public static String fill(String text, Person p, int age) {
        String s = text.replace("{primeiro_nome}", p.callName()).replace("{nome}", p.name.trim());
        return s.replace("{idade}", age > 0 ? String.valueOf(age) : "").replace("  ", " ").replace(" ,", ",").replace(" !", "!");
    }

    /** Modelos incluídos de origem: {tipo, tamanho, texto}. */
    public static final Object[][] BUILTIN = {
        {BIRTHDAY, SHORT, "Parabéns, {primeiro_nome}! Que tenhas um dia fantástico 🎉"},
        {BIRTHDAY, SHORT, "Muitos parabéns, {primeiro_nome}! Um grande abraço e que este ano seja dos melhores."},
        {BIRTHDAY, SHORT, "Feliz aniversário, {primeiro_nome}! 🎂 Aproveita muito o teu dia."},
        {BIRTHDAY, SHORT, "Parabéns! Que nunca te falte saúde, alegria e boa companhia. Um beijinho grande!"},
        {BIRTHDAY, SHORT, "{primeiro_nome}, hoje o dia é teu! Parabéns e muitas felicidades 🥳"},
        {BIRTHDAY, SHORT, "Parabéns pelos {idade} anos, {primeiro_nome}! Que venham muitos mais."},
        {BIRTHDAY, SHORT, "Um feliz aniversário, {primeiro_nome}! Festeja como mereces."},
        {BIRTHDAY, LONG, "Muitos parabéns, {primeiro_nome}! 🎉 Neste dia especial quero desejar-te muita saúde, sucesso e momentos felizes com quem mais gostas. Obrigado por fazeres parte da minha vida. Aproveita muito o teu dia!"},
        {BIRTHDAY, LONG, "Feliz aniversário, {primeiro_nome}! Mais um ano cheio de histórias para contar. Que este novo capítulo te traga tudo o que desejas e ainda mais umas surpresas boas pelo caminho. Um abraço apertado!"},
        {BIRTHDAY, LONG, "Parabéns, {primeiro_nome}! 🎂 Hoje é dia de celebrar a pessoa incrível que és. Que os teus {idade} anos sejam vividos com energia, amor e muitas gargalhadas. Temos de combinar algo para festejar!"},
        {BIRTHDAY, LONG, "{primeiro_nome}, muitos parabéns! Desejo-te um dia cheio de carinho e um ano cheio de conquistas. Que nunca te falte motivo para sorrir. Conta comigo para o que precisares!"},
        {BIRTHDAY, LONG, "Parabéns, {primeiro_nome}! Que sorte a minha ter-te por perto. Que este ano te traga saúde, paz e muitas aventuras. Celebra em grande, que mereces tudo de bom! 🥂"},
        {MISS, SHORT, "Olá {primeiro_nome}! Lembrei-me de ti. Como estás?"},
        {MISS, SHORT, "Olá {primeiro_nome}, há quanto tempo! Está tudo bem por aí?"},
        {MISS, SHORT, "{primeiro_nome}, temos de pôr a conversa em dia! Quando te dá jeito um café?"},
        {MISS, SHORT, "Olá! Só para saber como andas, {primeiro_nome} 😊"},
        {MISS, LONG, "Olá {primeiro_nome}! Já passou demasiado tempo desde a última vez que falámos e lembrei-me de ti. Como tens andado? Conta-me as novidades e vamos combinar alguma coisa em breve!"},
        {MISS, LONG, "{primeiro_nome}, estive a pensar em ti e apeteceu-me dar notícias. Por aqui está tudo bem. E contigo, como vão as coisas? Gostava muito de estar contigo um destes dias."},
        {PRO, SHORT, "Olá {primeiro_nome}, espero que esteja tudo bem. Temos de falar em breve!"},
        {PRO, SHORT, "Bom dia {primeiro_nome}! Como estão a correr as coisas?"},
        {PRO, SHORT, "Olá {primeiro_nome}, lembrei-me de si. Tem disponibilidade para um café esta semana?"},
        {PRO, LONG, "Olá {primeiro_nome}, espero que esteja tudo bem consigo. Há algum tempo que não falamos e gostava de saber como estão a correr os seus projetos. Se tiver disponibilidade, podíamos marcar um café ou uma chamada rápida nas próximas semanas."},
        {PRO, LONG, "Bom dia {primeiro_nome}! Espero que esteja a ter um bom ano. Gostava de retomar o contacto e trocar algumas ideias. Diga-me quando lhe dá mais jeito e combinamos."},
        {PRO, SHORT, "Muitos parabéns, {primeiro_nome}! Votos de um excelente dia e de um ano cheio de sucessos."},
    };
}
