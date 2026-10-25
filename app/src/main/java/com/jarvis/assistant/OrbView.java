package com.jarvis.assistant;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Grand orbe animé : il montre si l'assistant écoute, réfléchit ou parle. */
public class OrbView extends View {
    static final int IDLE = 0;
    static final int LISTENING = 1;
    static final int THINKING = 2;
    static final int SPEAKING = 3;
    static final int STANDBY = 4;

    private static final int ACCENT = 0xFF4FD6E6;
    private static final int DANGER = 0xFFFF7A70;
    private static final int INK = 0xFF032329;

    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inkFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inkStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
    private final float density;

    private int mode = IDLE;
    private boolean inCall = false;
    private float phase = 0f;
    private float cx, cy, base, maxR;

    public OrbView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        ringPaint.setStyle(Paint.Style.STROKE);
        inkFill.setColor(INK);
        inkFill.setStyle(Paint.Style.FILL);
        inkStroke.setColor(INK);
        inkStroke.setStyle(Paint.Style.STROKE);
        inkStroke.setStrokeCap(Paint.Cap.ROUND);
        anim.setDuration(2400);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                phase = (Float) a.getAnimatedValue();
                if (mode != IDLE || inCall) invalidate();
            }
        });
    }

    void setMode(int m) {
        if (m == mode) return;
        mode = m;
        invalidate();
    }

    void setInCall(boolean on) {
        inCall = on;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cx = w / 2f;
        cy = h / 2f;
        maxR = Math.min(cx, cy);
        base = maxR * 0.5f;
        corePaint.setShader(new RadialGradient(cx - base * 0.25f, cy - base * 0.3f, base * 1.3f,
                new int[]{0xFFC6F8FF, ACCENT, 0xFF0C6E7E}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        glowPaint.setShader(new RadialGradient(cx, cy, base * 1.7f,
                new int[]{withAlpha(ACCENT, 90), withAlpha(ACCENT, 0)}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!anim.isStarted()) anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        anim.cancel();
        super.onDetachedFromWindow();
    }

    private static int withAlpha(int color, int a) {
        return (a << 24) | (color & 0x00FFFFFF);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (maxR <= 0) return;
        float t = phase;
        double tau = Math.PI * 2;

        c.drawCircle(cx, cy, base * 1.7f, glowPaint);

        if (mode == LISTENING || mode == SPEAKING) {
            float speed = mode == SPEAKING ? 1.5f : 1f;
            for (int i = 0; i < 3; i++) {
                float p = (t * speed + i / 3f) % 1f;
                float r = base * 1.05f + (maxR - base * 1.05f) * p;
                ringPaint.setStrokeWidth(2f * density);
                ringPaint.setColor(withAlpha(ACCENT, (int) (150 * (1f - p))));
                c.drawCircle(cx, cy, r, ringPaint);
            }
        } else {
            ringPaint.setStrokeWidth(1.5f * density);
            ringPaint.setColor(withAlpha(ACCENT, mode == STANDBY ? 120 : 70));
            c.drawCircle(cx, cy, base * 1.2f, ringPaint);
        }

        if (mode == THINKING) {
            rect.set(cx - base * 1.22f, cy - base * 1.22f, cx + base * 1.22f, cy + base * 1.22f);
            ringPaint.setStrokeWidth(3f * density);
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
            ringPaint.setColor(withAlpha(0xFFFFFFFF, 220));
            c.drawArc(rect, t * 360f, 90f, false, ringPaint);
            ringPaint.setColor(withAlpha(ACCENT, 220));
            c.drawArc(rect, t * 360f + 180f, 60f, false, ringPaint);
            ringPaint.setStrokeCap(Paint.Cap.BUTT);
        }

        if (inCall) {
            ringPaint.setStrokeWidth(2f * density);
            ringPaint.setColor(withAlpha(DANGER, 170));
            c.drawCircle(cx, cy, base * 1.36f, ringPaint);
        }

        float scale = 1f;
        if (mode == LISTENING) scale = 1f + 0.04f * (float) Math.sin(t * tau * 2);
        else if (mode == SPEAKING) scale = 1f + 0.09f * (float) Math.abs(Math.sin(t * tau * 3));
        else if (mode == STANDBY) scale = 1f + 0.03f * (float) Math.sin(t * tau);

        c.save();
        c.scale(scale, scale, cx, cy);
        c.drawCircle(cx, cy, base, corePaint);
        if (inCall) {
            float s = base * 0.2f;
            rect.set(cx - s, cy - s, cx + s, cy + s);
            c.drawRoundRect(rect, s * 0.3f, s * 0.3f, inkFill);
        } else {
            float w = base * 0.15f;
            rect.set(cx - w, cy - base * 0.36f, cx + w, cy + base * 0.06f);
            c.drawRoundRect(rect, w, w, inkFill);
            inkStroke.setStrokeWidth(base * 0.07f);
            rect.set(cx - base * 0.27f, cy - base * 0.30f, cx + base * 0.27f, cy + base * 0.22f);
            c.drawArc(rect, 0f, 180f, false, inkStroke);
            c.drawLine(cx, cy + base * 0.22f, cx, cy + base * 0.40f, inkStroke);
            c.drawLine(cx - base * 0.13f, cy + base * 0.40f, cx + base * 0.13f, cy + base * 0.40f, inkStroke);
        }
        c.restore();
    }
}
