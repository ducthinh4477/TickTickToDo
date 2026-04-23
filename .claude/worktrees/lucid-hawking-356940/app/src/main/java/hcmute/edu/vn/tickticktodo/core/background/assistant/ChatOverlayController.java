package hcmute.edu.vn.doinbot.core.background.assistant;

import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

public class ChatOverlayController {

    private final WindowManager windowManager;
    private final String tag;

    public ChatOverlayController(WindowManager windowManager, String tag) {
        this.windowManager = windowManager;
        this.tag = tag;
    }

    public boolean isAttached(@Nullable View view) {
        return view != null && view.getParent() != null;
    }

    public boolean attach(@Nullable View view,
                          @Nullable WindowManager.LayoutParams params,
                          String overlayName) {
        if (windowManager == null || view == null || params == null || isAttached(view)) {
            return false;
        }
        try {
            windowManager.addView(view, params);
            return true;
        } catch (Exception e) {
            Log.w(tag, "Unable to attach " + overlayName, e);
            return false;
        }
    }

    public boolean detach(@Nullable View view, String overlayName) {
        if (windowManager == null || view == null || !isAttached(view)) {
            return false;
        }
        try {
            windowManager.removeView(view);
            return true;
        } catch (Exception e) {
            Log.w(tag, "Unable to detach " + overlayName, e);
            return false;
        }
    }

    public boolean update(@Nullable View view,
                          @Nullable WindowManager.LayoutParams params,
                          String overlayName) {
        if (windowManager == null || view == null || params == null || !isAttached(view)) {
            return false;
        }
        try {
            windowManager.updateViewLayout(view, params);
            return true;
        } catch (Exception e) {
            Log.w(tag, "Unable to update " + overlayName, e);
            return false;
        }
    }
}
