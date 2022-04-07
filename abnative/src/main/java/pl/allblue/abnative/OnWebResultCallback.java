package pl.allblue.abnative;

import org.json.JSONException;
import org.json.JSONObject;

public interface OnWebResultCallback
{

    void call(JSONObject result) throws JSONException;

}
