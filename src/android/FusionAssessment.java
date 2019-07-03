package com.fusionetics.plugins.bodymap;

import com.fusionetics.plugins.bodymap.ThisPlugin;
import com.fusionetics.plugins.bodymap.FusioneticsExercise;
import com.fusionetics.plugins.bodymap.ApiSettings;
import com.fusionetics.plugins.bodymap.VideoSubmitter;
import com.fusionetics.plugins.bodymap.UploadEventHandler;
import com.fusionetics.plugins.bodymap.FusionResult;
import com.fusionetics.plugins.bodymap.Objects.Video;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import java.io.File;
import java.lang.reflect.Method; 
import java.lang.reflect.Field; 
import java.lang.reflect.Constructor; 
import java.util.HashMap;

/**
 * This class echoes a string called from JavaScript.
 */
public class FusionAssessment extends CordovaPlugin {

    private static final String TAG = ThisPlugin.TAG;
    private CallbackContext callbackContext = null;
    private FusioneticsExercise exercise = null;
    private ApiSettings settings = null;

    private static final int TAKEVIDEO_VIEW_ID = 10101010;
    private static final int SHOWVIDEO_VIEW_ID = 10101011;
    private static final String[] VIDEO_PERMISSIONS = {
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET,
    };
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;

    // Ideally these should come in via the settings object
    private static final int defaultDesiredVideoWidth = 540;
    private static final int defaultDesiredVideoHeight = 960;
    private static final int defaultDesiredOrientation = Configuration.ORIENTATION_PORTRAIT;
    private static final int defaultDesiredCamera = 0; // 0=BACK_CAMERA


    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {

        Log.d(TAG, "Initialize");

        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, "In execute");
        this.callbackContext = callbackContext;

        loadParams(args);

        if (action.equals("takeVideo")) {
            Log.d(TAG, "takeVideo making a permissions request");
            cordova.requestPermissions(this, REQUEST_VIDEO_PERMISSIONS, VIDEO_PERMISSIONS);
            return true;
        }

