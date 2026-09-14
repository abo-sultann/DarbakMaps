package com.abosultan.darbakmaps;

import android.app.Activity;
import android.widget.TextView;

/** Visual shell for saved places/tracks. MainActivity owns display/navigation/edit/delete actions. */
final class DarbakSavedButton extends TextView {
    DarbakSavedButton(Activity activity) {
        super(activity);
    }
}
