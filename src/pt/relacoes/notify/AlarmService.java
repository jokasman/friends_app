package pt.relacoes.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.ArrayList;
import java.util.List;

import pt.relacoes.R;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Person;
import pt.relacoes.core.Reminder;
import pt.relacoes.data.Db;
import pt.relacoes.data.Prefs;
import pt.relacoes.ui.AlarmActivity;
import pt.relacoes.ui.PendingActivity;

/**
 * Serviço em primeiro plano (igual ao do Lembra) com dois papéis:
 * <ul>
 *   <li><b>Despertador</b>: toca no canal de ALARME (ouve-se mesmo em silêncio), com volume próprio, volume crescente
 *       e vibração, até o utilizador decidir (Feito, Adiar ou Já falei). Abre o ecrã do alarme por cima do bloqueio e
 *       volta a abri-lo sempre que o ecrã acende ou o telemóvel é desbloqueado. Vários alarmes tocam em fila.</li>
 *   <li><b>Vigilante de pendentes</b>: enquanto houver lembretes notificados por tratar, ao desbloquear o telemóvel
 *       abre o ecrã "Por tratar".</li>
 * </ul>
 */
public class AlarmService extends Service {
    static final String CH_ALARM = "despertador", CH_PENDING = "pendentes";
    static final int NOTIF_ID = 777_000;
    public static final long TEST_ID = -42;
    static final long NONE = Long.MIN_VALUE;
    static final String ACT_RING = "ring", ACT_WATCH = "watch", ACT_REPOST = "repost";

    private static AlarmService instance;
    private long currentId = NONE;
    private final List<Long> ringQueue = new ArrayList<>();
    private boolean paused;
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wake;
    private int restoreVolume = -1;
    private float vol;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean receiverOn;

    // ------------------------------------------------------------------ API estática

