package com.joker.clockit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;


import com.budiyev.android.codescanner.BarcodeUtils;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.android.gms.vision.face.Face;
import com.google.zxing.Result;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import com.joker.clockit.face.OpenFaceClient;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link FaceDetectorFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link FaceDetectorFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class FaceDetectorFragment extends Fragment implements Camera.PreviewCallback{

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        void onIdentityChanged(String id);

        /**
         * Identity has been confirmed, no further action is required
         * @param id
         */
        void onIdentityConfirmed(String id);
        void onDetectionStopped();
        void onDetectionStarted();
    }

    private static final String TAG = FaceDetectorFragment.class.getName();

    private static final int RC_HANDLE_CAMERA_PERM = 2;
    private static final int AUTO_STOP_TIMEOUT = 30000;

    private CameraPreview mPreview;
    private RelativeLayout previewPlaceholder;
    private OpenFaceClient mOpenFaceClient;
    private ImageView imageViewRxFrame;
    private int mRotationDegrees;
    private ExecutorService executorService;
    private Semaphore mSendingFrames;



    private double mRxConfidenceThreshold;
    public void setRxConfidenceThreshold(double th) { mRxConfidenceThreshold = th; }
    private Handler mAutoCameraOff;
    private boolean mCameraRunning;
    public boolean isDetecting() { return mCameraRunning; }

    private boolean mEnablePreview;
    public boolean isPreviewEnabled() {return mEnablePreview; }
    public void setPreviewEnabled(boolean en){
        mEnablePreview = en;
        imageViewRxFrame.setVisibility(mEnablePreview ? View.VISIBLE : View.GONE);
    }

    /**
     * Code to match frames against
     */
    private String mTargetCode;
    public void setTargetCode(String c) { mTargetCode = c ; }

    private OnFragmentInteractionListener mListener;

    private LightController mLightControler;




    public FaceDetectorFragment() {
        mRxConfidenceThreshold = 0.8;

    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment FaceDetectorFragment.
     */
    public static FaceDetectorFragment newInstance() {
        FaceDetectorFragment fragment = new FaceDetectorFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLightControler = LightController.getInstance(); // new LightController(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_face_detector, container, false);
    }

    private BarcodeDetector bcDetector;
    private Frame.Builder frameBuilder;
    com.google.android.gms.vision.face.FaceDetector faceDetector;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        previewPlaceholder = view.findViewById(R.id.previewPlaceholder);


        imageViewRxFrame = view.findViewById(R.id.imageViewRxFrame);

        mOpenFaceClient = null;
        mRotationDegrees = 0;

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }

        executorService = Executors.newSingleThreadExecutor();
        mSendingFrames = new Semaphore(3);

        BarcodeDetector.Builder b = new BarcodeDetector.Builder(FaceDetectorFragment.this.getActivity());
        bcDetector = b.build();
        frameBuilder = new Frame.Builder();

        faceDetector = new com.google.android.gms.vision.face.FaceDetector.Builder(this.getActivity())
                .setTrackingEnabled(false)
                .setLandmarkType(com.google.android.gms.vision.face.FaceDetector.ALL_LANDMARKS)
                .build();




    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        //startDetecting(AUTO_STOP_TIMEOUT);
    }

    @Override
    public void onPause() {
        super.onPause();
        //stopDetecting();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
        //mLightControler.close();
    }





    private void createCameraSource(){

    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(getActivity(), permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = getActivity();

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(previewPlaceholder, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(android.R.string.ok, listener)
                .show();
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                getActivity().finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.app_name)
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(android.R.string.ok, listener)
                .show();
    }

    private long mLastFrameSent = 0;
    private int mFrameCounter = 0;

    //@Override
    public void onPreviewFrame_(byte[] bytes, Camera camera) {
        long now = System.currentTimeMillis();
        if((now - mLastFrameSent) >= 100){

            Camera.Parameters parameters = camera.getParameters();
            Camera.Size previewSize = parameters.getPreviewSize();

            long delay = System.currentTimeMillis();

            Result r = BarcodeUtils.decodeYuv(bytes,previewSize.width, previewSize.height);

            delay = System.currentTimeMillis() - delay;
            Log.d(TAG, "decode takes "+delay);

            if(r != null) {
                Log.d(TAG, r.getText());
            }

            mLastFrameSent = System.currentTimeMillis();
        }
    }
    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {




        long now = System.currentTimeMillis();
        if((now - mLastFrameSent) >= 100){

            if(mSendingFrames.availablePermits() > 0) {
                try {
                    mSendingFrames.acquire();
                } catch (InterruptedException e) {
                    return;
                }
                FrameSender task = new FrameSender(mOpenFaceClient, camera, bytes, mRotationDegrees);
                task.setTargetCode(mTargetCode);
                task.setFlush(true);
                /*
                if(task.send()){
                    mLastFrameSent = System.currentTimeMillis();
                }
                */

                try {
                    executorService.submit(task);
                    bytes = null;
                    mLastFrameSent = System.currentTimeMillis();
                } catch (Exception e) {
                    e.printStackTrace();
                    mLastFrameSent = now + 1000;
                }

            }else{
                //Log.v(TAG, "Skipped frame");
            }
/*
            Bitmap bitmap = convertYuvByteArrayToBitmap(bytes, camera, mRotationDegrees);
            if(bitmap != null) {
                mOpenFaceClient.sendFrame(bitmap);
                //imageViewRxFrame.setImageBitmap(bitmap);
            }
*/


        }
/*
        if(bytes != null)
        {
            mPreview.getCamera().addCallbackBuffer(bytes);
        }
*/
    }

    private int findFrontFacingCamera() {
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                Log.v(TAG, "Camera found");
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    private SurfaceHolder surfaceHolder;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void startDetecting(int autoStopTimeout){

        setRxFrame(null);

        //mLightControler.init();

        //mLightControler.on();

        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(getContext());
        final String open_face_server = "wss://192.168.0.27:9000"; // spref.getString("open_face_server", "wss://faceid.sotubo.pt:9000");

        OpenFaceClient client = new OpenFaceClient();
        client.connect(open_face_server);
        mOpenFaceClient = client;

        imageViewRxFrame.setVisibility(View.VISIBLE);

        final OnFragmentInteractionListener listener = (OnFragmentInteractionListener) getActivity();

        mOpenFaceClient.setOnFrameReceivedCallback(new OpenFaceClient.OnFrameReceivedCallback() {
            @Override
            public void OnFrameReceived(final Bitmap bmp) {
                if(bmp != null) {
                    //final Bitmap copy = Bitmap.createBitmap(bmp);
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setRxFrame(bmp);
                        }
                    });

                }
            }

            @Override
            public void OnFrameIdentityReceived(final String id, double confidence) {

                if(confidence < mRxConfidenceThreshold)
                    return;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listener.onIdentityChanged(id);
                    }
                });

            }

            @Override
            public void OnTrainingStatus(int status) {

            }
        });

        int frontFacingCameraId = findFrontFacingCamera();

        // Set the second argument by your choice.
        // Usually, 0 for back-facing camera, 1 for front-facing camera.
        // If the OS is pre-gingerbreak, this does not have any effect.
        mPreview = new CameraPreview(getActivity(), frontFacingCameraId, CameraPreview.LayoutMode.FitToParent);
        FrameLayout.LayoutParams previewLayoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        previewPlaceholder.addView(mPreview, 0, previewLayoutParams);
        surfaceHolder = mPreview.getHolder();


        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;

        switch(rotation){
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
        Camera.CameraInfo info = mPreview.getCameraInfo();

        int result;
        if(info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT){
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        }else{
            result = (info.orientation - degrees + 360) % 360;
        }
        mRotationDegrees = result;
/*
        Camera cam = mPreview.getCamera();
        Camera.Size previewSize = cam.getParameters().getPreviewSize();
        int fmt = cam.getParameters().getPreviewFormat();
        int byteSize = 0;
        if(fmt == ImageFormat.YV12){
            int yStride   = (int) Math.ceil(previewSize.width / 16.0) * 16;
            int uvStride  = (int) Math.ceil( (yStride / 2) / 16.0) * 16;
            int ySize     = yStride * previewSize.height;
            int uvSize    = uvStride * previewSize.height / 2;
            byteSize      = ySize + uvSize * 2;
        }else{
            byteSize = previewSize.width*previewSize.height* ImageFormat.getBitsPerPixel(fmt)/8;
        }
        for(int i = 0; i < 10 ; i++){
            cam.addCallbackBuffer(new byte[byteSize]);
        }

*/


        mPreview.setOnPreviewReady(new CameraPreview.PreviewReadyCallback() {
            @Override
            public void onPreviewReady() {
                mPreview.setPreviewCallback(FaceDetectorFragment.this);
                //mPreview.setPreviewCallbackWithBuffer(FaceDetectorFragment.this);
            }
        });
        mPreview.setPreviewCallback(this);
        //mPreview.setPreviewCallbackWithBuffer(FaceDetectorFragment.this);



        mCameraRunning = true;
        resetAutoStop(autoStopTimeout);

        OnFragmentInteractionListener cb = mListener;
        if(cb != null)
            cb.onDetectionStarted();

    }



    public void stopDetecting(){
        if(mAutoCameraOff != null){
            mAutoCameraOff.removeCallbacks(autoStopCamera);
            mAutoCameraOff = null;
        }
        if(!mCameraRunning) {
            mLightControler.off();
            return;
        }

        imageViewRxFrame.setVisibility(View.GONE);

        mOpenFaceClient.setOnFrameReceivedCallback(null);
        mPreview.setPreviewCallback(null);
        mPreview.stop();
        previewPlaceholder.removeView(mPreview);
        mPreview = null;
        mOpenFaceClient.close();
        mCameraRunning = false;

        OnFragmentInteractionListener listener = (OnFragmentInteractionListener)getActivity();
        listener.onDetectionStopped();

        mLightControler.off();

        surfaceHolder = null;
    }


    private Runnable autoStopCamera = new Runnable() {
        @Override
        public void run() {
            stopDetecting();
        }
    };

    public void resetAutoStop(){
        resetAutoStop(AUTO_STOP_TIMEOUT);
    }

    public void resetAutoStop(int timeout_ms){


        if(mAutoCameraOff != null){
            mAutoCameraOff.removeCallbacks(autoStopCamera);
            mAutoCameraOff = null;
        }
        if(timeout_ms > 0) {
            mAutoCameraOff = new Handler();
            mAutoCameraOff.postDelayed(autoStopCamera, timeout_ms);
        }

    }


    private void setRxFrame(Bitmap frame){
        BitmapDrawable prev = ((BitmapDrawable)imageViewRxFrame.getDrawable());
        imageViewRxFrame.setImageBitmap(frame);

        if(prev != null && prev.getBitmap() != null){
            prev.getBitmap().recycle();
        }
    }

    private class FrameSender implements Runnable{

        private byte[] data;
        private int previewFormat;
        private Camera.Size previewSize;
        private int mRotation;
        private OpenFaceClient mClient;
        private long createTime;
        private boolean mFlush;
        public void setFlush(boolean f){ mFlush = f;}
        private String mTargetCode;
        public void setTargetCode(String c) { mTargetCode = c; }

        public FrameSender(OpenFaceClient cl, Camera camera, byte[] dt, int rotation){
            createTime = System.currentTimeMillis();
            mClient = cl;
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();
            data = dt;
            previewFormat = parameters.getPreviewFormat();
            previewSize =size;
            mRotation = rotation;
            mFlush = false;
            mTargetCode = "";
        }


        @Override
        public void run() {
            send();




        }


        public boolean send(){
            boolean sent = false;
            try {
                createTime = System.currentTimeMillis();

                if(mRotation == 90){
                    data = rotateYUV420Degree90(data, previewSize.width, previewSize.height);
                    int w = previewSize.height;
                    previewSize.height = previewSize.width;
                    previewSize.width = w;
                }else if(mRotation == 180){
                    data = rotateYUV420Degree180(data, previewSize.width, previewSize.height);
                }else if(mRotation == 270){
                    data = rotateYUV420Degree270(data, previewSize.width, previewSize.height);
                    int w = previewSize.height;
                    previewSize.height = previewSize.width;
                    previewSize.width = w;
                }



                YuvImage image = new YuvImage(data, previewFormat, previewSize.width, previewSize.height, null);
                ByteBuffer buffer = ByteBuffer.wrap(data);
                final Frame fr;
                synchronized(frameBuilder) {
                    frameBuilder.setImageData(buffer, previewSize.width, previewSize.height, previewFormat);
                    fr = frameBuilder.build();
                }

//                if(!sent){
//
//
///*
//                    ByteArrayOutputStream out = new ByteArrayOutputStream();
//                    image.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 50, out);
//
//                    byte[] imageBytes = out.toByteArray();
//                    final Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
//
//                    getActivity().runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//
//                            setRxFrame(bmp);
//                        }
//                    });
//*/
//
//
//                    SparseArray<Face> faces = faceDetector.detect(fr);
//                    if(faces.size() > 0){
//                        Log.d(TAG, "found faces "+faces.size());
//                        final Face face = faces.valueAt(0);
//                        StringBuilder sb = new StringBuilder();
//
//                        for (Landmark landmark : face.getLandmarks()) {
//                            sb.append("T");
//                            sb.append(landmark.getType());
//                            sb.append("=");
//                            sb.append(String.format("%.0f,%.0f",landmark.getPosition().x, landmark.getPosition().y));
//                            sb.append(" ");
//                        }
//                        Log.d(TAG, sb.toString());
//
//
//                        ByteArrayOutputStream out = new ByteArrayOutputStream();
//                        int left = Math.max(0, (int)face.getPosition().x);
//                        int top = Math.max(0,  (int)face.getPosition().y);
//                        int right = (int)(left + face.getWidth());
//                        int bottom= (int)(top + face.getHeight());
//                        Rect crop = new Rect(left, top, right, bottom);
//                        image.compressToJpeg(crop, 100, out);
//
//
//                        byte[] imageBytes = out.toByteArray();
//                        BitmapFactory.Options options = new BitmapFactory.Options();
//                        options.inPreferredConfig = Bitmap.Config.RGB_565;
//                        options.inMutable = true;
//                        //options.inPreferredConfig = Bitmap.Config.ARGB_8888;
//                        final Bitmap res = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
//                        Canvas c = new Canvas(res);
//                        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
//                        paint.setColor(Color.rgb(61, 61, 61));
//                        for (Landmark landmark : face.getLandmarks()) {
//                            c.drawCircle(landmark.getPosition().x - left, landmark.getPosition().y - top, 10, paint);
//                        }
//
//                        //res.copy(options, false);
//
//                        getActivity().runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//
//                                setRxFrame(res);
//                            }
//                        });
//
//                    }
//                }

                boolean debug = false;
                if(!sent && debug) {
                    final OnFragmentInteractionListener listener = (OnFragmentInteractionListener) getActivity();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onIdentityConfirmed("082");
                        }
                    });
                    sent = true;
                }

                if(!sent) {
                    SparseArray<Barcode> bcodes;
                    synchronized (bcDetector) {
                        bcodes = bcDetector.detect(fr);
                    }
                    if (bcodes.size() > 0) {
                        Log.d(TAG, "found " + bcodes.size());
                        String id = bcodes.valueAt(0).displayValue;
                        if(id != null) {
                            id = id.trim();
                            if(id.length() > 0) {
                                final OnFragmentInteractionListener listener = (OnFragmentInteractionListener) getActivity();
                                final String code = id;
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        listener.onIdentityConfirmed(code);
                                    }
                                });
                                sent = true;
                            }
                        }
                    }
                }



                if(!sent) {

                    SparseArray<Face> faces = faceDetector.detect(fr);
                    if (faces != null && faces.size() == 1) {
                        Face face = faces.valueAt(0);
                        if(face.getWidth() <= 400 && face.getHeight() <= 300) {

                            int midX = (int) (face.getPosition().x + face.getWidth() / 2);
                            int midY = (int) (face.getPosition().y + face.getHeight() / 2);


                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            int left = midX - 200;
                            int top = midY - 150;
                            int right = (int) (left + 400);
                            int bottom = (int) (top + 300);
                            Rect crop = new Rect(left, top, right, bottom);
                            image.compressToJpeg(crop, 100, out);

                            byte[] imageBytes = out.toByteArray();
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inPreferredConfig = Bitmap.Config.RGB_565;
                            //options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            final Bitmap res = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);

                            mLightControler.adjust(res);

                            final Bitmap preview = res.copy(res.getConfig(), false);

                            synchronized (mClient) {
                                try {
                                    sent = mClient.sendFrame(res, mTargetCode, 1000);
                                    if(!sent){
                                        Log.e(TAG, "Failed to post frame");
                                    }
                                    /*
                                    mClient.clearFrames();
                                    mClient.addFrame(res);
                                    //if (mFlush)
                                    sent = mClient.sendFrames(mTargetCode);
                                    */
                                } catch (Exception e) {
                                    mClient.clearFrames();
                                }
                            }


                            if (sent) {
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        setRxFrame(preview);
                                    }
                                });
                            }






                        }
                    }
                }




                //long elapsed = System.currentTimeMillis() - createTime;
                //Log.v(TAG, "Frame sending takes "+elapsed+" ms jpeg : " + (jpegTime - createTime) + " ms, decode : " + (decodedTime - createTime) + " ms, resized : " + (resizedTime - createTime) + " ms" );
            }finally {
                mSendingFrames.release();
            }

            return sent;
        }


        public byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) {
            byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
            // Rotate the Y luma
            int i = 0;
            for (int x = 0; x < imageWidth; x++) {
                for (int y = imageHeight - 1; y >= 0; y--) {
                    yuv[i] = data[y * imageWidth + x];
                    i++;
                }
            }
            // Rotate the U and V color components
            i = imageWidth * imageHeight * 3 / 2 - 1;
            for (int x = imageWidth - 1; x > 0; x = x - 2) {
                for (int y = 0; y < imageHeight / 2; y++) {
                    yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                    i--;
                    yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth)
                            + (x - 1)];
                    i--;
                }
            }
            return yuv;
        }

        private byte[] rotateYUV420Degree180(byte[] data, int imageWidth, int imageHeight) {
            byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
            int i = 0;
            int count = 0;
            for (i = imageWidth * imageHeight - 1; i >= 0; i--) {
                yuv[count] = data[i];
                count++;
            }
            i = imageWidth * imageHeight * 3 / 2 - 1;
            for (i = imageWidth * imageHeight * 3 / 2 - 1; i >= imageWidth
                    * imageHeight; i -= 2) {
                yuv[count++] = data[i - 1];
                yuv[count++] = data[i];
            }
            return yuv;
        }

        private byte[] rotateYUV420Degree270(byte[] data, int imageWidth,
                                             int imageHeight) {
            byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
            int nWidth = 0, nHeight = 0;
            int wh = 0;
            int uvHeight = 0;
            if (imageWidth != nWidth || imageHeight != nHeight) {
                nWidth = imageWidth;
                nHeight = imageHeight;
                wh = imageWidth * imageHeight;
                uvHeight = imageHeight >> 1;// uvHeight = height / 2
            }
            // ??Y
            int k = 0;
            for (int i = 0; i < imageWidth; i++) {
                int nPos = 0;
                for (int j = 0; j < imageHeight; j++) {
                    yuv[k] = data[nPos + i];
                    k++;
                    nPos += imageWidth;
                }
            }
            for (int i = 0; i < imageWidth; i += 2) {
                int nPos = wh;
                for (int j = 0; j < uvHeight; j++) {
                    yuv[k] = data[nPos + i];
                    yuv[k + 1] = data[nPos + i + 1];
                    k += 2;
                    nPos += imageWidth;
                }
            }
            return rotateYUV420Degree180(yuv, imageWidth, imageHeight);
        }

    }

}
