package com.fusionetics.plugins.bodymap;

import com.fusionetics.plugins.bodymap.ThisPlugin;
import com.fusionetics.plugins.bodymap.AutoFitTextureView;
import com.fusionetics.plugins.bodymap.BodymapEventHandler;
import com.fusionetics.plugins.bodymap.Camera;
import com.fusionetics.plugins.bodymap.Objects.Video;
import com.fusionetics.plugins.bodymap.ApiSettings;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.os.Bundle;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

// import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import org.apache.cordova.CordovaInterface;

import android.support.v13.app.FragmentCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CameraFragment extends Fragment
    implements View.OnClickListener //, SurfaceHolder.Callback
{
    private int _fragmentId = 0;

    private Activity activity = null;
    private BodymapEventHandler eventHandler = null;
    private AutoFitTextureView mTextureView;
    private int recordButtonId = 0;
    private int cancelButtonId = 0;
    private ImageButton mButtonVideo;
    private CaptureRequest.Builder mPreviewBuilder;
    private MediaRecorder mMediaRecorder;
    private CameraCaptureSession mPreviewSession;
    private boolean mIsRecordingVideo = false;
    private Camera camera = null;
    private Video video = null;

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    CameraFragment(Activity _activity, BodymapEventHandler _eventHandler) {
        this.activity = _activity;
        this.eventHandler = _eventHandler;
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int surfaceWidth, int surfaceHeight) {
            Log.d(ThisPlugin.TAG, "SurfaceTextureListener.onSurfaceTextureAvailable width:"+surfaceWidth+", height:"+surfaceHeight);
            openCamera(surfaceWidth, surfaceHeight);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int surfaceWidth, int surfaceHeight) {
            Log.d(ThisPlugin.TAG, "SurfaceTextureListener.onSurfaceTextureSizeChanged");
            configureTransform(surfaceWidth, surfaceHeight);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    public void destroyYourself() {
        if(_fragmentId != 0) {
            // derived from https://stackoverflow.com/a/31969693/1757997
            Fragment fragment = activity.getFragmentManager().findFragmentById(_fragmentId);
            activity.getFragmentManager().beginTransaction().remove(fragment).commit();
            _fragmentId = 0;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(ThisPlugin.TAG, "onCreateView");
        int _fragmentId = ThisPlugin.getAppResource(activity, "camera_fragment", "layout");
        Log.d(ThisPlugin.TAG, "CameraFragment's resource ID: " + _fragmentId);
        return inflater.inflate(_fragmentId, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        Log.d(ThisPlugin.TAG, "onViewCreated");
        int textureId = ThisPlugin.getAppResource(activity, "texture", "id");
        Log.d(ThisPlugin.TAG, "onViewCreated - textureId:"+textureId);
        mTextureView = (AutoFitTextureView) view.findViewById(textureId);

        recordButtonId = ThisPlugin.getAppResource(activity, "video", "id");
        Log.d(ThisPlugin.TAG, "onViewCreated - buttonId:"+recordButtonId);
        mButtonVideo = (ImageButton) view.findViewById(recordButtonId);
        mButtonVideo.setOnClickListener(this);
        // view.findViewById(R.id.info).setOnClickListener(this);

        cancelButtonId = ThisPlugin.getAppResource(activity, "cancel", "id");
        ImageButton cancelButton = (ImageButton) view.findViewById(cancelButtonId);
        cancelButton.setOnClickListener(this);
    }

    @Override
    public void onResume() {
        Log.d(ThisPlugin.TAG, "onResume");
        super.onResume();
        if (mTextureView.isAvailable()) {
            Log.d(ThisPlugin.TAG, "onResume - mTextureView.isAvailable() == true");
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            Log.d(ThisPlugin.TAG, "onResume - mTextureView.isAvailable() == false");
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }
    @Override
    public void onPause() {
        stopEverything();
        super.onPause();
    }

    private void stopEverything() {
        Log.d(ThisPlugin.TAG, "stopEverything");
        closePreviewSession();

        if(camera != null)
            camera.Close();

        if (null != mMediaRecorder) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }


    @SuppressWarnings("MissingPermission")
    private void openCamera(int surfaceWidth, int surfaceHeight) {
        Log.d(ThisPlugin.TAG, "openCamera");
        if (null == activity || activity.isFinishing()) {
            return;
        }
        try {
            Log.d(ThisPlugin.TAG, "openCamera - Camera.Open");

            camera = Camera.Open(activity, surfaceWidth, surfaceHeight, ThisPlugin.settings.desiredVideoWidth, ThisPlugin.settings.desiredVideoHeight, ThisPlugin.settings.desiredOrientation, ThisPlugin.settings.desiredCamera, new Runnable(){            
                @Override
                public void run() {
                    startPreview();
                    if (null != mTextureView) {
                        configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                    }
                }
            }, new Runnable(){
                @Override
                public void run() {
                    Log.e(ThisPlugin.TAG, "Camera.Open returned an error");
                }
            });

            if (camera.config.mOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(camera.config.mPreviewSize.getWidth(), camera.config.mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(camera.config.mPreviewSize.getHeight(), camera.config.mPreviewSize.getWidth());
            }

            configureTransform(surfaceWidth, surfaceHeight);
            mMediaRecorder = new MediaRecorder();

        } catch (NullPointerException e) {
            Log.e(ThisPlugin.TAG, "openCamera NPE", e);
        }
    }

    private void closeCamera() {
        stopEverything();
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Log.d(ThisPlugin.TAG, "configureTransform");
        if (null == camera || null == camera.config || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, camera.config.mPreviewSize.getHeight(), camera.config.mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / camera.config.mPreviewSize.getHeight(),
                    (float) viewWidth / camera.config.mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void startPreview() {
        if (null == camera || null == camera.config || !mTextureView.isAvailable() || null == camera.config.mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(camera.config.mPreviewSize.getWidth(), camera.config.mPreviewSize.getHeight());
            mPreviewBuilder = camera.createCaptureRequest();

            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);
            List<Surface> surfaces = Collections.singletonList(previewSurface);

            camera.createCaptureSession(surfaces, new CaptureSessionConfigured() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mPreviewSession = session;
                    updatePreview();
                }
            });
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (null == camera || !camera.isInitialized()) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            // TODO: this appears to just create and start an orphaned thread.
            // HandlerThread thread = new HandlerThread("CameraPreview");
            // thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, camera.getBackgroundHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }


    @Override
    public void onClick(View view) {
        int id = view.getId();
        if(id == recordButtonId) {
            Log.d(ThisPlugin.TAG, "Record button clicked");
            if (mIsRecordingVideo) {
                stopRecordingVideo();
            } else {
                startRecordingVideo();
            }
        }
        else if(id == cancelButtonId) {
            Log.d(ThisPlugin.TAG, "Cancel button clicked");

            AlertDialog.Builder builder = new AlertDialog.Builder(this.activity);
            builder.setTitle("Warning");
            builder.setMessage("You are about to leave this section of the test and will lose any data")
                .setCancelable(false)
                .setPositiveButton("OK", new OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        eventHandler.CancelRequested();
                    }
                })
                .setNegativeButton("Cancel",new OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                });

            // create alert dialog
            AlertDialog alertDialog = builder.create();
            Window window = alertDialog.getWindow();
            WindowManager.LayoutParams wlp = window.getAttributes();

            wlp.gravity = Gravity.CENTER;
//            wlp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            window.setAttributes(wlp);

            alertDialog.show();
        }
        else {
            Log.d(ThisPlugin.TAG, "Something clicked - ignoring");
        }
    }

    private void startRecordingVideo() {
        Log.d(ThisPlugin.TAG, "startRecordingVideo");
        if (null == camera || null == camera.config || !mTextureView.isAvailable() || null == camera.config.mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            Log.d(ThisPlugin.TAG, "startRecordingVideo - closed preview session");
            setUpMediaRecorder();
            Log.d(ThisPlugin.TAG, "startRecordingVideo - set up media recorder");
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(camera.config.mPreviewSize.getWidth(), camera.config.mPreviewSize.getHeight());
            mPreviewBuilder = camera.createCaptureRequest();
            Log.d(ThisPlugin.TAG, "startRecordingVideo - created capture request");
//            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<Surface>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);
            Log.d(ThisPlugin.TAG, "startRecordingVideo - set up preview serface");

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            camera.createCaptureSession(surfaces, new CaptureSessionConfigured() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    Log.d(ThisPlugin.TAG, "camera.createCaptureSession, in CaptureSessionConfigured.onConfigured");
                    mPreviewSession = session;
                    updatePreview();

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI
                            Log.d(ThisPlugin.TAG, "camera.createCaptureSession, calling mMediaRecorder.start()");

                            int imageId = ThisPlugin.getAppResource(activity, "btn_stop_record", "drawable");

                            mButtonVideo.setImageResource(imageId);
                            // mButtonVideo.setText("STOP"); //R.string.stop
                            mIsRecordingVideo = true;

                            // Start recording
                            mMediaRecorder.start();
                        }
                    });

                }
            });           
            Log.d(ThisPlugin.TAG, "startRecordingVideo - created capture session");

        } catch (CameraAccessException e) {
            Log.e(ThisPlugin.TAG, "startRecordingVideo exception", e);
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(ThisPlugin.TAG, "startRecordingVideo exception", e);
            e.printStackTrace();
        }

    }

    private void stopRecordingVideo() {
        Log.d(ThisPlugin.TAG, "stopRecordingVideo");

        // UI
        mIsRecordingVideo = false;

        int imageId = ThisPlugin.getAppResource(activity, "btn_start_record", "drawable");
        mButtonVideo.setImageResource(imageId);

        // Stop recording
        if(mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        }

        if (null != activity) {
            Log.d(ThisPlugin.TAG, "Video saved: " + video.fullFilename);
        }

        stopEverything();
        eventHandler.OnVideoRecorded(video);
    }


    private void closePreviewSession() {
        Log.d(ThisPlugin.TAG, "closePreviewSession");
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private void setUpMediaRecorder() throws IOException {
        Log.d(ThisPlugin.TAG, "setUpMediaRecorder");

//        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        Log.d(ThisPlugin.TAG, "setUpMediaRecorder - set a/v sources");
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        Log.d(ThisPlugin.TAG, "setUpMediaRecorder - set output format");
        mMediaRecorder.setMaxDuration(20000);
        Log.d(ThisPlugin.TAG, "setUpMediaRecorder - set max duration");

        mMediaRecorder.setOnInfoListener(new OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {                     
                Log.d(ThisPlugin.TAG, "setUpMediaRecorder - OnInfoListener - onInfo hit");
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    Log.d(ThisPlugin.TAG, "setUpMediaRecorder - OnInfoListener - onInfo - max duration");
                    stopRecordingVideo();
                } else {
                    Log.d(ThisPlugin.TAG, "setUpMediaRecorder - OnInfoListener - onInfo - something else");
                }
            }
        });

        video = setupVideo();

        mMediaRecorder.setOutputFile(video.fullFilename);
        Log.d(ThisPlugin.TAG, "setUpMediaRecorder - set output file");
        mMediaRecorder.setVideoEncodingBitRate(2600000);
        Log.d(ThisPlugin.TAG, "setUpMediaRecorder - set encoding bit rate");
        mMediaRecorder.setVideoFrameRate(24);
        Log.d(ThisPlugin.TAG, "setUpMediaRecorder - set video frame rate");

        Log.d(ThisPlugin.TAG, "setUpMediaRecorder - video size to set= width:"+video.actualWidth+", height:"+video.actualHeight);
        mMediaRecorder.setVideoSize(video.actualWidth, video.actualHeight);
        Log.d(ThisPlugin.TAG, "setUpMediaRecorder - set video size");

        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        Log.d(ThisPlugin.TAG, "setUpMediaRecorder - setup encoders");

        Integer rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        video.deviceRotation = rotation;
        Log.d(ThisPlugin.TAG, "setUpMediaRecorder - window rotation:"+rotation);
        Log.d(ThisPlugin.TAG, "setUpMediaRecorder - sensor orientation:"+camera.config.mSensorOrientation);

        switch (camera.config.mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                Log.d(ThisPlugin.TAG, "setUpMediaRecorder - sensor orientation is default");
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                Log.d(ThisPlugin.TAG, "setUpMediaRecorder - sensor orientation is inverse");
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
            default:
                Log.e(ThisPlugin.TAG, "setUpMediaRecorder - sensor orientation is not default or inverse, something else!!!");
                break;
        }

        mMediaRecorder.prepare();
    }

    private Video setupVideo() {
        Video video = new Video();
        String dir = getVideoTempDir(activity);
        video.path = dir;
        video.fullFilename = getVideoFilePath(activity, dir);
        video.actualWidth = camera.config.mVideoSize.getWidth();
        video.actualHeight = camera.config.mVideoSize.getHeight();
        video.sensorOrientation = camera.config.mSensorOrientation;
        return video;
    }

    private String getVideoTempDir(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"));
    }

    private String getVideoFilePath(Context context, String dir) {
        return dir + System.currentTimeMillis() + ".mp4";
    }

}
