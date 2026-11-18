package pt.relacoes.notify;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Lê as notificações de apps de comunicação (WhatsApp, SMS, chamadas, Messenger, Instagram…) para detetar
 * contactos recebidos de pessoas do círculo. Só usa o remetente; o conteúdo das mensagens não é guardado.
 */
public class ContactListener extends NotificationListenerService {

    @Override public void onListenerConnected() {
        pt.relacoes.data.Prefs.setStr(getApplicationContext(), "detect_connected", String.valueOf(System.currentTimeMillis()));
        pt.relacoes.data.Prefs.setStr(getApplicationContext(), "detect_disconnected", "");
    }

    @Override public void onListenerDisconnected() {
        pt.relacoes.data.Prefs.setStr(getApplicationContext(), "detect_disconnected", String.valueOf(System.currentTimeMillis()));
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            int medium = Detect.medium(sbn.getPackageName(), n.category);
            if (medium < 0) {
                // Só para o diagnóstico: a app, sem título nem texto.
                Detect.log(getApplicationContext(), System.currentTimeMillis(), Detect.appName(getApplicationContext(), sbn.getPackageName()),
                        "", "App não vigiada");
                return;
            }
            Bundle e = n.extras;
            if (e == null) return;
            List<String> senders = new ArrayList<>();
            // Remetentes das mensagens (MessagingStyle), do mais recente para o mais antigo.
            Parcelable[] msgs = e.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (msgs != null) {
                for (int i = msgs.length - 1; i >= 0; i--) {
                    if (!(msgs[i] instanceof Bundle)) continue;
                    Bundle m = (Bundle) msgs[i];
                    CharSequence s = m.getCharSequence("sender");
                    if (s == null && android.os.Build.VERSION.SDK_INT >= 28) {
                        Object person = m.get("sender_person");
                        if (person instanceof android.app.Person) s = ((android.app.Person) person).getName();
                    }
                    if (s != null) senders.add(s.toString());
                }
            }
            add(senders, e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
            add(senders, e.getCharSequence(Notification.EXTRA_TITLE));
            add(senders, e.getCharSequence(Notification.EXTRA_TITLE_BIG));
            // Nas chamadas, o nome ou número vem muitas vezes no texto.
            if (medium == pt.relacoes.core.Interaction.CALL) add(senders, e.getCharSequence(Notification.EXTRA_TEXT));
            if (senders.isEmpty()) {
                Detect.log(getApplicationContext(), System.currentTimeMillis(), Detect.appName(getApplicationContext(), sbn.getPackageName()),
                        "", "Sem nome de remetente na notificação");
                return;
            }
            final int med = medium;
            final String pkg = sbn.getPackageName();
            final long when = System.currentTimeMillis();
            new Thread(() -> {
                try { Detect.handle(getApplicationContext(), pkg, med, senders, when); } catch (RuntimeException ignored) { }
            }).start();
        } catch (RuntimeException ignored) {
            // Uma notificação estranha nunca deve parar o serviço.
        }
    }

    private static void add(List<String> out, CharSequence s) {
        if (s != null && s.length() > 0 && s.length() < 120) out.add(s.toString());
    }
}
