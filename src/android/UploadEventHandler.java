package com.fusionetics.plugins.bodymap;

public abstract class UploadEventHandler {
    public void OnProgress(int percent) {}
    public void OnCompleted() {}
    public void OnFailure() {}
}
