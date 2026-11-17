package pt.relacoes.core;

/** Ligação entre duas pessoas: "other" é o/a {kind} de "person" (ex.: filho de). Guardada nos dois sentidos. */
public class Relation {
    public static final String[] KINDS = {"Cônjuge", "Namorado(a)", "Filho(a)", "Pai/Mãe", "Irmão/Irmã", "Avô/Avó", "Neto(a)",
            "Primo(a)", "Tio(a)", "Sobrinho(a)", "Amigo(a)", "Colega", "Chefe", "Subordinado(a)", "Outro"};
    private static final int[] INVERSE = {0, 1, 3, 2, 4, 6, 5, 7, 9, 8, 10, 11, 13, 12, 14};

    public long id, personId, otherId;
    public int kind;

    public static int inverse(int k) { return k >= 0 && k < INVERSE.length ? INVERSE[k] : KINDS.length - 1; }
    public static String kindName(int k) { return k >= 0 && k < KINDS.length ? KINDS[k] : KINDS[KINDS.length - 1]; }
}
