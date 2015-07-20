package ru.gdo.android.library.foldinglayout;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;

import ru.gdo.android.library.foldinglayout.interfaces.IAnimationListener;
import ru.gdo.android.library.foldinglayout.interfaces.IAnimationNotifier;

/**
 * @author Danil Gudkov <d_n_l@mail.ru>
 * @copyrights, 2015
 * @since 17.07.15.
 */

public class FoldingPanelLayout extends LinearLayout
        implements
        Animator.AnimatorListener,
        View.OnClickListener,
        IAnimationListener {

    public enum PanelState {
        EXPANDED,
        COLLAPSED
    }

    private static final int START_ANIMATION_VALUE = 1;
    private static final int END_ANIMATION_VALUE = 100;
    private static final int ANIMATION_LENGTH = END_ANIMATION_VALUE - START_ANIMATION_VALUE + 1;

    /**
     * Default attributes for layout
     */
    private static final int[] DEFAULT_ATTRS = new int[]{
            android.R.attr.gravity
    };

    /**
     * Default initial state for the component
     */
    private static PanelState DEFAULT_PANEL_STATE = PanelState.COLLAPSED;

    /**
     * Default fold numbers
     */
    private static final int DEFAULT_FOLD_NUMBERS = 4;

    /**
     * Default animator durationTime
     */
    private static final int DEFAULT_DURATION_TIME = 750;

    /**
     * Default present exteranl animator
     */
    private static final boolean DEFAULT_HAS_EXTERNAL_ANIMATOR = false;

    /**
     * Default test animation mode
     */
    private static final boolean DEFAULT_TEST_MODE = false;

    /**
     * Initial state for the component
     */
    private PanelState mPanelState = DEFAULT_PANEL_STATE;

    /**
     * How far the panel is offset from its expanded position.
     * range [0, 1] where 0 = collapsed, 1 = expanded.
     */
    private float mMainPanelWeight = -1;

    /**
     * Fold numbers
     */
    private int mNumberOfFolds = DEFAULT_FOLD_NUMBERS;

    private boolean mHasExternalAnimator = DEFAULT_HAS_EXTERNAL_ANIMATOR;

    private boolean mTestingMode = DEFAULT_TEST_MODE;


    private View mMainView;

    private View mSlidingView;

    protected FoldingLayout mFoldingNavigationLayout = null;

    private IAnimationNotifier mAnimatorView;
    private View mAnimatorChildView;

    /**
     * Animator
     */
    private ValueAnimator mAnimator;

    /**
     * Animator durationTime
     */
    private int mDuration_Time = DEFAULT_DURATION_TIME;

    private int gravity = Gravity.NO_GRAVITY;

    public FoldingPanelLayout(Context context) {
        this(context, null);
    }

    public FoldingPanelLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FoldingPanelLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (attrs != null) {
            TypedArray defAttrs = context.obtainStyledAttributes(attrs, DEFAULT_ATTRS);

            if (defAttrs != null) {
                defAttrs.recycle();
            }


            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.FoldingPanelLayout);

            if (ta != null) {
                this.mPanelState = PanelState.values()[ta.getInt(R.styleable.FoldingPanelLayout_initialPanelState, DEFAULT_PANEL_STATE.ordinal())];
                this.mNumberOfFolds = ta.getInt(R.styleable.FoldingPanelLayout_foldNumbers, DEFAULT_FOLD_NUMBERS);
                this.mDuration_Time = ta.getInt(R.styleable.FoldingPanelLayout_foldingDuration, DEFAULT_DURATION_TIME);
                this.mHasExternalAnimator = ta.getBoolean(R.styleable.FoldingPanelLayout_externalAnimator, DEFAULT_HAS_EXTERNAL_ANIMATOR);
                this.mTestingMode = ta.getBoolean(R.styleable.FoldingPanelLayout_testingMode, DEFAULT_TEST_MODE);
                ta.recycle();
            }

        }
        initView(context);
    }

    private void initView(Context context) {
        this.mFoldingNavigationLayout = new FoldingLayout(context);
        mFoldingNavigationLayout.setNumberOfFolds(this.mNumberOfFolds);
        mFoldingNavigationLayout.setAnchorFactor(0);
        mFoldingNavigationLayout.setFoldingOrientation(getOrientation());

        if (mTestingMode) {
            mHasExternalAnimator = false;
            this.mAnimator = new AnimationSimulator(this);

        }

        if (!mHasExternalAnimator && !mTestingMode) {

            this.mAnimator = ValueAnimator.ofInt(START_ANIMATION_VALUE, END_ANIMATION_VALUE);
            this.mAnimator.addListener(this);
            this.mAnimator.setDuration(this.mDuration_Time);

            this.mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int val = (Integer) valueAnimator.getAnimatedValue();
                    float offset = (val / (float) ANIMATION_LENGTH);
                    if (FoldingPanelLayout.this.mPanelState == PanelState.COLLAPSED) {
                        FoldingPanelLayout.this.onAnimationProgress(offset);
                    } else {
                        FoldingPanelLayout.this.onAnimationProgress(1 - offset);
                    }
                }
            });
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        float mainPanelWeight = (this.mPanelState == PanelState.COLLAPSED) ? 0.0f : 1.0f;

        this.mMainView = this.getChildAt(0);
        this.setSlidingView(this.getChildAt(1));


        if (this.mMainView != null) {
            removeView(this.mMainView);
            this.mFoldingNavigationLayout.addView(this.mMainView);
            ((LinearLayout.LayoutParams) this.mMainView.getLayoutParams()).weight = 1;
            addView(mFoldingNavigationLayout, 0);
        }

        this.mFoldingNavigationLayout.setLayoutParams(generateChildLayoutParams(mainPanelWeight));
        this.mSlidingView.setLayoutParams(generateChildLayoutParams(1 - mainPanelWeight));

        dispatchPanelSize(mainPanelWeight);

        if (mHasExternalAnimator) {
            this.mAnimatorView = subscribeToAnimator();
        }

        if (!mHasExternalAnimator) {
            this.setClickListeners(this);
        }

    }

    @Override
    protected void onDetachedFromWindow() {
        if (this.mAnimatorView != null) {
            this.mAnimatorView.removeListener(this);
        }

        this.setClickListeners(null);

        super.onDetachedFromWindow();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    /**
     * Set the draggable view portion. Use to null, to allow the whole panel to be draggable
     *
     * @param slidingView A view that will be used to drag the panel.
     */
    public void setSlidingView(View slidingView) {
        if (this.mSlidingView != null) {
            this.mSlidingView.setOnClickListener(null);
        }
        this.mSlidingView = slidingView;
        if (this.mSlidingView != null) {
            this.mSlidingView.setClickable(false);
            this.mSlidingView.setFocusable(false);
            this.mSlidingView.setFocusableInTouchMode(false);
        }
    }

    @Override
    public IAnimationNotifier subscribeToAnimator() {
        IAnimationNotifier view = findAnimator(this.getParent());

        if (view != null) {
            view.addListener(this);
        }
        return view;

    }

    @Override
    public IAnimationNotifier findAnimator(ViewParent parent) {
        if (parent == null) {
            return null;
        }
        return (parent instanceof IAnimationNotifier) ?
                (IAnimationNotifier) parent :
                findAnimator(parent.getParent());
    }

    @Override
    public void onRemoveFromListener() {
        this.mAnimatorView = null;
        this.mAnimatorChildView = null;
    }

    @Override
    public void onAnimationProgress(float value) {
        this.dispatchPanelSize(value);
    }

    @Override
    public boolean hasParent(View listener, View child) {
        if (this.mAnimatorChildView != null) {
            return (child == this.mAnimatorChildView);
        }
        ViewParent parent = listener.getParent();
        if (parent == child) {
            this.mAnimatorChildView = child;
            return true;
        }
        if (parent == null) {
            return false;
        }
        if (parent instanceof View) {
            return hasParent((View) parent, child);
        } else {
            return false;
        }
    }

    private void dispatchPanelSize(float mainPanelWeight) {
        if (this.mMainPanelWeight != mainPanelWeight) {
            this.setMainPanelWeight(mainPanelWeight);

            LayoutParams layoutParams = (LayoutParams) this.mFoldingNavigationLayout.getLayoutParams();
            layoutParams.weight = this.mMainPanelWeight;

            layoutParams = (LayoutParams) this.mSlidingView.getLayoutParams();
            layoutParams.weight = (1 - this.mMainPanelWeight);

            if (this.mMainPanelWeight >= 1.0f) {
                this.mPanelState = PanelState.EXPANDED;
                this.mMainPanelWeight = 1.0f;
            } else if (this.mMainPanelWeight <= 0.0f) {
                this.mPanelState = PanelState.COLLAPSED;
                this.mMainPanelWeight = 0.0f;
            }

            mFoldingNavigationLayout.setFoldFactor(this.mMainPanelWeight);
            this.requestLayout();
        }
    }

    private LayoutParams generateChildLayoutParams(float childWeight) {
        LayoutParams lP;
        switch (this.getOrientation()) {
            case LinearLayout.HORIZONTAL:
                lP = new LayoutParams(0, LayoutParams.MATCH_PARENT, childWeight);
                break;
            default:
                lP = new LayoutParams(LayoutParams.MATCH_PARENT, 0, childWeight);
        }
        return lP;
    }

    private void setMainPanelWeight(float mainPanelWeight) {
        this.mMainPanelWeight = mainPanelWeight;
    }

    @Override
    public void onClick(View v) {
        if (!mHasExternalAnimator) {
            switch (this.mPanelState) {
                case EXPANDED:
                    this.setClickListeners(null);
                    if (mTestingMode) {
                        ((AnimationSimulator) this.mAnimator).setShift(-10);
                    }
                    this.mAnimator.start();
                    break;
                case COLLAPSED:
                    setClickListeners(null);
                    if (mTestingMode) {
                        ((AnimationSimulator) this.mAnimator).setShift(10);
                    }
                    this.mAnimator.start();
                    break;
            }
        }
    }

    @Override
    public void setClickListeners(View.OnClickListener view) {
        this.setOnClickListener(view);
    }

    @Override
    public void onAnimationStart(Animator animation) {

    }

    @Override
    public void onAnimationEnd(Animator animation) {
        this.setClickListeners(this);
    }

    @Override
    public void onAnimationCancel(Animator animation) {

    }

    @Override
    public void onAnimationRepeat(Animator animation) {

    }

}
