package pl.allblue.abnative;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NativeApp
{

    static private Handler CallHandler = null;
//    static private HandlerThread CallHandler_Thread = null;


    static public Handler GetCallHandler() {
        if (NativeApp.CallHandler == null) {
//            NativeApp.CallHandler_Thread = new HandlerThread("NativeApp");
//            NativeApp.CallHandler_Thread.start();
//            NativeApp.CallHandler = new Handler(
//                    NativeApp.CallHandler_Thread.getLooper());

            NativeApp.CallHandler = new Handler(Looper.getMainLooper());
        }

        return NativeApp.CallHandler;
    }

    static private String GetStringFromFile(Context context, String assetFilePath) {
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

    private Lock lock;

    private HandlerThread handler_Thread = null;
    private Handler handler = null;

    private WebView webView = null;
    private boolean initialized;
    private Map<String, ActionsSet> actionsSets;
    private Map<Integer, OnWebResultCallback> onWebResultCallbacks;

    private int web_ActionId_Next;

    private ArrayList<WebCall> webView_Init_WebCalls;
    private boolean webView_Initialized;

    public NativeApp() {
        lock = new ReentrantLock();

        initialized = false;
        actionsSets = new HashMap<>();
        this.onWebResultCallbacks = new ArrayMap<>();
        this.web_ActionId_Next = 0;
        webView_Init_WebCalls = new ArrayList<>();
        webView_Initialized = false;
    }

    public void addActionsSet(String actionsSetName, ActionsSet actionsSet) {
        lock.lock();

        if (initialized) {
            throw new AssertionError(
                    "Cannot add action set after initialization.");
        }

        actionsSets.put(actionsSetName, actionsSet);
        lock.unlock();
    }

    public void callWeb(final String actionsSetName, final String actionName,
            final JSONObject args, OnWebResultCallback onWebResultCallback) {
        GetCallHandler().post(() -> {
            lock.lock();

            if (!webView_Initialized) {
                webView_Init_WebCalls.add(new WebCall(actionsSetName, actionName, args,
                        onWebResultCallback));
                lock.unlock();
                return;
            }

            final NativeApp self = this;

            final int actionId = this.web_ActionId_Next;
            this.web_ActionId_Next++;

            this.onWebResultCallbacks.put(actionId, onWebResultCallback);

            String args_Str = args == null ? "null" : args.toString();
            evaluateJavascript("abNative.callWeb(" +
                    actionId + ", \"" + actionsSetName + "\", \"" +
                    actionName + "\", " + args_Str + ")");
            lock.unlock();
        });
    }

    public void callWeb(final String actionsSetName, final String actionName,
            OnWebResultCallback onWebResultCallback) {
        callWeb(actionsSetName, actionName, null, onWebResultCallback);
    }

    public void errorNative(final String message) {
        evaluateJavascript("abNative.errorNative(\"" +
                message + "\")");
    }

    public void errorNative_Post(final String message) {
        evaluateJavascript_Post("abNative.errorNative(\"" +
                message + "\")");
    }

    public void evaluateJavascript(String script) {
        webView.evaluateJavascript(script, (String s) -> {
            // Do nothing. Maybe check for success in the future.
        });
    }

    public void evaluateJavascript_Post(String script, boolean unlock) {
        GetCallHandler().post(() -> {
            evaluateJavascript(script);
            if (unlock)
                lock.unlock();
        });
    }

    public void evaluateJavascript_Post(String script) {
        evaluateJavascript_Post(script, false);
    }

    public ActionsSet getActionsSet(String actionsSetName) {
        return actionsSets.get(actionsSetName);
    }

//    public void init() {
//        NativeApp.GetCallHandler().post(() -> {
//            lock.lock();
//            initialized = true;
//            lock.unlock();
//        });
//    }

    public void init(Context context, WebView webView,
            WebViewClient webViewClient, String devUri, String extraUri,
            boolean debug, AfterInitWebViewCallback afterInitWebViewCallback) {
        GetCallHandler().post(() -> {
            lock.lock();
            initialized = true;
            this.webView = webView;

//            context.runOnUiThread(() -> {

                if (debug) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                        WebView.setWebContentsDebuggingEnabled(true);
                }

                webView.addJavascriptInterface(this, "abNative_Android");

                webView.setWebViewClient(webViewClient);

                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

                if (devUri != null) {
                    Log.d("NativeApp", "Debugging on: " + devUri);
                    webView.clearCache(true);
                    webView.loadUrl(devUri + extraUri);
                } else {
                    webView.clearCache(false);

                    String header = NativeApp.GetStringFromFile(context,
                            "web-app/cache/abWeb/web/header.html");
                    header += "\r\n" + NativeApp.GetStringFromFile(context,
                            "web-app/cache/abWeb/web/header_Scripts.html");
                    String postBody = NativeApp.GetStringFromFile(context,
                            "web-app/cache/abWeb/web/postBody.html");
                    postBody += "\r\n" + NativeApp.GetStringFromFile(context,
                            "web-app/cache/abWeb/web/postBody_Scripts.html");

                    String debug_Str = debug ? "true" : "false";

                    String index = NativeApp.GetStringFromFile(context,
                                    "web-app/index.base.html")
                            .replace("{{header}}", header)
                            .replace("{{postBody}}", postBody)
                            .replace("{{base}}",
                                    "/android_asset/web-app/")
                            .replace("{{debug}}", debug_Str);

                    webView.loadDataWithBaseURL(
                            "file:///android_asset/web-app/" + extraUri,
                            index, "text/html", "UTF-8",
                            "/android_asset/web-app/" + extraUri);
                }

                if (afterInitWebViewCallback != null)
                    afterInitWebViewCallback.afterInitWebView();

            lock.unlock();
        });
    }

    @JavascriptInterface
    public void callNative(final int actionId, String actionsSetName,
            String actionName, String argsString) {
        final NativeApp self = this;
        GetCallHandler().post(() -> {
            lock.lock();

            JSONObject args = null;
            if (argsString != null) {
                try {
                    args = new JSONObject(argsString);
                } catch (JSONException e) {
                    evaluateJavascript_Post(
                            "abNative.onNativeResult(" +
                                    actionId + ",null, \"Cannot parse 'argString': " +
                                    e.getMessage() + "\", null)", true);
                    lock.unlock();
                    return;
                }
            }

            ActionsSet actionsSet = this.getActionsSet(actionsSetName);
            if (actionsSet == null) {
                evaluateJavascript("abNative.onNativeResult(" +
                        actionId + ",null, \"ActionsSet '" + actionsSetName +
                        "' not implemented.\")");
                lock.unlock();
                return;
            }
            Pair<NativeAction, NativeActionCallback> actionPair =
                    actionsSet.getNative(actionName);

            if (actionPair == null) {
                evaluateJavascript("abNative.onNativeResult(" +
                        actionId + ",null, \"Action '" + actionsSetName +
                        ":" + actionName + "' not implemented.\")");
                lock.unlock();
                return;
            }

            /* Action Call */
            if (actionPair.first != null) {
                final JSONObject result;
                try {
                    result = actionPair.first.call(args);
                } catch (Exception e) {
                    evaluateJavascript(
                            "abNative.onNativeResult(" +
                                    actionId + ",null, \"Exception when calling '"
                                    + actionsSetName + ":" + actionName + "' -> " +
                                    e + "\")");
                    lock.unlock();
                    return;
                }

                String result_String = result == null ?
                        "null" : result.toString();

                evaluateJavascript("abNative.onNativeResult(" +
                        actionId + "," + result_String + ", null)");
                lock.unlock();
                /* Action Callback */
            } else if (actionPair.second != null) {
                try {
                    actionPair.second.call(args, new ActionResultCallback() {
                        @Override
                        public void onResult(JSONObject result) {
                            String result_String = result == null ?
                                    "null" : result.toString();

                            evaluateJavascript_Post(
                                    "abNative.onNativeResult(" +
                                            actionId + "," + result_String + ", null)",
                                    true);
                        }

                        @Override
                        public void onError(Exception e) {
                            evaluateJavascript_Post(
                                    "abNative.onNativeResult(" +
                                            actionId + ",null, \"Exception when calling '"
                                            + actionsSetName + ":" + actionName + "' -> " +
                                            e + "\")", true);
                        }
                    });
                } catch (Exception e) {
                    evaluateJavascript(
                            "abNative.onNativeResult(" +
                                    actionId + ",null, \"Exception when calling '"
                                    + actionsSetName + ":" + actionName + "' -> " +
                                    e + "\")");
                    lock.unlock();
                }
            } else {
                throw new AssertionError("Action pair is empty.");
            }
        });
    }

    @JavascriptInterface
    public void onWebResult(int actionId, String resultString, String error) {
        lock.lock();
        if (!this.onWebResultCallbacks.containsKey(actionId)) {
            Log.e("NativeApp", "Cannot find action id '" + actionId +
                    "'.");
            lock.unlock();
            return;
        }
        OnWebResultCallback onWebResultCallback = this.onWebResultCallbacks
                .get(actionId);

        if (error != null) {
            onWebResultCallback.onError(error);
            onWebResultCallbacks.remove(actionId);
            lock.unlock();
            return;
        }

        JSONObject result = null;
        try {
            if (resultString != null)
                result = new JSONObject(resultString);
        } catch (JSONException e) {
            onWebResultCallback.onError("Cannot parse result json: " + e);
            onWebResultCallbacks.remove(actionId);
            lock.unlock();
            return;
        }

        if (onWebResultCallback != null) {
            try {
                onWebResultCallback.onResult(result);
            } catch (JSONException e) {
                onWebResultCallback.onError("Cannot read json result: " + e);
            }
        }

        onWebResultCallbacks.remove(actionId);
        lock.unlock();
    }

    @JavascriptInterface
    public void webViewInitialized() {
        lock.lock();

        Log.d("NativeApp", "Web view initialized.");

        webView_Initialized = true;

        lock.unlock();

        Log.d("NativeApp", "Calling action stack: " +
                webView_Init_WebCalls.size());

        for (int i = 0; i < webView_Init_WebCalls.size(); i++) {
            WebCall wc = webView_Init_WebCalls.get(i);

            this.callWeb(wc.actionsSetName, wc.actionName, wc.args,
                    wc.onWebResultCallback);
        }
    }


    public interface AfterInitWebViewCallback {
        void afterInitWebView();
    }


    private class WebCall {

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
