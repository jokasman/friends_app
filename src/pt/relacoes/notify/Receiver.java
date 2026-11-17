package pt.relacoes.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import pt.relacoes.data.Prefs;

/** Recebe alarmes, ações das notificações e eventos do sistema (arranque, mudança de hora). */
public class Receiver extends BroadcastReceiver {
    static final String ACTION_DONE = "pt.relacoes.DONE", ACTION_SNOOZE = "pt.relacoes.SNOOZE", ACTION_STOP_TEST = "pt.relacoes.STOP_TEST";
    static final String EXTRA_MIN = "min";
    static final String EXTRA_ID = "id";

    @Override public void onReceive(Context c, Intent i) {
        final String a = i.getAction() == null ? "" : i.getAction();
        final long id = i.getLongExtra(EXTRA_ID, 0);
        final int min = i.getIntExtra(EXTRA_MIN, 0);
        if (Engine.ACTION_TEST.equals(a)) { AlarmService.start(c, AlarmService.TEST_ID); return; }
        if (ACTION_STOP_TEST.equals(a)) { AlarmService.stop(null); return; }
        final Context app = c.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                switch (a) {
                    case Engine.ACTION_FIRE: Engine.fire(app); break;
                    case Engine.ACTION_DAILY: Engine.daily(app); break;
                    case ACTION_DONE: Engine.done(app, id); break;
                    case ACTION_SNOOZE: Engine.snooze(app, id, min > 0 ? min : Prefs.snooze(app)); break;
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
