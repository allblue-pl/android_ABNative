package pl.allblue.abnative;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ActionsSet
{

    NativeApp nativeApp = null;
    String name = null;

    private Map<String, NativeAction> actions_Native = null;

    public ActionsSet(NativeApp nativeApp, String actionsSetName)
    {
        this.nativeApp = nativeApp;
        this.name = actionsSetName;

        this.actions_Native = new HashMap<>();
    }

    public ActionsSet addNative(String actionName, NativeAction action)
    {
        this.actions_Native.put(actionName, action);

        return this;
    }

    public void callWeb(String actionName, JSONObject args, OnWebResultCallback onWebResult)
    {
        this.nativeApp.callWeb(this.name, actionName, args, onWebResult);
    }

    public NativeAction getNative(String actionName)
    {
        return this.actions_Native.get(actionName);
    }

}
