package ru.gdo.android.library.foldinglayout;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import ru.gdo.android.library.foldinglayout.interfaces.IOnFoldListener;

/**
 * @author Danil Gudkov <d_n_l@mail.ru>
 * @copyrights, 2015
 * @since 17.07.15.
 */

public class FoldingLayout extends LinearLayout {

	/*
	 * A bug was introduced in Android 4.3 that ignores changes to the Canvas
	 * state between multiple calls to super.dispatchDraw() when running with
	 * hardware acceleration. To account for this bug, a slightly different
	 * approach was taken to fold a static image whereby a bitmap of the
	 * original contents is captured and drawn in segments onto the canvas.
	 * However, this method does not permit the folding of a TextureView hosting
	 * a live camera feed which continuously updates. Furthermore, the sepia
	 * effect was removed from the bitmap variation of the demo to simplify the
	 * logic when running with this workaround."
	 */

    private final float SHADING_ALPHA = 0.8f;
    private final float SHADING_FACTOR = 0.5f;
    private final int NUM_OF_POLY_POINTS = 8;

    private Rect[] mFoldRectArray;

    private Matrix[] mMatrix;

    protected int mOrientation = LinearLayout.VERTICAL;

    protected float mAnchorFactor = 2.2f;
    private float mFoldFactor = 0;

    private int mNumberOfFolds = 2;

    private boolean mIsHorizontal = true;

    private int mOriginalWidth = 0;
    private int mOriginalHeight = 0;

    private float mFoldMaxWidth = 0;
    private float mFoldMaxHeight = 0;
    private float mFoldDrawWidth = 0;
    private float mFoldDrawHeight = 0;

    private boolean mIsFoldPrepared = false;
    private boolean mShouldDraw = true;

    private Paint mSolidShadow;
    private Paint mGradientShadow;
    private LinearGradient mShadowLinearGradient;
    private Matrix mShadowGradientMatrix;

    private float[] mSrc;
    private float[] mDst;

    private IOnFoldListener mFoldListener;

    private float mPreviousFoldFactor = 0;

    private Bitmap mFullBitmap;
    private Rect mDstRect;

    private boolean mFirstLayout = true;

    public FoldingLayout(Context context) {
        super(context);
    }

