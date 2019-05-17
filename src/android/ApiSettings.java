package com.fusionetics.plugins.bodymap;

import com.fusionetics.plugins.bodymap.ThisPlugin;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;


// Note, this is much easier with a 3rd party like jackson.  But this being a plugin, the setup for that is a little more tedious so just keeping it simple (at the expense of a litte more code)
public class ApiSettings {

    public Integer desiredVideoWidth;
    public Integer desiredVideoHeight;
    public Integer desiredOrientation;
    public Integer desiredCamera;
    public String endpointUrl;
    public HashMap<String,String> headers = new HashMap<String,String>();

    public static ApiSettings FromJson(String data) throws JSONException {
        if(data == null) return new ApiSettings();

        JSONObject newObject = new JSONObject(data);
        return FromJson(newObject);
    }

    public static ApiSettings FromJson(JSONObject data) throws JSONException {
        ApiSettings newObject = new ApiSettings();
        if(data == null) return newObject;

        if(data.has("endpointUrl")) newObject.endpointUrl = data.getString("endpointUrl");
        if(data.has("width")) newObject.desiredVideoWidth = data.getInt("width");
        if(data.has("height")) newObject.desiredVideoHeight = data.getInt("height");
        if(data.has("orientation")) newObject.desiredOrientation = data.getInt("orientation");
        if(data.has("camera")) newObject.desiredCamera = data.getInt("camera");

        if(data.has("headers")) {
            JSONObject headersObject = data.getJSONObject("headers");

            newObject.headers.clear();

            Iterator<String> keys = headersObject.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                Object s = headersObject.get(key);
                if (s instanceof String) {
                    newObject.headers.put(key,s.toString());
                    Log.d(ThisPlugin.TAG, "key:"+key + "="+s.toString());
                }
            }
        }

        return newObject;    
    }
}

