package pt.relacoes.ui;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import pt.relacoes.R;
import pt.relacoes.core.Days;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Gift;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Person;
import pt.relacoes.core.Relation;
import pt.relacoes.core.Template;
import pt.relacoes.data.Db;
import pt.relacoes.data.Insights;

/** Ações rápidas de contacto com "temas de conversa" e mensagem sugerida. */
final class Contacting {
    private Contacting() {}

    static String digits(String phone) { return phone == null ? "" : phone.replaceAll("[^0-9+]", ""); }

    /** Número no formato internacional sem "+" (para wa.me); números portugueses de 9 dígitos ganham 351. */
    static String waNumber(String phone) {
        String t = digits(phone);
        if (t.startsWith("+")) return t.substring(1);
        if (t.startsWith("00")) return t.substring(2);
        return t.length() == 9 ? "351" + t : t;
    }

    /** Texto com os temas de conversa: notas, último contacto, pessoas ligadas, gostos e prendas. */
    static String topics(BaseActivity a, Person p) {
        StringBuilder sb = new StringBuilder();
        long today = Insights.today();
        if (!p.notes.trim().isEmpty()) sb.append("📝 ").append(p.notes.trim()).append("\n\n");
        List<Interaction> hist = Db.get(a).interactions(p.id);
        if (!hist.isEmpty()) {
            Interaction last = hist.get(0);
            sb.append("Último contacto: ").append(Fmt.ago(today - Days.of(last.at))).append(" · ").append(Interaction.mediumName(last.medium));
            if (!last.note.isEmpty()) sb.append("\n“").append(last.note).append("”");
            sb.append("\n\n");
        }
        List<Relation> rel = Db.get(a).relations(p.id);
        if (!rel.isEmpty()) {
            Map<Long, Person> byId = Db.get(a).peopleById();
            List<String> ask = new ArrayList<>();
            for (Relation r : rel) {
                Person o = byId.get(r.otherId);
                if (o != null) ask.add(o.firstName() + " (" + Relation.kindName(r.kind).toLowerCase() + ")");
            }
            if (!ask.isEmpty()) sb.append("Perguntar por: ").append(Views.joinNames(ask)).append("\n\n");
        }
        if (!p.likes.trim().isEmpty()) sb.append("Gosta de: ").append(p.likes.trim()).append("\n");
        if (!p.avoid.trim().isEmpty()) sb.append("Evitar: ").append(p.avoid.trim()).append("\n");
        return sb.toString().trim();
    }

    /** Modelos sugeridos para a pessoa (tipo e tamanho preferidos), sem os já enviados, por ordem aleatória. */
    static List<Template> suggestions(BaseActivity a, Person p, int type, int age) {
        List<Long> used = new ArrayList<>();
        for (Interaction i : Db.get(a).interactions(p.id)) if (i.templateId > 0) used.add(i.templateId);
        List<Template> pref = new ArrayList<>(), other = new ArrayList<>(), usedList = new ArrayList<>();
        int size = p.longMessages ? Template.LONG : Template.SHORT;
        for (Template t : Db.get(a).templates()) {
            if (t.type != type) continue;
            if (age <= 0 && t.text.contains("{idade}")) continue;
            if (used.contains(t.id)) usedList.add(t);
            else if (t.size == size) pref.add(t);
            else other.add(t);
        }
        Collections.shuffle(pref);
        Collections.shuffle(other);
        List<Template> out = new ArrayList<>(pref);
        out.addAll(other);
        out.addAll(usedList);
        return out;
    }

