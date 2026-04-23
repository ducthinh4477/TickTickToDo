package hcmute.edu.vn.doinbot.core.background.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

public class BubbleUiManager {

    public interface ChatDragCallback {
        void onPositionChanged(int x, int y);
    }

    private final Context context;
    private final WindowManager windowManager;
    private final ChatOverlayController chatOverlayController;
    private final SharedPreferences positionPrefs;
    private final String keyChatX;
    private final String keyChatY;
    private final String keyChatAlpha;
    private final float minChatAlpha;
    private final float maxChatAlpha;

    public BubbleUiManager(Context context,
                           WindowManager windowManager,
                           SharedPreferences positionPrefs,
                           String keyChatX,
                           String keyChatY,
                           String keyChatAlpha,
                           float minChatAlpha,
                           float maxChatAlpha,
                           String logTag) {
        this.context = context;
        this.windowManager = windowManager;
        this.chatOverlayController = new ChatOverlayController(windowManager, logTag);
        this.positionPrefs = positionPrefs;
        this.keyChatX = keyChatX;
        this.keyChatY = keyChatY;
        this.keyChatAlpha = keyChatAlpha;
        this.minChatAlpha = minChatAlpha;
        this.maxChatAlpha = maxChatAlpha;
    }

    public boolean isAttached(View view) {
        return chatOverlayController.isAttached(view);
    }

    public boolean attachOverlayView(View view,
                                     WindowManager.LayoutParams params,
                                     String overlayName) {
        return chatOverlayController.attach(view, params, overlayName);
    }

    public boolean detachOverlayView(View view, String overlayName) {
        return chatOverlayController.detach(view, overlayName);
    }

    public boolean updateOverlayView(View view,
                                     WindowManager.LayoutParams params,
                                     String overlayName) {
        return chatOverlayController.update(view, params, overlayName);
    }

    public WindowManager.LayoutParams createChatLayoutParams(boolean compatMode,
                                                             int defaultChatX,
                                                             int defaultChatY,
                                                             int minWidthDp,
                                                             int maxWidthDp,
                                                             int horizontalMarginDp) {
        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (compatMode) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        int maxWidth = context.getResources().getDisplayMetrics().widthPixels - dpToPx(horizontalMarginDp);
        int desiredWidth = Math.min(dpToPx(maxWidthDp), maxWidth);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Math.max(desiredWidth, dpToPx(minWidthDp)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = getSavedChatX(defaultChatX);
        params.y = getSavedChatY(defaultChatY);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
        return params;
    }

    public int getDefaultChatX(int horizontalMarginDp, int desiredWidthDp, int minWidthDp) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int maxWidth = screenWidth - dpToPx(horizontalMarginDp);
        int desiredWidth = Math.min(dpToPx(desiredWidthDp), maxWidth);
        int popupWidth = Math.max(desiredWidth, dpToPx(minWidthDp));
        return Math.max(dpToPx(12), (screenWidth - popupWidth) / 2);
    }

    public int getSavedChatX(int defaultValue) {
        return positionPrefs != null
                ? positionPrefs.getInt(keyChatX, defaultValue)
                : defaultValue;
    }

    public int getSavedChatY(int defaultValue) {
        return positionPrefs != null
                ? positionPrefs.getInt(keyChatY, defaultValue)
                : defaultValue;
    }

    public void saveChatPosition(int x, int y) {
        if (positionPrefs == null) {
            return;
        }
        positionPrefs.edit()
                .putInt(keyChatX, x)
                .putInt(keyChatY, y)
                .apply();
    }

    public float getSavedChatAlpha() {
        if (positionPrefs == null) {
            return maxChatAlpha;
        }
        float stored = positionPrefs.getFloat(keyChatAlpha, maxChatAlpha);
        return clampChatAlpha(stored);
    }

    public void saveChatAlpha(float alpha) {
        if (positionPrefs == null) {
            return;
        }
        positionPrefs.edit().putFloat(keyChatAlpha, clampChatAlpha(alpha)).apply();
    }

    public float applyChatAlpha(View floatingChatView,
                                WindowManager.LayoutParams chatParams,
                                float alpha,
                                boolean persist,
                                String overlayName) {
        float safeAlpha = clampChatAlpha(alpha);
        if (persist) {
            saveChatAlpha(safeAlpha);
        }

        if (floatingChatView == null) {
            return safeAlpha;
        }

        try {
            floatingChatView.setAlpha(safeAlpha);
            if (chatParams != null && isAttached(floatingChatView)) {
                updateOverlayView(floatingChatView, chatParams, overlayName);
            }
        } catch (Exception e) {
            Log.w("BubbleUiManager", "Unable to apply chat alpha", e);
        }
        return safeAlpha;
    }

    public void attachChatHeaderDrag(View dragHandle,
                                     View floatingChatView,
                                     WindowManager.LayoutParams chatParams,
                                     int minTopDp,
                                     int bottomMarginDp,
                                     View[] excludedViews,
                                     ChatDragCallback callback) {
        if (dragHandle == null) {
            return;
        }

        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (chatParams == null) {
                    return false;
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (isTouchInsideAnyExcluded(event, excludedViews)) {
                            return false;
                        }
                        initialX = chatParams.x;
                        initialY = chatParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        if (!isDragging && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
                            isDragging = true;
                        }
                        if (!isDragging) {
                            return true;
                        }

                        int nextX = initialX + deltaX;
                        int nextY = initialY + deltaY;
                        if (floatingChatView != null) {
                            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
                            int maxX = Math.max(0, screenWidth - floatingChatView.getWidth());
                            int maxY = Math.max(dpToPx(minTopDp), screenHeight - floatingChatView.getHeight() - dpToPx(bottomMarginDp));
                            nextX = Math.max(0, Math.min(nextX, maxX));
                            nextY = Math.max(dpToPx(minTopDp), Math.min(nextY, maxY));
                        }

                        chatParams.x = nextX;
                        chatParams.y = nextY;
                        updateOverlayView(floatingChatView, chatParams, "chat overlay drag");
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isDragging) {
                            saveChatPosition(chatParams.x, chatParams.y);
                            if (callback != null) {
                                callback.onPositionChanged(chatParams.x, chatParams.y);
                            }
                        }
                        return isDragging;
                    default:
                        return false;
                }
            }
        });
    }

    private boolean isTouchInsideAnyExcluded(MotionEvent event, View... views) {
        if (event == null || views == null || views.length == 0) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        for (View view : views) {
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (x >= view.getLeft() && x <= view.getRight()
                    && y >= view.getTop() && y <= view.getBottom()) {
                return true;
            }
        }
        return false;
    }

    private float clampChatAlpha(float alpha) {
        return Math.max(minChatAlpha, Math.min(maxChatAlpha, alpha));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
