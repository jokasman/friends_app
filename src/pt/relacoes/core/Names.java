package pt.relacoes.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Correspondência entre o remetente de uma notificação (nome ou número) e as pessoas da lista.
 * Exige o nome completo (ou um nome de uma só palavra igual) ou os últimos 9 dígitos do telefone,
 * para evitar falsos positivos.
 */
public final class Names {
    private Names() {}

    /** Minúsculas, sem acentos nem símbolos, espaços simples. */
    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        n = n.replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").trim();
        return n;
    }

    /** Chave de telefone: últimos 9 dígitos (ou vazio se tiver menos de 6 dígitos). */
    public static String phoneKey(String s) {
        if (s == null) return "";
        String d = s.replaceAll("[^0-9]", "");
        if (d.length() < 6) return "";
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    /**
     * Possíveis remetentes num título de notificação: retira contadores como "(3 mensagens)" e separa
     * "Grupo: Pessoa" / "Pessoa @ Grupo".
     */
    public static List<String> candidates(String title) {
        List<String> out = new ArrayList<>();
        if (title == null) return out;
        String t = title.replaceAll("\\(\\s*\\d+\\s*[^)]*\\)", " ").replace('‪', ' ').replace('‬', ' ').trim();
        if (t.isEmpty()) return out;
        out.add(t);
        for (String sep : new String[]{":", " @ ", " – ", " - "}) {
            int i = t.indexOf(sep);
            if (i > 0) {
                String a = t.substring(0, i).trim(), b = t.substring(i + sep.length()).trim();
                if (!a.isEmpty()) out.add(a);
                if (!b.isEmpty()) out.add(b);
            }
        }
        return out;
    }

    /** Índice das pessoas: nome completo, nome único de uma palavra e telefone. */
    public static final class Index {
        private final Map<String, Long> byName = new HashMap<>();
        private final Map<String, Long> byPhone = new HashMap<>();

        public Index(List<Person> people) {
            Map<String, Integer> count = new HashMap<>();
            for (Person p : people) {
                String f = fold(p.name);
                if (f.isEmpty()) continue;
                count.put(f, count.containsKey(f) ? count.get(f) + 1 : 1);
            }
            for (Person p : people) {
                String f = fold(p.name);
                if (!f.isEmpty() && count.get(f) == 1) byName.put(f, p.id);
                String k = phoneKey(p.phone);
                if (!k.isEmpty()) byPhone.put(k, p.id);
            }
        }

        /** Pessoa correspondente a um texto (nome ou número), ou 0. */
        public long match(String s) {
            for (String c : candidates(s)) {
                String k = phoneKey(c);
                if (!k.isEmpty() && c.replaceAll("[0-9+ ()-]", "").isEmpty() && byPhone.containsKey(k)) return byPhone.get(k);
                Long id = byName.get(fold(c));
                if (id != null) return id;
            }
            return 0;
        }
    }
}
