package com.fusionetics.plugins.bodymap;

import com.fusionetics.plugins.bodymap.ThisPlugin;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

public class FusionResult {
    private boolean cancelled = false;
    private boolean capturedImage = false;
    private boolean capturedVideo = false;
    private String videoUrl = null;
    private String videoImage = null;
    private int videoTimestamp;

    public boolean getCancelled() {
        return cancelled;
    }
    public boolean getCapturedImage() {
        return capturedImage;
    }
    public boolean getCapturedVideo() {
        return capturedVideo;
    }
    public String getVideoUrl() {
        return videoUrl;
    }
    public String getVideoImage() {
        return videoImage;
    }
    public int getVideoTimestamp() {
        return videoTimestamp;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
    public void setCapturedImage(boolean capturedImage) {
        this.capturedImage = capturedImage;
    }
    public void setCapturedVideo(boolean capturedVideo) {
        this.capturedVideo = capturedVideo;
    }
    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
    public void setVideoImage(String videoImage) {
        this.videoImage = videoImage;
    }
    public void setVideoTimestamp(int videoTimestamp) {
        this.videoTimestamp = videoTimestamp;
    }
    
    public String toJson() {
        try {
            JSONObject js = new JSONObject();

            js.put("cancelled", this.cancelled);
            js.put("capturedImage", this.capturedImage);
            js.put("capturedVideo", this.capturedVideo);
            js.put("videoUrl", this.videoUrl);
            js.put("videoImage", this.videoImage);
            js.put("videoTimestamp", this.videoTimestamp);
    
            return js.toString();
                
        } catch(JSONException je) {
            Log.e(ThisPlugin.TAG, "FusionResult.toJson - exception", je);
            je.printStackTrace();
            return null;
        }
    }
    
}

