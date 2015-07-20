package ru.gdo.android.library.foldinglayout;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.view.View;

import ru.gdo.android.library.foldinglayout.interfaces.IAnimationListener;

/**
 * @author Danil Gudkov <d_n_l@mail.ru>
 * @copyrights, 2015
 * @since 17.07.15.
 */

public class AnimationSimulator extends ValueAnimator {

    private int _offset, _delta;
    private Handler mHandler;

    private int mShift;
    private IAnimationListener view;

    public AnimationSimulator(IAnimationListener view) {
        this.view = view;
    }

    public void useHandler(int shift) {
        mHandler = new Handler();
        if (shift > 0) {
            _offset = 0;
            _delta = shift;
        } else {
            _offset = 100;
            _delta = shift;
        }
        mHandler.postDelayed(mRunnable, 200);
    }


    private Runnable mRunnable = new Runnable() {

        @Override
        public void run() {
            if ((_offset > 100) || (_offset < 0)) {
                AnimationSimulator.this.view.setClickListeners((View.OnClickListener) AnimationSimulator.this.view);
                return;
            }

            if (_delta > 0) {
                _offset += _delta;
                float offset = (_offset / (float) 100);
                AnimationSimulator.this.view.onAnimationProgress(offset);
            } else {
                _offset += _delta;
                float offset = (_offset / (float) 100);
                AnimationSimulator.this.view.onAnimationProgress(offset);
            }
            mHandler.postDelayed(mRunnable, 200);
        }
    };

    @Override
    public void start() {
        if (this.mShift != 0) {
            useHandler(this.mShift);
        }
    }

    public void setShift(int shift) {
        this.mShift = shift;
    }

}
