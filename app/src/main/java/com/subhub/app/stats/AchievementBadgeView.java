package com.subhub.app.stats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

/**
 * Small, reusable renderer for the illustrated achievement medallions.
 *
 * <p>The source artwork is kept in {@code drawable-nodpi} so that the view controls the final
 * size. Bitmap filtering is enabled for the occasional fractional scale, while the destination
 * bounds keep the medallion square and centered inside the view.</p>
 */
public final class AchievementBadgeView extends View {
    private static final int DEFAULT_SIZE_DP = 48;
    private static final String QUESTION_MARK = "?";
    private static final float LOCKED_LUMA = 0.66f;
    private static final float LOCKED_ALPHA = 0.72f;
    private static final ColorFilter LOCKED_FILTER = createLockedFilter();

    private final float density;
    private final Rect destination = new Rect();
    private final Paint questionBackdropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint questionPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint.FontMetrics questionFontMetrics = new Paint.FontMetrics();

    private Drawable badgeDrawable;
    private boolean concealed;
    private float questionCenterX;
    private float questionCenterY;
    private float questionRadius;
    private float questionBaseline;

    public AchievementBadgeView(Context context) {
        this(context, null);
    }

    public AchievementBadgeView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AchievementBadgeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;

        questionBackdropPaint.setColor(Color.argb(150, 0, 0, 0));
        questionPaint.setColor(Color.WHITE);
        questionPaint.setTextAlign(Paint.Align.CENTER);
        questionPaint.setTypeface(Typeface.DEFAULT_BOLD);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /**
     * Binds one medallion and its presentation state.
     *
     * @param drawableRes the {@code achievement_badge_*} drawable resource
     * @param unlocked whether the achievement is earned; concealed achievements remain muted
     * @param concealed whether the art should be shown as an undisclosed achievement
     * @param contentDescription the spoken description for accessibility services
     */
    public void bind(int drawableRes, boolean unlocked, boolean concealed,
            CharSequence contentDescription) {
        this.concealed = concealed;
        setContentDescription(contentDescription);

        if (drawableRes == 0) {
            badgeDrawable = null;
            destination.setEmpty();
            invalidate();
            return;
        }

        Drawable drawable = getResources().getDrawable(drawableRes, getContext().getTheme());
        badgeDrawable = drawable.mutate();
        if (badgeDrawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) badgeDrawable;
            bitmapDrawable.setAntiAlias(true);
            bitmapDrawable.setDither(true);
            bitmapDrawable.setFilterBitmap(true);
        }
        badgeDrawable.setColorFilter(unlocked && !concealed ? null : LOCKED_FILTER);
        updateGeometry(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int preferredSize = Math.max(getSuggestedMinimumWidth(), dp(DEFAULT_SIZE_DP));
        int measuredWidth = resolveSize(preferredSize, widthMeasureSpec);
        int measuredHeight = resolveSize(preferredSize, heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateGeometry(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (badgeDrawable == null || destination.isEmpty()) return;

        badgeDrawable.draw(canvas);
        if (!concealed) return;

        canvas.drawCircle(questionCenterX, questionCenterY, questionRadius, questionBackdropPaint);
        canvas.drawText(QUESTION_MARK, questionCenterX, questionBaseline, questionPaint);
    }

    private void updateGeometry(int width, int height) {
        int availableWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int availableHeight = Math.max(0, height - getPaddingTop() - getPaddingBottom());
        int size = Math.min(availableWidth, availableHeight);
        if (size <= 0) {
            destination.setEmpty();
            questionCenterX = 0f;
            questionCenterY = 0f;
            questionRadius = 0f;
            questionBaseline = 0f;
            return;
        }

        int left = getPaddingLeft() + (availableWidth - size) / 2;
        int top = getPaddingTop() + (availableHeight - size) / 2;
        destination.set(left, top, left + size, top + size);
        badgeDrawableBounds();

        questionCenterX = destination.exactCenterX();
        questionCenterY = destination.exactCenterY();
        questionRadius = size * 0.23f;
        questionPaint.setTextSize(size * 0.43f);
        questionPaint.getFontMetrics(questionFontMetrics);
        questionBaseline = questionCenterY
                - (questionFontMetrics.ascent + questionFontMetrics.descent) * 0.5f;
    }

    private void badgeDrawableBounds() {
        if (badgeDrawable != null) badgeDrawable.setBounds(destination);
    }

    private int dp(int value) {
        return (int) (value * density + 0.5f);
    }

    private static ColorFilter createLockedFilter() {
        float luma = LOCKED_LUMA;
        return new ColorMatrixColorFilter(new ColorMatrix(new float[] {
                0.2126f * luma, 0.7152f * luma, 0.0722f * luma, 0f, 0f,
                0.2126f * luma, 0.7152f * luma, 0.0722f * luma, 0f, 0f,
                0.2126f * luma, 0.7152f * luma, 0.0722f * luma, 0f, 0f,
                0f, 0f, 0f, LOCKED_ALPHA, 0f
        }));
    }
}
