package com.fusionetics.plugins.bodymap;

import com.fusionetics.plugins.bodymap.ThisPlugin;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Note, this is much easier with a 3rd party like jackson.  But this being a plugin, the setup for that is a little more tedious so just keeping it simple (at the expense of a litte more code)
public class FusioneticsExercise {
    public Integer testId;
    public Integer testTypeId;
    public String uniqueId;
    public String version;
    public Integer viewId;
    public Integer exerciseId;
    public Integer bodySideId;
    public String name;
    public String filePrefix;
    public String videoUrl;

    public static FusioneticsExercise FromJson(String data) throws JSONException {
        if(data == null) return new FusioneticsExercise();

        JSONObject newObject = new JSONObject(data);
        return FromJson(newObject);
    }

    public static FusioneticsExercise FromJson(JSONObject data) throws JSONException {
        FusioneticsExercise newObject = new FusioneticsExercise();
        if(data == null) return newObject;

        if(data.has("testId")) newObject.testId = data.optInt("testId");
        if(data.has("testTypeId")) newObject.testTypeId = data.optInt("testTypeId");
        if(data.has("viewId")) newObject.viewId = data.optInt("viewId");
        if(data.has("exerciseId")) newObject.exerciseId = data.optInt("exerciseId");
        if(data.has("bodySideId")) {
            int i = data.optInt("bodySideId"); // returns 0 as a fallback
            if(i != 0) {
                newObject.bodySideId = i;
            }
        }
        if(data.has("uniqueId")) newObject.uniqueId = data.getString("uniqueId");
        if(data.has("version")) newObject.version = data.getString("version");
        if(data.has("name")) newObject.name = data.getString("name");
        if(data.has("filePrefix")) newObject.filePrefix = data.getString("filePrefix");

        if(data.has("videoUrl")) newObject.videoUrl = data.getString("videoUrl");

        return newObject;
    }
}

