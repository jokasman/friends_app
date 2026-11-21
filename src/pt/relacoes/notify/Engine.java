package pt.relacoes.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import pt.relacoes.core.Days;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Person;
import pt.relacoes.core.Recur;
import pt.relacoes.core.Reminder;
import pt.relacoes.data.Db;
import pt.relacoes.data.Prefs;
import pt.relacoes.ui.Widget;

/** Motor dos lembretes: calcula datas, trata Feito/Adiar/Já falei e agenda os alarmes. */
public final class Engine {
    private Engine() {}

    static final String ACTION_FIRE = "pt.relacoes.FIRE", ACTION_DAILY = "pt.relacoes.DAILY";

    // ------------------------------------------------------------------ Lembretes automáticos

    /** Mantém os lembretes automáticos de uma pessoa (manter contacto e aniversário). */
    public static void syncPerson(Context c, Person p, boolean birthdayChanged) {
        Db db = Db.get(c);
        int morning = Prefs.morning(c);
        long today = Days.of(System.currentTimeMillis());
        Reminder keep = null, bDay = null, bBefore = null;
        List<Reminder> bdays = new ArrayList<>();
        for (Reminder r : db.reminders(p.id)) {
            if (r.auto == Reminder.AUTO_KEEP) keep = r;
            else if (r.auto == Reminder.AUTO_BDAY_DAY) bDay = r;
            else if (r.auto == Reminder.AUTO_BDAY_BEFORE) bBefore = r;
            if (r.kind == Reminder.BIRTHDAY) bdays.add(r);
        }

        // Manter contacto.
        if (p.everyDays > 0) {
            if (keep == null) {
                keep = new Reminder();
                keep.personId = p.id; keep.kind = Reminder.KEEP; keep.auto = Reminder.AUTO_KEEP;
                keep.nagMin = Prefs.nagDefault(c);
            }
            keep.text = "Falar com " + p.firstName();
            long due = Recur.keepDue(p, Days.of(p.lastAt), Days.of(p.created), morning);
            if (due != keep.dueAt || keep.done) {
                if (keep.id > 0 && keep.notified) Notify.cancel(c, keep.id);
                keep.dueAt = due;
                keep.done = false;
                keep.notified = false;
                keep.nextAt = due;
            }
            db.save(keep);
        } else if (keep != null) {
            db.deleteReminder(keep.id);
            Notify.cancel(c, keep.id);
        }

        // Aniversário: automáticos (no dia e N dias antes).
        if (!p.hasBirthday()) {
            for (Reminder r : bdays) { db.deleteReminder(r.id); Notify.cancel(c, r.id); }
            return;
        }
        bDay = ensureBirthday(c, p, bDay, p.bdayOnDay, 0, Reminder.AUTO_BDAY_DAY, "Aniversário de " + p.firstName(), birthdayChanged, today, morning);
        bBefore = ensureBirthday(c, p, bBefore, p.bdayBefore > 0, p.bdayBefore, Reminder.AUTO_BDAY_BEFORE,
                "Aniversário de " + p.firstName() + " daqui a " + p.bdayBefore + (p.bdayBefore == 1 ? " dia" : " dias"), birthdayChanged, today, morning);
        if (birthdayChanged) {
            for (Reminder r : bdays) {
                if (r.isManaged()) continue;
                r.dueAt = Recur.birthdayDue(p, r.offsetDays, today, Recur.minutesOf(r.dueAt, morning));
                r.notified = false; r.done = false; r.nextAt = r.dueAt;
                db.save(r);
            }
        }
    }

