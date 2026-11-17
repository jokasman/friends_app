package pt.relacoes.ui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

import pt.relacoes.R;
import pt.relacoes.core.Fmt;
import pt.relacoes.core.Health;
import pt.relacoes.core.Person;
import pt.relacoes.core.Recur;
import pt.relacoes.data.Insights;

/** Widget: pessoas há mais tempo sem contacto e próximos aniversários. */
public class Widget extends AppWidgetProvider {
    private static final int[] P_ROW = {R.id.prow0, R.id.prow1, R.id.prow2};
    private static final int[] P_DOT = {R.id.pdot0, R.id.pdot1, R.id.pdot2};
    private static final int[] P_NAME = {R.id.pname0, R.id.pname1, R.id.pname2};
    private static final int[] P_INFO = {R.id.pinfo0, R.id.pinfo1, R.id.pinfo2};
    private static final int[] B_ROW = {R.id.brow0, R.id.brow1, R.id.brow2};
    private static final int[] B_NAME = {R.id.bname0, R.id.bname1, R.id.bname2};
    private static final int[] B_INFO = {R.id.binfo0, R.id.binfo1, R.id.binfo2};

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { update(c, m, ids); }

    public static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            if (m == null) return;
            int[] ids = m.getAppWidgetIds(new ComponentName(c, Widget.class));
            if (ids != null && ids.length > 0) update(c, m, ids);
        } catch (RuntimeException ignored) {}
    }

    private static PendingIntent open(Context c, int code, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        long today = Insights.today();
        List<Person> late = Insights.longestWithoutContact(c, 3);
        v.setViewVisibility(R.id.pempty, late.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 3; i++) {
            if (i < late.size()) {
                Person p = late.get(i);
                int h = Insights.health(p, today);
                v.setViewVisibility(P_ROW[i], View.VISIBLE);
                v.setTextColor(P_DOT[i], Health.COLORS[h]);
                v.setTextViewText(P_NAME[i], p.name);
                v.setTextViewText(P_INFO[i], p.lastAt > 0 ? Fmt.ago(Insights.daysSince(p, today)) : "nunca");
                v.setOnClickPendingIntent(P_ROW[i], open(c, 7000 + (int) p.id,
                        new Intent(c, PersonActivity.class).putExtra(PersonActivity.EXTRA_PERSON, p.id)));
            } else {
                v.setViewVisibility(P_ROW[i], View.GONE);
            }
        }
        List<Person> bd = Insights.upcomingBirthdays(c, 60);
        v.setViewVisibility(R.id.bempty, bd.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 3; i++) {
            if (i < bd.size()) {
                Person p = bd.get(i);
                long d = Recur.nextBirthday(p, today) - today;
                v.setViewVisibility(B_ROW[i], View.VISIBLE);
                v.setTextViewText(B_NAME[i], p.name);
                v.setTextViewText(B_INFO[i], d == 0 ? "hoje 🎉" : d == 1 ? "amanhã" : p.bDay + " " + Fmt.MONTHS[p.bMonth - 1].substring(0, 3));
                Intent it = d == 0 ? new Intent(c, BirthdayActivity.class) : new Intent(c, PersonActivity.class);
                v.setOnClickPendingIntent(B_ROW[i], open(c, 8000 + (int) p.id, it.putExtra(PersonActivity.EXTRA_PERSON, p.id)));
            } else {
                v.setViewVisibility(B_ROW[i], View.GONE);
            }
        }
        v.setOnClickPendingIntent(R.id.header, open(c, 6000, new Intent(c, MainActivity.class)));
        m.updateAppWidget(ids, v);
    }
}
