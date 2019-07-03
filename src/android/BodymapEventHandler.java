package com.fusionetics.plugins.bodymap;
import com.fusionetics.plugins.bodymap.Objects.Video;

public abstract class BodymapEventHandler {

    public void CancelRequested() {}

    public void OnVideoRecorded(Video video) {}

    public void RetakeVideoRequested(Video video) {}

    public void RecordedVideoAccepted(Video video) {}

    public void RecordedVideoUploaded(Video video) {}
}