    private static Reminder ensureBirthday(Context c, Person p, Reminder r, boolean want, int offset, int auto, String text,
                                           boolean changed, long today, int morning) {
        Db db = Db.get(c);
        if (!want) {
            if (r != null) { db.deleteReminder(r.id); Notify.cancel(c, r.id); }
            return null;
        }
        boolean fresh = r == null;
        if (fresh) {
            r = new Reminder();
            r.personId = p.id; r.kind = Reminder.BIRTHDAY; r.auto = auto;
            r.nagMin = Prefs.nagDefault(c);
        }
        r.text = text;
        if (fresh || changed || r.offsetDays != offset) {
            r.offsetDays = offset;
            r.dueAt = Recur.birthdayDue(p, offset, today, morning);
            r.done = false; r.notified = false; r.nextAt = r.dueAt;
        }
        db.save(r);
        return r;
    }

    // ------------------------------------------------------------------ Ações

    /** Regista um contacto e recalcula o "manter contacto" a partir dessa data. */
    public static void contacted(Context c, Interaction i, long reminderId) {
        contacted(c, i, reminderId, 0);
    }

    /**
     * Regista um contacto. Fecha os follow-ups pendentes da pessoa (houve um novo contacto) e, se followUpDays > 0,
     * cria um follow-up para daqui a N dias caso não haja resposta.
     */
    public static void contacted(Context c, Interaction i, long reminderId, int followUpDays) {
        Db db = Db.get(c);
        db.add(i);
        db.closeDetections(i.personId);
        for (Reminder f : db.reminders(i.personId)) {
            if (f.auto != Reminder.AUTO_FOLLOWUP || f.done || f.id == reminderId) continue;
            f.done = true; f.doneAt = System.currentTimeMillis(); f.nextAt = Long.MAX_VALUE;
            db.save(f);
            Notify.cancel(c, f.id);
        }
        if (followUpDays > 0) {
            Reminder f = new Reminder();
            f.personId = i.personId; f.kind = Reminder.ONCE; f.auto = Reminder.AUTO_FOLLOWUP;
            f.text = "Sem resposta " + (i.message.isEmpty() ? "ao contacto" : "à mensagem") + " de "
                    + pt.relacoes.core.Fmt.date(i.at, System.currentTimeMillis()) + " (" + Interaction.mediumName(i.medium) + ")? Fazer follow-up";
            f.dueAt = Recur.at(Days.of(i.at) + followUpDays, Prefs.morning(c));
            if (f.dueAt <= System.currentTimeMillis()) f.dueAt = System.currentTimeMillis() + 60_000L;
            f.nagMin = Prefs.nagDefault(c);
            f.nextAt = f.dueAt;
            db.save(f);
        }
        Person p = db.person(i.personId);
        if (p != null) syncPerson(c, p, false);
        if (reminderId > 0) {
            Reminder r = db.reminder(reminderId);
            if (r != null && r.kind != Reminder.KEEP) complete(c, r);
            Notify.cancel(c, reminderId);
        }
        reschedule(c);
    }

    /** Marca como feito (os recorrentes e de aniversário passam para a próxima data). */
    public static void done(Context c, long id) {
        Reminder r = Db.get(c).reminder(id);
        if (r == null) return;
        if (r.kind == Reminder.KEEP) {
            Interaction i = new Interaction();
            i.personId = r.personId; i.at = System.currentTimeMillis(); i.medium = Interaction.OTHER;
            i.note = "Marcado como feito no lembrete";
            contacted(c, i, 0);
        } else {
            complete(c, r);
        }
        Notify.cancel(c, id);
        reschedule(c);
    }

    private static void complete(Context c, Reminder r) {
        long now = System.currentTimeMillis();
        Db db = Db.get(c);
        switch (r.kind) {
            case Reminder.REPEAT:
                r.dueAt = Recur.nextRepeat(r, now);
                r.notified = false; r.nextAt = r.dueAt; r.doneAt = now;
                break;
            case Reminder.BIRTHDAY: {
                Person p = db.person(r.personId);
                if (p != null && p.hasBirthday()) {
                    long from = Math.max(Days.of(r.dueAt), Days.of(now)) + 1;
                    r.dueAt = Recur.birthdayDue(p, r.offsetDays, from, Recur.minutesOf(r.dueAt, Prefs.morning(c)));
                    r.notified = false; r.nextAt = r.dueAt; r.doneAt = now;
                } else {
                    r.done = true; r.doneAt = now; r.nextAt = Long.MAX_VALUE;
                }
                break;
            }
            default:
                r.done = true; r.doneAt = now; r.nextAt = Long.MAX_VALUE;
        }
        r.notified = false;
        db.save(r);
    }

