package hcmute.edu.vn.doinbot.ui.countdown;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * StopwatchBarsView — draws 60 radial tick marks around a circle.
 *
 * Every 5th tick is a "major" tick (longer, slightly thicker).
 * When {@link #setCurrentSecond(int)} is called with 0–59, the tick at that
 * position glows in the theme's primary colour; the 3 preceding ticks fade out
 * as a trailing highlight, giving a smooth sweep effect.
 *
 * All colours are resolved from the current theme at construction time so the
 * view adapts to light / dark mode without any extra code.
 */
public class StopwatchBarsView extends View {

    private static final int TICK_COUNT   = 60;
    private static final int TRAIL_LENGTH = 4;   // ticks behind head that glow

    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int colorActive;
    private int colorInactive;

    /** The second (0–59) whose tick is fully lit. -1 = nothing highlighted. */
    private int currentSecond = -1;

    // ── Constructors ─────────────────────────────────────────────────────────

    public StopwatchBarsView(Context context) {
        super(context);
        init(context);
    }

    public StopwatchBarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public StopwatchBarsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    // ── Initialisation ───────────────────────────────────────────────────────

    private void init(Context context) {
        TypedValue tv = new TypedValue();

        // Active colour = theme primary (purple in our palette)
        context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, tv, true);
        colorActive = tv.data;

        // Inactive colour = colorOnSurface at ~20 % opacity
        context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface, tv, true);
        int onSurface = tv.data;
        colorInactive = Color.argb(
                50,
                Color.red(onSurface),
                Color.green(onSurface),
                Color.blue(onSurface));

        inactivePaint.setStyle(Paint.Style.STROKE);
        inactivePaint.setStrokeCap(Paint.Cap.ROUND);

        activePaint.setStyle(Paint.Style.STROKE);
        activePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth()  / 2f;
        float cy = getHeight() / 2f;
        // Leave a small margin so the stroke caps don't get clipped
        float outerR = Math.min(cx, cy) - 6f;

        for (int i = 0; i < TICK_COUNT; i++) {
            boolean isMajor = (i % 5 == 0);

            // Major ticks are longer and thicker
            float innerFrac = isMajor ? 0.78f : 0.87f;
            float innerR    = outerR * innerFrac;
            float baseStroke = isMajor ? 4f : 2.5f;

            // Angle: 0 at top (12-o'clock), going clockwise
            double angleRad = Math.toRadians(i * 6.0 - 90.0);
            float  cos      = (float) Math.cos(angleRad);
            float  sin      = (float) Math.sin(angleRad);

            float x1 = cx + cos * innerR;
            float y1 = cy + sin * innerR;
            float x2 = cx + cos * outerR;
            float y2 = cy + sin * outerR;

            if (currentSecond >= 0) {
                // diff = how many ticks behind the active head this tick is
                int diff = (currentSecond - i + TICK_COUNT) % TICK_COUNT;

                if (diff == 0) {
                    // Active head: full accent colour, slightly thicker
                    activePaint.setColor(colorActive);
                    activePaint.setAlpha(255);
                    activePaint.setStrokeWidth(baseStroke + 2f);
                    canvas.drawLine(x1, y1, x2, y2, activePaint);

                } else if (diff <= TRAIL_LENGTH) {
                    // Trail: fade from accent to transparent
                    float fraction = 1f - (float) diff / (TRAIL_LENGTH + 1f);
                    int   alpha    = (int) (210 * fraction);
                    activePaint.setColor(colorActive);
                    activePaint.setAlpha(Math.max(0, alpha));
                    activePaint.setStrokeWidth(baseStroke);
                    canvas.drawLine(x1, y1, x2, y2, activePaint);

                } else {
                    // Inactive
                    inactivePaint.setColor(colorInactive);
                    inactivePaint.setStrokeWidth(baseStroke);
                    canvas.drawLine(x1, y1, x2, y2, inactivePaint);
                }
            } else {
                // Idle — all ticks inactive
                inactivePaint.setColor(colorInactive);
                inactivePaint.setStrokeWidth(baseStroke);
                canvas.drawLine(x1, y1, x2, y2, inactivePaint);
            }
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Highlight the given second (0–59) and redraw.
     * The 3 preceding ticks will be drawn as a fading trail.
     * Call with -1 to return to the idle (no highlight) state.
     */
    public void setCurrentSecond(int second) {
        this.currentSecond = second;
        invalidate();
    }

    /** Return to idle — no tick is highlighted. */
    public void reset() {
        setCurrentSecond(-1);
    }
}
