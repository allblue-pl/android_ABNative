package pl.allblue.abnative;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class ActionResultCallback {

    public abstract void onResult(JSONObject result);
    public abstract void onError(Exception e);

}
