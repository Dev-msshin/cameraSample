package example.pnc.msshin.cameratest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class CameraPreview {
    private final String TAG = "CameraPreview";

    private Context mContext;
    private int mCameraID;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private Camera.CameraInfo mCameraInfo;
    private int mDisplayOrientation;
    private List<Size> mSupportedPreviewSizes, mSupportedPictureSizes;
    private Size mPreviewSize, mPictureSize;
    private boolean isPreview = false;
    private boolean mIsSilentMode;
    private int mProgressive;
    private Activity mActivity;
    private byte[] mPreviewData;
    private Timer mCaptureTimer;
    private OnTakePictureListener onTakePictureListener;
    private OnPreviewCallback onPreviewCallback;
    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            mPreviewData = data;
            if (onPreviewCallback != null){
                onPreviewCallback.onPreviewFrame(data);
            }
            onCapturedData(data, onTakePictureListener);
            camera.addCallbackBuffer(data);
        }
    };
    private SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated()");
            startCamera();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "surfaceChanged()");
//            changeCalculatePreviewOrientation();
            startCamera();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            stopCamera();
        }
    };

    public CameraPreview(Context context, Activity activity, int cameraID, SurfaceView surfaceView, boolean isSilentMode, int progressive) {
        Log.d(TAG, "Preview");
        mContext = context;
        mActivity = activity;
        mCameraID = cameraID;
        mSurfaceView = surfaceView;
        mIsSilentMode = isSilentMode;

        mSurfaceView.setVisibility(View.VISIBLE);

        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(surfaceHolderCallback);
        mProgressive = progressive;
        createCamera(mHolder);
    }

    /**
     * 안드로이드 디바이스 방향에 맞는 카메라 프리뷰를 화면에 보여주기 위해 계산합니다.
     */
    public static int calculatePreviewOrientation(Camera.CameraInfo info, int rotation) {
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        return result;
    }

    public void startCamera() {
        Log.d(TAG, "startCamera()");
        if (mHolder != null) {
            if (isPreview) {
                stopCamera();
            }
            if (mCamera == null) {
                createCamera(mHolder);
            }
            mCamera.addCallbackBuffer(new byte[mPreviewSize.height * mPreviewSize.width * 3 / 2]);
            mCamera.setPreviewCallbackWithBuffer(previewCallback);
            mCamera.startPreview();
            isPreview = true;
            Log.d(TAG, "Camera preview started.");
        }
    }

    private void onCapturedData(byte[] data, OnTakePictureListener onTakePictureListener) {
        if (onTakePictureListener != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            int width = parameters.getPreviewSize().width;
            int height = parameters.getPreviewSize().height;
            YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
            onTakePictureListener.onCapturedData(out.toByteArray());
            this.onTakePictureListener = null;
        }
    }

    public Camera.Parameters getCameraParam(){
        if (mCamera != null) {
            return mCamera.getParameters();
        }else{
            return null;
        }
    }

    public void stopCamera() {
        Log.d(TAG, "stopCamera()");
        if (mCamera != null) {
            if (isPreview) {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);
                mCamera.setPreviewCallbackWithBuffer(null);
            }
            mCamera.release();
            mCamera = null;
            isPreview = false;
        }
    }

    private void changeCalculatePreviewOrientation() {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null) {
            // preview surface does not exist
            Log.d(TAG, "Preview surface does not exist");
            return;
        }

        // stop preview before making changes
        stopCamera();

        int orientation = calculatePreviewOrientation(mCameraInfo, mDisplayOrientation);
        mCamera.setDisplayOrientation(orientation);

        startCamera();
    }

    private void createCamera(SurfaceHolder holder) {
        if (mCamera == null) {

            // Open an instance of the camera
            try {
                mCamera = Camera.open(mCameraID); // attempt to get a Camera instance
            } catch (Exception e) {
                Log.e(TAG, "Camera " + mCameraID + " is not available: " + e.getMessage());
            }

            // retrieve camera's info.
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(mCameraID, cameraInfo);

            mCameraInfo = cameraInfo;
            mDisplayOrientation = mActivity.getWindowManager().getDefaultDisplay().getRotation();

            // Orientation
            int orientation = calculatePreviewOrientation(mCameraInfo, mDisplayOrientation);
            mCamera.setDisplayOrientation(orientation);

            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            Camera.Parameters params = mCamera.getParameters();

            // FPS
            int[] frameRates = getMaxPreviewFpsRange(params);
            if (frameRates != null) {
                int minFps = frameRates[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
                int maxFps = frameRates[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
                Log.d(TAG, "set fps[min:" + minFps + ",max:" + maxFps + "]");
                params.setPreviewFpsRange(minFps, maxFps);
            }

            // Focus mode
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                // set the focus mode
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            // Preview size
            Size previewSize = params.getPreviewSize();
            if (mSurfaceView != null) {
                if (mSupportedPreviewSizes != null) {
                    Size preSize = null;
                    for (Size size : mSupportedPreviewSizes) {
                        if (size.height == mProgressive){
                            preSize = size;
                            break;
                        }
                    }
                    if (preSize == null){
                        preSize = getOptimalPreviewSize(mSupportedPreviewSizes, mSurfaceView.getWidth(), mSurfaceView.getHeight());
                    }
                    if (preSize != null){
                        params.setPreviewSize(preSize.width, preSize.height);
                        previewSize = preSize;
                    }
                }
            }
            mPreviewSize = previewSize;
            Log.d(TAG, "preview[w:" + previewSize.width + ", h:" + previewSize.height);

            // Picture size
            Size pictureSize = params.getPictureSize();
            mSupportedPictureSizes = getSupportedPictureSizes(mCamera);
            if (mSupportedPictureSizes != null && mSupportedPictureSizes.size() > 0) {
                Size maxSize = null;
                for (Size size : mSupportedPictureSizes) {
                    Log.d(TAG, "supported picture size - w:" + size.width + ",h:" + size.height);
                    if (maxSize == null) {
                        maxSize = size;
                    } else {
                        if (maxSize.height < size.height) {
                            maxSize = size;
                        }
                    }
                }
                if (maxSize != null) {
                    Log.d(TAG, "maxSize - width: " + maxSize.width + ", height: " + maxSize.height);
                    params.setPictureSize(maxSize.width, maxSize.height);
                    pictureSize = maxSize;
                } else {
                    Log.d(TAG, "maxSize is null");
                }
            }
            mPictureSize = pictureSize;
            Log.d(TAG, "picture[w:" + pictureSize.width + ", h:" + pictureSize.height + "]");

            try {
                mCamera.setParameters(params);
                mCamera.setPreviewDisplay(holder);
                mCamera.enableShutterSound(!mIsSilentMode);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<Size> getSupportedPictureSizes(Camera camera) {
        if (camera == null) {
            return null;
        }
        List<Size> pictureSizes = camera.getParameters().getSupportedPictureSizes();
        checkSupportedPictureSizeAtPreviewSize(pictureSizes, camera);
        return pictureSizes;
    }

    private void checkSupportedPictureSizeAtPreviewSize(List<Size> pictureSizes, Camera camera) {
        List<Size> previewSizes = camera.getParameters().getSupportedPreviewSizes();
        Size pictureSize;
        Size previewSize;
        double pictureRatio = 0;
        double previewRatio = 0;
        final double aspectTolerance = 0.05;
        boolean isUsablePicture = false;

        for (int indexOfPicture = pictureSizes.size() - 1; indexOfPicture >= 0; --indexOfPicture) {
            pictureSize = pictureSizes.get(indexOfPicture);
            pictureRatio = (double) pictureSize.width / (double) pictureSize.height;
            isUsablePicture = false;

            for (int indexOfPreview = previewSizes.size() - 1; indexOfPreview >= 0; --indexOfPreview) {
                previewSize = previewSizes.get(indexOfPreview);

                previewRatio = (double) previewSize.width / (double) previewSize.height;

                if (Math.abs(pictureRatio - previewRatio) < aspectTolerance) {
                    isUsablePicture = true;
                    break;
                }
            }

            if (isUsablePicture == false) {
                pictureSizes.remove(indexOfPicture);
                Log.d(TAG, "remove picture size : " + pictureSize.width + ", " + pictureSize.height);
            }
        }
    }

    public int[] getMaxPreviewFpsRange(Camera.Parameters params) {
        List<int[]> frameRates = params.getSupportedPreviewFpsRange();
        if (frameRates != null && frameRates.size() > 0) {
            return frameRates.get(frameRates.size() - 1);
        }
        return null;
    }

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        Log.d(TAG, "getOptimalPreviewSize() - w: " + w + ", h: " + h);
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        if (optimalSize != null) {
            Log.d(TAG, "getOptimalPreviewSize() - width:" + optimalSize.width + ", height:" + optimalSize.height);
        }
        return optimalSize;
    }

    public void takePicture(OnTakePictureListener onTakePictureListener, int timeOut) {
        Log.d(TAG, "takePicture() - mPreviewData: "+(mPreviewData != null ? mPreviewData.length : "null"));
        if (mPreviewData != null) {
            onCapturedData(mPreviewData, onTakePictureListener);
        } else {
            if (this.onTakePictureListener != null) {
                this.onTakePictureListener.onSkipped();
            }
            this.onTakePictureListener = onTakePictureListener;
            startCaptureTimer(timeOut);
        }
    }

    private void startCaptureTimer(int timeOut) {
        stopCaptureTimer();
        mCaptureTimer = new Timer();
        mCaptureTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (onTakePictureListener != null) {
                    onTakePictureListener.onTimeOut();
                    onTakePictureListener = null;
                }
            }
        }, timeOut);
    }

    private void stopCaptureTimer() {
        if (mCaptureTimer != null) {
            mCaptureTimer.cancel();
            mCaptureTimer = null;
        }
    }

    private void saveImage(byte[] data, Camera camera) {
        //이미지의 너비와 높이 결정
        int w = camera.getParameters().getPictureSize().width;
        int h = camera.getParameters().getPictureSize().height;
        int orientation = calculatePreviewOrientation(mCameraInfo, mDisplayOrientation);

        //byte array를 bitmap으로 변환
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);

        //이미지를 디바이스 방향으로 회전
        Matrix matrix = new Matrix();
        matrix.postRotate(orientation);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);

        //bitmap을 byte array로 변환
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] currentData = stream.toByteArray();

        //파일로 저장
        new SaveImageTask().execute(currentData);
    }

    public OnPreviewCallback getOnPreviewCallback() {
        return onPreviewCallback;
    }

    public void setOnPreviewCallback(OnPreviewCallback onPreviewCallback) {
        this.onPreviewCallback = onPreviewCallback;
    }

    public interface OnTakePictureListener {
        void onCapturedData(byte[] data);

        void onTimeOut();

        void onSkipped();
    }

    public interface OnPreviewCallback{
        void onPreviewFrame(byte[] data);
    }

    private class SaveImageTask extends AsyncTask<byte[], Void, Void> {

        @Override
        protected Void doInBackground(byte[]... data) {
            FileOutputStream outStream = null;


            try {

                File path = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/camtest");
                if (!path.exists()) {
                    path.mkdirs();
                }

                String fileName = String.format("%d.jpg", System.currentTimeMillis());
                File outputFile = new File(path, fileName);

                outStream = new FileOutputStream(outputFile);
                outStream.write(data[0]);
                outStream.flush();
                outStream.close();

                Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length + " to "
                        + outputFile.getAbsolutePath());

                if (mContext != null) {
                    // 갤러리에 반영
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(Uri.fromFile(outputFile));
                    mContext.sendBroadcast(mediaScanIntent);
                }

                startCamera();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

    }
}