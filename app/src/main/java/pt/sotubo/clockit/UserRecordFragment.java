package pt.sotubo.clockit;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.media.FaceDetector;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.davidmiguel.numberkeyboard.NumberKeyboard;
import com.davidmiguel.numberkeyboard.NumberKeyboardListener;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import pt.sotubo.clockit.pt.sotubo.clockit.face.OpenFaceClient;


public class UserRecordFragment extends PreferenceFragment implements Camera.PreviewCallback, NumberKeyboardListener {

    private static final String TAG = UserRecordFragment.class.getName();
    private static final int MAX_ALLOWED_CODE = 999;
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    private static final int REQUIRED_FRAMES = 100;


    private TextView textViewCode;
    private NumberKeyboard numberKeyboard;
    private int mCurrentCode;


    private CameraPreview mPreview;
    private RelativeLayout previewPlaceholder;
    private OpenFaceClient mOpenFaceClient;
    private ImageView imageViewRxFrame;

    private View mLayoutTraining;
    private Button mCancelButton;
    private ProgressBar mProgressBar;

    private int mRotationDegrees;
    private ExecutorService executorService;
    private Semaphore mSendingFrames;

    private boolean mCameraRunning;
    private int mFrameCounter = 0;

    private ProgressDialog mTrainingDialog;


    private LightController mLightControler;


    public UserRecordFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment UserRecordFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static UserRecordFragment newInstance() {
        UserRecordFragment fragment = new UserRecordFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mTrainingDialog = new ProgressDialog(getActivity());
        mTrainingDialog.setMessage(getString(R.string.txt_training));
        mTrainingDialog.dismiss();

        mLightControler = LightController.getInstance(); // new LightController(getActivity());

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_user_record, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        previewPlaceholder = view.findViewById(R.id.previewPlaceholder);
        imageViewRxFrame = view.findViewById(R.id.imageViewRxFrame);

        mProgressBar = view.findViewById(R.id.progressBarTrainProgress);
        mCancelButton = view.findViewById(R.id.btnCancel);

        mLayoutTraining = view.findViewById(R.id.linearLayoutTraining);
        mLayoutTraining.setVisibility(textViewCode.GONE);

        textViewCode = view.findViewById(R.id.textViewCode);
        numberKeyboard = view.findViewById(R.id.numberKeyboard);
        numberKeyboard.setListener(this);

        mOpenFaceClient = null;
        mRotationDegrees = 0;



        executorService = Executors.newSingleThreadExecutor();
        mSendingFrames = new Semaphore(3);
        mCameraRunning = false;

        mCurrentCode = 0;
        updateDisplayCode();


        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopTraining(false);
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            startActivity(new Intent(getActivity(), SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final String open_face_server = spref.getString("open_face_server", "wss://faceid.sotubo.pt:9000");

        if(mOpenFaceClient != null){
            mOpenFaceClient.close();
        }
        OpenFaceClient client = new OpenFaceClient();
        client.connect(open_face_server);
        mOpenFaceClient = client;
    }

    @Override
    public void onPause() {
        stopTraining(false);
        if(mOpenFaceClient != null) {
            mOpenFaceClient.close();
            mOpenFaceClient = null;
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        executorService.shutdown();
        //mLightControler.close();
    }


    @Override
    public void onNumberClicked(int number) {
        int newAmount = (int) (mCurrentCode * 10.0 + number);
        if (newAmount <= MAX_ALLOWED_CODE) {
            mCurrentCode = newAmount;
            updateDisplayCode();
        }
    }

    @Override
    public void onLeftAuxButtonClicked() {


        final String id = textViewCode.getText().toString();
        boolean check = true;

        if("".equals(id) || "000".equals(id)){
            Snackbar.make(textViewCode, R.string.txt_bad_user_code,
                    Snackbar.LENGTH_LONG)
                    .show();
        }else {




            if(mCameraRunning) {
                stopTraining(false);

            }else {

                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.txt_title_confirmation_required)
                        .setMessage(R.string.txt_msg_overwrite_images)
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                startTraining(id);
                            }})
                        .setNegativeButton(android.R.string.no, null).show();



            }
        }

    }

    @Override
    public void onRightAuxButtonClicked() {
        mCurrentCode = (int) (mCurrentCode / 10.0);
        updateDisplayCode();
    }

    private void updateDisplayCode() {



        textViewCode.setText(String.format("%03d", mCurrentCode));
    }


    private void setProgress(int p){
        mProgressBar.setProgress(p);
    }


    private long mLastFrameSent = 0;


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
                UserRecordFragment.FrameSender task = new UserRecordFragment.FrameSender(mOpenFaceClient, camera, bytes, mRotationDegrees);
                task.setFlush(true);
                /*
                if(task.send()){
                    mLastFrameSent = System.currentTimeMillis();
                }
                */

                try {
                    executorService.submit(task);
                    mLastFrameSent = System.currentTimeMillis();
                } catch (Exception e) {
                    e.printStackTrace();
                    mLastFrameSent = now + 1000;
                }

            }else{
                Log.v(TAG, "Skipped frame");
            }
