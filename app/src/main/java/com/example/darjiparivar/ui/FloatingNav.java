package com.example.darjiparivar.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.example.darjiparivar.R;

/**
 * Floating "glass" bottom navigation. On Android 12+ it blurs the screen content behind it,
 * with a stronger blur and darker tint inside the active-tab bubble. Tabs have a fixed width,
 * so switching never re-lays out the bar: the bubble just glides to the selected tab.
 */
public class FloatingNav extends FrameLayout {

    public interface Listener {
        void onTabSelected(int index);
    }

    private static final int[] ICONS = {
            R.drawable.ic_nav_discover, R.drawable.ic_nav_matches,
            R.drawable.ic_nav_inbox, R.drawable.ic_nav_account };
    private static final int[] LABELS = {
            R.string.nav_discover, R.string.nav_matches, R.string.nav_inbox, R.string.nav_account };

    private final View indicator;
    private final LinearLayout row;
    private final ImageView[] icons = new ImageView[ICONS.length];
    private final TextView[] labels = new TextView[ICONS.length];
    private final int activeColor, inactiveColor;
    private final float radius;
    private final GradientDrawable glass = new GradientDrawable();
    private Backdrop backdrop;
    private Listener listener;
    private int selected;

    public FloatingNav(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        int pad = dp(6);
        setPadding(pad, pad, pad, pad);
        radius = dp(34);
        glass.setCornerRadius(radius);
        glass.setColor(ContextCompat.getColor(context, R.color.nav_bg_solid));
        glass.setStroke(dp(1), ContextCompat.getColor(context, R.color.nav_stroke));
        activeColor = ContextCompat.getColor(context, R.color.nav_active);
        inactiveColor = ContextCompat.getColor(context, R.color.ink_muted);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);

        indicator = new View(context);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(ContextCompat.getColor(context, R.color.nav_pill));
        pill.setStroke(dp(1), ContextCompat.getColor(context, R.color.nav_pill_stroke));
        pill.setCornerRadius(dp(28));
        indicator.setBackground(pill);
        addView(indicator, new LayoutParams(0, LayoutParams.MATCH_PARENT));

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        for (int i = 0; i < ICONS.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setContentDescription(context.getString(LABELS[i]));
            item.setOnClickListener(v -> select(index, true));

            ImageView icon = new ImageView(context);
            icon.setImageResource(ICONS[i]);
            item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

            TextView label = new TextView(context);
            label.setText(LABELS[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setMaxLines(1);
            label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(2);
            item.addView(label, lp);

            row.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            icons[i] = icon;
            labels[i] = label;
        }
        paint(0);
    }

    /**
     * The view whose content scrolls behind the bar (a sibling, not a parent). On Android 12+
     * the bar blurs it; elsewhere the bar stays a solid frosted colour.
     */
    public void setBlurSource(View source, int backgroundColor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        backdrop = new Backdrop(source, backgroundColor, dp(22), dp(44));
        glass.setColor(ContextCompat.getColor(getContext(), R.color.nav_bg));
        // Redraw the blur whenever the content behind it draws (scrolling, swiping...).
        final boolean[] ownFrame = {false};
        source.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // Skip the frame our own invalidate() schedules, so we don't redraw forever.
                if (ownFrame[0]) {
                    ownFrame[0] = false;
                } else {
                    ownFrame[0] = true;
                    invalidate();
                }
                return true;
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int getSelected() {
        return selected;
    }

    public void select(int index, boolean fromUser) {
        if (index == selected) return;
        selected = index;
        paint(index);
        indicator.animate()
                .translationX(tabX(index))
                .setInterpolator(new OvershootInterpolator(0.9f))
                .setDuration(fromUser ? 300 : 0)
                .setUpdateListener(a -> invalidate())
                .start();
        if (fromUser && listener != null) listener.onTabSelected(index);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        glass.setBounds(0, 0, w, h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // The bubble is one tab wide; position it here rather than via LayoutParams.
        int l = getPaddingLeft();
        indicator.layout(l, getPaddingTop(), l + tabWidth(), getHeight() - getPaddingBottom());
        if (changed) indicator.setTranslationX(tabX(selected));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (backdrop != null && canvas.isHardwareAccelerated()) {
            backdrop.draw(canvas, this, indicator);
        }
        glass.draw(canvas); // Tint + hairline border over the blur.
        super.dispatchDraw(canvas); // Active bubble, then icons and labels.
    }

    private int tabWidth() {
        return (getWidth() - getPaddingLeft() - getPaddingRight()) / ICONS.length;
    }

    private float tabX(int index) {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        return (rtl ? ICONS.length - 1 - index : index) * tabWidth();
    }

    private void paint(int index) {
        for (int i = 0; i < ICONS.length; i++) {
            int color = i == index ? activeColor : inactiveColor;
            icons[i].setColorFilter(color);
            labels[i].setTextColor(color);
            row.getChildAt(i).setSelected(i == index);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * Backdrop blur with RenderNodes: the content behind the bar is recorded once per frame,
     * then drawn through two blur effects — a soft one for the bar, a strong one for the bubble.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private static final class Backdrop {
        private final View source;
        private final int background;
        private final RenderNode content = new RenderNode("navContent");
        private final RenderNode soft = new RenderNode("navBlur");
        private final RenderNode strong = new RenderNode("navBlurStrong");
        private final Path path = new Path();
        private final int[] navPos = new int[2], sourcePos = new int[2];

        Backdrop(View source, int background, float softRadius, float strongRadius) {
            this.source = source;
            this.background = background;
            soft.setRenderEffect(RenderEffect.createBlurEffect(softRadius, softRadius, Shader.TileMode.CLAMP));
            strong.setRenderEffect(RenderEffect.createBlurEffect(strongRadius, strongRadius, Shader.TileMode.CLAMP));
        }

        void draw(Canvas canvas, FloatingNav nav, View bubble) {
            int w = nav.getWidth(), h = nav.getHeight();
            if (w == 0 || h == 0) return;
            nav.getLocationInWindow(navPos);
            source.getLocationInWindow(sourcePos);

            content.setPosition(0, 0, w, h);
            RecordingCanvas rc = content.beginRecording();
            rc.drawColor(background);
            rc.translate(sourcePos[0] - navPos[0], sourcePos[1] - navPos[1]);
            source.draw(rc);
            content.endRecording();

            record(soft, w, h);
            record(strong, w, h);

            canvas.save();
            path.reset();
            path.addRoundRect(0, 0, w, h, nav.radius, nav.radius, Path.Direction.CW);
            canvas.clipPath(path);
            canvas.drawRenderNode(soft);
            // Stronger blur inside the active bubble.
            path.reset();
            float left = bubble.getX(), top = bubble.getY();
            float r = bubble.getHeight() / 2f;
            path.addRoundRect(left, top, left + bubble.getWidth(), top + bubble.getHeight(), r, r, Path.Direction.CW);
            canvas.clipPath(path);
            canvas.drawRenderNode(strong);
            canvas.restore();
        }

        private void record(RenderNode node, int w, int h) {
            node.setPosition(0, 0, w, h);
            RecordingCanvas rc = node.beginRecording();
            rc.drawRenderNode(content);
            node.endRecording();
        }
    }
}
