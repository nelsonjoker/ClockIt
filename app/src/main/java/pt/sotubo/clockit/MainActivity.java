package pt.sotubo.clockit;


import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AlertDialog;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.davidmiguel.numberkeyboard.NumberKeyboard;
import com.davidmiguel.numberkeyboard.NumberKeyboardListener;
import com.medavox.library.mutime.MissingTimeDataException;
import com.medavox.library.mutime.MuTime;
import com.medavox.library.mutime.Ntp;
import com.medavox.library.mutime.TimeDataPreserver;


import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;


public class MainActivity extends FullScreenAppCompatActivity implements FaceDetectorFragment.OnFragmentInteractionListener, NumberKeyboardListener {

    private static final String TAG = MainActivity.class.getName();
    private static final int MAX_ALLOWED_CODE = 999;

    private static final int REQUIRED_CONFIRMATION = 3;
    private static final int WINDOW_SIZE = 10;
    //private static final double RECOGNITION_THRESHOLD = 0.30;

    private double mRecognitionThreshold;

    private TextView textViewCode;
    private NumberKeyboard numberKeyboard;
    private int mCurrentCode;

    private FaceDetectorFragment mDetector;
    private List<String> mRxIdentities;

    private Switch swAdvancedMode;
    public boolean isAdvancedMode;
    public void setAdvancedMode(boolean advanced){
        swAdvancedMode.setChecked(advanced);
        isAdvancedMode = advanced;
    }


    private long mIdentityConfirmBackoff;
    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private long mLastNTPSync;

    private Handler mRingHandler;
    private List<Integer> mRingingSchedule;

    private ImageView imageViewUSB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        isAdvancedMode = false;

        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        int th = spref.getInt("threshold", 30);
        mRecognitionThreshold = th/100.0;

        mRxIdentities = new ArrayList<>(WINDOW_SIZE);
        mDetector = (FaceDetectorFragment) getSupportFragmentManager().findFragmentById(R.id.frgDaceDetector);
        mDetector.setRxConfidenceThreshold(mRecognitionThreshold);

        textViewCode = findViewById(R.id.textViewCode);
        numberKeyboard = findViewById(R.id.numberKeyboard);
        numberKeyboard.setListener(this);

        imageViewUSB = findViewById(R.id.imageViewUSB);

        mIdentityConfirmBackoff = -1;

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if(proximitySensor == null) {
            proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }



        //mDetector.startDetecting(-1);

        /*
        final EditText code = findViewById(R.id.editTextCode);
        ToggleButton btnRec = findViewById(R.id.toggleButtonTrain);
        btnRec.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b) {
                    String name = code.getText().toString();
                    mOpenFaceClient.trainStart(name);
                }else {
                    mOpenFaceClient.trainStop();
                }
            }
        });
        */

        MuTime.enableDiskCaching(this);
        mNTPUpdateListener.OnUpdateRequired();
        MuTime.registerDataPreserver(this, mNTPUpdateListener);
/*
        try {
            long theActualTime = mu.now();//throws MissingTimeDataException if we don't know the time
        }
        catch (MissingTimeDataException e) {
            Log.e("MuTime", "failed to get the actual time:+e.getMessage());
        }
*/

        LightController.getInstance().init(this);

        if(LightController.getInstance().isConnected()){
            imageViewUSB.setVisibility(View.VISIBLE);
        } else {
            imageViewUSB.setVisibility(View.INVISIBLE);
        }

        mRingingSchedule = new ArrayList<>();
        mRingingSchedule.add(6*3600+0*60);
        mRingingSchedule.add(7*3600+50*60);
        mRingingSchedule.add(8*3600+0*60);
        mRingingSchedule.add(12*3600+30*60);
        mRingingSchedule.add(13*3600+50*60);
        mRingingSchedule.add(14*3600+0*60);
        mRingingSchedule.add(17*3600+30*60);
        mRingingSchedule.add(18*3600+30*60);
        mRingingSchedule.add(20*3600+0*60);
        mRingingSchedule.add(20*3600+30*60);


