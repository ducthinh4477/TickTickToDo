package hcmute.edu.vn.tickticktodo.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.MainActivity;
import hcmute.edu.vn.tickticktodo.R;

/**
 * Splash screen (Welcome screen) — hiển thị trong 2 giây rồi chuyển sang MainActivity.
 *
 * Dùng Handler thay vì Thread.sleep() để không block UI thread.
 * finish() được gọi ngay sau khi start MainActivity để user không thể
 * nhấn Back quay lại màn hình splash.
 */
public class SplashActivity extends BaseActivity {

    private static final long SPLASH_DELAY_MS = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        handler.postDelayed(this::goToMain, SPLASH_DELAY_MS);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        // Huỷ callback nếu Activity bị destroy sớm (ví dụ: xoay màn hình)
        // tránh memory leak hoặc start Activity sau khi đã finish
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}