    /** Começa a tocar para um lembrete (ou TEST_ID). */
    public static void start(Context ctx, long id) {
        Intent i = new Intent(ctx, AlarmService.class).setAction(ACT_RING).putExtra("id", id);
        try {
            ctx.startForegroundService(i);
        } catch (Exception e) {
            diag(ctx, "Não foi possível abrir o despertador (" + e.getClass().getSimpleName() + "); avisou pela notificação de reserva.");
            Reminder r = Db.get(ctx).reminder(id);
            if (r != null) Notify.reminder(ctx, r);
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** O estado do toque só é alterado na thread principal. */
    private static void onMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run(); else MAIN.post(r);
    }

    /** Pára o toque (ação do utilizador). id == null pára qualquer toque. */
    public static void stop(Long id) {
        onMain(() -> {
            AlarmService s = instance;
            if (s == null) return;
            if (id != null && s.currentId != id) { s.dequeue(id); return; }
            if (s.currentId != NONE) s.stopRinging();
        });
    }

    /** Início de uma pausa de alarmes: pára o toque e tudo o que está na fila volta a tocar no fim da pausa. */
    public static void deferForPause(Context ctx) {
        final Context app = ctx.getApplicationContext();
        onMain(() -> { AlarmService s = instance; if (s != null) s.deferAll(app); });
    }

    /** Silencia enquanto o utilizador escolhe (adiar, já falei); o alarme continua ativo. */
    public static void pause() { onMain(() -> { AlarmService s = instance; if (s != null && s.currentId != NONE) s.pausePlayback(); }); }

    /** Retoma o toque se o utilizador cancelou. */
    public static void resume() { onMain(() -> { AlarmService s = instance; if (s != null && s.currentId != NONE && s.paused) s.resumePlayback(); }); }

    public static boolean isRinging(long id) { AlarmService s = instance; return s != null && s.currentId == id; }

    /** Chamado após cada alteração: liga/desliga o vigilante de pendentes. */
    public static void sync(Context ctx) {
        AlarmService s = instance;
        if (s != null && s.currentId != NONE) return;
        boolean watch = Prefs.showPending(ctx) && !pending(ctx).isEmpty();
        if (watch) {
            try {
                ctx.startForegroundService(new Intent(ctx, AlarmService.class).setAction(ACT_WATCH));
            } catch (Exception ignored) { /* sem autorização para arrancar agora; tenta na próxima alteração */ }
        } else if (s != null && s.ringQueue.isEmpty()) {
            s.stopForeground(STOP_FOREGROUND_REMOVE);
            s.stopSelf();
        }
    }

    /** Lembretes já notificados e ainda por tratar. */
    public static List<Reminder> pending(Context ctx) {
        List<Reminder> out = new ArrayList<>();
        java.util.Map<Long, Person> people = Db.get(ctx).peopleById();
        for (Reminder r : Db.get(ctx).reminders()) if (!r.done && r.notified && people.containsKey(r.personId)) out.add(r);
        return out;
    }

    // ------------------------------------------------------------------ Ciclo de vida

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        restoreRingQueue();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, f);
        receiverOn = true;
    }

    /** Ecrã ligado / telemóvel desbloqueado. */
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (currentId != NONE) {
                if (!paused) openAlarmScreen();
            } else if (Intent.ACTION_USER_PRESENT.equals(i.getAction()) && Prefs.showPending(c) && !pending(c).isEmpty()) {
                openActivity(new Intent(c, PendingActivity.class));
            }
        }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent == null ? ACT_WATCH : intent.getAction();
        if (ACT_RING.equals(act)) {
            long id = intent.getLongExtra("id", NONE);
            Reminder r = id == TEST_ID ? null : Db.get(this).reminder(id);
            if (id != TEST_ID && (r == null || r.done)) {
                dequeue(id);
                if (currentId == NONE) startNextQueuedOrWatch();
                return START_NOT_STICKY;
            }
            if (id != TEST_ID && Engine.pauseActive(this)) {
                // Pausa de alarmes: toca no fim da pausa.
                r.nextAt = Prefs.pauseEnd(this);
                Db.get(this).save(r);
                Engine.reschedule(this);
                if (currentId == NONE) foregroundWatcherOrStop();
                return START_STICKY;
            }
            if (id == currentId) {
                // Já está a tocar (p.ex. insistência): não reinicia o som.
                foreground(buildAlarmNotification(id), true);
                if (id != TEST_ID) Notify.watchAlarm(this, r);
                if (!paused) openAlarmScreen();
                return START_STICKY;
            }
            if (currentId != NONE && currentId != TEST_ID && id != TEST_ID) {
                enqueue(id);
                return START_STICKY;
            }
            if (id != TEST_ID && currentId == NONE && !ringQueue.isEmpty()) {
                enqueue(id);
                startNextQueuedOrWatch();
                return START_STICKY;
            }
            if (currentId != NONE && currentId != TEST_ID) {
                queueFirst(currentId);
                Notify.cancelWatchAlarm(this, currentId);
            }
            releasePlayback();
            currentId = NONE;
            if (id != TEST_ID) dequeue(id);
            beginRinging(id);
            return START_STICKY;
        }
        if (ACT_REPOST.equals(act)) {
            // O utilizador deslizou a notificação do alarme: volta a mostrá-la (o alarme só pára com uma decisão).
            if (currentId != NONE) {
                foreground(buildAlarmNotification(currentId), true);
                if (currentId != TEST_ID) {
                    Reminder r = Db.get(this).reminder(currentId);
                    if (r != null) Notify.watchAlarm(this, r);
                }
            } else startNextQueuedOrWatch();
            return START_STICKY;
        }
        if (currentId == NONE) startNextQueuedOrWatch();
        return START_STICKY;
    }

    private void beginRinging(long id) {
        currentId = id;
        paused = false;
        persistCurrent();
        if (id != TEST_ID) Notify.cancel(this, id);
        foreground(buildAlarmNotification(id), true);
        if (id != TEST_ID) {
            Reminder r = Db.get(this).reminder(id);
            if (r != null) Notify.watchAlarm(this, r);
        }
        startPlayback();
        openAlarmScreen();
    }

    private void enqueue(long id) {
        if (id > 0 && !ringQueue.contains(id)) { ringQueue.add(id); persistQueue(); }
    }

    private void queueFirst(long id) {
        if (id <= 0) return;
        ringQueue.remove(Long.valueOf(id));
        ringQueue.add(0, id);
        persistQueue();
    }

    private void dequeue(long id) {
        if (id > 0 && ringQueue.remove(Long.valueOf(id))) persistQueue();
    }

    private void startNextQueuedOrWatch() {
        if (currentId != NONE) return;
        if (Engine.pauseActive(this)) {
            deferAll(this);
            foregroundWatcherOrStop();
            return;
        }
        while (!ringQueue.isEmpty()) {
            long next = ringQueue.remove(0);
            persistQueue();
            Reminder r = Db.get(this).reminder(next);
            if (r == null || r.done || !r.notified || !r.alarm) continue;
            beginRinging(next);
            return;
        }
        foregroundWatcherOrStop();
    }

    private void deferAll(Context ctx) {
        long end = Prefs.pauseEnd(ctx);
        List<Long> ids = new ArrayList<>(ringQueue);
        if (currentId != NONE && currentId != TEST_ID) ids.add(0, currentId);
        for (long id : ids) {
            Reminder r = Db.get(ctx).reminder(id);
            if (r != null && !r.done) { r.nextAt = end; Db.get(ctx).save(r); }
            Notify.cancelWatchAlarm(ctx, id);
        }
        ringQueue.clear();
        persistQueue();
        if (currentId != NONE) {
            releasePlayback();
            currentId = NONE;
            persistCurrent();
            AlarmActivity.closeAll();
        }
        Engine.reschedule(ctx);
    }

    private void restoreRingQueue() {
        String active = Prefs.str(this, "alarm_current");
        if (active != null && !active.isEmpty()) {
            try { queueFirst(Long.parseLong(active)); } catch (NumberFormatException ignored) { }
        }
        String saved = Prefs.str(this, "alarm_ring_queue");
        if (saved != null && !saved.isEmpty()) {
            for (String item : saved.split(",")) {
                try { enqueue(Long.parseLong(item)); } catch (NumberFormatException ignored) { }
            }
        }
        Prefs.setStr(this, "alarm_current", "");
        persistQueue();
    }

    private void persistCurrent() {
        Prefs.setStr(this, "alarm_current", currentId > 0 ? String.valueOf(currentId) : "");
    }

    private void persistQueue() {
        StringBuilder v = new StringBuilder();
        for (Long id : ringQueue) { if (v.length() > 0) v.append(','); v.append(id.longValue()); }
        Prefs.setStr(this, "alarm_ring_queue", v.toString());
    }

    private void foreground(Notification n, boolean ringing) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ringing ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else startForeground(NOTIF_ID, n);
    }

    private void foregroundWatcherOrStop() {
        List<Reminder> p = pending(this);
        if (p.isEmpty() || !Prefs.showPending(this)) {
            // startForeground é obrigatório depois de startForegroundService, mesmo que vá parar logo.
            foreground(buildWatcherNotification(p), false);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        foreground(buildWatcherNotification(p), false);
    }

    @Override public void onDestroy() {
        releasePlayback();
        handler.removeCallbacksAndMessages(null);
        if (receiverOn) { try { unregisterReceiver(screen); } catch (Exception ignored) { } receiverOn = false; }
        if (instance == this) instance = null;
        super.onDestroy();
    }

    // ------------------------------------------------------------------ Ecrãs

    private void openAlarmScreen() {
        openActivity(new Intent(this, AlarmActivity.class).putExtra(AlarmActivity.EXTRA_ID, currentId));
    }

    /** Abre um ecrã a partir do serviço (com o telemóvel desbloqueado requer "Mostrar sobre outras apps"). */
    private void openActivity(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception ignored) { /* o full-screen intent da notificação continua a funcionar */ }
    }

    // ------------------------------------------------------------------ Notificações

    private void channels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CH_ALARM, "Despertador", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Lembretes que tocam como despertador");
        ch.setSound(null, null); // o som é tocado pelo serviço no canal de alarme
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        NotificationChannel pc = new NotificationChannel(CH_PENDING, "Lembretes por tratar", NotificationManager.IMPORTANCE_LOW);
        pc.setDescription("Resumo dos lembretes que ainda não marcou como feitos nem adiou");
        nm.createNotificationChannel(pc);
    }

    private PendingIntent repostIntent() {
        return PendingIntent.getService(this, 777_002, new Intent(this, AlarmService.class).setAction(ACT_REPOST),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent alarmScreen(int code, long id, boolean done) {
        Intent full = new Intent(this, AlarmActivity.class).putExtra(AlarmActivity.EXTRA_ID, id).putExtra(AlarmActivity.EXTRA_DONE, done)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        return PendingIntent.getActivity(this, code, full, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification buildAlarmNotification(long id) {
        channels();
        String[] t = id == TEST_ID ? new String[]{"Teste do despertador", "Se ouviu o som com o telemóvel em silêncio, está tudo a funcionar.", ""}
                : Notify.texts(this, Db.get(this).reminder(id));
        PendingIntent fullPi = alarmScreen(777_001, id, false);
        Notification.Builder b = new Notification.Builder(this, CH_ALARM)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(getColor(R.color.accent))
                .setContentTitle("⏰ " + t[0])
                .setContentText(t[1])
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi)
                .setDeleteIntent(repostIntent());
        if (id != TEST_ID) {
            int quick = Prefs.quickSnooze(this);
            // "Feito" abre o ecrã do alarme para confirmar com a impressão digital.
            b.addAction(new Notification.Action.Builder(null, "Feito", alarmScreen(777_005, id, true)).build());
            b.addAction(new Notification.Action.Builder(null, "+" + Fmt.duration(quick), Notify.snoozeAction(this, id, quick)).build());
        } else {
            b.addAction(new Notification.Action.Builder(null, "Parar", Notify.stopTestAction(this)).build());
        }
        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT | Notification.FLAG_NO_CLEAR;
        return n;
    }

    private Notification buildWatcherNotification(List<Reminder> p) {
        channels();
        PendingIntent open = PendingIntent.getActivity(this, 777_003,
                new Intent(this, PendingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String title = p.size() == 1 ? "1 lembrete por tratar" : p.size() + " lembretes por tratar";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, p.size()); i++) {
            if (i > 0) sb.append(" · ");
            sb.append(Notify.texts(this, p.get(i))[0]);
        }
        return new Notification.Builder(this, CH_PENDING)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(sb.toString())
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    // ------------------------------------------------------------------ Som

    private void startPlayback() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (wake == null) {
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "relacoes:alarm");
            wake.setReferenceCounted(false);
        }
        wake.acquire(60 * 60000L);

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int cur = am.getStreamVolume(AudioManager.STREAM_ALARM);
            int target = alarmStreamLevel(this, max);
            if (cur != target) { if (restoreVolume < 0) restoreVolume = cur; am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0); }
        } catch (Exception ignored) { }

        AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        // Reserva: som escolhido → alarme do sistema → som incluído na app.
        List<Uri> candidates = new ArrayList<>();
        String custom = Prefs.alarmSound(this);
        if (custom != null) candidates.add(Uri.parse(custom));
        try { Uri a = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM); if (a != null) candidates.add(a); } catch (Exception ignored) { }
        Uri da = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (da != null) candidates.add(da);
        candidates.add(null); // som da app
        String used = null, errors = "";
        for (Uri u : candidates) {
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setAudioAttributes(attrs);
                if (u == null) {
                    android.content.res.AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.alarm_tone);
                    mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                } else {
                    mp.setDataSource(this, u);
                }
                mp.setLooping(true);
                vol = Prefs.crescendo(this) ? 0.25f : 1f;
                mp.setVolume(vol, vol);
                mp.prepare();
                mp.start();
                player = mp;
                used = u == null ? "som da app" : "som do sistema";
                break;
            } catch (Exception e) {
                errors += e.getClass().getSimpleName() + " ";
                try { mp.release(); } catch (Exception ignored) { }
            }
        }
        if (player != null) handler.post(crescendo);
        int av = am.getStreamVolume(AudioManager.STREAM_ALARM), amax = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        diag(this, player == null ? "Falhou a reproduzir som (" + errors.trim() + "). Volume de alarme " + av + "/" + amax + "."
                : "Último alarme tocou (" + used + ", volume de alarme " + av + "/" + amax + ")"
                + (errors.isEmpty() ? "." : "; falhas antes: " + errors.trim() + "."));

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 500}, 0), attrs);
        }
        handler.removeCallbacks(keepAwake);
        handler.postDelayed(keepAwake, 50 * 60000L);
    }

    private final Runnable keepAwake = new Runnable() {
        public void run() {
            if (currentId == NONE || wake == null) return;
            wake.acquire(60 * 60000L);
            handler.postDelayed(this, 50 * 60000L);
        }
    };

    /** Volume crescente durante ~20 s. */
    private final Runnable crescendo = new Runnable() {
        public void run() {
            if (player == null) return;
            vol = Math.min(1f, vol + 0.085f);
            try { player.setVolume(vol, vol); } catch (Exception ignored) { }
            if (vol < 1f) handler.postDelayed(this, 2000);
        }
    };

    public static int alarmStreamLevel(Context c, int max) {
        return Math.max(1, Math.round(max * Prefs.alarmVolume(c) / 100f));
    }

    static void diag(Context c, String msg) {
        Prefs.setStr(c, "alarm_diag", Fmt.when(System.currentTimeMillis(), System.currentTimeMillis()) + " — " + msg);
    }

    private void pausePlayback() {
        paused = true;
        handler.removeCallbacks(crescendo);
        if (player != null) { try { player.stop(); } catch (Exception ignored) { } player.release(); player = null; }
        if (vibrator != null) { vibrator.cancel(); vibrator = null; }
    }

    private void resumePlayback() {
        paused = false;
        startPlayback();
        openAlarmScreen();
    }

    private void releasePlayback() {
        pausePlayback();
        paused = false;
        handler.removeCallbacks(keepAwake);
        if (restoreVolume >= 0) {
            try { ((AudioManager) getSystemService(AUDIO_SERVICE)).setStreamVolume(AudioManager.STREAM_ALARM, restoreVolume, 0); } catch (Exception ignored) { }
            restoreVolume = -1;
        }
        if (wake != null && wake.isHeld()) wake.release();
    }

    private void stopRinging() {
        if (currentId != NONE && currentId != TEST_ID) Notify.cancelWatchAlarm(this, currentId);
        releasePlayback();
        currentId = NONE;
        persistCurrent();
        AlarmActivity.closeAll();
        startNextQueuedOrWatch();
    }
}
