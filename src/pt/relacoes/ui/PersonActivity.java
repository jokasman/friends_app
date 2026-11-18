package pt.relacoes.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pt.relacoes.R;
import pt.relacoes.core.Days;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Gift;
import pt.relacoes.core.Group;
import pt.relacoes.core.Health;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Person;
import pt.relacoes.core.Recur;
import pt.relacoes.core.Relation;
import pt.relacoes.core.Reminder;
import pt.relacoes.data.Db;
import pt.relacoes.data.Insights;
import pt.relacoes.data.Photos;
import pt.relacoes.notify.Engine;

/** Ficha da pessoa: dados, notas, grupos, lembretes, prendas, ligações e histórico de contactos e mensagens. */
public class PersonActivity extends BaseActivity {
    public static final String EXTRA_PERSON = "person", EXTRA_REMINDER = "reminder";
    private long id, reminderId;
    private LinearLayout content;
    private TextView title;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        id = getIntent().getLongExtra(EXTRA_PERSON, 0);
        reminderId = getIntent().getLongExtra(EXTRA_REMINDER, 0);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = Views.topBar(this, "", true);
        title = bar.findViewWithTag("title");
        bar.addView(Ui.iconButton(this, R.drawable.ic_edit, "Editar",
                v -> startActivity(new Intent(this, EditPersonActivity.class).putExtra(EXTRA_PERSON, id))));
        bar.addView(Ui.iconButton(this, R.drawable.ic_delete, "Apagar", v -> confirmDelete()));
        col.addView(bar);
        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(this, 24));
        sv.addView(content);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(col);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        Db db = Db.get(this);
        Person p = db.person(id);
        if (p == null) { finish(); return; }
        title.setText(p.name);
        content.removeAllViews();
        long now = System.currentTimeMillis();
        long today = Days.of(now);
        int health = Insights.health(p, today);

        // Cabeçalho.
        LinearLayout head = Ui.card(this);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.addView(Views.avatar(this, p, 88, health));
        TextView name = Ui.text(this, p.name, 22, R.color.text, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        head.addView(name);
        List<Long> gids = db.groupsOf(p.id);
        if (!gids.isEmpty()) {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout gl = new LinearLayout(this);
            for (Group g : db.groups()) if (gids.contains(g.id)) gl.addView(Ui.chip(this, g.name, false, g.color));
            hs.addView(gl);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            glp.topMargin = Ui.dp(this, 6);
            head.addView(hs, glp);
        }
        String last = p.lastAt > 0 ? "Último contacto " + Fmt.ago(today - Days.of(p.lastAt))
                + (p.lastMedium >= 0 ? " · " + Interaction.mediumName(p.lastMedium) : "") : "Ainda sem contactos registados";
        TextView lt = Ui.text(this, last, 14, R.color.text2, false);
        lt.setGravity(Gravity.CENTER);
        lt.setPadding(0, Ui.dp(this, 8), 0, 0);
        head.addView(lt);
        if (p.everyDays > 0) {
            long due = Days.of(Recur.keepDue(p, Days.of(p.lastAt), Days.of(p.created), 0)) - today;
            String s = Health.NAMES[health] + " · " + Fmt.everyName(p.everyDays) + " · "
                    + (due < 0 ? "atrasado " + (-due) + " dias" : due == 0 ? "falar hoje" : "próximo " + Fmt.inDays(due));
            TextView t = Views.badge(this, s, health == Health.RED ? Views.BADGE_DANGER : health == Health.YELLOW ? Views.BADGE_WARN : Views.BADGE_ACCENT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 10);
            head.addView(t, lp);
        }
        HorizontalScrollView qs = new HorizontalScrollView(this);
        qs.setHorizontalScrollBarEnabled(false);
        LinearLayout qa = Contacting.quickActions(this, p, reminderId);
        qa.setPadding(0, Ui.dp(this, 14), 0, 0);
        qs.addView(qa);
        head.addView(qs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout regs = new LinearLayout(this);
        TextView reg = Ui.button(this, "Contactei", true);
        reg.setOnClickListener(v -> startActivity(LogActivity.intent(this, p.id, reminderId, -1, "", 0)));
        TextView got = Ui.button(this, p.firstName() + " contactou", false);
        Ui.ellipsize(got, 1);
        got.setOnClickListener(v -> startActivity(LogActivity.intent(this, p.id, reminderId, -1, "", 0)
                .putExtra(LogActivity.EXTRA_BY_THEM, true)));
        regs.addView(reg, Ui.lpWeight(1));
        LinearLayout.LayoutParams glp0 = Ui.lpWeight(1);
        glp0.setMarginStart(Ui.dp(this, 8));
        regs.addView(got, glp0);
        LinearLayout.LayoutParams rlp = Ui.lpMW();
        rlp.topMargin = Ui.dp(this, 14);
        head.addView(regs, rlp);
        if (p.hasBirthday()) {
            long nb = Recur.nextBirthday(p, today);
            if (nb - today <= 1) {
                TextView bm = Ui.button(this, nb == today ? "🎂 Enviar mensagem de parabéns" : "🎂 Preparar mensagem de parabéns", false);
                bm.setOnClickListener(v -> startActivity(new Intent(this, BirthdayActivity.class).putExtra(EXTRA_PERSON, p.id)
                        .putExtra(EXTRA_REMINDER, reminderId)));
                LinearLayout.LayoutParams blp = Ui.lpMW();
                blp.topMargin = Ui.dp(this, 8);
                head.addView(bm, blp);
            }
        }
        content.addView(head);

        // Temas de conversa.
        String topics = Contacting.topics(this, p);
        if (!topics.isEmpty()) {
            LinearLayout tc = Ui.card(this);
            tc.addView(Ui.cardHeader(this, R.drawable.ic_chat, "Temas de conversa"));
            tc.addView(Ui.text(this, topics, 15, R.color.text, false));
            content.addView(tc);
        }

        // Detalhes.
        LinearLayout info = Ui.card(this);
        info.addView(Ui.cardHeader(this, R.drawable.ic_person, "Detalhes"));
        if (p.hasBirthday()) {
            long nb = Recur.nextBirthday(p, today);
            int age = Insights.age(p, nb);
            info.addView(Ui.optionRow(this, "🎂 " + Fmt.birthday(p), (nb == today ? "É hoje!" : "Faltam " + (nb - today) + " dias")
                    + (age > 0 ? " · faz " + age + " anos" : ""), null));
        }
        if (!p.nickname.trim().isEmpty()) info.addView(Ui.optionRow(this, p.nickname.trim(), "Como lhe chama (usado nas mensagens)", null));
        if (!p.phone.isEmpty()) info.addView(Ui.optionRow(this, p.phone, "Telefone", null));
        if (!p.email.isEmpty()) info.addView(Ui.optionRow(this, p.email, "Email", null));
        info.addView(Ui.optionRow(this, Fmt.everyName(p.everyDays), "Frequência de contacto", null));
        info.addView(Ui.optionRow(this, (p.tone == Person.TONE_PRO ? "Profissional" : "Pessoal") + " · mensagens "
                + (p.longMessages ? "longas" : "curtas"), "Mensagens sugeridas", null));
        content.addView(info);

        // Lembretes.
        LinearLayout rc = Ui.card(this);
        rc.addView(Ui.cardHeader(this, R.drawable.ic_notif, "Lembretes"));
        int n = 0;
        for (Reminder r : db.reminders(p.id)) {
            if (r.done) continue;
            rc.addView(RemindersTab.row(this, r, p, now, this::render));
            n++;
        }
        if (n == 0) rc.addView(Views.empty(this, "Sem lembretes."));
        TextView addR = Ui.button(this, "Novo lembrete", false);
        addR.setOnClickListener(v -> startActivity(new Intent(this, ReminderActivity.class).putExtra(EXTRA_PERSON, p.id)));
        LinearLayout.LayoutParams alp = Ui.lpMW();
        alp.topMargin = Ui.dp(this, 10);
        rc.addView(addR, alp);
        content.addView(rc);

        // Preferências e prendas.
        LinearLayout gc = Ui.card(this);
        gc.addView(Ui.cardHeader(this, R.drawable.ic_gift, "Preferências e prendas"));
        if (!p.likes.isEmpty()) gc.addView(Ui.optionRow(this, p.likes, "Gosta de", null));
        if (!p.avoid.isEmpty()) gc.addView(Ui.optionRow(this, p.avoid, "Evitar", null));
        if (!p.likes.isEmpty() || !p.avoid.isEmpty()) gc.addView(Ui.divider(this));
        gc.addView(Ui.subLabel(this, "Ideias de prenda"));
        List<Gift> gifts = db.gifts(p.id);
        if (gifts.isEmpty()) gc.addView(Views.empty(this, "Vá juntando ideias ao longo do ano."));
        for (Gift g : gifts) {
            CheckBox cb = new CheckBox(this);
            cb.setText(g.text);
            cb.setTextColor(Ui.col(this, g.done ? R.color.text2 : R.color.text));
            cb.setTextSize(15);
            cb.setChecked(g.done);
            if (g.done) cb.setPaintFlags(cb.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            cb.setOnCheckedChangeListener((v, on) -> { g.done = on; Db.get(this).save(g); render(); });
            cb.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage("Apagar \"" + g.text + "\"?")
                        .setPositiveButton("Apagar", (d, w) -> { Db.get(this).deleteGift(g.id); render(); })
                        .setNegativeButton("Cancelar", null).show();
                return true;
            });
            gc.addView(cb);
        }
        LinearLayout addG = new LinearLayout(this);
        addG.setGravity(Gravity.CENTER_VERTICAL);
        EditText gi = Views.input(this, "Nova ideia de prenda", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        ((LinearLayout.LayoutParams) gi.getLayoutParams()).bottomMargin = 0;
        addG.addView(gi, Ui.lpWeight(1));
        addG.addView(Ui.iconButton(this, R.drawable.ic_add, "Adicionar ideia", v -> {
            String s = gi.getText().toString().trim();
            if (s.isEmpty()) return;
            Gift g = new Gift();
            g.personId = p.id; g.text = s;
            Db.get(this).save(g);
            render();
        }));
        LinearLayout.LayoutParams glp2 = Ui.lpMW();
        glp2.topMargin = Ui.dp(this, 8);
        gc.addView(addG, glp2);
        if (!gifts.isEmpty()) gc.addView(Views.hint(this, "Marque as prendas já oferecidas. Toque sem largar para apagar."));
        content.addView(gc);

        // Ligações.
        LinearLayout lc = Ui.card(this);
        lc.addView(Ui.cardHeader(this, R.drawable.ic_link, "Ligações"));
        Map<Long, Person> byId = db.peopleById();
        List<Relation> rels = db.relations(p.id);
        if (rels.isEmpty()) lc.addView(Views.empty(this, "Ex.: cônjuge, filhos, pais, colegas."));
        for (Relation r : rels) {
            Person o = byId.get(r.otherId);
            if (o == null) continue;
            LinearLayout row = Views.personRow(this, o, -1, Relation.kindName(r.kind), null, 0,
                    v -> startActivity(new Intent(this, PersonActivity.class).putExtra(EXTRA_PERSON, o.id)));
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage("Remover a ligação com " + o.name + "?")
                        .setPositiveButton("Remover", (d, w) -> { Db.get(this).unrelate(p.id, o.id); render(); })
                        .setNegativeButton("Cancelar", null).show();
                return true;
            });
            lc.addView(row);
        }
        TextView addL = Ui.button(this, "Adicionar ligação", false);
        addL.setOnClickListener(v -> addRelation(p));
        LinearLayout.LayoutParams llp = Ui.lpMW();
        llp.topMargin = Ui.dp(this, 10);
        lc.addView(addL, llp);
        content.addView(lc);

        // Histórico.
        LinearLayout hc = Ui.card(this);
        hc.addView(Ui.cardHeader(this, R.drawable.ic_history, "Histórico"));
        List<Interaction> hist = db.interactions(p.id);
        if (hist.isEmpty()) hc.addView(Views.empty(this, "Ainda não há contactos registados."));
        for (Interaction i : hist) {
            String t = (i.byMe ? "↗ " : "↙ ") + Interaction.mediumName(i.medium) + (i.byMe ? " · eu" : " · " + p.firstName())
                    + " · " + Fmt.date(i.at, now) + " (" + Fmt.ago(today - Days.of(i.at)) + ")";
            String sub = i.note;
            if (!i.message.isEmpty()) sub = (sub.isEmpty() ? "" : sub + "\n") + "✉ " + i.message;
            LinearLayout row = Ui.optionRow(this, t, sub.isEmpty() ? null : sub, null);
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage("Apagar este registo?")
                        .setPositiveButton("Apagar", (d, w) -> {
                            Db.get(this).delete(i);
                            Person pp = Db.get(this).person(p.id);
                            if (pp != null) Engine.syncPerson(this, pp, false);
                            Engine.reschedule(this);
                            render();
                        })
                        .setNegativeButton("Cancelar", null).show();
                return true;
            });
            hc.addView(row);
        }
        if (!hist.isEmpty()) hc.addView(Views.hint(this, "Toque sem largar num registo para o apagar."));
        content.addView(hc);
    }

    private void addRelation(Person p) {
        List<Person> others = new ArrayList<>();
        for (Person o : Db.get(this).people()) if (o.id != p.id) others.add(o);
        if (others.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("Adicione primeiro outras pessoas para as poder ligar.").setPositiveButton("OK", null).show();
            return;
        }
        String[] names = new String[others.size()];
        for (int i = 0; i < names.length; i++) names[i] = others.get(i).name;
        new AlertDialog.Builder(this).setTitle("Ligar " + p.firstName() + " a…").setItems(names, (d, w) -> {
            Person o = others.get(w);
            String[] kinds = new String[Relation.KINDS.length];
            for (int i = 0; i < kinds.length; i++) kinds[i] = o.firstName() + " é " + Relation.KINDS[i].toLowerCase() + " de " + p.firstName();
            new AlertDialog.Builder(this).setTitle("Que relação?").setItems(kinds, (d2, k) -> {
                Db.get(this).relate(p.id, o.id, k);
                render();
            }).show();
        }).show();
    }

    private void confirmDelete() {
        Person p = Db.get(this).person(id);
        if (p == null) return;
        new AlertDialog.Builder(this).setTitle("Apagar " + p.name + "?")
                .setMessage("A pessoa, os lembretes, as prendas e todo o histórico serão apagados.")
                .setPositiveButton("Apagar", (d, w) -> {
                    for (Reminder r : Db.get(this).reminders(p.id)) { pt.relacoes.notify.Notify.cancel(this, r.id); pt.relacoes.notify.AlarmService.stop(r.id); }
                    Photos.delete(this, p.photo);
                    Db.get(this).deletePerson(p.id);
                    Engine.reschedule(this);
                    finish();
                })
                .setNegativeButton("Cancelar", null).show();
    }
}
