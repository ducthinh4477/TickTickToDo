package hcmute.edu.vn.tickticktodo.core.background.assistant;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import hcmute.edu.vn.tickticktodo.R;

public class BubbleWindowManager {

    private final Context context;
    private final WindowManager windowManager;
    private final SharedPreferences positionPrefs;
    private final String keyBubbleX;
    private final String keyBubbleY;
    private final int defaultBubbleX;
    private final int defaultBubbleY;
    private final int bubbleCollapsedSizeDp;
    private final float dismissBottomZoneRatio;
    private final float dismissCircleDiameterRatio;
    private final int dismissTargetSizeDp;
    private final int dismissTargetBottomMarginDp;
    private final long edgeSnapDurationMs;

    private View dismissTargetView;
    private WindowManager.LayoutParams dismissTargetParams;

    public BubbleWindowManager(
            Context context,
            WindowManager windowManager,
            SharedPreferences positionPrefs,
            String keyBubbleX,
            String keyBubbleY,
            int defaultBubbleX,
            int defaultBubbleY,
            int bubbleCollapsedSizeDp,
            float dismissBottomZoneRatio,
            float dismissCircleDiameterRatio,
            int dismissTargetSizeDp,
            int dismissTargetBottomMarginDp,
            long edgeSnapDurationMs
    ) {
        this.context = context;
        this.windowManager = windowManager;
        this.positionPrefs = positionPrefs;
        this.keyBubbleX = keyBubbleX;
        this.keyBubbleY = keyBubbleY;
        this.defaultBubbleX = defaultBubbleX;
        this.defaultBubbleY = defaultBubbleY;
        this.bubbleCollapsedSizeDp = bubbleCollapsedSizeDp;
        this.dismissBottomZoneRatio = dismissBottomZoneRatio;
        this.dismissCircleDiameterRatio = dismissCircleDiameterRatio;
        this.dismissTargetSizeDp = dismissTargetSizeDp;
        this.dismissTargetBottomMarginDp = dismissTargetBottomMarginDp;
        this.edgeSnapDurationMs = edgeSnapDurationMs;
    }

    public int getSavedBubbleX(int bubbleWidthPx) {
        int saved = positionPrefs != null
                ? positionPrefs.getInt(keyBubbleX, defaultBubbleX)
                : defaultBubbleX;
        return clampBubbleX(saved, resolveBubbleSizePx(bubbleWidthPx));
    }

    public int getSavedBubbleY(int bubbleHeightPx) {
        int saved = positionPrefs != null
                ? positionPrefs.getInt(keyBubbleY, defaultBubbleY)
                : defaultBubbleY;
        return clampBubbleY(saved, resolveBubbleSizePx(bubbleHeightPx));
    }

    public void saveBubblePosition(int x, int y, int bubbleWidthPx, int bubbleHeightPx) {
        if (positionPrefs == null) {
            return;
        }

        int safeX = clampBubbleX(x, resolveBubbleSizePx(bubbleWidthPx));
        int safeY = clampBubbleY(y, resolveBubbleSizePx(bubbleHeightPx));
        positionPrefs.edit()
                .putInt(keyBubbleX, safeX)
                .putInt(keyBubbleY, safeY)
                .apply();
    }

