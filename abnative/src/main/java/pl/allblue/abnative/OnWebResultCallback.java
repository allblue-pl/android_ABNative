package pl.allblue.abnative;

import org.json.JSONException;
import org.json.JSONObject;

public interface OnWebResultCallback
{

    void onResult(JSONObject result) throws JSONException;
    void onError(String error);

}
