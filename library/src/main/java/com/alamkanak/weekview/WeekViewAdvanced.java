package com.alamkanak.weekview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static com.alamkanak.weekview.WeekView.Direction.VERTICAL;

/**
 * Created by Raquib-ul-Alam Kanak on 7/21/2014.
 * Website: http://alamkanak.github.io/
 */
public class WeekViewAdvanced extends WeekView {

    //Weekly
    protected float startOriginForScale = 0;
    protected float startOriginForScroll = 0;

    protected float distanceDone = 0;
    protected float sizeOfWeekView;
    protected float distanceMin;
    protected boolean isScaling = false;

    protected int offsetValueToSecureScreen = 9;

    protected FinishedLoadingListener mFinishedLoadingListener;
    protected Boolean firstDrawDone = false;

    protected class NewGestureDetector extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            startOriginForScale = mCurrentOrigin.x;
            isScaling = false;
            distanceDone = 0;
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // Check if view is zoomed.
            if (mIsZooming)
                return true;

            if (e2.getPointerCount() == 2) {
                isScaling = true;
                return false;
            } else if (e2.getPointerCount() == 1 && isScaling) {
                return false;
            }

            switch (mCurrentScrollDirection) {
                case NONE: {
                    // Allow scrolling only in one direction.
                    if (Math.abs(distanceX) > Math.abs(distanceY)) {
                        if (distanceX > 0) {
                            mCurrentScrollDirection = Direction.LEFT;
                        } else {
                            mCurrentScrollDirection = Direction.RIGHT;
                        }
                    } else {
                        mCurrentScrollDirection = VERTICAL;
                    }
                    break;
                }
                case LEFT: {
                    // Change direction if there was enough change.
                    if (Math.abs(distanceX) > Math.abs(distanceY) && (distanceX < -mScaledTouchSlop)) {
                        mCurrentScrollDirection = Direction.RIGHT;
                    }
                    break;
                }
                case RIGHT: {
                    // Change direction if there was enough change.
                    if (Math.abs(distanceX) > Math.abs(distanceY) && (distanceX > mScaledTouchSlop)) {
                        mCurrentScrollDirection = Direction.LEFT;
                    }
                    break;
                }
                default:
                    break;
            }

            float minX = getXMinLimit();
            float maxX = getXMaxLimit();

            // Calculate the new origin after scroll.
            switch (mCurrentScrollDirection) {
                case LEFT:
                case RIGHT:
                    if (e2.getX() < 0) {
                        distanceDone = e2.getX() - e1.getX();
                    } else {
                        distanceDone = e1.getX() - e2.getX();
                    }

                    if (!isScaling) {
                        if ((mCurrentOrigin.x - (distanceX * mXScrollingSpeed)) > maxX) {
                            mCurrentOrigin.x = maxX;
                        } else if ((mCurrentOrigin.x - (distanceX * mXScrollingSpeed)) < minX) {
                            mCurrentOrigin.x = minX;
                        } else {
                            mCurrentOrigin.x -= distanceX * mXScrollingSpeed;
                        }
                        ViewCompat.postInvalidateOnAnimation(WeekViewAdvanced.this);
                    }

                    break;
                case VERTICAL:

                    float minY = getYMinLimit();
                    float maxY = getYMaxLimit();
                    if ((mCurrentOrigin.y - (distanceY)) > maxY) {
                        mCurrentOrigin.y = maxY;
                    } else if ((mCurrentOrigin.y - (distanceY)) < minY) {
                        mCurrentOrigin.y = minY;
                    } else {
                        mCurrentOrigin.y -= distanceY;
                    }
                    ViewCompat.postInvalidateOnAnimation(WeekViewAdvanced.this);
                    break;
                default:
                    break;
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mIsZooming) {
                return false;
            }

            if (mCurrentFlingDirection == VERTICAL && !mVerticalFlingEnabled) {
                return true;
            }

            mScroller.forceFinished(true);

