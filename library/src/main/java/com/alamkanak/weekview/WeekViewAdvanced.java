package com.alamkanak.weekview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.alamkanak.weekview.WeekViewUtil.daysBetween;
import static com.alamkanak.weekview.WeekViewUtil.getPassedMinutesInDay;
import static com.alamkanak.weekview.WeekViewUtil.isSameDay;
import static com.alamkanak.weekview.WeekViewUtil.today;

/**
 * Created by Raquib-ul-Alam Kanak on 7/21/2014.
 * Website: http://alamkanak.github.io/
 */
public class WeekViewAdvanced extends WeekView {

    //Weekly
    protected float mOriginOfNextWeek;
    protected float mOriginOfPastWeek;
    protected float sizeOfWeekView;

    protected final GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            goToNearestOrigin();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // Check if view is zoomed.
            if (mIsZooming)
                return true;

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
                        mCurrentScrollDirection = Direction.VERTICAL;
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
                    if ((mCurrentOrigin.x - (distanceX * mXScrollingSpeed)) > maxX) {
                        mCurrentOrigin.x = maxX;
                    } else if ((mCurrentOrigin.x - (distanceX * mXScrollingSpeed)) < minX) {
                        mCurrentOrigin.x = minX;
                    } else {
                        mCurrentOrigin.x -= distanceX * mXScrollingSpeed;
                    }
                    ViewCompat.postInvalidateOnAnimation(WeekViewAdvanced.this);
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
            if (mIsZooming)
                return true;

            if ((mCurrentFlingDirection == Direction.LEFT && !mHorizontalFlingEnabled) ||
                    (mCurrentFlingDirection == Direction.RIGHT && !mHorizontalFlingEnabled) ||
                    (mCurrentFlingDirection == Direction.VERTICAL && !mVerticalFlingEnabled)) {
                return true;
            }

            mScroller.forceFinished(true);