        mRingHandler = new Handler();
        scheduleNextRing();
    }


    private void scheduleNextRing(){
        long now = System.currentTimeMillis();
        if(MuTime.hasTheTime()){
            try {
                now = MuTime.now();
            } catch (MissingTimeDataException e) {
                now = System.currentTimeMillis();
            }
        }



        TimeZone z = TimeZone.getDefault();
        int offset = z.getOffset(now);
        now += offset;
        long t = now % (24*3600*1000);

        long next = -1;
        for (int i = 0; i < mRingingSchedule.size(); i++){
            if(mRingingSchedule.get(i)*1000 > t){
                next = mRingingSchedule.get(i)*1000;
                break;
            }
        }

        if(next <= 0){
            next = mRingingSchedule.get(0)*1000 + (24*3600*1000);
        }

        long delay_ms = next - t;

        mRingHandler.removeCallbacksAndMessages(null);

        mRingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Make it ring at "+ System.currentTimeMillis());
                final LightController cnt = LightController.getInstance();
                if(!cnt.isConnected()){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            cnt.init(MainActivity.this);
                            if(cnt.isConnected()){
                                imageViewUSB.setVisibility(View.VISIBLE);
                            } else {
                                imageViewUSB.setVisibility(View.INVISIBLE);
                            }
                        }
                    });
                }

                cnt.ring();
                scheduleNextRing();
            }
        }, delay_ms);

    }


    private TimeDataPreserver.OnUpdateRequiredListener mNTPUpdateListener = new TimeDataPreserver.OnUpdateRequiredListener() {
        @Override
        public void OnUpdateRequired() {
            mLastNTPSync = System.currentTimeMillis();
            new InitMuTimeAsyncTask().execute();
        }
    };

    private class InitMuTimeAsyncTask extends AsyncTask<Void, Void, Void> {

        protected Void doInBackground(Void... params) {
            try {

                try {
                    MuTime.requestTimeFromServer("ntp02.oal.ul.pt");
                } catch (Exception e1) {
                    Log.e(TAG, "failed to sync time from ntp02, check ntp04");
                    MuTime.requestTimeFromServer("ntp04.oal.ul.pt");
                }
                // Ntp.performNtpAlgorithm(Ntp.resolveMultipleNtpHosts("ntp02.oal.ul.pt", "ntp04.oal.ul.pt") );
                Log.d(TAG, "finished sync time with NTP");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "something went wrong when trying to initialize MuTime", e);
            }
            return null;
        }

    }

    private SensorEventListener ProximitySensorEventListener = new SensorEventListener() {

        private double lastDetectionValue = 0;

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_PROXIMITY)
            {
                Log.d(TAG, "sensor event TYPE_PROXIMITY");
                if(!mDetector.isDetecting())
                    mDetector.startDetecting(30000);
            }else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            {
                Log.d(TAG, "sensor event TYPE_ACCELEROMETER");
                if(!mDetector.isDetecting())
                    mDetector.startDetecting(30000);
            }else if (event.sensor.getType() == Sensor.TYPE_LIGHT)
            {

                double v = event.values[0];

                double var =  lastDetectionValue > 0 ? 1 - v/lastDetectionValue : 0;
                lastDetectionValue = v;

                if(Math.abs(var) > 0.5)
                {
                    Log.d(TAG, "sensor event TYPE_LIGHT");
                    if (!mDetector.isDetecting())
                        mDetector.startDetecting(30000);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem item = menu.findItem(R.id.swAppMode);
        swAdvancedMode = item.getActionView().findViewById(R.id.switchForActionBar);

        swAdvancedMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

                if(b == isAdvancedMode)
                    return;

                setAdvancedMode(b);
            }
        });

        return true;
    }


    @Override
    protected void onResume() {
        super.onResume();

        if(LightController.getInstance().isConnected()){
            imageViewUSB.setVisibility(View.VISIBLE);
        } else {
            imageViewUSB.setVisibility(View.INVISIBLE);
        }


        mCurrentCode = 0;
        updateDisplayCode();

        mDetector.startDetecting(30000);

        if(proximitySensor != null)
            sensorManager.registerListener(ProximitySensorEventListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);



    }

    @Override
    protected void onPause() {
        super.onPause();
        mDetector.stopDetecting();
        setBrightnessHigh();
        if(proximitySensor != null)
            sensorManager.unregisterListener(ProximitySensorEventListener);

    }

    @Override
    protected void onDestroy() {
        //mDetector.stopDetecting();
        MuTime.unregisterDataPreserver(this);
        LightController.getInstance().close();
        super.onDestroy();
    }

    @Override
    public void onNumberClicked(int number) {
        int newAmount = (int) (mCurrentCode * 10.0 + number);
        if (newAmount <= MAX_ALLOWED_CODE) {
            mCurrentCode = newAmount;
            updateDisplayCode();
        }

        if(!mDetector.isDetecting())
            mDetector.startDetecting(30000);
    }

    @Override
    public void onLeftAuxButtonClicked() {


        final String id = textViewCode.getText().toString();
        boolean check = true;
/*
        synchronized (mRxIdentities) {
            check = mRxIdentities.size() > 0;
            for (String i : mRxIdentities) {
                if (!id.equals(i))
                    check = false;
            }
        }
*/

        if(check){


            if(isAdvancedMode) {

                final DialogAdvancedMode dlg = new DialogAdvancedMode(this);
                dlg.setOnDialogResultListener(new DialogAdvancedMode.OnDialogResultListener() {


                    @Override
                    public void OnCancelClicked() {

                        setAdvancedMode(false);
                    }

                    @Override
                    public void OnClockClicked() {
                        setAdvancedMode(false);
                        Intent i = new Intent(MainActivity.this, ClockEntryActivity.class);
                        i.putExtra("code", id);
                        startActivity(i);
                    }

                    @Override
                    public void OnAdminClicked() {

                        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        final String password = spref.getString("password", "123456");

                        AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                        alert.setTitle(R.string.title_password);
                        alert.setMessage(R.string.txt_password_prompt);
                        final EditText input = new EditText(MainActivity.this);
                        input.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        alert.setView(input);

                        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                String pass = input.getText().toString();

                                if(pass.equals(password)){
                                    Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                                    startActivity(i);

                                }else{
                                    Snackbar.make(textViewCode, R.string.txt_password_error,
                                            Snackbar.LENGTH_LONG)
                                            .show();
                                }

                                //dialog.dismiss();

                            }
                        });

                        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        });

                        alert.show();


                        setAdvancedMode(false);
                    }
                });

                dlg.show(id);



            }else {
                identityConfirmed(id, 20000);
            }


        }else{
            Snackbar.make(textViewCode, R.string.id_failed,
                    Snackbar.LENGTH_LONG)
                    .show();
        }


        mCurrentCode = 0;
        updateDisplayCode();

    }

    @Override
    public void onRightAuxButtonClicked() {
        mCurrentCode = (int) (mCurrentCode / 10.0);
        updateDisplayCode();
    }

    private void identityConfirmed(String id, int delay){

        if(mIdentityConfirmBackoff > System.currentTimeMillis())
            return;

        Log.d(TAG, "Identity verified for " + id);

        Intent i = new Intent(this, ClockEntryActivity.class);
        i.putExtra("code", id);
        i.putExtra("delay", delay);
        startActivity(i);
        mIdentityConfirmBackoff = System.currentTimeMillis() + 3000;
    }

    private void updateDisplayCode() {



        //textViewCode.setText(String.format("%03d", mCurrentCode));
        String code = String.format("%03d", mCurrentCode);
        textViewCode.setText(code);

        if(code.length() >= 3)
            mDetector.setTargetCode(code);
        else
            mDetector.setTargetCode("000");

        if(mCurrentCode != 0) {

            if(!mDetector.isPreviewEnabled())
                mDetector.setPreviewEnabled(true);

            String id = textViewCode.getText().toString();
            boolean check = false;
            synchronized (mRxIdentities) {
                //check = mRxIdentities.size() >= WINDOW_SIZE;
                int hits = 0;
                for (String i : mRxIdentities) {
                    if (id.equals(i))
                        hits++;
                }
                check = hits >= REQUIRED_CONFIRMATION;
            }
            if(check)
                numberKeyboard.showLeftAuxButton();
            //else
            //    numberKeyboard.hideLeftAuxButton();

        }else {
            numberKeyboard.hideLeftAuxButton();
            synchronized (mRxIdentities) {
                mRxIdentities.clear();
            }

            if(mDetector.isPreviewEnabled())
                mDetector.setPreviewEnabled(false);
        }
        //textViewCode.setText(mCodeNumberFormat.format(mCurrentCode));
        mDetector.resetAutoStop(30000);
    }



    @Override
    public void onIdentityChanged(String id) {
        synchronized (mRxIdentities) {
            mRxIdentities.add(id);
            while (mRxIdentities.size() > WINDOW_SIZE)
                mRxIdentities.remove(0);
        }
        //mRxIdentities= ids;
        updateDisplayCode();
    }

    @Override
    public void onIdentityConfirmed(String id) {
        identityConfirmed(id, 1000);
    }

    @Override
    public void onDetectionStopped() {
        mCurrentCode = 0;
        updateDisplayCode();
        setBrightnessLow();
        if((System.currentTimeMillis() - mLastNTPSync) > 24*3600*1000){
            mNTPUpdateListener.OnUpdateRequired();
        }
    }

    @Override
    public void onDetectionStarted() {
        setBrightnessHigh();
        if((System.currentTimeMillis() - mLastNTPSync) > 24*3600*1000){
            mNTPUpdateListener.OnUpdateRequired();
        }
        if(LightController.getInstance().isConnected()){
            imageViewUSB.setVisibility(View.VISIBLE);
        } else {
            imageViewUSB.setVisibility(View.INVISIBLE);
        }
    }
}
