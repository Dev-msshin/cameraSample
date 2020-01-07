package example.pnc.msshin.cameratest;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends Activity implements View.OnClickListener{
    private final String TAG = getClass().getSimpleName();
    private final int PERMISSION_REQUEST = 101;

    private CameraPreview mCameraPreview;
    private SurfaceView mSurfaceView;
    private ImageButton mCameraBtn;
    private byte[] mPreviewData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        mSurfaceView = findViewById(R.id.surface_video_preview);
        mSurfaceView.setVisibility(View.GONE);

        mCameraBtn = findViewById(R.id.btnCamera);
        mCameraBtn.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermission()) {
            startCameraSource();
        }
    }

    private boolean checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this
                    , new String[]{Manifest.permission.CAMERA
                            , Manifest.permission.WRITE_EXTERNAL_STORAGE}
                    , PERMISSION_REQUEST);
            return false;
        }
        return true;
    }

    private boolean hasAllPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    private void startCameraSource() {
        if (mCameraPreview == null) {
            mCameraPreview = new CameraPreview(this, this, Camera.CameraInfo.CAMERA_FACING_FRONT, mSurfaceView, true, -1);
            mCameraPreview.setOnPreviewCallback(onPreviewCallback);
            mSurfaceView.setVisibility(View.VISIBLE);
        }
    }

    private void stopCameraSource() {
        if (mCameraPreview != null){
            mCameraPreview.stopCamera();
            mCameraPreview = null;
        }
        if (mSurfaceView != null){
            mSurfaceView.setVisibility(View.GONE);
        }
    }

    private CameraPreview.OnPreviewCallback onPreviewCallback = new CameraPreview.OnPreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data) {
            mPreviewData = data;
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST: {
                if (hasAllPermission()) {
                    Log.d(TAG, "Permission granted");
                    startCameraSource();
                } else {
                    Log.e(TAG, "Permission deny");
                    finish();
                }
            }
            break;
        }
    }

    private void takePreview() {
        Log.d(TAG, "takePreview()");
        if (mPreviewData != null){
            Camera.Parameters parameters = mCameraPreview.getCameraParam();
            int width = parameters.getPreviewSize().width;
            int height = parameters.getPreviewSize().height;
            YuvImage yuv = new YuvImage(mPreviewData, parameters.getPreviewFormat(), width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
            byte[] data = out.toByteArray();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, options);

            Matrix matrix = new Matrix();
            matrix.preRotate(-90, 0, 0);
            Bitmap bmpRotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);

            Matrix sideInversion = new Matrix();
            sideInversion.setScale(-1, 1);
            Bitmap bmpInversion = Bitmap.createBitmap(bmpRotated, 0, 0, bmpRotated.getWidth(), bmpRotated.getHeight(), sideInversion, false);

            if (bmpInversion != null) {
                String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "msshin" + File.separator + "picture_test.jpg";
                try {
                    FileOutputStream fileOps = new FileOutputStream(path);
                    bmpInversion.compress(Bitmap.CompressFormat.JPEG, 50, fileOps);
                    fileOps.close();
                } catch (FileNotFoundException exception) {
                    Log.e("FileNotFoundException", exception.getMessage());
                } catch (IOException exception) {
                    Log.e("IOException", exception.getMessage());
                }
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnCamera:
                takePreview();
                break;
        }
    }
}
