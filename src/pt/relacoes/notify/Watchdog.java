package pt.relacoes.notify;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Vigilância de reserva: de ~15 em 15 minutos o Android acorda a app para enviar lembretes que tenham ficado
 * para trás (se o sistema suspendeu a app) e voltar a armar os alarmes.
 */
public class Watchdog extends JobService {
    private static final int JOB_ID = 4242;

    /** Agenda a tarefa periódica (uma só vez; sobrevive a reinícios). */
    public static void ensure(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null || js.getPendingJob(JOB_ID) != null) return;
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, Watchdog.class))
                    .setPeriodic(15 * 60_000L)
                    .setPersisted(true)
                    .build();
            js.schedule(job);
        } catch (RuntimeException ignored) { }
    }

    @Override public boolean onStartJob(JobParameters params) {
        final Context app = getApplicationContext();
        new Thread(() -> {
            try { Engine.catchUp(app); } catch (RuntimeException ignored) { }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
