package pl.allblue.abnative;

import org.json.JSONException;
import org.json.JSONObject;

public interface NativeActionCallback
{

    void call(JSONObject args, ActionResultCallback resultCallback)
            throws JSONException;

}
