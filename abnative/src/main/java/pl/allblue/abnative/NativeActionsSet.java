package pl.allblue.abnative;

import org.json.JSONObject;

public class NativeActionsSet
{

    private NativeApp nativeApp = null;
    private String name = null;
    private ActionsSet actionsSet = null;

    public NativeActionsSet(NativeApp nativeApp, String actionsSetName, ActionsSet actionsSet)
    {
        this.nativeApp = nativeApp;
        this.name = actionsSetName;
        this.actionsSet = actionsSet;
    }

    public void callWeb(String actionName, JSONObject args,
            OnWebResultCallback onWebResult)
    {
        this.nativeApp.callWeb(this.name, actionName, args, onWebResult);
    }

}
