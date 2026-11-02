package app.awanpro.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * The mihrab: a 600×900 pointed arch. Symmetric, so it needs no mirroring in Arabic. Home's is the
 * short one, 600×440, with the month's calendar below it.
 */
final class ArchView extends View {
    static final int REST = 0, FOCUSED = 1, APPROACHING = 2;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outer = new Path();
    private final Path innerLine = new Path();
    private int state = REST;
    private final boolean short_;

    ArchView(Context c) {
        this(c, false);
    }

    ArchView(Context c, boolean shortArch) {
        super(c);
        short_ = shortArch;
        fill.setColor(Tokens.LAPIS_800);
        stroke.setStyle(Paint.Style.STROKE);
        inner.setStyle(Paint.Style.STROKE);
        inner.setColor(Tokens.ARCH_INNER);
        inner.setStrokeWidth(Ui.px(1.5f));
    }

    /** The arch's own colour, see-through over a background photo (Photos.panel). */
    void setFill(int color) {
        fill.setColor(color);
        invalidate();
    }

    void setState(int s) {
        if (s == state) return;
        state = s;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float k = w / 600f;
        // design/afnon-mihrab/design/Home.dc.html: M0 900 L0 330 C0 170 140 70 300 0 C460 70 600 170 600 330 L600 900 Z
        // Short: M0 440 L0 220 C0 120 140 48 300 0 C460 48 600 120 600 220 L600 440 Z
        float bottom = short_ ? 440 : 900, side = short_ ? 220 : 330, c1 = short_ ? 120 : 170, c2 = short_ ? 48 : 70;
        outer.reset();
        outer.moveTo(0, bottom * k);
        outer.lineTo(0, side * k);
        outer.cubicTo(0, c1 * k, 140 * k, c2 * k, 300 * k, 0);
        outer.cubicTo(460 * k, c2 * k, 600 * k, c1 * k, 600 * k, side * k);
        outer.lineTo(600 * k, bottom * k);
        outer.close();
        // M18 900 L18 334 C18 184 150 92 300 22 C450 92 582 184 582 334 L582 900 (short: 224, 134, 66)
        float iside = short_ ? 224 : 334, ic1 = short_ ? 134 : 184, ic2 = short_ ? 66 : 92;
        innerLine.reset();
        innerLine.moveTo(18 * k, bottom * k);
        innerLine.lineTo(18 * k, iside * k);
        innerLine.cubicTo(18 * k, ic1 * k, 150 * k, ic2 * k, 300 * k, 22 * k);
        innerLine.cubicTo(450 * k, ic2 * k, 582 * k, ic1 * k, 582 * k, iside * k);
        innerLine.lineTo(582 * k, bottom * k);
    }

    @Override
    protected void onDraw(Canvas c) {
        // border.arch-rest / arch-focused / arch-approaching
        if (state == APPROACHING) {
            stroke.setColor(Tokens.GOLD_200);
            stroke.setStrokeWidth(Ui.px(6));
        } else if (state == FOCUSED) {
            stroke.setColor(Tokens.PLASTER_100);
            stroke.setStrokeWidth(Ui.px(5));
        } else {
            stroke.setColor(Tokens.GOLD_400);
            stroke.setStrokeWidth(Ui.px(3));
        }
        c.drawPath(outer, fill);
        c.drawPath(innerLine, inner);
        c.drawPath(outer, stroke);
    }
}
