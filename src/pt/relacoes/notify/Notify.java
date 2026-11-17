package pt.relacoes.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

import pt.relacoes.R;
import pt.relacoes.core.Days;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Gift;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Person;
import pt.relacoes.core.Recur;
import pt.relacoes.core.Reminder;
import pt.relacoes.data.Db;
import pt.relacoes.data.Insights;
import pt.relacoes.data.Prefs;
import pt.relacoes.ui.BirthdayActivity;
import pt.relacoes.ui.LogActivity;
import pt.relacoes.ui.MainActivity;
import pt.relacoes.ui.PersonActivity;
import pt.relacoes.ui.SummaryActivity;

/** Notificações: lembretes (com ações Feito, Adiar, Já falei), sugestões do dia e resumo mensal. */
public final class Notify {
    private Notify() {}

    static final String CH_REMIND = "lembretes", CH_BDAY = "aniversarios", CH_SUGGEST = "sugestoes", CH_SUMMARY = "resumo";
    private static final int ID_SUGGEST = 1, ID_MONTHLY = 2;

    public static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_BDAY, "Aniversários", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_REMIND, "Lembretes", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_SUGGEST, "Sugestões do dia", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CH_SUMMARY, "Resumo mensal", NotificationManager.IMPORTANCE_LOW));
    }

    private static int nid(long reminderId) { return (int) (1000 + reminderId); }

    public static void cancel(Context c, long reminderId) {
        c.getSystemService(NotificationManager.class).cancel(nid(reminderId));
    }

    private static PendingIntent activity(Context c, int code, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent broadcast(Context c, String action, long id) {
        Intent i = new Intent(c, Receiver.class).setAction(action).putExtra(Receiver.EXTRA_ID, id);
        return PendingIntent.getBroadcast(c, (int) (id * 4 + (Receiver.ACTION_DONE.equals(action) ? 1 : 2)), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void reminder(Context c, Reminder r) {
        channels(c);
        Person p = Db.get(c).person(r.personId);
        if (p == null) return;
        long today = Days.of(System.currentTimeMillis());
        String title, body;
        StringBuilder big = new StringBuilder();
        Intent open;
        if (r.kind == Reminder.BIRTHDAY) {
            long bday = Days.of(r.dueAt) + r.offsetDays; // o aniversário a que este aviso se refere
            long d = bday - today;
            int age = Insights.age(p, bday);
            title = "🎂 " + (!r.isManaged() ? p.firstName() + ": " + r.text
                    : d == 0 ? "Hoje é o aniversário de " + p.name : "Aniversário de " + p.name + " " + Fmt.inDays(d));
            body = (age > 0 ? (d < 0 ? "Fez " : "Faz ") + age + " anos" : "Aniversário") + " · " + Fmt.birthday(p);
            big.append(body);
            List<String> ideas = new ArrayList<>();
            for (Gift g : Db.get(c).gifts(p.id)) if (!g.done) ideas.add(g.text);
            if (!ideas.isEmpty()) big.append("\nIdeias de prenda: ").append(String.join(", ", ideas));
            if (!p.likes.isEmpty()) big.append("\nGosta de: ").append(p.likes);
            open = d <= 0 ? new Intent(c, BirthdayActivity.class) : new Intent(c, PersonActivity.class);
        } else if (r.kind == Reminder.KEEP) {
            title = "Está na hora de falar com " + p.name;
            body = p.lastAt > 0 ? "Último contacto " + Fmt.ago(today - Days.of(p.lastAt))
                    + (p.lastMedium >= 0 ? " (" + Interaction.mediumName(p.lastMedium) + ")" : "") : "Ainda sem contactos registados";
            big.append(body);
            if (!p.notes.isEmpty()) big.append("\nTemas: ").append(p.notes);
            open = new Intent(c, PersonActivity.class);
        } else {
            title = p.firstName() + ": " + r.text;
            body = Fmt.when(r.dueAt, System.currentTimeMillis()) + (r.kind == Reminder.REPEAT ? " · " + r.repeatText() : "");
            big.append(body);
            open = new Intent(c, PersonActivity.class);
        }
        open.putExtra(PersonActivity.EXTRA_PERSON, p.id).putExtra(PersonActivity.EXTRA_REMINDER, r.id);
        Intent talked = new Intent(c, LogActivity.class).putExtra(PersonActivity.EXTRA_PERSON, p.id)
                .putExtra(PersonActivity.EXTRA_REMINDER, r.id);
        int snooze = Prefs.snooze(c);
        String snoozeLabel = snooze < 60 ? "Adiar " + snooze + " min" : snooze % 60 == 0 && snooze < 24 * 60 ? "Adiar " + (snooze / 60) + " h" : "Adiar";
        Notification.Builder b = new Notification.Builder(c, r.kind == Reminder.BIRTHDAY ? CH_BDAY : CH_REMIND)
                .setSmallIcon(r.kind == Reminder.BIRTHDAY ? R.drawable.ic_cake : R.drawable.ic_notif)
                .setContentTitle(title).setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(big.toString()))
                .setColor(c.getColor(R.color.accent))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(activity(c, nid(r.id), open))
                .setOnlyAlertOnce(false)
                .addAction(new Notification.Action.Builder(null, "Feito", broadcast(c, Receiver.ACTION_DONE, r.id)).build())
                .addAction(new Notification.Action.Builder(null, snoozeLabel, broadcast(c, Receiver.ACTION_SNOOZE, r.id)).build())
                .addAction(new Notification.Action.Builder(null, "Já falei", activity(c, 500000 + nid(r.id), talked)).build());
        c.getSystemService(NotificationManager.class).notify(nid(r.id), b.build());
    }

    public static void suggestions(Context c) {
        channels(c);
        List<Person> s = Insights.suggestions(c, Prefs.suggestCount(c));
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (s.isEmpty()) { nm.cancel(ID_SUGGEST); return; }
        List<String> names = new ArrayList<>();
        for (Person p : s) names.add(p.firstName());
        String list = names.size() == 1 ? names.get(0)
                : String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.get(names.size() - 1);
        int[] g = Insights.goal(c);
        String body = g[1] > 0 ? "Meta da semana: " + g[0] + " de " + g[1] + " pessoas" : "Um contacto rápido faz a diferença.";
        Notification n = new Notification.Builder(c, CH_SUGGEST).setSmallIcon(R.drawable.ic_people)
                .setContentTitle("Hoje, fale com " + list).setContentText(body)
                .setColor(c.getColor(R.color.accent)).setAutoCancel(true)
                .setContentIntent(activity(c, 10, new Intent(c, MainActivity.class))).build();
        nm.notify(ID_SUGGEST, n);
    }

    public static void monthly(Context c) {
        channels(c);
        int[] ym = Insights.previousMonth();
        Insights.Month m = Insights.month(c, ym[0], ym[1]);
        String title = "Resumo de " + Fmt.MONTHS[ym[1] - 1];
        String body = "Falou com " + m.contacted.size() + (m.contacted.size() == 1 ? " pessoa" : " pessoas")
                + " · " + m.late.size() + " em atraso · " + m.nextMonthBirthdays.size() + " aniversários este mês";
        Intent open = new Intent(c, SummaryActivity.class).putExtra("y", ym[0]).putExtra("m", ym[1]);
        Notification n = new Notification.Builder(c, CH_SUMMARY).setSmallIcon(R.drawable.ic_chart)
                .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setColor(c.getColor(R.color.accent)).setAutoCancel(true)
                .setContentIntent(activity(c, 11, open)).build();
        c.getSystemService(NotificationManager.class).notify(ID_MONTHLY, n);
    }
}
