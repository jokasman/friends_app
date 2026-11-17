package pt.relacoes.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import pt.relacoes.data.Prefs;

/** Recebe alarmes, ações das notificações e eventos do sistema (arranque, mudança de hora). */
public class Receiver extends BroadcastReceiver {
    static final String ACTION_DONE = "pt.relacoes.DONE", ACTION_SNOOZE = "pt.relacoes.SNOOZE";
    static final String EXTRA_ID = "id";

    @Override public void onReceive(Context c, Intent i) {
        final String a = i.getAction() == null ? "" : i.getAction();
        final long id = i.getLongExtra(EXTRA_ID, 0);
        final Context app = c.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                switch (a) {
                    case Engine.ACTION_FIRE: Engine.fire(app); break;
                    case Engine.ACTION_DAILY: Engine.daily(app); break;
                    case ACTION_DONE: Engine.done(app, id); break;
                    case ACTION_SNOOZE: Engine.snooze(app, id, Prefs.snooze(app)); break;
                    default:
                        Notify.channels(app);
                        Engine.fire(app);
                }
            } finally {
                pr.finish();
            }
        }).start();
    }
}
