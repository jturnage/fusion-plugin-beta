package com.fusionetics.plugins.bodymap;
import android.app.Activity;
import com.fusionetics.plugins.bodymap.FusioneticsExercise;
import com.fusionetics.plugins.bodymap.ApiSettings;

public class ThisPlugin {

    public static final String TAG = "FusioneticsBodymap";

    public static int getAppResource(Activity activity, String name, String type) {
        return activity.getResources().getIdentifier(name, type, activity.getPackageName());
    }

    public static ApiSettings settings = null;
    public static FusioneticsExercise exercise = null;

    public static Boolean seenMediaInstructions = false;
    public static Boolean debug = false;
}
