package com.fusionetics.plugins.bodymap;

import com.fusionetics.plugins.bodymap.ThisPlugin;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;

import android.app.Activity;
import android.app.Fragment;
import android.content.pm.PackageManager;
import android.util.Log;

// import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
// import android.support.v4.content.ContextCompat;
import android.support.v13.app.FragmentCompat;

public class PermissionHelper {

    public static boolean shouldShowRequestPermissionRationale(Fragment fragment, String[] permissions) {
        for (String permission : permissions) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(fragment, permission)) {
                return true;
            }
        }
        return false;
    }

    public static void requestPermissions(Fragment fragment, String[] permissions, int request) {
        Log.d(ThisPlugin.TAG, "PermissionHelper.requestPermissions");
        FragmentCompat.requestPermissions(fragment, permissions, request);
    }
}