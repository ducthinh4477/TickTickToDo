package hcmute.edu.vn.tickticktodo.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.worker.SchoolSyncWorker;

public class SchoolLoginActivity extends BaseActivity {

    private static final String LOGIN_URL = "https://utexlms.hcmute.edu.vn/login/index.php";
    private static final String CALENDAR_EXPORT_URL = "https://utexlms.hcmute.edu.vn/calendar/export.php";
    public static final String PREFS_NAME = "SchoolPrefs";
    public static final String KEY_ICAL_URL = "ical_url";

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_school_login); // Cần tạo layout này chứa WebView id=webView

        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        // Thêm interface để JavaScript gọi lại Android
        webView.addJavascriptInterface(new MyJavaScriptInterface(this), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d("WebView", "Loading: " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Nếu đã vào trang Calendar Export, thực hiện Injection
                if (url.contains("calendar/export.php")) {
                    injectScriptToGetCalendarUrl();
                }
                // Nếu đăng nhập thành công (thường chuyển về trang chủ), tự động nhảy sang trang Calendar Export
                else if (url.equals("https://utexlms.hcmute.edu.vn/") || url.contains("my/")) {
                    webView.loadUrl(CALENDAR_EXPORT_URL);
                }
            }
        });

        // Xóa cookie cũ để đảm bảo đăng nhập mới
        CookieManager.getInstance().removeAllCookies(null);
        webView.loadUrl(LOGIN_URL);
    }

    private void injectScriptToGetCalendarUrl() {
        // Updated logic based on Moodle structure:
        // 1. Check if 'calendarurl' input exists and contains 'export_execute.php'.
        // 2. If not, select 'All events', 'Recent and next 60 days', then click 'Get calendar URL'.

        String js = "javascript:(function() {" +
                "   // 1. Kiểm tra xem đã có URL kết quả chưa (do Moodle load lại)" +
                "   var resultInput = document.getElementById('calendarurl');" +
                "   if (resultInput && resultInput.value && resultInput.value.includes('export_execute.php')) {" +
                "       window.Android.processUrl(resultInput.value);" +
                "       return;" +
                "   }" +
                "" +
                "   // 2. Nếu chưa có, thao tác form" +
                "   try {" +
                "       // Click Radio 'All events' (name='events[exportevents]', value='all')" +
                "       var radioAll = document.querySelector('input[name=\"events[exportevents]\"][value=\"all\"]');" +
                "       if (radioAll) radioAll.click();" +
                "" +
                "       // Click Radio 'Recent and next 60 days' (name='period[timeperiod]', value='recentupcoming')" +
                "       var radioRecent = document.querySelector('input[name=\"period[timeperiod]\"][value=\"recentupcoming\"]');" +
                "       if (radioRecent) radioRecent.click();" +
                "" +
                "       // Click nút 'Get calendar URL' (id='id_generateurl')" +
                "       var genBtn = document.getElementById('id_generateurl');" +
                "       if (genBtn) {" +
                "           genBtn.click();" +
                "       } else {" +
                "           // Fallback tìm theo text nếu id thay đổi" +
                "           var buttons = document.querySelectorAll('input[type=\"submit\"], button');" +
                "           for (var i=0; i<buttons.length; i++) {" +
                "               var val = buttons[i].value || buttons[i].innerText;" +
                "               if (val && (val.includes('Get calendar URL') || val.includes('Lấy địa chỉ mạng'))) {" +
                "                   buttons[i].click();" +
                "                   break;" +
                "               }" +
                "           }" +
                "       }" +
                "   } catch(e) { console.log('Injection Error: ' + e); }" +
                "})()";

        webView.evaluateJavascript(js, null);
    }

    // Interface nhận kết quả từ JS
    public class MyJavaScriptInterface {
        Context mContext;
        MyJavaScriptInterface(Context c) { mContext = c; }

        @JavascriptInterface
        public void processUrl(String url) {
            Log.d("SchoolLogin", "Found iCal URL: " + url);

            // Lưu URL vào SharedPreferences
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(KEY_ICAL_URL, url);
            editor.apply();

            runOnUiThread(() -> {
                Toast.makeText(mContext, "Đồng bộ thành công!", Toast.LENGTH_SHORT).show();
                // Kích hoạt Worker đồng bộ ngay lập tức
                SchoolSyncWorker.triggerManualSync(mContext);
                finish();
            });
        }
    }
}
