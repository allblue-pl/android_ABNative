package pl.allblue.abnative;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ActionsSet
{

    private Map<String, NativeAction> actions_Native = null;

    public ActionsSet()
    {
        this.actions_Native = new HashMap<>();
    }

    public ActionsSet addNative(String actionName, NativeAction action)
    {
        this.actions_Native.put(actionName, action);

        return this;
    }

    public NativeAction getNative(String actionName)
    {
        return this.actions_Native.get(actionName);
    }

}
