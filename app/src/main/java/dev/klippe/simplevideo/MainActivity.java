package dev.klippe.simplevideo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity implements ProgressTimer.OnTimer {

    public static final int PERMISSION_REQUEST_CODE = 200;
    public static int LIBRARY_REQUEST = 1;

    private ImageView flash, front, record, back;
    private TextView gallery;
    private ProgressBar progress;
    private FrameLayout myCameraPreview;
    private VideoView videoView;
    private RelativeLayout relativeTop, relativeBot;

    private MediaController mediaController;
    private Camera myCamera;
    private CameraPreview mPreview;
    private MediaRecorder mediaRecorder;
    private int camId = 0;

    public WindowManager windowManager;
    public Display display;
    public int orientation = 1;

    private Handler handler;
    private String videoPath;
    private Runnable runnable;

    private ProgressTimer progressTimer;
    private int timerProgress = 100;

    private int countClickTouch = 0;
    private boolean isFlashOn = false;
    private boolean isFrontCam = false;
    private boolean flagClickTouch = false;
    private boolean permissionsFlag = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        fullScrenn();
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            checkPermission();
    }

    public void fullScrenn() {
        if (Build.VERSION.SDK_INT < 16) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void checkPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestMultiplePermissions();
        } else
            permissionsFlag = true;
    }

    public void requestMultiplePermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST_CODE) {
            requestMultiplePermissions();
        } else {
            permissionsFlag = true;
        }
    }

    private void initView() {
        gallery = (TextView) findViewById(R.id.gallery);
        flash = (ImageView) findViewById(R.id.flash);
        front = (ImageView) findViewById(R.id.front);
        record = (ImageView) findViewById(R.id.record);
        back = (ImageView) findViewById(R.id.back);
        progress = (ProgressBar) findViewById(R.id.progress);
        videoView = (VideoView) findViewById(R.id.showVideo);
        relativeBot = (RelativeLayout) findViewById(R.id.relative_bot);
        relativeTop = (RelativeLayout) findViewById(R.id.relative_top);
        handler = new Handler(Looper.getMainLooper());

        runnable = new Runnable() {
            @Override
            public void run() {
                startRecording();
            }
        };

        mediaController = new MediaController(MainActivity.this);
        mediaController.setAnchorView(videoView);

        myCamera = getCameraInstance(camId);
        Camera.Parameters params = myCamera.getParameters();
        List<Camera.Size> mSupportedPreviewSizes = params.getSupportedPreviewSizes();
        List<Camera.Size> mSupportedVideoSizes = params.getSupportedVideoSizes();
        Camera.Size optimalSize = getOptimalVideoSize(mSupportedVideoSizes,
                mSupportedPreviewSizes, 1280, 720);
        params.setPreviewSize(optimalSize.width, optimalSize.height);
        myCamera.setParameters(params);

        mPreview = new CameraPreview(this, myCamera);
        myCameraPreview = (FrameLayout) findViewById(R.id.frameVideo);
        myCameraPreview.removeAllViews();
        myCameraPreview.addView(mPreview);
    }

    private void listeners() {
        gallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent selectIntent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(selectIntent, LIBRARY_REQUEST);
            }
        });
        front.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isFrontCam) {
                    isFrontCam = false;
                    releaseCamera();
                    camId = 0;
                    myCamera = getCameraInstance(camId);
                    mPreview.refreshCamera(myCamera);
                } else {
                    isFrontCam = true;
                    releaseCamera();
                    camId = findFrontFacingCamera();
                    myCamera = getCameraInstance(camId);
                    mPreview.refreshCamera(myCamera);
                }
            }
        });

        flash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                    return;
                }
                if (isFlashOn) {
                    isFlashOn = false;
                    Camera.Parameters p = myCamera.getParameters();
                    p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    myCamera.setParameters(p);
                } else {
                    isFlashOn = true;
                    Camera.Parameters p = myCamera.getParameters();
                    p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    myCamera.setParameters(p);
                }
            }
        });

        record.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        handler.postDelayed(runnable, 500);
                        break;

                    case MotionEvent.ACTION_UP:
                        if (flagClickTouch) {
                            if (countClickTouch > 0) {
                                stopRecording();
                                flagClickTouch = false;
                            }
                        } else
                            handler.removeCallbacks(runnable);
                        break;
                }
                return false;
            }
        });

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stateViewScreen(false);
            }
        });
    }

    public void stopRecording() {
        mediaRecorder.stop();
        progressTimer.cancel();
        myCamera.lock();
        countClickTouch = 0;
        releaseMediaRecorder();
        stateViewScreen(true);
    }

    public void startRecording() {
        releaseCamera();

        if (!prepareMediaRecorder()) {
            Toast.makeText(MainActivity.this,
                    "Fail in prepareMediaRecorder()!\n - Ended -",
                    Toast.LENGTH_LONG).show();
            finish();
        }

        flagClickTouch = true;
        mediaRecorder.start();
        progressTimer = new ProgressTimer(timerProgress * 100, 100);
        progressTimer.setListener(MainActivity.this);
        progressTimer.start();
    }

    public void stateViewScreen(boolean f) {
        if (f) {
            mPreview.setLayoutParams(new FrameLayout.LayoutParams(1, 1));

            relativeBot.setVisibility(View.GONE);
            myCameraPreview.setVisibility(View.INVISIBLE);
            videoView.setVisibility(View.VISIBLE);
            relativeTop.setVisibility(View.VISIBLE);

            videoView.setVideoURI(Uri.parse(videoPath));
//            videoView.setMediaController(mediaController);
            videoView.start();
        } else {
            mPreview.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            relativeBot.setVisibility(View.VISIBLE);
            myCameraPreview.setVisibility(View.VISIBLE);
            videoView.setVisibility(View.GONE);
            relativeTop.setVisibility(View.GONE);

            timerProgress = 100;
            progress.setProgress(0);
        }
    }

    public static Camera getCameraInstance(int cameraId) {
        Camera c = null;
        try {
            c = Camera.open(cameraId);
        } catch (Exception e) {
            Log.e("camera instance", e.getMessage());
        }
        return c;
    }

    private int findFrontFacingCamera() {
        int cameraId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                Log.d("", "Camera found");
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    private String getFileName_CustomFormat() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH_mm_ss");
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }

    private boolean prepareMediaRecorder() {
        if (myCamera == null) {
            releaseCamera();
            myCamera = getCameraInstance(camId);
            Camera.Parameters params = myCamera.getParameters();
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

            if (!isFlashOn)
                params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            else
                params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);

            List<Camera.Size> mSupportedPreviewSizes = params.getSupportedPreviewSizes();
            List<Camera.Size> mSupportedVideoSizes = params.getSupportedVideoSizes();
            Camera.Size optimalSize = getOptimalVideoSize(mSupportedVideoSizes,
                    mSupportedPreviewSizes, 1280, 720);

            params.setPreviewSize(optimalSize.width, optimalSize.height);
            myCamera.setParameters(params);
            switch(orientation){
                case 0:
                    myCamera.setDisplayOrientation(90);
                    break;
                case 1:
                    myCamera.setDisplayOrientation(0);
                    break;
                case 2:
                    myCamera.setDisplayOrientation(270);
                    break;
                case 3:
                    myCamera.setDisplayOrientation(180);
                    break;
            }
        }

        mediaRecorder = new MediaRecorder();
        myCamera.unlock();
        mediaRecorder.setCamera(myCamera);
        Log.e("LOGI", "orientation " + getResources().getConfiguration().orientation);
        if (isFrontCam) {
            switch(orientation) {
                case 0:
                    mediaRecorder.setOrientationHint(270);
                    break;
                case 1:
                    mediaRecorder.setOrientationHint(0);
                    break;
                case 2:
                    mediaRecorder.setOrientationHint(90);
                    break;
                case 3:
                    mediaRecorder.setOrientationHint(180);
                    break;
            }
        } else {
            switch(orientation) {
                case 0:
                    mediaRecorder.setOrientationHint(90);
                    break;
                case 1:
                    mediaRecorder.setOrientationHint(0);
                    break;
                case 2:
                    mediaRecorder.setOrientationHint(270);
                    break;
                case 3:
                    mediaRecorder.setOrientationHint(180);
                    break;
            }
        }


        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        mediaRecorder.setOutputFile("/sdcard/" + "Simple-" + getFileName_CustomFormat() + ".mp4");
        videoPath = "/sdcard/" + "Simple-" + getFileName_CustomFormat() + ".mp4";
        mediaRecorder.setMaxDuration(10000);    //10 sec
        mediaRecorder.setMaxFileSize(100000000); //100 mb
        mediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            releaseMediaRecorder();
            return false;
        }
        return true;

    }

    public static Camera.Size getOptimalVideoSize(List<Camera.Size> supportedVideoSizes,
                                                  List<Camera.Size> previewSizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;

        List<Camera.Size> videoSizes;
        if (supportedVideoSizes != null) {
            videoSizes = supportedVideoSizes;
        } else {
            videoSizes = previewSizes;
        }
        Camera.Size optimalSize = null;

        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Camera.Size size : videoSizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff && previewSizes.contains(size)) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : videoSizes) {
                if (Math.abs(size.height - targetHeight) < minDiff && previewSizes.contains(size)) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = new MediaRecorder();
            myCamera.lock();
        }
    }

    private void releaseCamera() {
        Log.e("LOGI", "releaseCamera 1");
        if (myCamera != null) {
            Log.e("LOGI", "releaseCamera 2");
            myCamera.release();
            myCamera = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LIBRARY_REQUEST) {
            if (resultCode == RESULT_OK) {
                Uri selectedImageUri = data.getData();
                try {
                    String[] filePathColumn = {MediaStore.Video.Media.DATA};
                    Cursor cursor = getContentResolver().query(selectedImageUri, filePathColumn, null, null, null);
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    videoPath = cursor.getString(columnIndex);
                    cursor.close();
                    Log.e("LOGI", "filePath: " + videoPath);

                    stateViewScreen(true);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.e("LOGI", "Pause");
        releaseMediaRecorder();
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e("LOGI", "Resume 1");
        if (permissionsFlag) {
            Log.e("LOGI", "Resume 2");
//            fullScrenn();
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            display = windowManager.getDefaultDisplay();
            orientation = display.getRotation();
            Log.e("LOGI", "orientation " + orientation);

            initView();
            if (myCamera == null) {
                Toast.makeText(MainActivity.this,
                        "Fail to get Camera",
                        Toast.LENGTH_LONG).show();
            }
            listeners();
            if (myCamera == null) {
                Log.e("LOGI", "Resume 3");
                myCamera = getCameraInstance(camId);
                mPreview.refreshCamera(myCamera);
                switch(orientation){
                    case 0:
                        myCamera.setDisplayOrientation(90);
                        break;
                    case 1:
                        myCamera.setDisplayOrientation(0);
                        break;
                    case 2:
                        myCamera.setDisplayOrientation(270);
                        break;
                    case 3:
                        myCamera.setDisplayOrientation(180);
                        break;
                }
                timerProgress = 100;
                progress.setProgress(0);
            }
            else{
                switch(orientation){
                    case 0:
                        myCamera.setDisplayOrientation(90);
                        break;
                    case 1:
                        myCamera.setDisplayOrientation(0);
                        break;
                    case 2:
                        myCamera.setDisplayOrientation(270);
                        break;
                    case 3:
                        myCamera.setDisplayOrientation(180);
                        break;
                }
            }
        }
    }

    @Override
    public void changeProgress(int l) {
        progress.setProgress(progress.getMax() - l);
        timerProgress = l;
        countClickTouch++;
        Log.d("QWE", "timer " + countClickTouch);
    }

    @Override
    public void setProgressMax() {
        progress.setProgress(progress.getMax());
        mediaRecorder.stop();
        progressTimer.cancel();
        myCamera.lock();
        countClickTouch = 0;
        releaseMediaRecorder();
        stateViewScreen(true);
    }
}
