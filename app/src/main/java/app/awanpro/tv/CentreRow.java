package app.awanpro.tv;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * The screensaver's top row. The clock at the start side, the weather at the end side and, between
 * them, a column of a set width centred on the screen itself (under it the city and the day are centred too), not
 * in the room left between a wide clock and a narrow weather. Only when the clock is too wide for that ("12:08 PM")
 * does the column move aside, just enough to keep a gap. All three are centred vertically.
 */
final class CentreRow extends ViewGroup {
    private final View start, middle, end;
    private final int middleWidth, gap;

    CentreRow(Context c, View start, View middle, View end, int middleWidth, int gap) {
        super(c);
        this.start = start;
        this.middle = middle;
        this.end = end;
        this.middleWidth = middleWidth;
        this.gap = gap;
        addView(start, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(middle, new LayoutParams(middleWidth, LayoutParams.WRAP_CONTENT));
        addView(end, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int any = MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST);
        int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        start.measure(any, free);
        end.measure(any, free);
        middle.measure(MeasureSpec.makeMeasureSpec(Math.min(middleWidth, w), MeasureSpec.EXACTLY), free);
        int h = Math.max(start.getMeasuredHeight(), Math.max(middle.getMeasuredHeight(), end.getMeasuredHeight()));
        setMeasuredDimension(w, resolveSize(h, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int sw = start.getMeasuredWidth(), ew = end.getMeasuredWidth(), mw = middle.getMeasuredWidth();
        // In left-to-right terms: what is on the left and the right, and where the middle may go between them.
        int leftW = rtl ? ew : sw, rightW = rtl ? sw : ew;
        int min = leftW + gap, max = w - rightW - gap - mw;
        int x = (w - mw) / 2;
        if (min > max) x = (min + max) / 2;
        else x = Math.max(min, Math.min(max, x));
        View left = rtl ? end : start, right = rtl ? start : end;
        put(left, 0, h);
        put(right, w - right.getMeasuredWidth(), h);
        put(middle, x, h);
    }

    private static void put(View v, int x, int h) {
        int y = (h - v.getMeasuredHeight()) / 2;
        v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
    }
}