    /** Mostra os temas de conversa e, para mensagens, uma mensagem sugerida editável; depois abre a app. */
    static void start(BaseActivity a, Person p, int medium, long reminderId) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(a, 22), Ui.dp(a, 8), Ui.dp(a, 22), 0);
        String topics = topics(a, p);
        if (!topics.isEmpty()) {
            box.addView(Ui.subLabel(a, "Temas de conversa"));
            TextView t = Ui.text(a, topics, 14, R.color.text, false);
            t.setBackground(Ui.round(Ui.col(a, R.color.chip), Ui.dp(a, 12)));
            t.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
            box.addView(t, Ui.lpMW());
            box.addView(Ui.spacer(a, 12));
        }
        boolean msg = medium == Interaction.WHATSAPP || medium == Interaction.SMS || medium == Interaction.EMAIL || medium == Interaction.OTHER;
        final EditText text = Views.multiline(a, "Mensagem", "");
        final long[] tplId = {0};
        if (msg) {
            int type = p.tone == Person.TONE_PRO ? Template.PRO : Template.MISS;
            List<Template> sug = suggestions(a, p, type, 0);
            final int[] idx = {0};
            Runnable next = () -> {
                if (sug.isEmpty()) return;
                Template t = sug.get(idx[0] % sug.size());
                idx[0]++;
                tplId[0] = t.id;
                text.setText(Template.fill(t.text, p, 0));
            };
            next.run();
            box.addView(Ui.subLabel(a, "Mensagem sugerida"));
            box.addView(text);
            if (sug.size() > 1) {
                TextView other = Ui.button(a, "Outra sugestão", false);
                other.setOnClickListener(v -> next.run());
                box.addView(other, Ui.lpMW());
            }
        }
        if (box.getChildCount() == 0) box.addView(Views.empty(a, "Ainda não há notas nem contactos com " + p.firstName() + "."));
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        String go;
        switch (medium) {
            case Interaction.CALL: go = "Ligar"; break;
            case Interaction.WHATSAPP: go = "Abrir WhatsApp"; break;
            case Interaction.SMS: go = "Abrir SMS"; break;
            case Interaction.EMAIL: go = "Abrir email"; break;
            default: go = "Partilhar";
        }
        new AlertDialog.Builder(a).setTitle(p.name).setView(sv)
                .setPositiveButton(go, (d, w) -> {
                    String m = msg ? text.getText().toString().trim() : "";
                    if (open(a, p, medium, m)) a.expectContact(p.id, medium, m, msg ? tplId[0] : 0, reminderId);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    /** Abre a app correspondente. Devolve false se não houver app para a ação. */
    static boolean open(BaseActivity a, Person p, int medium, String message) {
        String tel = digits(p.phone);
        Intent i;
        try {
            switch (medium) {
                case Interaction.CALL:
                    i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(tel)));
                    break;
                case Interaction.SMS:
                    i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(tel))).putExtra("sms_body", message);
                    break;
                case Interaction.WHATSAPP:
                    if (!tel.isEmpty()) {
                        i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + waNumber(p.phone)
                                + (message.isEmpty() ? "" : "?text=" + URLEncoder.encode(message, "UTF-8").replace("+", "%20"))));
                    } else {
                        i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message).setPackage("com.whatsapp");
                    }
                    break;
                case Interaction.EMAIL:
                    i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(p.email.trim())))
                            .putExtra(Intent.EXTRA_TEXT, message);
                    break;
                default:
                    i = Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message), "Enviar mensagem");
            }
            a.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            if (medium == Interaction.WHATSAPP) {
                Toast.makeText(a, "WhatsApp não encontrado. A abrir o menu de partilha.", Toast.LENGTH_SHORT).show();
                return open(a, p, Interaction.OTHER, message);
            }
            Toast.makeText(a, "Nenhuma app disponível para esta ação.", Toast.LENGTH_SHORT).show();
            return false;
        } catch (java.io.UnsupportedEncodingException e) {
            return false;
        }
    }

    /** Botões de ação rápida para uma pessoa (só os disponíveis). */
    static LinearLayout quickActions(BaseActivity a, Person p, long reminderId) {
        LinearLayout acts = new LinearLayout(a);
        String tel = digits(p.phone);
        if (!tel.isEmpty()) {
            acts.addView(action(a, R.drawable.ic_phone, "Ligar", v -> start(a, p, Interaction.CALL, reminderId)));
            acts.addView(action(a, R.drawable.ic_chat, "WhatsApp", v -> start(a, p, Interaction.WHATSAPP, reminderId)));
            acts.addView(action(a, R.drawable.ic_send, "SMS", v -> start(a, p, Interaction.SMS, reminderId)));
        }
        if (!p.email.trim().isEmpty()) acts.addView(action(a, R.drawable.ic_mail, "Email", v -> start(a, p, Interaction.EMAIL, reminderId)));
        acts.addView(action(a, R.drawable.ic_share, "Partilhar", v -> start(a, p, Interaction.OTHER, reminderId)));
        return acts;
    }

    static View action(BaseActivity a, int icon, String label, View.OnClickListener l) {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        b.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4));
        b.setBackground(Ui.ripple(a, null, Ui.dp(a, 14)));
        android.widget.ImageView ic = Ui.icon(a, icon, R.color.accent, 46, 11);
        ic.setBackground(Ui.round(Ui.col(a, R.color.accentSoft), Ui.dp(a, 23)));
        b.addView(ic);
        TextView t = Ui.text(a, label, 12, R.color.text, false);
        t.setPadding(0, Ui.dp(a, 4), 0, 0);
        b.addView(t);
        b.setOnClickListener(l);
        return b;
    }

    static List<Gift> openGifts(BaseActivity a, long person) {
        List<Gift> out = new ArrayList<>();
        for (Gift g : Db.get(a).gifts(person)) if (!g.done) out.add(g);
        return out;
    }
}