/*
            Bitmap bitmap = convertYuvByteArrayToBitmap(bytes, camera, mRotationDegrees);
            if(bitmap != null) {
                mOpenFaceClient.sendFrame(bitmap);
                //imageViewRxFrame.setImageBitmap(bitmap);
            }
*/


        }

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


    public void startTraining(String userCode){

        setProgress(0);

        //mLightControler.init();
        //mLightControler.on();

        mFrameCounter = 0;
        numberKeyboard.setVisibility(View.GONE);
        mLayoutTraining.setVisibility(View.VISIBLE);

        imageViewRxFrame.setVisibility(View.VISIBLE);

        mOpenFaceClient.setOnFrameReceivedCallback(new OpenFaceClient.OnFrameReceivedCallback() {
            @Override
            public void OnFrameReceived(final Bitmap bmp) {
                if(bmp != null) {
                    //final Bitmap copy = Bitmap.createBitmap(bmp);
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            BitmapDrawable prev = ((BitmapDrawable)imageViewRxFrame.getDrawable());
                            imageViewRxFrame.setImageBitmap(bmp);
                            if(prev != null){
                                prev.getBitmap().recycle();
                            }
                            mFrameCounter++;
                            setProgress(100 * mFrameCounter / REQUIRED_FRAMES);
                            if(mFrameCounter >= REQUIRED_FRAMES){
                                mTrainingDialog.show();
                                stopTraining(true);

                            }

                            mCancelButton.setText(String.format("%s ( %d / %d )", getString(android.R.string.cancel), mFrameCounter, REQUIRED_FRAMES));

                        }
                    });

                }
            }

            @Override
            public void OnFrameIdentityReceived(String id, double confidence) {

            }

            @Override
            public void OnTrainingStatus(int status) {

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(mTrainingDialog.isShowing()){
                            mTrainingDialog.dismiss();
                        }
                    }
                });

            }
        });

        int frontFacingCameraId = findFrontFacingCamera();

        // Set the second argument by your choice.
        // Usually, 0 for back-facing camera, 1 for front-facing camera.
        // If the OS is pre-gingerbreak, this does not have any effect.
        mPreview = new CameraPreview(getActivity(), frontFacingCameraId, CameraPreview.LayoutMode.FitToParent);
        FrameLayout.LayoutParams previewLayoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        previewPlaceholder.addView(mPreview, 0, previewLayoutParams);


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

        mPreview.setOnPreviewReady(new CameraPreview.PreviewReadyCallback() {
            @Override
            public void onPreviewReady() {
                mPreview.setPreviewCallback(UserRecordFragment.this);
            }
        });
        mPreview.setPreviewCallback(this);

        mCameraRunning = true;
        mOpenFaceClient.trainStart(userCode);
    }




    public void stopTraining(boolean commit){


        mLayoutTraining.setVisibility(textViewCode.GONE);
        numberKeyboard.setVisibility(View.VISIBLE);


        if(!mCameraRunning)
            return;

        mOpenFaceClient.trainStop(commit);

        imageViewRxFrame.setVisibility(View.GONE);

        //FIXED: cannot close client for we need the result feedback
        //mOpenFaceClient.setOnFrameReceivedCallback(null);
        mPreview.setPreviewCallback(null);
        mPreview.stop();
        previewPlaceholder.removeView(mPreview);
        mPreview = null;
        //mOpenFaceClient.close();
        mCameraRunning = false;

        mLightControler.off();

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
        }


        @Override
        public void run() {
            send();
        }
        public boolean send(){
            boolean sent = false;
            try {
                createTime = System.currentTimeMillis();


                YuvImage image = new YuvImage(data, previewFormat, previewSize.width, previewSize.height, null);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                //crop it to a centered square... thats all we need anyway
                int c = Math.min(previewSize.width, previewSize.height);
                c = Math.min(c, 2*400);
                int left = (previewSize.width - c) / 2;
                int top = (previewSize.height - c) / 2;
                Rect crop = new Rect(left, top, left + c, top + c);
                image.compressToJpeg(crop, 100, out);

                long jpegTime = System.currentTimeMillis();

                byte[] imageBytes = out.toByteArray();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                final Bitmap res = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);

                long decodedTime = System.currentTimeMillis();

                Matrix matrix = new Matrix();

                int cx = res.getWidth() / 2;
                int cy = res.getHeight() / 2;
                float s = res.getHeight() / 400.0f > res.getWidth() / 300.0f ? 1.0f / (res.getWidth() / 300.0f) : 1.0f / (res.getHeight() / 400.0f);
                matrix.postScale(-1 * s, 1 * s, cx, cy);

                matrix.postRotate(mRotation);

                boolean landscape = mRotation == 90 || mRotation == 270;
                //Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 300, 400, true);
                int h = landscape ? (int) (400 / s) : (int) (300 / s);
                int w = landscape ? (int) (300 / s) : (int) (400 / s);
                int x = (int) ((res.getWidth() - w) / 2);
                int y = (int) ((res.getHeight() - h) / 2);
                final Bitmap resized = Bitmap.createBitmap(res, x, y, w, h, matrix, true);

                long resizedTime = System.currentTimeMillis();

                res.recycle();

                FaceDetector detector = new FaceDetector(400, 300, 1);

                FaceDetector.Face[] faces = new FaceDetector.Face[1];
                int found = detector.findFaces(resized, faces);

                mLightControler.adjust(resized);

                if (found > 0)
                {
                    FaceDetector.Face f = faces[0];
                    if(f.confidence() > 0.50)
                    {


                        /*
                        PointF mid = new PointF();
                        faces[0].getMidPoint(mid);
                        for (int dx = -10; dx < 10; dx++) {
                            for (int dy = -10; dy < 10; dy++) {
                                resized.setPixel((int) mid.x + dx, (int) mid.y + dy, Color.RED);
                            }
                        }
                        */



                        synchronized (mClient) {
                            try {
                                mClient.addFrame(resized);
                                //if (mFlush)
                                sent = mClient.sendFrames("");
                            }catch (Exception e){
                                mClient.clearFrames();
                            }
                        }


                    }else {
                        Log.v(TAG, "rejected frame with confidence :"+f.confidence());
                    }


                }





                //long elapsed = System.currentTimeMillis() - createTime;
                //Log.v(TAG, "Frame sending takes "+elapsed+" ms jpeg : " + (jpegTime - createTime) + " ms, decode : " + (decodedTime - createTime) + " ms, resized : " + (resizedTime - createTime) + " ms" );
            }finally {
                mSendingFrames.release();
            }

            return sent;
        }
    }
}
