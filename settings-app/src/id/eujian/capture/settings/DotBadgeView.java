package id.eujian.capture.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Settings preview matching the in-exam dot badge geometry. */
public final class DotBadgeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] values = {1, 5};
    private int verticalDp = 2, horizontalDp = 8, sizeSp = 12;
    public DotBadgeView(Context context) { super(context); paint.setColor(Color.WHITE); }
    public void setDotColor(int color) { paint.setColor(color); invalidate(); }
    public void configure(int[] choices, int vertical, int horizontal, int textSize) {
        values = choices.clone(); verticalDp = vertical; horizontalDp = horizontal; sizeSp = textSize;
        requestLayout(); invalidate();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int diameter() { return Math.max(dp(3), dp(sizeSp * .34f)); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int diameter = diameter(), count = Math.max(1, values.length), max = 1;
        for (int value : values) max = Math.max(max, value);
        setMeasuredDimension(dp(14) + count * diameter + (count - 1) * dp(horizontalDp),
                dp(8) + max * diameter + (max - 1) * dp(verticalDp));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = diameter() / 2f, left = dp(7) + radius, top = dp(4) + radius;
        for (int column = 0; column < values.length; column++) {
            float x = left + column * (diameter() + dp(horizontalDp));
            for (int row = 0; row < values[column]; row++)
                canvas.drawCircle(x, top + row * (diameter() + dp(verticalDp)), radius, paint);
        }
    }
}
