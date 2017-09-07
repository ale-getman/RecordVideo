package dev.klippe.simplevideo;

import android.content.Context;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;

import static dev.klippe.simplevideo.MainActivity.getOptimalVideoSize;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;
        mCamera.setDisplayOrientation(90);
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d("", "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (mHolder.getSurface() == null) {
            return;
        }

        try {
            mCamera.stopPreview();
        } catch (Exception e) {
        }

        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
            Camera.Parameters params = mCamera.getParameters();
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null) {
                Log.i("video", Build.MODEL);
                if (((Build.MODEL.startsWith("GT-I950")) || (Build.MODEL.endsWith("SCH-I959"))
                        || (Build.MODEL.endsWith("MEIZU MX3"))) && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {

                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if ((Build.MODEL.startsWith("GT"))) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                } else
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            }
            List<Camera.Size> mSupportedPreviewSizes = params.getSupportedPreviewSizes();
            List<Camera.Size> mSupportedVideoSizes = params.getSupportedVideoSizes();
            Camera.Size optimalSize = getOptimalVideoSize(mSupportedVideoSizes,
                    mSupportedPreviewSizes, 1280, 720);
            params.setPreviewSize(optimalSize.width, optimalSize.height);
            mCamera.setParameters(params);

        } catch (Exception e) {
            Log.d("", "Error starting camera preview: " + e.getMessage());
        }
    }

    public void refreshCamera(Camera camera) {
        if (mHolder.getSurface() == null) {
            return;
        }
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
        }
        setCamera(camera);
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
            Camera.Parameters params = mCamera.getParameters();
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null) {
                Log.i("video", Build.MODEL);
                if (((Build.MODEL.startsWith("GT-I950"))
                        || (Build.MODEL.endsWith("SCH-I959"))
                        || (Build.MODEL.endsWith("MEIZU MX3"))) && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {

                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if ((Build.MODEL.startsWith("GT"))) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                } else
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            }
            List<Camera.Size> mSupportedPreviewSizes = params.getSupportedPreviewSizes();
            List<Camera.Size> mSupportedVideoSizes = params.getSupportedVideoSizes();
            Camera.Size optimalSize = getOptimalVideoSize(mSupportedVideoSizes,
                    mSupportedPreviewSizes, 1280, 720);
            params.setPreviewSize(optimalSize.width, optimalSize.height);
            mCamera.setParameters(params);
        } catch (Exception e) {
            Log.d(VIEW_LOG_TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    public void setCamera(Camera camera) {
        mCamera = camera;
        mCamera.setDisplayOrientation(90);
    }
}

