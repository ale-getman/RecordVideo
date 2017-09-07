package dev.klippe.simplevideo;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;
import android.Manifest;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ProgressTimer.OnTimer {

    public static final int PERMISSION_REQUEST_CODE = 200;
    public static int LIBRARY_REQUEST = 1;

    private ImageView galery, flash, front, record, back;
    private ProgressBar progress;
    private ProgressDialog progressDialog;
    private FrameLayout myCameraPreview;
    private VideoView videoView;
    public MediaController mediaController;
    public Camera myCamera;
    public CameraPreview mPreview;
    private MediaRecorder mediaRecorder;
    public int camId = 0;

    private Handler handler;
    private String videoPath;
    private Runnable runnable;

    private ProgressTimer progressTimer;
    private int timerProgress = 100;

    public int countClickTouch = 0;
    boolean isFlashOn = false;
    boolean isFrontCam = false;
    public boolean flagClickTouch = false;
    private boolean permissionsFlag = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            checkPermission();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void checkPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestMultiplePermissions();
        }
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
        galery = (ImageView) findViewById(R.id.galery);
        flash = (ImageView) findViewById(R.id.flash);
        front = (ImageView) findViewById(R.id.front);
        record = (ImageView) findViewById(R.id.record);
        back = (ImageView) findViewById(R.id.back);
        progress = (ProgressBar) findViewById(R.id.progress);
        videoView = (VideoView) findViewById(R.id.showVideo);
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
        mPreview = new CameraPreview(this, myCamera);
        myCameraPreview = (FrameLayout) findViewById(R.id.frameVideo);
        myCameraPreview.addView(mPreview);

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please Wait");
        progressDialog.setMessage("Your video is preparing...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCanceledOnTouchOutside(false);
    }

    private void listeners() {
        galery.setOnClickListener(new View.OnClickListener() {
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
        record.setImageResource(android.R.drawable.presence_video_online);
        stateViewScreen(true);
    }

    public void startRecording() {
        record.setImageResource(android.R.drawable.presence_video_busy);
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
            videoView.setVisibility(View.VISIBLE);
            back.setVisibility(View.VISIBLE);
            progress.setVisibility(View.GONE);
            myCameraPreview.setVisibility(View.INVISIBLE);
            mPreview.setLayoutParams(new FrameLayout.LayoutParams(1, 1));
            record.setVisibility(View.GONE);
            front.setVisibility(View.GONE);
            flash.setVisibility(View.GONE);
            galery.setVisibility(View.GONE);

            videoView.setVideoURI(Uri.parse(videoPath));
            videoView.setMediaController(mediaController);
            videoView.start();
        } else {
            videoView.setVisibility(View.GONE);
            back.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            myCameraPreview.setVisibility(View.VISIBLE);
            mPreview.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            record.setVisibility(View.VISIBLE);
            front.setVisibility(View.VISIBLE);
            flash.setVisibility(View.VISIBLE);
            galery.setVisibility(View.VISIBLE);
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
            myCamera.setParameters(params);
            myCamera.setDisplayOrientation(90);
        }

        mediaRecorder = new MediaRecorder();
        myCamera.unlock();
        mediaRecorder.setCamera(myCamera);
        if (isFrontCam) {
            mediaRecorder.setOrientationHint(270);
        } else
            mediaRecorder.setOrientationHint(90);
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
        releaseMediaRecorder();
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionsFlag) {
            initView();
            if (myCamera == null) {
                Toast.makeText(MainActivity.this,
                        "Fail to get Camera",
                        Toast.LENGTH_LONG).show();
            }
            listeners();
            if (myCamera == null) {
                myCamera = getCameraInstance(camId);
                mPreview.refreshCamera(myCamera);
                timerProgress = 100;
                progress.setProgress(0);
            }
        }
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
        if (myCamera != null) {
            myCamera.release();
            myCamera = null;
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
        record.setImageResource(android.R.drawable.presence_video_online);
        stateViewScreen(true);
    }
}