        Log.e(TAG, "Invalid action - " + action);
        return false;
    }

    private void loadParams(JSONArray args) throws JSONException {
        if(args != null) {

            String exerciseString = args.getString(0);
            String settingsString = args.getString(1);

            Log.d(ThisPlugin.TAG, "Exercise: "+exerciseString);
            Log.d(ThisPlugin.TAG, "Settings: "+settingsString);

            exercise = FusioneticsExercise.FromJson(exerciseString);
            settings = ApiSettings.FromJson(settingsString);
            settings.desiredVideoWidth = settings.desiredVideoWidth != null ? settings.desiredVideoWidth : defaultDesiredVideoWidth;
            settings.desiredVideoHeight = settings.desiredVideoHeight != null ? settings.desiredVideoHeight : defaultDesiredVideoHeight;
            settings.desiredOrientation = settings.desiredOrientation != null ? settings.desiredOrientation : defaultDesiredOrientation;
            settings.desiredCamera = settings.desiredCamera != null ? settings.desiredCamera : defaultDesiredCamera;

            ThisPlugin.exercise = exercise;
            ThisPlugin.settings = settings;
            
            Log.v(ThisPlugin.TAG, "Exercise.testId: "+exercise.testId);
        }
    }

    private BodymapEventHandler eventHandler = new BodymapEventHandler(){
        @Override
        public void CancelRequested() {
            Log.d(ThisPlugin.TAG, "BodyMapEventHandler.CancelRequested");
            FusionResult result = new FusionResult();
            result.setCancelled(true);
            endWithResponse(result);
        }

        @Override
        public void OnVideoRecorded(Video video) {
            Log.d(ThisPlugin.TAG, "BodyMapEventHandler.OnVideoRecorded -path:" + video.fullFilename);
            showVideo(video);
        }

        @Override
        public void RetakeVideoRequested(Video video) {
            Log.d(ThisPlugin.TAG, "BodyMapEventHandler.RetakeVideoRequested");
            takeVideo();
        }

        @Override public void RecordedVideoUploaded(Video video) {
            Log.d(ThisPlugin.TAG, "BodyMapEventHandler.RecordedVideoUploaded");
            FusionResult result = new FusionResult();
            result.setCapturedVideo(true);
            result.setVideoUrl(ThisPlugin.exercise.videoUrl);
            endWithResponse(result);
        }
    };

    private void endWithResponse(FusionResult result) {
        killShowVideoWorkerView();
        killTakeVideoWorkerView();

        Log.d(TAG, "setting contentView");
        cordova.getActivity().setContentView(webView.getView());

        String d = result.toJson();
        Log.d(ThisPlugin.TAG, "Returning json: " + d);
        callbackContext.success(d);
    }

    private void showVideo(Video video) {
        Log.d(TAG, "In showVideo");

        final Video videoInner = video;

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "In showVideo - UI thread");
                showVideoWorker(videoInner);
            }
        });
    }

    private void takeVideo() {
        Log.d(TAG, "In takeVideo");

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "In takeVideo - UI thread");
                takeVideoWorker();
            }
        });
    }

    private void killShowVideoWorkerView() {
        if(_previewFragment != null) {
            Log.d(TAG, "_previewFragment exists, let's destroy it");
            _previewFragment.destroyYourself();
            _previewFragment = null;
        }
    }
    private void killTakeVideoWorkerView() {
        if(_cameraFragment != null) {
            Log.d(TAG, "_cameraFragment exists, let's destroy it");
            _cameraFragment.destroyYourself();
            _cameraFragment = null;
        }
    }

    private CameraFragment _cameraFragment = null;
    private PreviewFragment _previewFragment = null;
    private void showVideoWorker(Video video) {
        try {
            // // Get screen dimensions
            // DisplayMetrics displaymetrics = new DisplayMetrics();
            // cordova.getActivity().getWindowManager().getDefaultDisplay().getRealMetrics(displaymetrics);
            FrameLayout _frameLayout = new FrameLayout(cordova.getActivity().getApplicationContext());
            _frameLayout.setId(SHOWVIDEO_VIEW_ID);
            _frameLayout.setBackgroundColor(Color.parseColor("#FFFDDB"));
            
            _previewFragment = new PreviewFragment(cordova.getActivity(), eventHandler, video);
            cordova.getActivity()
                .getFragmentManager()
                .beginTransaction()
                .add(SHOWVIDEO_VIEW_ID, _previewFragment)
                .commit();

            cordova.getActivity().setContentView(_frameLayout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        } catch (Exception e) {
            Log.e(TAG, "showVideoWorker exception", e);
            callbackContext.error(e.getMessage());
        }
    }

    private void takeVideoWorker() {
        try {
            FrameLayout frameLayout = new FrameLayout(cordova.getActivity().getApplicationContext());
            frameLayout.setId(TAKEVIDEO_VIEW_ID);
            frameLayout.setBackgroundColor(Color.parseColor("#FFFDDB"));
            
            _cameraFragment = new CameraFragment(cordova.getActivity(), eventHandler);
            cordova.getActivity()
                .getFragmentManager()
                .beginTransaction()
                .add(TAKEVIDEO_VIEW_ID, _cameraFragment)
                .commit();

            cordova.getActivity().setContentView(frameLayout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        } catch (Exception e) {
            Log.e(TAG, "takeVideoWorker exception", e);
            callbackContext.error(e.getMessage());
        }
    }

    private View getView() {
        try {
            Log.d(ThisPlugin.TAG, "getView");
            // I've seen this in a few places on SO so just going with it. I'm not sure why 
            // webView.getView() wouldn't work (and maybe it does..? i haven't tried, just kinda using this since I've seen it several places and can confirm it works)
            return (View)webView.getClass().getMethod("getView").invoke(webView);
        } catch (Exception e) {
            Log.e(ThisPlugin.TAG, "getView exception", e);
            return (View)webView;
        }
    }

    public void onRequestPermissionResult(
            int requestCode, 
            /*@NonNull*/ String[] permissions,
            /*@NonNull*/ int[] grantResults) {

        Log.d(TAG, "onRequestPermissionResult");
        if(grantResults != null) {
            for(int r: grantResults) {
                if(r == PackageManager.PERMISSION_DENIED)
                {
                    Log.d(TAG, "onRequestPermissionResult");
                    this.callbackContext.error("permission denied");
                    return;
                }
            }    
        }

        if(requestCode == REQUEST_VIDEO_PERMISSIONS) {
            this.takeVideo();
        }

    }


}
