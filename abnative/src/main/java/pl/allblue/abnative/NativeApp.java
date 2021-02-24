package pl.allblue.abnative;

import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class NativeApp
{

    private WebView webView = null;
    private Map<String, ActionsSet> actionsSets = new HashMap<>();
    private Map<Integer, OnWebResultInfo> onWebResultInfos = new ArrayMap<>();

    private int web_ActionId_Last = 0;

    public NativeApp(WebView webView)
    {
        this.webView = webView;

        this.web_ActionId_Last = 0;
//        this.onWebResultInfos = ;

        webView.addJavascriptInterface(this, "abNative_Android");
    }

    public void addActionsSet(String actionsSetName, ActionsSet actionsSet)
    {
        this.actionsSets.put(actionsSetName, actionsSet);
    }

    public void callWeb(final String actionsSetName, final String actionName,
            final JSONObject args, OnWebResultCallback onWebResultCallback)
    {
        final NativeApp self = this;

        final int actionId = ++this.web_ActionId_Last;
        this.onWebResultInfos.put(actionId, new OnWebResultInfo(onWebResultCallback));

        this.webView.post(new Runnable() {
            @Override
            public void run() {
                self.webView.evaluateJavascript("abNative.callWeb(" +
                        Integer.toString(actionId) + ", \"" + actionsSetName + "\", \"" +
                        actionName + "\", " + args.toString() + ")", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        // Do nothing. Maybe check for success in the future.
                    }
                });
            }
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


    @JavascriptInterface
    public void callNative(final int actionId, String actionsSetName, String actionName,
            String argsString)
    {
        JSONObject args = null;
        try {
            args = new JSONObject(argsString);
        } catch (JSONException e) {
            Log.e("NativeApp", "Cannot parse 'argString'.", e);
            this.errorNative("Cannot parser 'argString': " + e.getMessage());
            return;
        }

        ActionsSet actionsSet = this.getActionsSet(actionsSetName);
        if (actionsSet == null) {
            Log.e("NativeApp", "ActionsSet '" + actionsSetName +
                    "' does not exist.");
            this.errorNative("ActionsSet '" + actionsSetName + "' does not exist.");
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
        if (onWebResultInfo.callback != null)
            onWebResultInfo.callback.call(result);
        this.onWebResultInfos.remove(actionId);
    }

}