    public int clampBubbleX(int x, int bubbleWidthPx) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int safeWidth = Math.max(1, bubbleWidthPx);
        int maxX = Math.max(0, screenWidth - safeWidth);
        return Math.max(0, Math.min(x, maxX));
    }

    public int clampBubbleY(int y, int bubbleHeightPx) {
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int safeHeight = Math.max(1, bubbleHeightPx);
        int maxY = Math.max(0, screenHeight - safeHeight);
        return Math.max(0, Math.min(y, maxY));
    }

    public void snapBubbleToNearestHorizontalEdge(
            WindowManager.LayoutParams bubbleParams,
            View bubbleView,
            boolean animated
    ) {
        if (bubbleParams == null || windowManager == null || bubbleView == null) {
            return;
        }

        int bubbleWidth = resolveBubbleSizePx(bubbleParams.width);
        int bubbleHeight = resolveBubbleSizePx(bubbleParams.height);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int maxX = Math.max(0, screenWidth - bubbleWidth);

        int currentX = clampBubbleX(bubbleParams.x, bubbleWidth);
        int targetX = currentX <= (maxX / 2) ? 0 : maxX;
        int safeY = clampBubbleY(bubbleParams.y, bubbleHeight);

        if (!animated || Math.abs(targetX - currentX) <= dpToPx(2)) {
            bubbleParams.x = targetX;
            bubbleParams.y = safeY;
            try {
                if (bubbleView.getParent() != null) {
                    windowManager.updateViewLayout(bubbleView, bubbleParams);
                }
            } catch (Exception ignored) {
            }
            saveBubblePosition(targetX, safeY, bubbleWidth, bubbleHeight);
            return;
        }

        ValueAnimator snapAnimator = ValueAnimator.ofInt(currentX, targetX);
        snapAnimator.setDuration(edgeSnapDurationMs);
        snapAnimator.setInterpolator(new DecelerateInterpolator());
        snapAnimator.addUpdateListener(animation -> {
            bubbleParams.x = (int) animation.getAnimatedValue();
            bubbleParams.y = safeY;
            try {
                if (bubbleView.getParent() != null) {
                    windowManager.updateViewLayout(bubbleView, bubbleParams);
                }
            } catch (Exception ignored) {
            }
        });
        snapAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                saveBubblePosition(targetX, safeY, bubbleWidth, bubbleHeight);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                saveBubblePosition(targetX, safeY, bubbleWidth, bubbleHeight);
            }
        });
        snapAnimator.start();
    }

    public void showDismissTarget() {
        if (windowManager == null) {
            return;
        }

        if (dismissTargetView == null) {
            dismissTargetView = LayoutInflater.from(context).inflate(R.layout.layout_floating_dismiss_target, null);
        }

        if (dismissTargetParams == null) {
            int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            dismissTargetParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            dismissTargetParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            dismissTargetParams.y = dpToPx(dismissTargetBottomMarginDp);
        }

        try {
            if (dismissTargetView.getParent() == null) {
                windowManager.addView(dismissTargetView, dismissTargetParams);
            }
            dismissTargetView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.w("BubbleWindowManager", "Unable to show dismiss target", e);
        }
    }

    public void hideDismissTarget() {
        if (windowManager == null || dismissTargetView == null) {
            return;
        }
        try {
            if (dismissTargetView.getParent() != null) {
                windowManager.removeView(dismissTargetView);
            }
        } catch (Exception e) {
            Log.w("BubbleWindowManager", "Unable to hide dismiss target", e);
        }
    }

    public void updateDismissTargetHighlight(boolean hovered) {
        if (dismissTargetView == null) {
            return;
        }

        TextView targetText = dismissTargetView.findViewById(R.id.tv_dismiss_target);
        if (targetText == null) {
            return;
        }

        targetText.setBackgroundResource(
                hovered
                        ? R.drawable.bg_floating_dismiss_target_active
                        : R.drawable.bg_floating_dismiss_target
        );
        targetText.animate()
                .scaleX(hovered ? 1.08f : 1f)
                .scaleY(hovered ? 1.08f : 1f)
                .setDuration(120)
                .start();
    }

    public boolean isBubbleInDismissBottomBand(WindowManager.LayoutParams bubbleParams) {
        if (bubbleParams == null) {
            return false;
        }

        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int bottomZoneTop = Math.round(screenHeight * (1f - dismissBottomZoneRatio));
        int bubbleHeight = resolveBubbleSizePx(bubbleParams.height);
        int bubbleCenterY = bubbleParams.y + bubbleHeight / 2;
        return bubbleCenterY >= bottomZoneTop;
    }

    public boolean isBubbleOverDismissTarget(WindowManager.LayoutParams bubbleParams) {
        if (bubbleParams == null) {
            return false;
        }

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int bottomZoneTop = Math.round(screenHeight * (1f - dismissBottomZoneRatio));

        int bubbleWidth = resolveBubbleSizePx(bubbleParams.width);
        int bubbleHeight = resolveBubbleSizePx(bubbleParams.height);
        int bubbleCenterX = bubbleParams.x + bubbleWidth / 2;
        int bubbleCenterY = bubbleParams.y + bubbleHeight / 2;
        if (bubbleCenterY < bottomZoneTop) {
            return false;
        }

        int dismissTargetSizePx = dpToPx(dismissTargetSizeDp);
        int dismissBottomMarginPx = dismissTargetParams != null
                ? dismissTargetParams.y
                : dpToPx(dismissTargetBottomMarginDp);
        int dismissCenterX = screenWidth / 2;
        int dismissCenterY = screenHeight - dismissBottomMarginPx - dismissTargetSizePx / 2;

        int screenBasedDiameter = Math.round(Math.min(screenWidth, screenHeight) * dismissCircleDiameterRatio);
        int allowedDistance = Math.max(dpToPx(120), Math.max(screenBasedDiameter, dismissTargetSizePx));
        double distance = Math.hypot(bubbleCenterX - dismissCenterX, bubbleCenterY - dismissCenterY);
        boolean isNearBottomCenter = bubbleCenterY > screenHeight - dpToPx(150)
                && Math.abs(bubbleCenterX - dismissCenterX) < dpToPx(100);
        return distance <= allowedDistance || isNearBottomCenter;
    }

    public void release() {
        hideDismissTarget();
        dismissTargetView = null;
        dismissTargetParams = null;
    }

    private int resolveBubbleSizePx(int currentSizePx) {
        if (currentSizePx > 0) {
            return currentSizePx;
        }
        return dpToPx(bubbleCollapsedSizeDp);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
