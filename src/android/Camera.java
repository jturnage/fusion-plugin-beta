package com.fusionetics.plugins.bodymap;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.security.Permission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import java.util.List;


public class Camera extends CameraDevice.StateCallback {

    public CameraConfig config = null;

    private CameraDevice mCameraDevice;
    private static Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private Runnable startPreview = null;
    private Runnable onError = null;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    @Override
    public void onOpened( CameraDevice cameraDevice) {
        mCameraDevice = cameraDevice;
        if(startPreview != null)
            startPreview.run();
        mCameraOpenCloseLock.release();
    }

    @Override
    public void onDisconnected( CameraDevice cameraDevice) {
        mCameraOpenCloseLock.release();
        mCameraDevice = cameraDevice;
        closeCameraDevice();
    }

    @Override
    public void onError( CameraDevice cameraDevice, int error) {
        mCameraOpenCloseLock.release();
        mCameraDevice = cameraDevice;
        closeCameraDevice();
        if(onError != null)
            onError.run();
    }

    private void closeCameraDevice() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    public boolean isInitialized() {
        return this.config != null && this.mCameraDevice != null;
    }

    public CaptureRequest.Builder createCaptureRequest() throws CameraAccessException {
        return mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
    };

    public void createCaptureSession(List<Surface> surfaces, CaptureSessionConfigured onceConfigured) throws CameraAccessException {

        final CaptureSessionConfigured onceConfiguredInner = onceConfigured;

        mCameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(/*@NonNull*/ CameraCaptureSession session) {
                            if(onceConfiguredInner != null) {
                                Log.d(ThisPlugin.TAG, "createCaptureSession onConfigured");
                                onceConfiguredInner.onConfigured(session);
                            }
                        }

                        @Override
                        public void onConfigureFailed(/*@NonNull*/ CameraCaptureSession session) {
                            Log.e(ThisPlugin.TAG, "createCaptureSession onConfigureFailed");
                        }
                    }, mBackgroundHandler);
    }


    public void Open(Activity activity, CameraConfig config, Runnable startPreview, Runnable onError) throws CameraAccessException {
        Log.d(ThisPlugin.TAG, "camera.Open (instance)");
        startBackgroundThread();

        this.startPreview = startPreview;
        this.onError = onError;
        this.config = config;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        manager.openCamera(config.cameraId, /*mStateCallback*/ this, null);
    }

    public void Close()
    {
        Log.d(ThisPlugin.TAG, "camera.Close (instance)");
        try {
            mCameraOpenCloseLock.acquire();
            closeCameraDevice();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }

        stopBackgroundThread();
    }

    public Handler getBackgroundHandler() {
        return mBackgroundHandler;
    }


    private void startBackgroundThread() {
        Log.d(ThisPlugin.TAG, "startBackgroundThread");
        stopBackgroundThread();
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        Log.d(ThisPlugin.TAG, "stopBackgroundThread");
        if(mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            Log.d(ThisPlugin.TAG, "stopBackgroundThread - thread quit");
            try {
                mBackgroundThread.join();
                Log.d(ThisPlugin.TAG, "stopBackgroundThread - thread joined");
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }    
        } else {
            Log.d(ThisPlugin.TAG, "stopBackgroundThread - thread already null");
        }
    }


    public static Camera Open(Activity activity, int surfaceWidth, int surfaceHeight, int videoWidth, int videoHeight, int orientation, int whichCamera, Runnable startPreview, Runnable onError) {
        Log.d(ThisPlugin.TAG, "Camera.Open (static)");

        try {
            
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            String cameraId = manager.getCameraIdList()[whichCamera];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }
    
            CameraConfig config = new CameraConfig();
            config.cameraId = cameraId;
            // if(orientation == 0) {
                config.mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            // } else {
            //     config.mSensorOrientation = orientation;
            // }
            config.mVideoSize = chooseVideoSize(videoWidth, videoHeight, orientation, map.getOutputSizes(MediaRecorder.class));
            config.mPreviewSize = chooseOptimalPreviewSize(map.getOutputSizes(SurfaceTexture.class), surfaceWidth, surfaceHeight, config.mVideoSize);
            config.mOrientation = activity.getResources().getConfiguration().orientation;
            Log.d(ThisPlugin.TAG, "Camera config: sensorOrientation:"+config.mSensorOrientation + ", videoSize:"+config.mVideoSize + ", previewSize:"+config.mPreviewSize + ", orientation:"+config.mOrientation);

            Camera camera = new Camera();
            camera.Open(activity, config, startPreview, onError);
            
            return camera;    
        } catch (CameraAccessException e) {
            Log.e(ThisPlugin.TAG, "Camera.Open CAE", e);
            throw new RuntimeException("CameraAccessException.");
        } catch (NullPointerException e) {
            Log.e(ThisPlugin.TAG, "Camera.Open NPE", e);
            throw new RuntimeException("NullPointerException.");
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private static Size chooseVideoSize(int desiredWidth, int desiredHeight, int orientation, Size[] choices) {
        Size size = chooseExactVideoSize(desiredWidth, desiredHeight, orientation, choices);
        if(size == null) {
            size = chooseBestVideoSizeWithSameAspectRatio(desiredWidth, desiredHeight, orientation, choices);
        }
        if(size == null) {
            size = chooseSmallestVideoSizeThatsBiggerThanDesired(desiredWidth, desiredHeight, orientation, choices);
        }
        if(size == null) {
            Log.e(ThisPlugin.TAG, "Couldn't find any suitable video size, going with default");
            size = choices[choices.length - 1];
        }
        return size;
    }

    private static Size chooseExactVideoSize(int desiredWidth, int desiredHeight, int orientation, Size[] choices) {
        for (Size size : choices) {
            int width = size.getWidth();
            int height = size.getHeight();
            Log.d(ThisPlugin.TAG, "chooseExactVideoSize - orientation:" + orientation + " width:" + width + " height:" + height);
            if((orientation == Configuration.ORIENTATION_PORTRAIT && width == desiredWidth && height == desiredHeight) || 
               (orientation == Configuration.ORIENTATION_LANDSCAPE && width == desiredHeight && height == desiredWidth)) {
                Log.d(ThisPlugin.TAG, "Bingo...");
                return size;
            }
        }
        Log.e(ThisPlugin.TAG, "Couldn't find an exact video size");
        return null;
    }
    private static Size chooseBestVideoSizeWithSameAspectRatio(int desiredWidth, int desiredHeight, int orientation, Size[] choices) {
        for (int i = choices.length - 1 ; i >= 0 ; i--) {
            Size size = choices[i];

            int width = size.getWidth();
            int height = size.getHeight();

            Log.d(ThisPlugin.TAG, "chooseBestVideoSizeWithSameAspectRatio - orientation:" + orientation + " width:"+width + " height:"+height);

            // If it's too small in either direction, we don't want it.  We want at least as big as the desired size, not smaller
            if((orientation == Configuration.ORIENTATION_PORTRAIT && (width < desiredWidth || height < desiredHeight)) ||
               (orientation == Configuration.ORIENTATION_LANDSCAPE && (width < desiredHeight || height < desiredWidth))) {
                Log.d(ThisPlugin.TAG, "chooseBestVideoSizeWithSameAspectRatio - this one's too small in one or more of the directions");
                continue;
            }

            double desiredAr = 0.0;
            double thisAr = (double) width / (double) height;
            if(orientation == Configuration.ORIENTATION_PORTRAIT) {
                desiredAr = (double) desiredHeight / (double) desiredWidth;
            } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                desiredAr = (double) desiredWidth / (double) desiredHeight;
            }
            Log.d(ThisPlugin.TAG, "chooseBestVideoSizeWithSameAspectRatio - desired AR:" + desiredAr + ", this AR:" + thisAr);
            if(desiredAr == thisAr) {
                Log.d(ThisPlugin.TAG, "chooseBestVideoSizeWithSameAspectRatio - this one works");
                return size;
            }
        }

        Log.e(ThisPlugin.TAG, "Couldn't find a video size with the same aspect ratio");
        return null;
    }
    private static Size chooseSmallestVideoSizeThatsBiggerThanDesired(int desiredWidth, int desiredHeight, int orientation, Size[] choices) {
        for (int i = choices.length - 1 ; i >= 0 ; i--) {
            Size size = choices[i];

            int width = size.getWidth();
            int height = size.getHeight();

            Log.d(ThisPlugin.TAG, "chooseSmallestVideoSizeThatsBiggerThanDesired - orientation:" + orientation + " width:"+width + " height:"+height);

            // If it's too small in either direction, we don't want it.  We want at least as big as the desired size, not smaller
            if((orientation == Configuration.ORIENTATION_PORTRAIT && (width < desiredWidth || height < desiredHeight)) ||
               (orientation == Configuration.ORIENTATION_LANDSCAPE && (width < desiredHeight || height < desiredWidth))) {
                Log.d(ThisPlugin.TAG, "chooseSmallestVideoSizeThatsBiggerThanDesired - this one's too small in one or more of the directions");
                continue;
            }
            Log.d(ThisPlugin.TAG, "chooseSmallestVideoSizeThatsBiggerThanDesired - this one works");
            return size;
        }

        Log.e(ThisPlugin.TAG, "Couldn't find a video size with the same aspect ratio");
        return null;
    }


    private static Size chooseOptimalPreviewSize(Size[] choices, int surfaceWidth, int surfaceHeight, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                option.getWidth() >= surfaceWidth && 
                option.getHeight() >= surfaceHeight) {

                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(ThisPlugin.TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }


    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }


}

abstract class CaptureSessionConfigured
{
    public abstract void onConfigured(CameraCaptureSession session);
    //  {
    //     mPreviewSession = session;
    //     updatePreview();
    // }

}

class CameraConfig {
    public Integer mSensorOrientation;
    public Size mPreviewSize;
    public Size mVideoSize;
    public Integer mOrientation;
    public String cameraId;
}

