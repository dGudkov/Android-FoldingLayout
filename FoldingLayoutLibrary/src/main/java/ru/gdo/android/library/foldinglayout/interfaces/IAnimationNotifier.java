package ru.gdo.android.library.foldinglayout.interfaces;

import android.view.ViewGroup;

/**
 * @author Danil Gudkov <d_n_l@mail.ru>
 * @copyrights, 2015
 * @since 17.07.15.
 */

public interface IAnimationNotifier {

    void addListener(IAnimationListener listener);
    void removeListener(IAnimationListener listener);
    void notifyListenters(ViewGroup child, float value);

}
