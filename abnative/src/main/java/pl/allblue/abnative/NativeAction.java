package pl.allblue.abnative;

import org.json.JSONException;
import org.json.JSONObject;

public interface NativeAction
{

    JSONObject call(JSONObject args) throws JSONException;

}
