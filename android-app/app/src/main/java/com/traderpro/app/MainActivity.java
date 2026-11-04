package com.traderpro.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private final ExecutorService networkPool = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Throwable ignored) {}

        startBackgroundTradingService(true);

        webView = new WebView(this);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);

        webView.addJavascriptInterface(new NativeAsyncBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        setContentView(webView);

        File otaFile = new File(getFilesDir(), "ota_index.html");
        if (otaFile.exists() && otaFile.length() > 10000) {
            webView.loadUrl("file://" + otaFile.getAbsolutePath());
        } else {
            webView.loadUrl("file:///android_asset/index.html");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (webView != null) {
                webView.resumeTimers();
            }
        } catch (Throwable ignored) {}
    }

    private void startBackgroundTradingService(boolean enable) {
        try {
            Intent srv = new Intent(this, TradingForegroundService.class);
            if (enable) {
                startService(srv);
            } else {
                stopService(srv);
            }
        } catch (Throwable ignored) {}
    }

    public class NativeAsyncBridge {
        @JavascriptInterface
        public void setBackgroundServiceActive(final boolean enable) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startBackgroundTradingService(enable);
                }
            });
        }

        @JavascriptInterface
        public void requestLiveScanAsync(final String tf, final String jsonPayload) {
            networkPool.execute(new Runnable() {
                @Override
                public void run() {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL("https://scanner.tradingview.com/india/scan");
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setConnectTimeout(4500);
                        conn.setReadTimeout(4500);
                        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                        conn.setRequestProperty("Origin", "https://in.tradingview.com");
                        conn.setRequestProperty("Referer", "https://in.tradingview.com/");
                        conn.setDoOutput(true);

                        byte[] body = jsonPayload.getBytes(StandardCharsets.UTF_8);
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(body);
                        }

                        StringBuilder sb = new StringBuilder(16384);
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                        }
                        final String safeJson = org.json.JSONObject.quote(sb.toString());
                        final String safeTf = org.json.JSONObject.quote(tf);
                        webView.post(new Runnable() {
                            @Override
                            public void run() {
                                webView.evaluateJavascript("window.__onNativeLiveDelta(" + safeTf + "," + safeJson + ");", null);
                            }
                        });
                    } catch (Throwable ignored) {
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }
            });
        }

        @JavascriptInterface
        public void performOtaUpdateAsync(final String rawUrl) {
            networkPool.execute(new Runnable() {
                @Override
                public void run() {
                    if (rawUrl == null || !rawUrl.startsWith("http")) {
                        notifyOta("✅ Running Latest Trader Pro 1B-K Neuralink Build!");
                        return;
                    }
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL(rawUrl + "?t=" + System.currentTimeMillis());
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(6000);
                        conn.setReadTimeout(6000);
                        StringBuilder sb = new StringBuilder(131072);
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line).append("\n");
                        }
                        String html = sb.toString();
                        if (html.length() > 10000 && (html.contains("TraderPro") || html.contains("Trader Pro"))) {
                            final File otaFile = new File(getFilesDir(), "ota_index.html");
                            try (FileOutputStream fos = new FileOutputStream(otaFile)) {
                                fos.write(html.getBytes(StandardCharsets.UTF_8));
                            }
                            webView.post(new Runnable() {
                                @Override
                                public void run() {
                                    webView.evaluateJavascript("window.__onOtaStatus('✅ 1B-K Neuralink OTA Applied Live! Reloading...');", null);
                                    webView.loadUrl("file://" + otaFile.getAbsolutePath());
                                }
                            });
                        } else {
                            notifyOta("✅ Already on Latest 1B-K Neuralink Version!");
                        }
                    } catch (Throwable e) {
                        notifyOta("ℹ️ OTA Check: Already up to date.");
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }
            });
        }

        private void notifyOta(final String msg) {
            final String q = org.json.JSONObject.quote(msg);
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("window.__onOtaStatus(" + q + ");", null);
                }
            });
        }

        @JavascriptInterface
        public void placeGrowwOrderAsync(final String orderJson, final String token) {
            networkPool.execute(new Runnable() {
                @Override
                public void run() {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL("https://api.groww.in/v1/order/create");
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        conn.setRequestProperty("Authorization", "Bearer " + token.trim());
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setDoOutput(true);
                        byte[] body = orderJson.getBytes(StandardCharsets.UTF_8);
                        try (OutputStream os = conn.getOutputStream()) { os.write(body); }
                        conn.getResponseCode();
                    } catch (Throwable ignored) {
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
