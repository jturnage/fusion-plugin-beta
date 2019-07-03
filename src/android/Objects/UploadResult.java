package com.fusionetics.plugins.bodymap.Objects;
import com.fusionetics.plugins.bodymap.ThisPlugin;

public class UploadResult {

    private Boolean success = false;
    private String response = null;
    private Exception e = null;

    public void SetSuccess(String message) {
        this.success = true;
        this.response = message;
    }

    public void SetFailure(String message) {
        this.success = false;
        this.response = message;
    }

    public void SetFailure(String message, Exception ex) {
        this.success = false;
        this.response = message;
        this.e = ex;
    }

    public Boolean WasSuccessful() {
        return this.success;
    }
}

