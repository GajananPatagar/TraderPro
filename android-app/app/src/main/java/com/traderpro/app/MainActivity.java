package com.traderpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private final ExecutorService networkPool = Executors.newFixedThreadPool(3);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setRenderPriority(WebSettings.RenderPriority.HIGH);

        webView.addJavascriptInterface(new NativeAsyncBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        setContentView(webView);

        // Instant 0ms local asset load (Zero network block on startup!)
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class NativeAsyncBridge {
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
                        conn.setRequestProperty("Connection", "keep-alive");
                        conn.setDoOutput(true);

                        byte[] body = jsonPayload.getBytes(StandardCharsets.UTF_8);
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(body);
                        }

                        StringBuilder sb = new StringBuilder(8192);
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }
                        }
                        final String resStr = sb.toString();
                        final String safeJson = org.json.JSONObject.quote(resStr);
                        final String safeTf = org.json.JSONObject.quote(tf);

                        webView.post(new Runnable() {
                            @Override
                            public void run() {
                                webView.evaluateJavascript("window.__onNativeLiveDelta(" + safeTf + "," + safeJson + ");", null);
                            }
                        });
                    } catch (Exception ignored) {
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
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
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(body);
                        }
                        conn.getResponseCode();
                    } catch (Exception ignored) {
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
