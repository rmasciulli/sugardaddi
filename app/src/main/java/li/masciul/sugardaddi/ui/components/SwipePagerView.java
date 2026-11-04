package li.masciul.sugardaddi.ui.components;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SwipePagerView - pages side by side, one visible at a time, moved by swiping.
 *
 * Generic: knows nothing about what the pages contain. Every direct child is a
 * page. Pair it with PagerDotsView for the page indicator.
 *
 * TOUCH:
 * - A drag is taken over only once the finger has moved past the touch slop AND
 *   more sideways than up or down. Until then, taps reach the pages' buttons and
 *   fields, and vertical moves stay with the scrolling screen.
 * - While dragging, the pages follow the finger and the parent is asked not to
 *   steal the gesture (no vertical scroll in the middle of a swipe).
 * - On release: a flick goes to the next page in its direction; otherwise the
 *   pager settles on the page that shows the most. Past the first or the last
 *   page the movement is damped, then springs back.
 * - A finger landing on a page that is still settling catches it.
 *
 * LAYOUT:
 * - Each page is as wide as the pager (minus its padding) and the pager is as
 *   tall as its tallest page, so it never changes height when swiping. Pages
 *   that are shorter keep their own height, aligned at the top.
 * - Page margins are ignored: put spacing inside the pages (padding).
 * - Right-to-left languages: pages are mirrored, page 0 is on the right.
 * - The pager clips the pages to its own bounds.
 *
 * ACCESSIBILITY AND FOCUS:
 * - Only the current page is visible to TalkBack, and only its views can take
 *   focus, so a hidden field can never hold the cursor or the keyboard.
 * - TalkBack gets "scroll forward" / "scroll backward" actions to change page.
 *
 * Not ViewPager2 on purpose: ViewPager2 cannot size itself to its content
 * (pages must fill a fixed height), it recycles pages that hold live input, and
 * it needs an extra wrapper to coexist with buttons inside a vertical scroll.
 *
 * State: the current page is NOT saved with the view hierarchy. The screen that
 * owns the pager restores it explicitly (its views are often rebuilt after an
 * asynchronous load, too late for the automatic restore).
 */
public class SwipePagerView extends ViewGroup {

    /** Told when the current page changes, as soon as the target page is known. */
    public interface OnPageChangeListener {
        void onPageSelected(int page);
    }

    // ========== FEEL ==========

    /** Share of the finger movement applied past the first or last page. */
    private static final float EDGE_RESISTANCE = 0.35f;
    /** How far past an end the pages can be pulled, as a share of a page. */
    private static final float MAX_OVERSCROLL = 0.25f;
    /** Settle animation, scaled by the distance left to travel. */
    private static final int MIN_SETTLE_MS = 120;
    private static final int MAX_SETTLE_MS = 280;
    /** A flick must also have moved at least this far to turn the page. */
    private static final int MIN_FLING_DISTANCE_DP = 16;

    private final OverScroller scroller;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;
    private final int minFlingDistance;

    private final List<OnPageChangeListener> listeners = new ArrayList<>();

    // ========== STATE ==========

    private int currentPage;

    @Nullable private VelocityTracker velocityTracker;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private float downX;
    private float downY;
    private float lastX;
    /** Scroll position while dragging, kept as a float so damping loses nothing. */
    private float dragScrollX;
    private boolean dragging;

    // ========== CONSTRUCTORS ==========

    public SwipePagerView(@NonNull Context context) {
        this(context, null);
    }

