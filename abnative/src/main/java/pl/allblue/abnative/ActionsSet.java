package pl.allblue.abnative;

import android.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class ActionsSet
{

    private Map<String, Pair<NativeAction, NativeActionCallback>> actions_Native = null;

    public ActionsSet()
    {
        this.actions_Native = new HashMap<>();
    }

    public ActionsSet addNative(String actionName, NativeAction action)
    {
        this.actions_Native.put(actionName, new Pair(action, null));

        return this;
    }

    public ActionsSet addNativeCallback(String actionName,
            NativeActionCallback actionCallback)
    {
        this.actions_Native.put(actionName, new Pair(null, actionCallback));

        return this;
    }

    public Pair<NativeAction, NativeActionCallback> getNative(String actionName)
    {
        return this.actions_Native.get(actionName);
    }

}