    public static void snooze(Context c, long id, int minutes) {
        Reminder r = Db.get(c).reminder(id);
        if (r == null) return;
        snoozeUntil(c, r, System.currentTimeMillis() + minutes * 60_000L);
    }

    /** Adia até uma data/hora: deixa de estar "por tratar" e volta a avisar nessa altura. */
    public static void snoozeUntil(Context c, Reminder r, long until) {
        r.nextAt = until;
        r.notified = false;
        Db.get(c).save(r);
        Notify.cancel(c, r.id);
        reschedule(c);
    }

    /** Volta a ativar um lembrete concluído ou alterado (nova data). */
    public static void saved(Context c, Reminder r) {
        r.done = false;
        r.notified = false;
        r.nextAt = r.dueAt;
        Db.get(c).save(r);
        Notify.cancel(c, r.id);
        reschedule(c);
    }

    /** Apaga um lembrete (e a sua notificação). */
    public static void deleted(Context c, long id) {
        Db.get(c).deleteReminder(id);
        Notify.cancel(c, id);
        reschedule(c);
    }

    // ------------------------------------------------------------------ Alarmes

    /** Notifica os lembretes vencidos e marca a próxima insistência. */
    static void fire(Context c) {
        long now = System.currentTimeMillis();
        Db db = Db.get(c);
        for (Reminder r : db.reminders()) {
            if (r.done || r.nextAt > now + 30_000L) continue;
            r.notified = true;
            // Repete a notificação de N em N minutos até ser marcado como feito (0 = não repete).
            r.nextAt = r.nagMin > 0 ? nextNag(c, now + r.nagMin * 60_000L) : Long.MAX_VALUE;
            db.save(r);
            Notify.reminder(c, r);
        }
        reschedule(c);
    }

    /** Próxima insistência, respeitando a pausa noturna (se estiver ligada). */
    static long nextNag(Context c, long t) {
        return Prefs.nightPause(c) ? Recur.outsideQuiet(t, Prefs.quietStart(c), Prefs.morning(c)) : t;
    }

    /** Tarefas da manhã: sugestões do dia e resumo mensal (dia 1). */
    static void daily(Context c) {
        Db db = Db.get(c);
        for (Person p : db.people()) if (p.everyDays > 0) syncPerson(c, p, false);
        db.purgeDone(System.currentTimeMillis() - 90L * Days.DAY_MS);
        db.purgeDetections(System.currentTimeMillis() - 30L * Days.DAY_MS);
        if (Prefs.suggestions(c)) Notify.suggestions(c);
        if (Prefs.monthly(c) && Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == 1) Notify.monthly(c);
        reschedule(c);
    }

    /** Agenda o próximo lembrete e a tarefa da manhã; atualiza o widget. */
    public static void reschedule(Context c) {
        long next = Long.MAX_VALUE;
        for (Reminder r : Db.get(c).reminders()) if (!r.done && r.nextAt < next) next = r.nextAt;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent fire = pi(c, ACTION_FIRE, 1);
        if (next == Long.MAX_VALUE) am.cancel(fire);
        else setExact(c, am, Math.max(next, System.currentTimeMillis() + 1000), fire);

        int m = Prefs.morning(c);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, m / 60);
        cal.set(Calendar.MINUTE, m % 60);
        cal.set(Calendar.SECOND, 5);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1);
        setExact(c, am, cal.getTimeInMillis(), pi(c, ACTION_DAILY, 2));
        Widget.updateAll(c);
    }

    public static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    private static void setExact(Context c, AlarmManager am, long at, PendingIntent p) {
        try {
            if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    private static PendingIntent pi(Context c, String action, int code) {
        Intent i = new Intent(c, Receiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