            mCurrentFlingDirection = mCurrentScrollDirection;
            switch (mCurrentFlingDirection) {
                case LEFT:
                    mScroller.startScroll((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, (int) mOriginOfNextWeek, 0, 200);
                    break;
                case RIGHT:
                    mScroller.startScroll((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, (int) mOriginOfPastWeek, 0, 200);
                    break;
                case VERTICAL:
                    mScroller.fling((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, 0, (int) velocityY, (int) getXMinLimit(), (int) getXMaxLimit(), (int) getYMinLimit(), (int) getYMaxLimit());
                    break;
                default:
                    break;
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
    };

    public WeekViewAdvanced(Context context) {
        this(context, null);
    }

    public WeekViewAdvanced(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeekViewAdvanced(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    protected void init() {
        resetHomeDate();

        // Scrolling initialization.
        mGestureDetector = new GestureDetectorCompat(mContext, mGestureListener);
        mScroller = new OverScroller(mContext, new FastOutLinearInInterpolator());

        mMinimumFlingVelocity = ViewConfiguration.get(mContext).getScaledMinimumFlingVelocity();
        mScaledTouchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();

        // Measure settings for time column.
        mTimeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTimeTextPaint.setTextAlign(Paint.Align.RIGHT);
        mTimeTextPaint.setTextSize(mTextSize);
        mTimeTextPaint.setColor(mHeaderColumnTextColor);
        Rect rect = new Rect();
        final String exampleTime = (mTimeColumnResolution % 60 != 0) ? "00:00 PM" : "00 PM";
        mTimeTextPaint.getTextBounds(exampleTime, 0, exampleTime.length(), rect);
        mTimeTextWidth = mTimeTextPaint.measureText(exampleTime);
        mTimeTextHeight = rect.height();
        mHeaderMarginBottom = mTimeTextHeight / 2;
        initTextTimeWidth();

        // Measure settings for header row.
        mHeaderTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHeaderTextPaint.setColor(mHeaderColumnTextColor);
        mHeaderTextPaint.setTextAlign(Paint.Align.CENTER);
        mHeaderTextPaint.setTextSize(mTextSize);
        mHeaderTextPaint.getTextBounds(exampleTime, 0, exampleTime.length(), rect);
        mHeaderTextHeight = rect.height();
        mHeaderTextPaint.setTypeface(mTypeface);


        // Prepare header background paint.
        mHeaderBackgroundPaint = new Paint();
        mHeaderBackgroundPaint.setColor(mHeaderRowBackgroundColor);

        // Prepare day background color paint.
        mDayBackgroundPaint = new Paint();
        mDayBackgroundPaint.setColor(mDayBackgroundColor);
        mFutureBackgroundPaint = new Paint();
        mFutureBackgroundPaint.setColor(mFutureBackgroundColor);
        mPastBackgroundPaint = new Paint();
        mPastBackgroundPaint.setColor(mPastBackgroundColor);
        mFutureWeekendBackgroundPaint = new Paint();
        mFutureWeekendBackgroundPaint.setColor(mFutureWeekendBackgroundColor);
        mPastWeekendBackgroundPaint = new Paint();
        mPastWeekendBackgroundPaint.setColor(mPastWeekendBackgroundColor);

        // Prepare hour separator color paint.
        mHourSeparatorPaint = new Paint();
        mHourSeparatorPaint.setStyle(Paint.Style.STROKE);
        mHourSeparatorPaint.setStrokeWidth(mHourSeparatorHeight);
        mHourSeparatorPaint.setColor(mHourSeparatorColor);

        // Prepare the "now" line color paint
        mNowLinePaint = new Paint();
        mNowLinePaint.setStrokeWidth(mNowLineThickness);
        mNowLinePaint.setColor(mNowLineColor);

        // Prepare today background color paint.
        mTodayBackgroundPaint = new Paint();
        mTodayBackgroundPaint.setColor(mTodayBackgroundColor);

        // Prepare today header text color paint.
        mTodayHeaderTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTodayHeaderTextPaint.setTextAlign(Paint.Align.CENTER);
        mTodayHeaderTextPaint.setTextSize(mTextSize);
        mTodayHeaderTextPaint.setTypeface(mTypeface);

        mTodayHeaderTextPaint.setColor(mTodayHeaderTextColor);

        // Prepare event background color.
        mEventBackgroundPaint = new Paint();
        mEventBackgroundPaint.setColor(Color.rgb(174, 208, 238));
        // Prepare empty event background color.
        mNewEventBackgroundPaint = new Paint();
        mNewEventBackgroundPaint.setColor(Color.rgb(60, 147, 217));

        // Prepare header column background color.
        mHeaderColumnBackgroundPaint = new Paint();
        mHeaderColumnBackgroundPaint.setColor(mHeaderColumnBackgroundColor);

        // Prepare event text size and color.
        mEventTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
        mEventTextPaint.setStyle(Paint.Style.FILL);
        mEventTextPaint.setColor(mEventTextColor);
        mEventTextPaint.setTextSize(mEventTextSize);


        //mStartDate = (Calendar) mFirstVisibleDay.clone();

        // Set default event color.
        mDefaultEventColor = Color.parseColor("#9fc6e7");
        // Set default empty event color.
        mNewEventColor = Color.parseColor("#3c93d9");

        mScaleDetector = new ScaleGestureDetector(mContext, new WeekViewGestureListener());
    }

    /////////////////////////////////////////////////////////////////
    //
    //      Functions related to scrolling.
    //
    /////////////////////////////////////////////////////////////////

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mScroller.isFinished() && !mScaleDetector.isInProgress() || mIsZooming) {
            mScaleDetector.onTouchEvent(event);
            boolean val = mGestureDetector.onTouchEvent(event);
            // Check after call of mGestureDetector, so mCurrentFlingDirection and mCurrentScrollDirection are set.
            if (event.getAction() == MotionEvent.ACTION_UP && !mIsZooming && mCurrentFlingDirection == Direction.NONE) {
                if (mCurrentScrollDirection == Direction.RIGHT || mCurrentScrollDirection == Direction.LEFT) {
                    goToNearestOrigin();
                }
                mCurrentScrollDirection = Direction.NONE;
            }
        }

        return mScroller.isFinished() && !mScaleDetector.isInProgress();
    }

    protected void calculateNextOrBeforeOrigin() {
        sizeOfWeekView = (mWidthPerDay + mColumnGap) * getNumberOfVisibleDays();

        mOriginOfPastWeek = mCurrentOrigin.x + sizeOfWeekView;
        if (mOriginOfPastWeek > getXMaxLimit())
            mOriginOfPastWeek = getXMaxLimit();

        mOriginOfNextWeek = mCurrentOrigin.x - sizeOfWeekView;
        if (mOriginOfNextWeek < getXMinLimit())
            mOriginOfNextWeek = getXMinLimit();
    }

    protected void goToNearestOrigin() {
        double leftDays = mCurrentOrigin.x / (mWidthPerDay + mColumnGap);

        if (mCurrentFlingDirection != Direction.NONE) {
            // snap to nearest day
            leftDays = Math.round(leftDays);
        } else if (mCurrentScrollDirection == Direction.LEFT) {
            // snap to last day
            leftDays = Math.floor(leftDays);
        } else if (mCurrentScrollDirection == Direction.RIGHT) {
            // snap to next day
            leftDays = Math.ceil(leftDays);
        } else {
            // snap to nearest day
            leftDays = Math.round(leftDays);
        }

        int nearestOrigin = (int) (mCurrentOrigin.x - leftDays * (mWidthPerDay + mColumnGap));
        boolean mayScrollHorizontal = mCurrentOrigin.x - nearestOrigin < getXMaxLimit()
                && mCurrentOrigin.x - nearestOrigin > getXMinLimit();

        if (mayScrollHorizontal) {
            if (mCurrentScrollDirection == Direction.LEFT) {
                mScroller.startScroll((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, (int) 0, 0);
            } else if (mCurrentScrollDirection == Direction.RIGHT) {
                mScroller.startScroll((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, (int) 0, 0);
            }
            ViewCompat.postInvalidateOnAnimation(WeekViewAdvanced.this);
        }

        if (nearestOrigin != 0 && mayScrollHorizontal) {
            // Stop current animation.
            mScroller.forceFinished(true);
            // Snap to date.
            if (mCurrentScrollDirection == Direction.LEFT) {
                int distance = (int) mOriginOfNextWeek - (int) mCurrentOrigin.x;
                mScroller.startScroll((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, (int) distance, 0, (int) 200);
            } else if (mCurrentScrollDirection == Direction.RIGHT) {
                int distance = (int) mOriginOfNextWeek - (int) mCurrentOrigin.x;
                mScroller.startScroll((int) mCurrentOrigin.x, (int) mCurrentOrigin.y, (int) distance, 0, (int) 200);
            }
            ViewCompat.postInvalidateOnAnimation(WeekViewAdvanced.this);
        }
        // Reset scrolling and fling direction.
        mCurrentScrollDirection = mCurrentFlingDirection = Direction.NONE;
        calculateNextOrBeforeOrigin();
    }


    /////////////////////////////////////////////////////////////////
    //
    //      Public methods.
    //
    /////////////////////////////////////////////////////////////////

    /**
     * Show a specific day on the week view.
     *
     * @param date The date to show.
     */
    public void goToDate(Calendar date) {
        mScroller.forceFinished(true);
        mCurrentScrollDirection = mCurrentFlingDirection = Direction.NONE;

        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);

        if (mAreDimensionsInvalid) {
            mScrollToDay = date;
            return;
        }

        mRefreshEvents = true;

        mCurrentOrigin.x = -daysBetween(mHomeDate, date) * (mWidthPerDay + mColumnGap);
        calculateNextOrBeforeOrigin();
        invalidate();
    }
}