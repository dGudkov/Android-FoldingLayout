package ru.gdo.android.library.foldinglayout.interfaces;

import android.view.View;
import android.view.ViewParent;

/**
 * @author Danil Gudkov <d_n_l@mail.ru>
 * @copyrights, 2015
 * @since 17.07.15.
 */

public interface IAnimationListener {
    IAnimationNotifier subscribeToAnimator();
    IAnimationNotifier findAnimator(ViewParent view);
    void onRemoveFromListener();
    void onAnimationProgress(float value);
    void setClickListeners(View.OnClickListener view);
    boolean hasParent(View listener, View child);
}
