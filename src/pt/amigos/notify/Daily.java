package pt.amigos.notify;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import pt.amigos.R;
import pt.amigos.core.Days;
import pt.amigos.core.Friend;
import pt.amigos.core.Schedule;
import pt.amigos.data.Db;
import pt.amigos.data.Prefs;
import pt.amigos.ui.FriendActivity;
import pt.amigos.ui.MainActivity;

/** Aviso diário: aniversários próximos e amigos com quem já devia ter falado. */
public class Daily extends BroadcastReceiver {
    private static final String CH_BDAY = "aniversarios", CH_TOUCH = "contactos";
    private static final int ID_OVERDUE = 1;

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            schedule(c);
            return;
        }
        PendingResult pr = goAsync();
        new Thread(() -> {
            try { run(c); } finally { schedule(c); pr.finish(); }
        }).start();
    }

    /** Agenda o próximo aviso à hora definida nas preferências (sem alarme exato: janela de 15 min). */
    public static void schedule(Context c) {
        int t = Prefs.notifyTime(c);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, t / 60);
        cal.set(Calendar.MINUTE, t % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_MONTH, 1);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.setWindow(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), 15 * 60_000L, pending(c));
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, Daily.class).setAction("pt.amigos.DAILY");
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_BDAY, "Aniversários", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_TOUCH, "Falar com amigos", NotificationManager.IMPORTANCE_DEFAULT));
    }

    /** Mostra as notificações do dia. Também usado pelo botão "Testar aviso" das definições. */
    public static void run(Context c) {
        channels(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        List<Friend> all = Db.get(c).all();
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        long today = Days.of(now.getTimeInMillis());
        int adv = Prefs.birthdayAdvance(c);

        List<String> overdue = new ArrayList<>();
        for (Friend f : all) {
            int d = Schedule.daysToBirthday(f, year, today);
            if (d == 0 || (adv > 0 && d == adv)) {
                int age = Schedule.turning(f, year, today);
                String title = d == 0 ? "Hoje é o aniversário de " + f.name : "Aniversário de " + f.name + " " + Schedule.inDays(d);
                String body = (age > 0 ? "Faz " + age + " anos. " : "") + (d == 0 ? "Dê-lhe os parabéns!" : "Não se esqueça.");
                Intent open = new Intent(c, FriendActivity.class).putExtra(FriendActivity.EXTRA_ID, f.id);
                PendingIntent pi = PendingIntent.getActivity(c, (int) (1000 + f.id), open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification n = new Notification.Builder(c, CH_BDAY)
                        .setSmallIcon(R.drawable.ic_cake).setContentTitle(title).setContentText(body)
                        .setColor(c.getColor(R.color.accent)).setContentIntent(pi).setAutoCancel(true).build();
                nm.notify((int) (1000 + f.id), n);
            }
            if (Prefs.remindOverdue(c)) {
                long od = Schedule.overdue(f, Days.of(f.lastContact), Days.of(f.created), today);
                if (od >= 0) overdue.add(f.name);
            }
        }
        if (!overdue.isEmpty()) {
            String title = overdue.size() == 1 ? "Está na hora de falar com " + overdue.get(0)
                    : overdue.size() + " amigos à espera de notícias";
            String body = String.join(", ", overdue);
            PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CH_TOUCH)
                    .setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setColor(c.getColor(R.color.accent)).setContentIntent(pi).setAutoCancel(true).build();
            nm.notify(ID_OVERDUE, n);
        } else {
            nm.cancel(ID_OVERDUE);
        }
    }
}
