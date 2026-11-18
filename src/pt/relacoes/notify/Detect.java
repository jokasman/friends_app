package pt.relacoes.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import java.util.List;

import pt.relacoes.R;
import pt.relacoes.core.Interaction;
import pt.relacoes.core.Names;
import pt.relacoes.core.Person;
import pt.relacoes.data.Db;
import pt.relacoes.data.Prefs;
import pt.relacoes.ui.MainActivity;

/** Contactos detetados pelas notificações de outras apps (mensagens e chamadas recebidas). */
public final class Detect {
    private Detect() {}

    private static final String CH = "detetados";
    private static final int NOTIF = 3;
    /** Não volta a detetar a mesma pessoa pelo mesmo meio dentro deste intervalo. */
    private static final long WINDOW = 6 * 3_600_000L;

    private static Names.Index index;
    private static int indexStamp = -1;

    /** A app tem acesso às notificações? */
    public static boolean enabled(Context c) {
        ComponentName cn = new ComponentName(c, ContactListener.class);
        if (Build.VERSION.SDK_INT >= 27) {
            return c.getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(cn);
        }
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(cn.flattenToString());
    }

    public static Intent settingsIntent() {
        return new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
    }

    /** Meio de contacto de uma app, ou -1 se não for uma app de comunicação. */
    static int medium(String pkg, String category) {
        if (pkg == null) return -1;
        if (pkg.startsWith("com.whatsapp")) return Interaction.WHATSAPP;
        if (pkg.equals("com.facebook.orca") || pkg.equals("com.facebook.mlite")) return Interaction.MESSENGER;
        if (pkg.equals("com.instagram.android")) return Interaction.INSTAGRAM;
        if (pkg.equals("com.facebook.katana") || pkg.equals("com.facebook.lite")) return Interaction.FACEBOOK;
        if (pkg.contains("messaging") || pkg.contains(".mms") || pkg.endsWith(".sms") || pkg.contains("android.messages")) return Interaction.SMS;
        if (pkg.contains("dialer") || pkg.contains("incallui") || pkg.equals("com.android.phone") || pkg.equals("com.android.server.telecom")
                || Notification.CATEGORY_CALL.equals(category) || Notification.CATEGORY_MISSED_CALL.equals(category)) return Interaction.CALL;
        if (pkg.equals("com.google.android.gm") || pkg.contains("outlook") || pkg.contains(".email")) return Interaction.EMAIL;
        if (pkg.equals("org.telegram.messenger") || pkg.equals("org.thoughtcrime.securesms") || pkg.equals("com.viber.voip")
                || pkg.equals("com.skype.raider") || pkg.equals("com.microsoft.teams")) return Interaction.OTHER;
        if (Notification.CATEGORY_MESSAGE.equals(category)) return Interaction.OTHER;
        return -1;
    }

