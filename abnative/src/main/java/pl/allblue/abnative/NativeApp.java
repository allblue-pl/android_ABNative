package pl.allblue.abnative;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Native;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NativeApp
{

    static public void InitWebView(Context context, WebView webView, String devUri,
            boolean debug)
    {
        if (debug) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                WebView.setWebContentsDebuggingEnabled(true);
        }

        WebViewClient webClient = new WebViewClient();
        webView.setWebViewClient(webClient);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        else
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        if (devUri != null) {
            Log.d("NativeApp", "Debugging on: " + devUri);
            webView.clearCache(true);
            webView.loadUrl("http://" + devUri);
        } else {
            webView.clearCache(false);

            String header = NativeApp.GetStringFromFile(context,
                    "web-app/cache/abWeb/web/header.html");
            String postBodyInit = NativeApp.GetStringFromFile(context,
                    "web-app/cache/abWeb/web/postBodyInit.html");

            String debug_Str = debug ? "true" : "false";

            String index = NativeApp.GetStringFromFile(context,
              "web-app/index.base.html")
                    .replace("{{header}}", header)
                    .replace("{{postBodyInit}}", postBodyInit)
                    .replace("{{base}}", "/android_asset/web-app/")
                    .replace("{{debug}}", debug_Str);

            webView.loadDataWithBaseURL("file:///android_asset/web-app/",
                    index, "text/html", "UTF-8",
                    "/android_asset/web-app/");
        }
    }

    static private String GetStringFromFile(Context context, String assetFilePath)
    {
        try {
            InputStream is = context.getAssets().open(assetFilePath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            return sb.toString();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private Lock lock = new ReentrantLock();

    private WebView webView = null;
    private boolean initialized = false;
    private Map<String, ActionsSet> actionsSets = new HashMap<>();
    private Map<Integer, OnWebResultInfo> onWebResultInfos = new ArrayMap<>();

    private int web_ActionId_Last = 0;

    private ArrayList<WebCall> webView_Init_WebCalls = new ArrayList<>();
    private boolean webView_Initialized = false;

    public NativeApp(WebView webView)
    {
        this.webView = webView;

        this.web_ActionId_Last = 0;
//        this.onWebResultInfos = ;
    }

    public void addActionsSet(String actionsSetName, ActionsSet actionsSet)
    {
        this.lock.lock();

        if (this.initialized) {
            throw new AssertionError(
                    "Cannot add action set after initialization.");
        }

        this.lock.unlock();

        this.actionsSets.put(actionsSetName, actionsSet);
    }

    public void callWeb(final String actionsSetName, final String actionName,
            final JSONObject args, OnWebResultCallback onWebResultCallback)
    {
        this.lock.lock();

        if (!this.webView_Initialized) {
            Log.d("NativeApp", "Adding action to stack.");

            this.webView_Init_WebCalls.add(new WebCall(actionsSetName, actionName, args,
                    onWebResultCallback));
            this.lock.unlock();
            return;
        }

        this.lock.unlock();

        final NativeApp self = this;

        final int actionId = ++this.web_ActionId_Last;
        this.onWebResultInfos.put(actionId, new OnWebResultInfo(onWebResultCallback));

        this.webView.post(() -> {
            String args_Str = args == null ? "null" : args.toString();
            self.webView.evaluateJavascript("abNative.callWeb(" +
                    Integer.toString(actionId) + ", \"" + actionsSetName + "\", \"" +
                    actionName + "\", " + args_Str + ")", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String s) {
                    // Do nothing. Maybe check for success in the future.
                }
            });
        });
    }

    public void errorNative(final String message)
    {
        final WebView webView = this.webView;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript("abNative.errorNative(\"" + message + "\")",
                        new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        // Do nothing. Maybe check for success in the future.
                    }
                });
            }
        });
    }

    public ActionsSet getActionsSet(String actionsSetName)
    {
        return this.actionsSets.get(actionsSetName);
    }

    public void init()
    {
        this.lock.lock();
        this.initialized = true;
        this.lock.unlock();

        this.webView.addJavascriptInterface(this, "abNative_Android");
    }


    @JavascriptInterface
    public void callNative(final int actionId, String actionsSetName, String actionName,
            String argsString)
    {
        JSONObject args = null;
        if (argsString != null) {
            try {
                args = new JSONObject(argsString);
            } catch (JSONException e) {
                Log.e("NativeApp", "Cannot parse 'argString'.", e);
                this.errorNative("Cannot parser 'argString': " + e.getMessage());
                return;
            }
        }

        ActionsSet actionsSet = this.getActionsSet(actionsSetName);
        if (actionsSet == null) {
            Log.e("NativeApp", "ActionsSet '" + actionsSetName +
                    "' does not exist.");
            this.errorNative("ActionsSet '" + actionsSetName + "' not implemented.");
            return;
        }
        NativeAction action = actionsSet.getNative(actionName);

        if (action == null) {
            Log.e("NativeApp", "Action '" + actionsSetName + ":" +
                    actionName + " not implemented.");
            this.errorNative("Action '" + actionsSetName + ":" +
                    actionName + " not implemented.");
            return;
        }

        final JSONObject result;
        try {
            result = action.call(args);
        } catch (JSONException e) {
            Log.e("NativeApp", "JSONException when calling '" +
                    actionsSetName + ":" + actionName + ".", e);
            this.errorNative("JSONException when calling '" + actionsSetName + ":" + actionName + ".");
            return;
        }

        final WebView webView = this.webView;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                String result_String = result == null ? "null" : result.toString();

                webView.evaluateJavascript("abNative.onNativeResult(" +
                        actionId + "," + result_String + ")",
                        new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        // Do nothing. Maybe check for success in the future.
                    }
                });
            }
        });
    }

    @JavascriptInterface
    public void onWebResult(int actionId, String resultString)
    {
        JSONObject result = null;
        try {
            if (resultString != null)
                result = new JSONObject(resultString);
        } catch (JSONException e) {
            Log.e("NativeApp", "Cannot parse 'resultString'.", e);
            return;
        }

        OnWebResultInfo onWebResultInfo = this.onWebResultInfos.get(actionId);
        if (onWebResultInfo.callback != null) {
            try {
                onWebResultInfo.callback.call(result);
            } catch (JSONException e) {
                Log.e("NativeApp", "Cannot read json result.", e);
                return;
            }
        }
        this.onWebResultInfos.remove(actionId);
    }

    @JavascriptInterface
    public void webViewInitialized()
    {
        Log.d("NativeApp", "Web view initialized.");

        this.lock.lock();

        this.webView_Initialized = true;

        this.lock.unlock();

        Log.d("NativeApp", "Calling action stack: " +
                this.webView_Init_WebCalls.size());

        for (int i = 0; i < this.webView_Init_WebCalls.size(); i++) {
            WebCall wc = this.webView_Init_WebCalls.get(i);

            this.callWeb(wc.actionsSetName, wc.actionName, wc.args,
                    wc.onWebResultCallback);
        }
    }


    private class WebCall
    {

        public String actionsSetName = null;
        public String actionName = null;
        public JSONObject args;
        public OnWebResultCallback onWebResultCallback;

        public WebCall(String actionsSetName, String actionName, JSONObject args,
                       OnWebResultCallback onWebResultCallback)
        {
            this.actionsSetName = actionsSetName;
            this.actionName = actionName;
            this.args = args;
            this.onWebResultCallback = onWebResultCallback;
        }

    }

}