    public SwipePagerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipePagerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        scroller = new OverScroller(context, new DecelerateInterpolator(1.5f));
        ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        minFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        maxFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        minFlingDistance = Math.round(MIN_FLING_DISTANCE_DP * getResources().getDisplayMetrics().density);
    }

    // ========== PUBLIC API ==========

    public int getPageCount() {
        return getChildCount();
    }

    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * Show a page. Out-of-range values are clamped.
     *
     * @param animate slide to it, or jump straight there (also used before the
     *                pager is laid out, when there is nothing to animate)
     */
    public void setCurrentPage(int page, boolean animate) {
        if (getPageCount() == 0) return;
        int target = clampPage(page);
        boolean changed = target != currentPage;
        currentPage = target;

        int targetX = scrollXForPage(target);
        if (animate && isLaidOut() && getPageWidth() > 0) {
            smoothScrollTo(targetX);
        } else {
            scroller.forceFinished(true);
            scrollTo(targetX, 0);
        }

        updatePageAccess();
        if (changed) notifyPageSelected();
    }

    public void addOnPageChangeListener(@NonNull OnPageChangeListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeOnPageChangeListener(@NonNull OnPageChangeListener listener) {
        listeners.remove(listener);
    }

    // ========== MEASURE / LAYOUT ==========

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Meant for match_parent or a fixed width: pages are as wide as the pager.
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? getSuggestedMinimumWidth()
                : MeasureSpec.getSize(widthMeasureSpec);
        int pageWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int pageWidthSpec = MeasureSpec.makeMeasureSpec(pageWidth, MeasureSpec.EXACTLY);
        int verticalPadding = getPaddingTop() + getPaddingBottom();

        int tallest = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View page = getChildAt(i);
            if (page.getVisibility() == GONE) continue;
            int pageHeightSpec = getChildMeasureSpec(heightMeasureSpec, verticalPadding,
                    page.getLayoutParams().height);
            page.measure(pageWidthSpec, pageHeightSpec);
            tallest = Math.max(tallest, page.getMeasuredHeight());
        }

        setMeasuredDimension(width, resolveSize(tallest + verticalPadding, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int pageWidth = getPageWidth();
        for (int i = 0; i < getChildCount(); i++) {
            View page = getChildAt(i);
            if (page.getVisibility() == GONE) continue;
            int left = getPaddingLeft() + slotOf(i) * pageWidth;
            page.layout(left, getPaddingTop(),
                    left + page.getMeasuredWidth(), getPaddingTop() + page.getMeasuredHeight());
        }
        // A new width (rotation, first layout) moves every page: stay on the current one.
        if (!dragging && scroller.isFinished()) {
            scrollTo(scrollXForPage(currentPage), 0);
        }
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updatePageAccess();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        currentPage = getPageCount() == 0 ? 0 : clampPage(currentPage);
        updatePageAccess();
    }

    // ========== TOUCH ==========

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (getPageCount() < 2 || !isEnabled()) return false;

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startGesture(event);
            return dragging;
        }

        trackVelocity(event);
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (!dragging) maybeStartDrag(event);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                endGesture();
                break;
            default:
                break;
        }
        return dragging;
    }

    /**
     * Reached for touches no page view consumed (on a label, between rows), and
     * for every event once a drag has been taken over from the pages.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getPageCount() < 2 || !isEnabled()) return false;

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            // Already started by onInterceptTouchEvent when it caught a settling
            // page: starting again would forget that catch.
            if (!dragging) startGesture(event);
            return true;
        }

        trackVelocity(event);
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) maybeStartDrag(event);
                if (dragging) {
                    int index = event.findPointerIndex(activePointerId);
                    if (index < 0) break;
                    float x = event.getX(index);
                    dragBy(lastX - x);
                    lastX = x;
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                // A second finger takes over from where it lands.
                int index = event.getActionIndex();
                activePointerId = event.getPointerId(index);
                lastX = event.getX(index);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                break;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    settle(true);
                } else if (getScrollX() != scrollXForPage(currentPage)) {
                    // A settling page was caught and released without moving.
                    settle(false);
                } else {
                    performClick();
                }
                endGesture();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) settle(false);
                endGesture();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        // Nothing happens on a tap between the page's views; declared for accessibility.
        return super.performClick();
    }

    private void startGesture(@NonNull MotionEvent event) {
        activePointerId = event.getPointerId(0);
        downX = event.getX();
        downY = event.getY();
        lastX = downX;
        recycleVelocity();
        trackVelocity(event);

        // Catch a page that is still settling: the finger takes it over.
        dragging = !scroller.isFinished();
        if (dragging) {
            scroller.forceFinished(true);
            dragScrollX = getScrollX();
            disallowParentIntercept();
        }
    }

    private void maybeStartDrag(@NonNull MotionEvent event) {
        int index = event.findPointerIndex(activePointerId);
        if (index < 0) return;
        float x = event.getX(index);
        float dx = Math.abs(x - downX);
        float dy = Math.abs(event.getY(index) - downY);
        if (dx > touchSlop && dx > dy) {
            dragging = true;
            lastX = x;   // start from here: no jump by the slop distance
            dragScrollX = getScrollX();
            disallowParentIntercept();
        }
    }

    /** Move the pages by delta pixels, damped past the first or last page. */
    private void dragBy(float delta) {
        int pageWidth = getPageWidth();
        if (pageWidth <= 0) return;
        float min = 0f;
        float max = (getPageCount() - 1) * (float) pageWidth;
        float target = dragScrollX + delta;
        if (target < min || target > max) {
            target = dragScrollX + delta * EDGE_RESISTANCE;
            float limit = pageWidth * MAX_OVERSCROLL;
            target = Math.max(min - limit, Math.min(max + limit, target));
        }
        dragScrollX = target;
        scrollTo(Math.round(target), 0);
    }

    /** Pick the page to land on after a drag, and slide to it. */
    private void settle(boolean allowFling) {
        int pageWidth = getPageWidth();
        if (pageWidth <= 0) return;

        float position = getScrollX() / (float) pageWidth;   // in slots, 1.3 = 30% into slot 1
        float velocity = 0f;
        if (allowFling && velocityTracker != null && activePointerId != MotionEvent.INVALID_POINTER_ID) {
            velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
            velocity = velocityTracker.getXVelocity(activePointerId);
        }

        int slot;
        if (Math.abs(velocity) > minFlingVelocity && Math.abs(lastX - downX) > minFlingDistance) {
            // Finger moving left (negative velocity) brings the next slot in from the right.
            slot = velocity < 0 ? (int) Math.ceil(position) : (int) Math.floor(position);
        } else {
            slot = Math.round(position);
        }
        slot = Math.max(0, Math.min(getPageCount() - 1, slot));
        setCurrentPage(pageAtSlot(slot), true);
    }

    private void onSecondaryPointerUp(@NonNull MotionEvent event) {
        int index = event.getActionIndex();
        if (event.getPointerId(index) != activePointerId) return;
        // The tracked finger left: follow another one.
        int newIndex = index == 0 ? 1 : 0;
        activePointerId = event.getPointerId(newIndex);
        lastX = event.getX(newIndex);
        if (velocityTracker != null) velocityTracker.clear();
    }

    private void endGesture() {
        dragging = false;
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        recycleVelocity();
    }

    private void trackVelocity(@NonNull MotionEvent event) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);
    }

    private void recycleVelocity() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void disallowParentIntercept() {
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    // ========== ANIMATION ==========

    private void smoothScrollTo(int targetX) {
        int startX = getScrollX();
        int dx = targetX - startX;
        if (dx == 0) {
            scroller.forceFinished(true);
            return;
        }
        float share = Math.min(1f, Math.abs(dx) / (float) Math.max(1, getPageWidth()));
        int duration = Math.round(MIN_SETTLE_MS + (MAX_SETTLE_MS - MIN_SETTLE_MS) * share);
        scroller.startScroll(startX, 0, dx, 0, duration);
        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Never leave the pages between two positions.
        scroller.forceFinished(true);
        endGesture();
        scrollTo(scrollXForPage(currentPage), 0);
        super.onDetachedFromWindow();
    }

    // ========== ACCESSIBILITY / FOCUS ==========

    /**
     * Hide every page but the current one from TalkBack and from focus. A hidden
     * page losing focus also drops its cursor (and with it the keyboard target).
     */
    private void updatePageAccess() {
        for (int i = 0; i < getChildCount(); i++) {
            View page = getChildAt(i);
            boolean shown = i == currentPage;
            if (!shown && page.hasFocus()) {
                View focused = page.findFocus();
                if (focused != null) focused.clearFocus();
            }
            page.setImportantForAccessibility(shown
                    ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if (page instanceof ViewGroup) {
                ((ViewGroup) page).setDescendantFocusability(shown
                        ? FOCUS_BEFORE_DESCENDANTS
                        : FOCUS_BLOCK_DESCENDANTS);
            }
        }
    }

    private void notifyPageSelected() {
        for (OnPageChangeListener listener : new ArrayList<>(listeners)) {
            listener.onPageSelected(currentPage);
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        // Lets TalkBack treat it as horizontally scrollable content.
        return HorizontalScrollView.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setScrollable(getPageCount() > 1);
        if (currentPage < getPageCount() - 1) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        }
        if (currentPage > 0) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setScrollable(getPageCount() > 1);
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            event.setItemCount(getPageCount());
            event.setFromIndex(currentPage);
            event.setToIndex(currentPage);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && currentPage < getPageCount() - 1) {
            setCurrentPage(currentPage + 1, true);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && currentPage > 0) {
            setCurrentPage(currentPage - 1, true);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    // ========== GEOMETRY ==========

    private int getPageWidth() {
        return Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
    }

    private boolean isRtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    /** Horizontal slot of a page: the same as the page in LTR, mirrored in RTL. */
    private int slotOf(int page) {
        return isRtl() ? getPageCount() - 1 - page : page;
    }

    /** Inverse of slotOf (the mirroring is its own inverse). */
    private int pageAtSlot(int slot) {
        return slotOf(slot);
    }

    private int scrollXForPage(int page) {
        return slotOf(page) * getPageWidth();
    }

    private int clampPage(int page) {
        return Math.max(0, Math.min(getPageCount() - 1, page));
    }
}
