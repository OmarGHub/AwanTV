package app.awanpro.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The weather beside a clock (Home, the screensaver): a thin line, the sky's icon and the temperature.
 * Gone while there is no reading ({@link Weather#now}): turned off, offline for hours, or not read yet.
 */
final class WeatherBadge extends LinearLayout {

    private final Context c;
    private final Text t;
    private final Sky icon;
    private final TextView temp;
    /** The temperature's own size, and the room the rest takes (line, gaps, icon), in screen pixels: fitWithin. */
    private final float tempSize;
    private int fixedWidth;

    /**
     * @param iconSize  the icon's side in design pixels
     * @param textSize  the temperature's size in design pixels
     * @param gap       design pixels between the clock and the line, and between the line and the icon
     */
    WeatherBadge(Context c, Text t, Typeface tf, float iconSize, float textSize, float gap, int iconColor, int textColor,
                 int lineColor) {
        super(c);
        this.c = c;
        this.t = t;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        View line = new View(c);
        line.setBackgroundColor(lineColor);
        LayoutParams llp = new LayoutParams(Math.max(1, Ui.ipx(1)), Ui.ipx(iconSize * 1.1f));
        llp.setMarginStart(Ui.ipx(gap));
        llp.setMarginEnd(Ui.ipx(gap));
        // No line at all for lineColor 0 (the screensaver's weather stands apart from its clock).
        if (lineColor != 0) addView(line, llp);
        icon = new Sky(c, iconColor);
        // Thinner strokes as the icon grows, so a large one stays as light as the clock's figures.
        icon.stroke = iconSize > 100 ? 1.1f : iconSize > 60 ? 1.2f : 1.4f;
        addView(icon, new LayoutParams(Ui.ipx(iconSize), Ui.ipx(iconSize)));
        temp = new TextView(c);
        temp.setTypeface(tf);
        temp.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(textSize));
        temp.setTextColor(textColor);
        temp.setSingleLine(true);
        temp.setIncludeFontPadding(false);
        temp.setTextDirection(t.rtl() ? TEXT_DIRECTION_RTL : TEXT_DIRECTION_LTR);
        LayoutParams tlp = new LayoutParams(-2, -2);
        tlp.setMarginStart(Ui.ipx(textSize * 0.18f));
        addView(temp, tlp);
        tempSize = Ui.px(textSize);
        fixedWidth = Ui.ipx(iconSize) + Ui.ipx(textSize * 0.18f);
        if (lineColor != 0) fixedWidth += llp.width + 2 * Ui.ipx(gap);
        setVisibility(GONE);
    }

    /** Shows the latest reading (or hides), and asks for a new one when it is due. On the screen's minute tick. */
    void update() {
        Weather.refresh(c);
        Weather.Now n = Weather.now(c);
        if (n == null) {
            setVisibility(GONE);
            return;
        }
        // The night sky's moon as it is tonight, by the Hijri day (mirrored south of the equator).
        icon.set(n.sky, n.day, Hijri.of(c, new java.util.Date())[2], Prefs.city(c).lat < 0);
        temp.setText(Weather.temperature(c, t, n));
        setContentDescription(t.sky(n.sky) + " " + temp.getText());
        setVisibility(VISIBLE);
    }

    /**
     * Keeps the whole badge within maxWidth screen pixels (Home: the clock's line ends before the radio button):
     * the temperature's figures alone grow smaller, as far as 70%, only for a reading too wide ("-12°", "113°" beside
     * a 12-hour clock); otherwise their own size.
     */
    void fitWithin(int maxWidth) {
        android.graphics.Paint p = new android.graphics.Paint(temp.getPaint());
        p.setTextSize(tempSize);
        float full = p.measureText(String.valueOf(temp.getText()));
        float size = tempSize;
        if (full > 0 && fixedWidth + full > maxWidth) {
            float scale = Math.max(0.7f, Math.min(1f, (maxWidth - fixedWidth) / full));
            size = tempSize * scale;
        }
        if (Math.abs(temp.getTextSize() - size) > 0.5f) temp.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }

    /** The sky as a stroke icon on a 24px grid, like {@link Icon}: sun or moon, cloud, rain, snow, fog, storm. */
    static final class Sky extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path(), cloud = new Path();
        private final RectF r = new RectF();
        private int sky = Weather.CLEAR;
        float stroke = 1.4f;
        private boolean day = true;
        /** The Hijri day of the month (the moon's age near enough), and whether the moon is seen from the south. */
        private int moonDay = 15;
        private boolean south;

        Sky(Context c, int color) {
            super(c);
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void set(int sky, boolean day, int moonDay, boolean south) {
            if (this.sky == sky && this.day == day && this.moonDay == moonDay && this.south == south) return;
            this.sky = sky;
            this.day = day;
            this.moonDay = moonDay;
            this.south = south;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float k = getWidth() / 24f;
            p.setStrokeWidth(stroke * k);
            p.setStyle(Paint.Style.STROKE);
            switch (sky) {
                case Weather.CLEAR:
                    if (day) sun(c, k, 12, 12, 4.4f, 2.2f, 3.2f);
                    else moon(c, k, 12, 12, 7f);
                    break;
                case Weather.PARTLY: {
                    cloud(k, 11.5f, 15.2f, 3.5f, 16f, 13.4f, 4.3f, 7.8f, 20.3f, 15.2f, 19.8f);
                    c.save();
                    c.clipOutPath(cloud);
                    if (day) sun(c, k, 8.5f, 8.5f, 3.2f, 1.5f, 2.4f);
                    else moon(c, k, 8.5f, 8.5f, 4.6f);
                    c.restore();
                    c.drawPath(cloud, p);
                    break;
                }
                case Weather.CLOUDY:
                    cloud(k, 8.8f, 12.6f, 4f, 14.6f, 10.8f, 5.2f, 3.6f, 20.4f, 12.8f, 18.4f);
                    c.drawPath(cloud, p);
                    break;
                default: {
                    // A cloud up top and what falls from it below.
                    cloud(k, 8.8f, 9.4f, 3.6f, 14.4f, 7.8f, 4.6f, 4.2f, 19.8f, 9.6f, 14.4f);
                    c.drawPath(cloud, p);
                    below(c, k);
                }
            }
        }

        private void below(Canvas c, float k) {
            switch (sky) {
                case Weather.DRIZZLE:
                    for (float x : new float[] {8, 12, 16}) c.drawLine(x * k, 17.2f * k, (x - 0.4f) * k, 18.6f * k, p);
                    break;
                case Weather.RAIN:
                    for (float x : new float[] {8.4f, 12.4f, 16.4f}) c.drawLine(x * k, 16.6f * k, (x - 1.2f) * k, 20.6f * k, p);
                    break;
                case Weather.SNOW:
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(8 * k, 17.6f * k, 1f * k, p);
                    c.drawCircle(12 * k, 20f * k, 1f * k, p);
                    c.drawCircle(16 * k, 17.6f * k, 1f * k, p);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                case Weather.FOG:
                    c.drawLine(5 * k, 17.5f * k, 19 * k, 17.5f * k, p);
                    c.drawLine(7 * k, 20.5f * k, 17 * k, 20.5f * k, p);
                    break;
                default: // STORM
                    path.reset();
                    path.moveTo(13.4f * k, 15.6f * k);
                    path.lineTo(10.6f * k, 19f * k);
                    path.lineTo(13.2f * k, 19f * k);
                    path.lineTo(11f * k, 22.4f * k);
                    c.drawPath(path, p);
            }
        }

        /** A sun: a circle with eight rays from {@code in} to {@code in + len} beyond it. */
        private void sun(Canvas c, float k, float cx, float cy, float radius, float gap, float len) {
            c.drawCircle(cx * k, cy * k, radius * k, p);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float from = radius + gap, to = from + len;
                c.drawLine((float) (cx + Math.cos(a) * from) * k, (float) (cy + Math.sin(a) * from) * k,
                           (float) (cx + Math.cos(a) * to) * k, (float) (cy + Math.sin(a) * to) * k, p);
            }
        }

        /**
         * The moon in its phase by the Hijri day: its outline, and the lit part filled. Waxing, the lit
         * side is the right one as seen from the north (the left from the south); the terminator is a half
         * ellipse whose width follows the phase.
         */
        private void moon(Canvas c, float k, float cx, float cy, float radius) {
            float x = cx * k, y = cy * k, r = radius * k;
            double phase = Math.max(0, Math.min(29.5, moonDay - 0.5)) / 29.53;   // 0 new, 0.5 full
            boolean waxing = phase < 0.5;
            boolean right = waxing != south;
            float rx = (float) Math.abs(Math.cos(2 * Math.PI * phase)) * r;
            boolean gibbous = phase > 0.25 && phase < 0.75;
            // The lit half disc.
            path.reset();
            RectF disc = new RectF(x - r, y - r, x + r, y + r);
            path.addArc(disc, right ? -90 : 90, 180);
            path.close();
            Path ellipse = new Path();
            ellipse.addOval(new RectF(x - rx, y - r, x + rx, y + r), Path.Direction.CW);
            path.op(ellipse, gibbous ? Path.Op.UNION : Path.Op.DIFFERENCE);
            p.setStyle(Paint.Style.FILL);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            c.drawCircle(x, y, r, p);
        }

        /** A cloud's outline: two puffs over a rounded base, joined into one shape. */
        private void cloud(float k, float x1, float y1, float r1, float x2, float y2, float r2,
                           float left, float right, float top, float bottom) {
            cloud.reset();
            cloud.addCircle(x1 * k, y1 * k, r1 * k, Path.Direction.CW);
            Path puff = new Path();
            puff.addCircle(x2 * k, y2 * k, r2 * k, Path.Direction.CW);
            cloud.op(puff, Path.Op.UNION);
            Path base = new Path();
            float h = bottom - top;
            r.set(left * k, top * k, right * k, bottom * k);
            base.addRoundRect(r, h / 2 * k, h / 2 * k, Path.Direction.CW);
            cloud.op(base, Path.Op.UNION);
        }
    }
}
