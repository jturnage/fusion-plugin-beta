package com.fusionetics.plugins.bodymap.Objects;

import android.hardware.camera2.CameraCaptureSession;

public abstract class CaptureSessionConfigured
{
    public abstract void onConfigured(CameraCaptureSession session);
    public abstract void onFailed(CameraCaptureSession session);
}