    public FoldingLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FoldingLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void init() {
        mNumberOfFolds = 2;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.mFirstLayout = true;

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.mFirstLayout = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View child = getChildAt(0);
        measureChild(child, widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        calculateMatrices();
        View child = getChildAt(0);
        child.layout(l, t, r, b);
        this.mFirstLayout = false;
    }

    /**
     * The custom exception to be thrown so as to limit the number of views in
     * this layout to at most one.
     */
    private class NumberOfFoldingLayoutChildrenException extends
            RuntimeException {
        /**
         *
         */
        private static final long serialVersionUID = 1L;

        public NumberOfFoldingLayoutChildrenException(String message) {
            super(message);
        }
    }

    /**
     * Throws an exception if the number of views added to this layout exceeds
     * one.
     */
    private void throwCustomException(int numOfChildViews) {
        if (numOfChildViews == 1) {
            throw new NumberOfFoldingLayoutChildrenException(
                    "Folding Layout can only 1 child at most");
        }
    }

    public void setFoldListener(IOnFoldListener foldListener) {
        mFoldListener = foldListener;
    }

    /**
     * Sets the fold factor of the folding view and updates all the
     * corresponding matrices and values to account for the new fold factor.
     * Once that is complete, it redraws itself with the new fold.
     */
    public void setFoldFactor(float foldFactor) {
        if (foldFactor > 1) {
            foldFactor = 1;
        }
        if (foldFactor < 0) {
            foldFactor = 0;
        }
        if (foldFactor != mFoldFactor) {
            mFoldFactor = foldFactor;
            invalidate();
        }
    }

    public void setFoldingOrientation(int orientation) {
        if (orientation != mOrientation) {
            mOrientation = orientation;
            updateFold();
        }
    }

    public void setAnchorFactor(float anchorFactor) {
        if (anchorFactor != mAnchorFactor) {
            mAnchorFactor = anchorFactor;
            updateFold();
        }
    }

    public void setNumberOfFolds(int numberOfFolds) {
        if (numberOfFolds != mNumberOfFolds) {
            mNumberOfFolds = numberOfFolds;
            updateFold();
        }
    }

    public float getAnchorFactor() {
        return mAnchorFactor;
    }

    public int getFoldingOrientation() {
        return mOrientation;
    }

    public float getFoldFactor() {
        return mFoldFactor;
    }

    public int getNumberOfFolds() {
        return mNumberOfFolds;
    }

    private void updateFold() {
        prepareFold(mOrientation, mAnchorFactor, mNumberOfFolds);
        calculateMatrices();
        invalidate();
    }

    /**
     * This method is called in order to update the fold's orientation, anchor
     * point and number of folds. This creates the necessary setup in order to
     * prepare the layout for a fold with the specified parameters. Some of the
     * dimensions required for the folding transformation are also acquired
     * here.
     *
     * After this method is called, it will be in a completely unfolded state by
     * default.
     */
    private void prepareFold(int orientation, float anchorFactor,
                             int numberOfFolds) {
        mSrc = new float[NUM_OF_POLY_POINTS];
        mDst = new float[NUM_OF_POLY_POINTS];

        mDstRect = new Rect();

        mFoldFactor = 0;
        mPreviousFoldFactor = 0;

        mIsFoldPrepared = false;

        mSolidShadow = new Paint();
        mGradientShadow = new Paint();

        mOrientation = orientation;
        mIsHorizontal = (orientation == LinearLayout.HORIZONTAL);

        if (mIsHorizontal) {
            mShadowLinearGradient = new LinearGradient(0, 0, SHADING_FACTOR, 0,
                    Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        } else {
            mShadowLinearGradient = new LinearGradient(0, 0, 0, SHADING_FACTOR,
                    Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        }

        mGradientShadow.setStyle(Paint.Style.FILL);
        mGradientShadow.setShader(mShadowLinearGradient);
        mShadowGradientMatrix = new Matrix();

        mAnchorFactor = anchorFactor;
        mNumberOfFolds = numberOfFolds;

        mFoldRectArray = new Rect[mNumberOfFolds];
        mMatrix = new Matrix[mNumberOfFolds];

        for (int x = 0; x < mNumberOfFolds; x++) {
            mMatrix[x] = new Matrix();
        }

        mIsFoldPrepared = true;
    }

    /*
     * Calculates the transformation matrices used to draw each of the separate
     * folding segments from this view.
     */
    private void calculateMatrices() {

        mShouldDraw = true;

        if (!mIsFoldPrepared) {
            return;
        }

        /**
         * If the fold factor is 1 than the folding view should not be seen and
         * the canvas can be left completely empty.
         */
        if (mFoldFactor == 1) {
            mShouldDraw = false;
            return;
        }

        if (mFoldFactor == 1 && mPreviousFoldFactor > 0
                && mFoldListener != null) {
            mFoldListener.onEndFold();
        }

        if (mPreviousFoldFactor == 0 && mFoldFactor > 0
                && mFoldListener != null) {
            mFoldListener.onStartFold();
        }

        mPreviousFoldFactor = mFoldFactor;

		/*
		 * Reset all the transformation matrices back to identity before
		 * computing the new transformation
		 */
        for (int x = 0; x < mNumberOfFolds; x++) {
            mMatrix[x].reset();
        }

        float cTranslationFactor = 1;

        this.mOriginalWidth = this.getRight() - this.getLeft();
        this.mOriginalHeight = this.getBottom() - this.getTop();

        int delta = Math.round(mIsHorizontal ?
                ((float) mOriginalWidth) / ((float) mNumberOfFolds) :
                ((float)mOriginalHeight) / ((float) mNumberOfFolds));

		/*
		 * Loops through the number of folds and segments the full layout into a
		 * number of smaller equal components. If the number of folds is odd,
		 * then one of the components will be smaller than all the rest. Note
		 * that deltap below handles the calculation for an odd number of folds.
		 */
        for (int x = 0; x < mNumberOfFolds; x++) {
            if (mIsHorizontal) {
                int deltap = (x + 1) * delta > mOriginalWidth ? mOriginalWidth - x * delta : delta;
                mFoldRectArray[x] = new Rect(x * delta, 0, x * delta + deltap,
                        mOriginalHeight);
            } else {
                int deltap = (x == mNumberOfFolds -1) ?  mOriginalHeight - (x * delta) : (x + 1) * delta > mOriginalHeight ? mOriginalHeight - x * delta : delta;
                mFoldRectArray[x] = new Rect(0, x * delta, mOriginalWidth, x * delta
                        + deltap);
            }
        }

        if (mIsHorizontal) {
            mFoldMaxHeight = mOriginalHeight;
            mFoldMaxWidth = delta;
        } else {
            mFoldMaxHeight = delta;
            mFoldMaxWidth = mOriginalWidth;
        }

        float cTF = (1 - mFoldFactor);

        float translatedDistance = mIsHorizontal ?
                mOriginalWidth * cTF :
                mOriginalHeight * cTF;

        float translatedDistancePerFold = Math.round(translatedDistance
                / mNumberOfFolds);

		/*
		 * For an odd number of folds, the rounding error may cause the
		 * translatedDistancePerFold to be grater than the max fold width or
		 * height.
		 */
        mFoldDrawWidth = mFoldMaxWidth < translatedDistancePerFold ? translatedDistancePerFold
                : mFoldMaxWidth;
        mFoldDrawHeight = mFoldMaxHeight < translatedDistancePerFold ? translatedDistancePerFold
                : mFoldMaxHeight;

		/*
		 * The size of some object is always inversely proportional to the
		 * distance it is away from the viewpoint. The constant can be varied to
		 * to affect the amount of perspective.
		 */
        float scaleFactor = 1.0f - (0.10f * (1 - this.mFoldFactor)) ;

        float scaledWidth, scaledHeight, bottomScaledPoint, topScaledPoint, rightScaledPoint, leftScaledPoint;

        if (mIsHorizontal) {
            scaledWidth = mFoldDrawWidth * cTranslationFactor;
            scaledHeight = mFoldDrawHeight * scaleFactor;
        } else {
            scaledWidth = mFoldDrawWidth * scaleFactor;
            scaledHeight = mFoldDrawHeight * cTranslationFactor;
        }

        topScaledPoint = (mFoldDrawHeight - scaledHeight) / 2.0f;
        bottomScaledPoint = topScaledPoint + scaledHeight;

        leftScaledPoint = (mFoldDrawWidth - scaledWidth) / 2.0f;
        rightScaledPoint = leftScaledPoint + scaledWidth;

        float anchorPoint = mIsHorizontal ? mAnchorFactor * mOriginalWidth
                : mAnchorFactor * mOriginalHeight;

		/* The fold along which the anchor point is located. */
        float midFold = mIsHorizontal ? (anchorPoint / mFoldDrawWidth)
                : anchorPoint / mFoldDrawHeight;

        mSrc[0] = 0;
        mSrc[1] = 0;
        mSrc[2] = 0;
        mSrc[3] = mFoldDrawHeight;
        mSrc[4] = mFoldDrawWidth;
        mSrc[5] = 0;
        mSrc[6] = mFoldDrawWidth;
        mSrc[7] = mFoldDrawHeight;

		/*
		 * Computes the transformation matrix for each fold using the values
		 * calculated above.
		 */
        for (int x = 0; x < mNumberOfFolds; x++) {

            boolean isEven = (x % 2 == 0);

            if (mIsHorizontal) {
                mDst[0] = (anchorPoint > x * mFoldDrawWidth) ? anchorPoint
                        + (x - midFold) * scaledWidth : anchorPoint
                        - (midFold - x) * scaledWidth;
                mDst[1] = isEven ? 0 : topScaledPoint;
                mDst[2] = mDst[0];
                mDst[3] = isEven ? mFoldDrawHeight : bottomScaledPoint;
                mDst[4] = (anchorPoint > (x + 1) * mFoldDrawWidth) ? anchorPoint
                        + (x + 1 - midFold) * scaledWidth
                        : anchorPoint - (midFold - x - 1) * scaledWidth;
                mDst[5] = isEven ? topScaledPoint : 0;
                mDst[6] = mDst[4];
                mDst[7] = isEven ? bottomScaledPoint : mFoldDrawHeight;

            } else {
                mDst[0] = isEven ? 0 : leftScaledPoint;
                mDst[1] = (anchorPoint > x * mFoldDrawHeight) ? anchorPoint
                        + (x - midFold) * scaledHeight :
                        anchorPoint - (midFold - x) * scaledHeight;
                mDst[2] = isEven ? leftScaledPoint : 0;
                mDst[3] = (anchorPoint > (x + 1) * mFoldDrawHeight) ? anchorPoint
                        + (x + 1 - midFold) * scaledHeight :
                        anchorPoint - (midFold - x - 1) * scaledHeight;
                mDst[4] = isEven ? mFoldDrawWidth : rightScaledPoint;
                mDst[5] = mDst[1];
                mDst[6] = isEven ? rightScaledPoint : mFoldDrawWidth;
                mDst[7] = mDst[3];
            }

			/*
			 * Pixel fractions are present for odd number of folds which need to
			 * be rounded off here.
			 */
            for (int y = 0; y < 8; y++) {
                mDst[y] = Math.round(mDst[y]);
            }

			/*
			 * If it so happens that any of the folds have reached a point where
			 * the width or height of that fold is 0, then nothing needs to be
			 * drawn onto the canvas because the view is essentially completely
			 * folded.
			 */
            if (mIsHorizontal) {
                if (mDst[4] <= mDst[0] || mDst[6] <= mDst[2]) {
                    mShouldDraw = false;
                    return;
                }
            } else {
                if (mDst[3] <= mDst[1] || mDst[7] <= mDst[5]) {
                    mShouldDraw = false;
                    return;
                }
            }

			/* Sets the shadow and bitmap transformation matrices. */
            mMatrix[x].setPolyToPoly(mSrc, 0, mDst, 0, NUM_OF_POLY_POINTS / 2);
        }
		/*
		 * The shadows on the folds are split into two parts: Solid shadows and
		 * gradients. Every other fold has a solid shadow which overlays the
		 * whole fold. Similarly, the folds in between these alternating folds
		 * also have an overlaying shadow. However, it is a gradient that takes
		 * up part of the fold as opposed to a solid shadow overlaying the whole
		 * fold.
		 */

		/* Solid shadow paint object. */
        int alpha = (int) ((1 - mFoldFactor) * 255 * SHADING_ALPHA);

        mSolidShadow.setColor(Color.argb(alpha, 0, 0, 0));

        if (mIsHorizontal) {
            mShadowGradientMatrix.setScale(mFoldDrawWidth, 1);
            mShadowLinearGradient.setLocalMatrix(mShadowGradientMatrix);
        } else {
            mShadowGradientMatrix.setScale(1, mFoldDrawHeight);
            mShadowLinearGradient.setLocalMatrix(mShadowGradientMatrix);
        }

        mGradientShadow.setAlpha(alpha);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        /**
         * If prepareFold has not been called or if preparation has not
         * completed yet, then no custom drawing will take place so only need to
         * invoke super's onDraw and return.
         */
        if (!mIsFoldPrepared || mFoldFactor == 1) {
            super.dispatchDraw(canvas);
            return;
        }

        if (!mShouldDraw) {
            return;
        }

        Rect src;
		/*
		 * Draws the bitmaps and shadows on the canvas with the appropriate
		 * transformations.
		 */
        for (int x = 0; x < mNumberOfFolds; x++) {

            src = mFoldRectArray[x];
			/* The canvas is saved and restored for every individual fold */
            canvas.save();

			/*
			 * Concatenates the canvas with the transformation matrix for the
			 * the segment of the view corresponding to the actual image being
			 * displayed.
			 */
            canvas.concat(mMatrix[x]);
            if (Util.IS_JBMR2 ) {
                mDstRect.set(0, 0, src.width(), src.height());
                canvas.drawBitmap(mFullBitmap, src, mDstRect, null);
            } else {
				/*
				 * The same transformation matrix is used for both the shadow
				 * and the image segment. The canvas is clipped to account for
				 * the size of each fold and is translated so they are drawn in
				 * the right place. The shadow is then drawn on top of the
				 * different folds using the sametransformation matrix.
				 */
                canvas.clipRect(0, 0, src.right - src.left, src.bottom
                        - src.top);

                if (mIsHorizontal) {
                    canvas.translate(-src.left, 0);
                } else {
                    canvas.translate(0, -src.top);
                }

                super.dispatchDraw(canvas);

                if (mIsHorizontal) {
                    canvas.translate(src.left, 0);
                } else {
                    canvas.translate(0, src.top);
                }
            }
			/* Draws the shadows corresponding to this specific fold. */
            if (x % 2 == 0) {
                canvas.drawRect(0, 0, mFoldDrawWidth, mFoldDrawHeight,
                        mSolidShadow);
            } else {
                canvas.drawRect(0, 0, mFoldDrawWidth, mFoldDrawHeight,
                        mGradientShadow);
            }

            canvas.restore();
        }
    }

}