    static String appName(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private static synchronized Names.Index index(Context c) {
        if (index == null || indexStamp != Db.stamp) {
            indexStamp = Db.stamp;
            index = new Names.Index(Db.get(c).people());
        }
        return index;
    }

    /** Chamado pelo serviço com os textos de uma notificação (remetentes possíveis). */
    static void handle(Context c, String pkg, int medium, List<String> senders, long when) {
        String app = appName(c, pkg);
        String who = senders.isEmpty() ? "" : senders.get(0);
        Names.Index ix = index(c);
        long person = 0;
        for (String s : senders) {
            person = ix.match(s);
            if (person != 0) break;
        }
        if (person == 0) { log(c, when, app, who, "Sem correspondência com ninguém do círculo"); return; }
        Db db = Db.get(c);
        long since = when - WINDOW;
        Person p = db.person(person);
        if (p == null) return;
        if (db.detectedSince(person, medium, since)) { log(c, when, app, who, "Já detetado nas últimas 6 h (" + p.name + ")"); return; }
        // Já registou um contacto com esta pessoa há pouco: não duplica.
        if (p.lastAt >= since) { log(c, when, app, who, "Contacto com " + p.name + " registado há menos de 6 h"); return; }
        if (Prefs.detectMode(c) == 1) {
            db.addDetection(person, when, medium, app, Db.DET_ACCEPTED);
            Engine.contacted(c, interaction(person, when, medium, app), 0, 0);
            log(c, when, app, who, "Registado: " + p.name);
        } else {
            db.addDetection(person, when, medium, app, Db.DET_PENDING);
            notifyPending(c);
            Engine.reschedule(c);
            log(c, when, app, who, "Por confirmar em Hoje: " + p.name);
        }
    }

    // ------------------------------------------------------------------ Diagnóstico

    private static final int LOG_MAX = 15;

    /** Guarda as últimas notificações vistas (app, remetente e resultado; nunca o conteúdo). */
    static synchronized void log(Context c, long when, String app, String who, String result) {
        try {
            org.json.JSONArray old = new org.json.JSONArray(Prefs.str(c, "detect_log") == null ? "[]" : Prefs.str(c, "detect_log"));
            org.json.JSONArray out = new org.json.JSONArray();
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("t", when); o.put("app", app); o.put("who", who); o.put("res", result);
            out.put(o);
            for (int i = 0; i < old.length() && out.length() < LOG_MAX; i++) {
                org.json.JSONObject e = old.getJSONObject(i);
                // Não repete a mesma app sem remetente seguida (apps não vigiadas).
                if (who.isEmpty() && e.optString("who").isEmpty() && e.optString("app").equals(app) && i == 0) continue;
                out.put(e);
            }
            Prefs.setStr(c, "detect_log", out.toString());
        } catch (org.json.JSONException ignored) { }
    }

    /** Últimas entradas: {hora, app, remetente, resultado}. */
    public static List<String[]> recent(Context c) {
        List<String[]> out = new java.util.ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(Prefs.str(c, "detect_log") == null ? "[]" : Prefs.str(c, "detect_log"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                out.add(new String[]{pt.relacoes.core.Fmt.when(o.optLong("t"), System.currentTimeMillis()), o.optString("app"),
                        o.optString("who"), o.optString("res")});
            }
        } catch (org.json.JSONException ignored) { }
        return out;
    }

    /** Hora em que o Android ligou o serviço de leitura (0 = nunca). */
    public static long connectedAt(Context c) {
        String s = Prefs.str(c, "detect_connected");
        try { return s == null ? 0 : Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    /** Pede ao Android para voltar a ligar o serviço (útil depois de atualizar a app). */
    public static void rebind(Context c) {
        if (!enabled(c)) return;
        try {
            android.service.notification.NotificationListenerService.requestRebind(new ComponentName(c, ContactListener.class));
        } catch (RuntimeException ignored) { }
    }

    static Interaction interaction(long person, long at, int medium, String app) {
        Interaction i = new Interaction();
        i.personId = person;
        i.at = at;
        i.medium = medium;
        i.byMe = false;
        i.note = "Detetado pela notificação" + (app.isEmpty() ? "" : " (" + app + ")");
        return i;
    }

    /** Confirma: regista o contacto (recebido) e fecha os follow-ups da pessoa. */
    public static void accept(Context c, long id) {
        Db db = Db.get(c);
        Db.Detection d = db.detection(id);
        if (d == null || d.status != Db.DET_PENDING) return;
        db.setDetectionStatus(id, Db.DET_ACCEPTED);
        Engine.contacted(c, interaction(d.personId, d.at, d.medium, d.app), 0, 0);
        notifyPending(c);
    }

    public static void dismiss(Context c, long id) {
        Db.get(c).setDetectionStatus(id, Db.DET_DISMISSED);
        notifyPending(c);
    }

    /** Notificação discreta com os contactos por confirmar. */
    static void notifyPending(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        List<Db.Detection> ds = Db.get(c).pendingDetections();
        if (ds.isEmpty()) { nm.cancel(NOTIF); return; }
        NotificationChannel ch = new NotificationChannel(CH, "Contactos detetados", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Mensagens e chamadas de pessoas do seu círculo, para registar como contacto");
        nm.createNotificationChannel(ch);
        Db.Detection d = ds.get(0);
        Person p = Db.get(c).person(d.personId);
        String title = ds.size() == 1 && p != null ? p.firstName() + " contactou-o por " + Interaction.mediumName(d.medium)
                : ds.size() + " contactos detetados";
        PendingIntent open = PendingIntent.getActivity(c, 12, new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CH).setSmallIcon(R.drawable.ic_people)
                .setContentTitle(title).setContentText("Toque para confirmar e registar no histórico")
                .setColor(c.getColor(R.color.accent)).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build();
        nm.notify(NOTIF, n);
    }
}
