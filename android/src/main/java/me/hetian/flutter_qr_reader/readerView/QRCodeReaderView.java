/*
 * Copyright 2014 David Lázaro Esparcia.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.hetian.flutter_qr_reader.readerView;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import com.google.zxing.client.android.camera.CameraManager;

import me.hetian.flutter_qr_reader.reader.DecodeFormatManager;
import me.hetian.flutter_qr_reader.reader.MyMultiFormatReader;
import me.hetian.flutter_qr_reader.reader.MyPlanarYUVLuminanceSource;

import static android.hardware.Camera.getCameraInfo;

/**
 * QRCodeReaderView Class which uses ZXING lib and let you easily integrate a QR decoder view.
 * Take some classes and made some modifications in the original ZXING - Barcode Scanner project.
 *
 * @author David Lázaro
 */
public class QRCodeReaderView extends SurfaceView
        implements SurfaceHolder.Callback, Camera.PreviewCallback {

    public interface OnQRCodeReadListener {

        void onQRCodeRead(String text, PointF[] points);
    }

    private OnQRCodeReadListener mOnQRCodeReadListener;

    private static final String TAG = QRCodeReaderView.class.getName();

    private MyMultiFormatReader mQRCodeReader;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private CameraManager mCameraManager;
    private boolean mQrDecodingEnabled = true;
    private DecodeFrameTask decodeFrameTask;
    private Map<DecodeHintType, Object> decodeHints;

    public QRCodeReaderView(Context context) {
        this(context, null);
    }

    public QRCodeReaderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (isInEditMode()) {
            return;
        }
        decodeHints = new EnumMap<>(DecodeHintType.class);
        decodeHints.put(DecodeHintType.TRY_HARDER, BarcodeFormat.QR_CODE);
        decodeHints.put(DecodeHintType.CHARACTER_SET, "utf-8");
//        decodeHints.put(DecodeHintType.PURE_BARCODE, Boolean.FALSE);

        if (checkCameraHardware()) {
            mCameraManager = new CameraManager(getContext());
            mCameraManager.setPreviewCallback(this);
            getHolder().addCallback(this);
            setBackCamera();
        } else {
            throw new RuntimeException("Error: Camera not found");
        }
    }

    /**
     * Set the callback to return decoding result
     *
     * @param onQRCodeReadListener the listener
     */
    public void setOnQRCodeReadListener(OnQRCodeReadListener onQRCodeReadListener) {
        mOnQRCodeReadListener = onQRCodeReadListener;
    }

    /**
     * Enable/disable logging, false by default
     *
     * @param enabled logging enabled/disabled.
     */
    public void setLoggingEnabled(boolean enabled) {
        SimpleLog.setLoggingEnabled(enabled);
    }

    /**
     * Set QR decoding enabled/disabled.
     * default value is true
     *
     * @param qrDecodingEnabled decoding enabled/disabled.
     */
    public void setQRDecodingEnabled(boolean qrDecodingEnabled) {
        this.mQrDecodingEnabled = qrDecodingEnabled;
    }

    /**
     * 设置解码器，默认为二维码、条形码
     * @param decodeMode DecodeFormatManager.ALL_MODE, DecodeFormatManager.QRCODE_MODE, DecodeFormatManager.BARCODE_MODE
     */
    public void setDecodeHints(int decodeMode) {
        Collection<BarcodeFormat> decodeFormats = new ArrayList<BarcodeFormat>();
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.AZTEC));
        decodeFormats.addAll(EnumSet.of(BarcodeFormat.PDF_417));
        switch (decodeMode) {
            case DecodeFormatManager.BARCODE_MODE:
                decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
                break;

            case DecodeFormatManager.QRCODE_MODE:
                decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
                break;

            case DecodeFormatManager.ALL_MODE:
                decodeFormats.addAll(DecodeFormatManager.getBarCodeFormats());
                decodeFormats.addAll(DecodeFormatManager.getQrCodeFormats());
                break;

            default:
                break;
        }
        decodeHints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
    }

    /**
     * Starts google.zxing.client.android.android.com.google.zxing.client.android.camera preview and decoding
     */
    public void startCamera() {
        mCameraManager.startPreview();
    }

    /**
     * Stop google.zxing.client.android.android.com.google.zxing.client.android.camera preview and decoding
     */
    public void stopCamera() {
        mCameraManager.stopPreview();
    }

    /**
     * Set Camera autofocus interval value
     * default value is 5000 ms.
     *
     * @param autofocusIntervalInMs autofocus interval value
     */
    public void setAutofocusInterval(long autofocusIntervalInMs) {
        if (mCameraManager != null) {
            mCameraManager.setAutofocusInterval(autofocusIntervalInMs);
        }
    }

    /**
     * Trigger an auto focus
     */
    public void forceAutoFocus() {
        if (mCameraManager != null) {
            mCameraManager.forceAutoFocus();
        }
    }

    /**
     * Set Torch enabled/disabled.
     * default value is false
     *
     * @param enabled torch enabled/disabled.
     */
    public void setTorchEnabled(boolean enabled) {
        if (mCameraManager != null) {
            mCameraManager.setTorchEnabled(enabled);
        }
    }

    /**
     * Allows user to specify the google.zxing.client.android.android.com.google.zxing.client.android.camera ID, rather than determine
     * it automatically based on available cameras and their orientation.
     *
     * @param cameraId google.zxing.client.android.android.com.google.zxing.client.android.camera ID of the google.zxing.client.android.android.com.google.zxing.client.android.camera to use. A negative value means "no preference".
     */
    public void setPreviewCameraId(int cameraId) {
        mCameraManager.setPreviewCameraId(cameraId);
    }

    /**
     * Camera preview from device back google.zxing.client.android.android.com.google.zxing.client.android.camera
     */
    public void setBackCamera() {
        setPreviewCameraId(Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    /**
     * Camera preview from device front google.zxing.client.android.android.com.google.zxing.client.android.camera
     */
    public void setFrontCamera() {
        setPreviewCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (decodeFrameTask != null) {
            decodeFrameTask.cancel(true);
            decodeFrameTask = null;
        }
    }

    /****************************************************
     * SurfaceHolder.Callback,Camera.PreviewCallback
     ****************************************************/

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        SimpleLog.d(TAG, "surfaceCreated");

        try {
            // Indicate google.zxing.client.android.android.com.google.zxing.client.android.camera, our View dimensions
            mCameraManager.openDriver(holder, this.getWidth(), this.getHeight());
        } catch (IOException | RuntimeException e) {
            SimpleLog.w(TAG, "Can not openDriver: " + e.getMessage());
            mCameraManager.closeDriver();
        }

        try {
            mQRCodeReader = new MyMultiFormatReader();
            mCameraManager.startPreview();
        } catch (Exception e) {
            SimpleLog.e(TAG, "Exception: " + e.getMessage());
            mCameraManager.closeDriver();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        SimpleLog.d(TAG, "surfaceChanged");

        if (holder.getSurface() == null) {
            SimpleLog.e(TAG, "Error: preview surface does not exist");
            return;
        }

        if (mCameraManager.getPreviewSize() == null) {
            SimpleLog.e(TAG, "Error: preview size does not exist");
            return;
        }

        mPreviewWidth = mCameraManager.getPreviewSize().x;
        mPreviewHeight = mCameraManager.getPreviewSize().y;

        mCameraManager.stopPreview();

        // Fix the google.zxing.client.android.android.com.google.zxing.client.android.camera sensor rotation
        mCameraManager.setPreviewCallback(this);
        mCameraManager.setDisplayOrientation(getCameraDisplayOrientation());

        mCameraManager.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        SimpleLog.d(TAG, "surfaceDestroyed");

        mCameraManager.setPreviewCallback(null);
        mCameraManager.stopPreview();
        mCameraManager.closeDriver();
    }

    // Called when google.zxing.client.android.android.com.google.zxing.client.android.camera take a frame
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (!mQrDecodingEnabled || decodeFrameTask != null
                && (decodeFrameTask.getStatus() == AsyncTask.Status.RUNNING
                || decodeFrameTask.getStatus() == AsyncTask.Status.PENDING)) {
            return;
        }
        if (!decodeHints.containsKey(DecodeHintType.POSSIBLE_FORMATS)) {
            this.setDecodeHints(DecodeFormatManager.ALL_MODE);
        }
        decodeFrameTask = new DecodeFrameTask(this, decodeHints);
        decodeFrameTask.execute(data);
    }

    /**
     * Check if this device has a google.zxing.client.android.android.com.google.zxing.client.android.camera
     */
    private boolean checkCameraHardware() {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a google.zxing.client.android.android.com.google.zxing.client.android.camera
            return true;
        } else if (getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            // this device has a front google.zxing.client.android.android.com.google.zxing.client.android.camera
            return true;
        } else {
            // this device has any google.zxing.client.android.android.com.google.zxing.client.android.camera
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                    && getContext().getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_CAMERA_ANY);
        }
    }

    /**
     * Fix for the google.zxing.client.android.android.com.google.zxing.client.android.camera Sensor on some devices (ex.: Nexus 5x)
     */
    @SuppressWarnings("deprecation")
    private int getCameraDisplayOrientation() {

        Camera.CameraInfo info = new Camera.CameraInfo();
        getCameraInfo(mCameraManager.getPreviewCameraId(), info);
        WindowManager windowManager =
                (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        int rotation = windowManager.getDefaultDisplay().getRotation();
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
            default:
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

    private static class DecodeFrameTask extends AsyncTask<byte[], Void, Result> {

        private final WeakReference<QRCodeReaderView> viewRef;
        private final WeakReference<Map<DecodeHintType, Object>> hintsRef;
        private final QRToViewPointTransformer qrToViewPointTransformer =
                new QRToViewPointTransformer();

        DecodeFrameTask(QRCodeReaderView view, Map<DecodeHintType, Object> hints) {
            viewRef = new WeakReference<>(view);
            hintsRef = new WeakReference<>(hints);
        }

        @Override
        protected Result doInBackground(byte[]... params) {
            final QRCodeReaderView view = viewRef.get();
            if (view == null) {
                return null;
            }

            LuminanceSource source =
                    view.mCameraManager.buildLuminanceSource(params[0], view.mPreviewWidth,
                            view.mPreviewHeight);

            source = source.rotateCounterClockwise();
            final HybridBinarizer hybBin = new HybridBinarizer(source);
            final BinaryBitmap bitmap = new BinaryBitmap(hybBin);

            try {
                Log.i(TAG, "doInBackground: " + hintsRef.get());
                return view.mQRCodeReader.decode(bitmap, hintsRef.get());
            } catch (NotFoundException e) {
                Log.i(TAG, "doInBackground: " + e.getLocalizedMessage());
                e.printStackTrace();
            } finally {
                view.mQRCodeReader.reset();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Result result) {
            super.onPostExecute(result);

            final QRCodeReaderView view = viewRef.get();

            // Notify we found a QRCode
            if (view != null && result != null && view.mOnQRCodeReadListener != null) {
                // Transform resultPoints to View coordinates
                final PointF[] transformedPoints = transformToViewCoordinates(view, result.getResultPoints());
                view.mOnQRCodeReadListener.onQRCodeRead(result.getText(), transformedPoints);
            }
        }

        /**
         * Transform result to surfaceView coordinates
         * <p>
         * This method is needed because coordinates are given in landscape google.zxing.client.android.android.com.google.zxing.client.android.camera coordinates when
         * device is in portrait mode and different coordinates otherwise.
         *
         * @return a new PointF array with transformed points
         */
        private PointF[] transformToViewCoordinates(QRCodeReaderView view,
                                                    ResultPoint[] resultPoints) {
            int orientationDegrees = view.getCameraDisplayOrientation();
            Orientation orientation =
                    orientationDegrees == 90 || orientationDegrees == 270 ? Orientation.PORTRAIT
                            : Orientation.LANDSCAPE;
            Point viewSize = new Point(view.getWidth(), view.getHeight());
            Point cameraPreviewSize = view.mCameraManager.getPreviewSize();
            boolean isMirrorCamera =
                    view.mCameraManager.getPreviewCameraId()
                            == Camera.CameraInfo.CAMERA_FACING_FRONT;

            return qrToViewPointTransformer.transform(resultPoints, isMirrorCamera, orientation,
                    viewSize, cameraPreviewSize);
        }
    }
}
