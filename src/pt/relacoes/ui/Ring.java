package pt.relacoes.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Anel de progresso (meta da semana) com texto ao centro. */
final class Ring extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG),
            label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float fraction;
    private String text = "";

    Ring(Context c, int trackColor, int arcColor, int textColor, float strokeDp) {
        super(c);
        float sw = Ui.dp(c, strokeDp);
        for (Paint p : new Paint[]{track, arc}) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sw); p.setStrokeCap(Paint.Cap.ROUND); }
        track.setColor(trackColor);
        arc.setColor(arcColor);
        label.setColor(textColor);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Ui.font(c, 800));
    }

    void set(float fraction, String text) {
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        this.text = text;
        setContentDescription(text);
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float sw = track.getStrokeWidth();
        box.set(sw / 2, sw / 2, getWidth() - sw / 2, getHeight() - sw / 2);
        c.drawOval(box, track);
        if (fraction > 0) c.drawArc(box, -90, Math.max(2f, 360f * fraction), false, arc);
        label.setTextSize(getHeight() * 0.24f);
        Paint.FontMetrics fm = label.getFontMetrics();
        c.drawText(text, getWidth() / 2f, getHeight() / 2f - (fm.ascent + fm.descent) / 2, label);
    }
}