            mCurrentFlingDirection = mCurrentScrollDirection;
            if (mCurrentFlingDirection == VERTICAL) {
                mScroller.fling((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, 0, (int) velocityY, (int) getXMinLimit(), (int) getXMaxLimit(), (int) getYMinLimit(), (int) getYMaxLimit());
            }

            ViewCompat.postInvalidateOnAnimation(WeekViewAdvanced.this);
            return true;
        }


        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {

            // If the tap was on an event then trigger the callback.
            if (mEventRects != null && mEventClickListener != null) {
                List<EventRect> reversedEventRects = mEventRects;
                Collections.reverse(reversedEventRects);
                for (EventRect eventRect : reversedEventRects) {
                    if (eventRect.event.getId() != mNewEventId && eventRect.rectF != null && e.getX() > eventRect.rectF.left && e.getX() < eventRect.rectF.right && e.getY() > eventRect.rectF.top && e.getY() < eventRect.rectF.bottom) {
                        mEventClickListener.onEventClick(eventRect.originalEvent, eventRect.rectF);
                        playSoundEffect(SoundEffectConstants.CLICK);
                        return super.onSingleTapConfirmed(e);
                    }
                }
            }

            // If the tap was on add new Event space, then trigger the callback
            if (mAddEventClickListener != null && mNewEventRect != null && mNewEventRect.rectF != null && e.getX() > mNewEventRect.rectF.left && e.getX() < mNewEventRect.rectF.right && e.getY() > mNewEventRect.rectF.top && e.getY() < mNewEventRect.rectF.bottom) {
                mAddEventClickListener.onAddEventClicked(mNewEventRect.event.getStartTime(), mNewEventRect.event.getEndTime());
                return super.onSingleTapConfirmed(e);
            }

            // If the tap was on an empty space, then trigger the callback.
            if ((mEmptyViewClickListener != null || mAddEventClickListener != null) && e.getX() > mHeaderColumnWidth && e.getY() > (mHeaderHeight + mHeaderRowPadding * 2 + mHeaderMarginBottom)) {
                Calendar selectedTime = getTimeFromPoint(e.getX(), e.getY());
                List<EventRect> tempEventRects = new ArrayList<>(mEventRects);
                mEventRects = new ArrayList<EventRect>();
                if (selectedTime != null) {
                    if (mNewEventRect != null) {
                        tempEventRects.remove(mNewEventRect);
                        mNewEventRect = null;
                    }

                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (mEmptyViewClickListener != null)
                        mEmptyViewClickListener.onEmptyViewClicked(selectedTime);

                    if (mAddEventClickListener != null) {
                        //round selectedTime to resolution
                        int unroundedMinutes = selectedTime.get(Calendar.MINUTE);
                        int mod = unroundedMinutes % mNewEventTimeResolutionInMinutes;
                        selectedTime.add(Calendar.MINUTE, mod < Math.ceil(mNewEventTimeResolutionInMinutes / 2) ? -mod : (mNewEventTimeResolutionInMinutes - mod));

                        Calendar endTime = (Calendar) selectedTime.clone();
                        endTime.add(Calendar.MINUTE, Math.min(mNewEventLengthInMinutes, (24 - selectedTime.get(Calendar.HOUR_OF_DAY)) * 60 - selectedTime.get(Calendar.MINUTE)));
                        WeekViewEvent newEvent = new WeekViewEvent(mNewEventId, "", null, selectedTime, endTime);

                        int marginTop = mHourHeight * mMinTime;
                        float top = selectedTime.get(Calendar.HOUR_OF_DAY) * 60;
                        top = mHourHeight * top / 60 + mCurrentOrigin.y + mHeaderHeight + mHeaderRowPadding * 2 + mHeaderMarginBottom + mTimeTextHeight / 2 + mEventMarginVertical - marginTop;
                        float bottom = endTime.get(Calendar.HOUR_OF_DAY) * 60;
                        bottom = mHourHeight * bottom / 60 + mCurrentOrigin.y + mHeaderHeight + mHeaderRowPadding * 2 + mHeaderMarginBottom + mTimeTextHeight / 2 - mEventMarginVertical - marginTop;

                        // Calculate left and right.
                        float left = 0;
                        float right = left + mWidthPerDay;
                        // Draw the event and the event name on top of it.
                        if (left < right &&
                                left < getWidth() &&
                                top < getHeight() &&
                                right > mHeaderColumnWidth &&
                                bottom > 0
                                ) {
                            RectF dayRectF = new RectF(left, top, right, bottom);
                            newEvent.setColor(mNewEventColor);
                            mNewEventRect = new EventRect(newEvent, newEvent, dayRectF);
                            tempEventRects.add(mNewEventRect);
                        }
                    }
                }
                computePositionOfEvents(tempEventRects);
                invalidate();
            }
            return super.onSingleTapConfirmed(e);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            super.onLongPress(e);

            if (mEventLongPressListener != null && mEventRects != null) {
                List<EventRect> reversedEventRects = mEventRects;
                Collections.reverse(reversedEventRects);
                for (EventRect event : reversedEventRects) {
                    if (event.rectF != null && e.getX() > event.rectF.left && e.getX() < event.rectF.right && e.getY() > event.rectF.top && e.getY() < event.rectF.bottom) {
                        mEventLongPressListener.onEventLongPress(event.originalEvent, event.rectF);
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        return;
                    }
                }
            }

            // If the tap was on in an empty space, then trigger the callback.
            if (mEmptyViewLongPressListener != null && e.getX() > mHeaderColumnWidth && e.getY() > (mHeaderHeight + mHeaderRowPadding * 2 + mHeaderMarginBottom)) {
                Calendar selectedTime = getTimeFromPoint(e.getX(), e.getY());
                if (selectedTime != null) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    mEmptyViewLongPressListener.onEmptyViewLongPress(selectedTime);
                }
            }
        }

    }

    public WeekViewAdvanced(Context context) {
        this(context, null);
    }

    public WeekViewAdvanced(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeekViewAdvanced(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void init() {
        super.init();
        mGestureDetector = new GestureDetectorCompat(mContext, new NewGestureDetector());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    /////////////////////////////////////////////////////////////////
    //
    //      Functions related to scrolling.
    //
    /////////////////////////////////////////////////////////////////

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        sizeOfWeekView = (mWidthPerDay + mColumnGap) * getNumberOfVisibleDays();
        distanceMin = sizeOfWeekView / offsetValueToSecureScreen;

        mScaleDetector.onTouchEvent(event);
        boolean val = mGestureDetector.onTouchEvent(event);

        // Check after call of mGestureDetector, so mCurrentFlingDirection and mCurrentScrollDirection are set.
        if (event.getAction() == MotionEvent.ACTION_UP && !mIsZooming && mCurrentFlingDirection == Direction.NONE) {
            if (mCurrentScrollDirection == Direction.RIGHT || mCurrentScrollDirection == Direction.LEFT) {
                goToNearestOrigin();
            }
        }

        return val;
    }

    @Override
    protected void goToNearestOrigin() {

        float beforeScroll = startOriginForScroll;
        boolean isPassed = false;

        if (distanceDone > distanceMin || distanceDone < -distanceMin) {

            if (mCurrentScrollDirection == Direction.LEFT) {
                // snap to last day
                startOriginForScroll -= sizeOfWeekView;
                isPassed = true;
            } else if (mCurrentScrollDirection == Direction.RIGHT) {
                // snap to next day
                startOriginForScroll += sizeOfWeekView;
                isPassed = true;
            }

            boolean mayScrollHorizontal = beforeScroll - startOriginForScroll < getXMaxLimit()
                    && mCurrentOrigin.x - startOriginForScroll > getXMinLimit();

            // Stop current animation.
            mScroller.forceFinished(true);

            if (isPassed && mayScrollHorizontal) {
                // Snap to date.
                if (mCurrentScrollDirection == Direction.LEFT) {
                    mScroller.startScroll((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, (int) ((beforeScroll - mCurrentOrigin.x) - sizeOfWeekView), 0, 200);
                } else if (mCurrentScrollDirection == Direction.RIGHT) {
                    mScroller.startScroll((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, (int) (sizeOfWeekView - (mCurrentOrigin.x - beforeScroll)), 0, 200);
                }
                ViewCompat.postInvalidateOnAnimation(WeekViewAdvanced.this);
            }
            // Reset scrolling and fling direction.
            mCurrentScrollDirection = mCurrentFlingDirection = Direction.NONE;
        } else {
            mScroller.forceFinished(true);
            if (mCurrentScrollDirection == Direction.LEFT) {
                mScroller.startScroll((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, (int) beforeScroll - (int) mCurrentOrigin.x, 0, 200);
            } else if (mCurrentScrollDirection == Direction.RIGHT) {
                mScroller.startScroll((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, (int) beforeScroll - (int) mCurrentOrigin.x, 0, 200);
            }
            ViewCompat.postInvalidateOnAnimation(WeekViewAdvanced.this);

            // Reset scrolling and fling direction.
            mCurrentScrollDirection = mCurrentFlingDirection = Direction.NONE;
        }

    }

    /**
     * Show a specific day on the week view.
     *
     * @param date The date to show.
     */
    @Override
    public void goToDate(Calendar date) {
        super.goToDate(date);
        startOriginForScroll = mCurrentOrigin.x;
    }

    /////////////////////////////////////////////////////////////////
    //
    //      Others Functions.
    //
    /////////////////////////////////////////////////////////////////

    /**
     * Be careful: The higher the value is, the smaller the area will be
     *
     * @param value
     */
    public void setDivideNumberOfOffsetToSecureScreen(int value) {
        offsetValueToSecureScreen = value;
    }

    /**
     * Return the height of the weekview without the date part and without the hour part
     *
     * @return
     */
    public float getWeekViewHeightWithoutDateNorHour() {
        return getHeight() - (mHeaderHeight + mHeaderRowPadding * 2 + mHeaderMarginBottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!firstDrawDone) {
            firstDrawDone = true;
            if (mFinishedLoadingListener != null)
                mFinishedLoadingListener.onFinishedLoading();
        }
    }

    /////////////////////////////////////////////////////////////////
    //
    //      Interfaces
    //
    /////////////////////////////////////////////////////////////////

    public interface FinishedLoadingListener {
        /**
         * Triggered when view finished loading
         */
        void onFinishedLoading();
    }

    public void setFinishedLoadingListener(FinishedLoadingListener finishedLoadingListener) {
        this.mFinishedLoadingListener = finishedLoadingListener;
    }

    public FinishedLoadingListener getFinishedLoadingListener() {
        return mFinishedLoadingListener;
    }
}