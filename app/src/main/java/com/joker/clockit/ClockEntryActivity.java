package com.joker.clockit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kelio.webservice.ArrayOfAskedEmployee;
import com.kelio.webservice.ArrayOfClocking;
import com.kelio.webservice.AskedEmployee;
import com.kelio.webservice.Clocking;
import com.kelio.webservice.ClockingServiceSoapBinding;
import com.medavox.library.mutime.MissingTimeDataException;
import com.medavox.library.mutime.MuTime;

import org.ksoap2.HeaderProperty;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


public class ClockEntryActivity extends FullScreenAppCompatActivity {

    private static final String TAG = ClockEntryActivity.class.getName();
    private int SELF_DESTRUCT_TIMEOUT = 20;

    private View entryLayout;
    private View mProgressView;
    private String mUserCode;

    private RecyclerView gridViewEntries;
    private EntryRecyclerViewAdapter entriesGridViewAdapter;

    private int mSelfDestructCounter;
    private Handler mSelfDestruct;
    private Button buttonOK;
    private TextView textViewTime;
    private TextView textViewDescription;
    private TextView textViewAction;
    private TextView textViewEmployee;

    private ImageView imageViewInOut;


    private SoundPool soundPool;
    private int enterSoundId;
    private int leaveSoundId;
    private AlertDialog mAlertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clock_entry);

        ActionBar a = getSupportActionBar();
        if(a != null) {
            a.setDisplayHomeAsUpEnabled(true);
        }
        setTitle(R.string.title_clock_entry);


        entryLayout = findViewById(R.id.entryLayout);
        mProgressView = findViewById(R.id.progressView);
        buttonOK = findViewById(R.id.buttonOK);
        textViewTime = findViewById(R.id.textViewTime);
        textViewDescription = findViewById(R.id.textViewDescription);
        textViewAction = findViewById(R.id.textViewAction);
        textViewEmployee = findViewById(R.id.textViewEmployee);
        imageViewInOut = findViewById(R.id.imageViewInOut);


        gridViewEntries = findViewById(R.id.gridViewEntries);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        gridViewEntries.setLayoutManager(linearLayoutManager);

        entriesGridViewAdapter = new EntryRecyclerViewAdapter(this);
        gridViewEntries.setAdapter(entriesGridViewAdapter);


        Intent intent = getIntent();
        mUserCode = intent.getStringExtra("code");
        int delay = intent.getIntExtra("delay", 20000);
        SELF_DESTRUCT_TIMEOUT = delay/1000;


        showProgress(true);
        ClockEntryTask task = new ClockEntryTask(mUserCode);
        task.execute((Void) null);

        buttonOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ClockEntryActivity.this.finish();
            }
        });



        LinearLayout linearLayoutRight = findViewById(R.id.linearLayoutRight);
        linearLayoutRight.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                resetTTL();

            }
        });


        // Set the hardware buttons to control the music
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        enterSoundId = soundPool.load(this, R.raw.sound_1, 1);
        leaveSoundId = soundPool.load(this, R.raw.confirm, 1);

        mAlertDialog = null;

    }



    private Runnable selfDestruct = new Runnable() {
        @Override
        public void run() {
            if(mSelfDestructCounter <= 0) {
                ClockEntryActivity.this.finish();
            }else{
                buttonOK.setText(String.format("%s (%d)", getString(android.R.string.ok), mSelfDestructCounter));
                mSelfDestruct.postDelayed(this, 1000);
            }
            mSelfDestructCounter--;
        }
    };

    private void resetTTL(){
        if(mSelfDestruct != null){
            mSelfDestruct.removeCallbacks(selfDestruct);
        }
        mSelfDestructCounter = SELF_DESTRUCT_TIMEOUT;
        mSelfDestruct = new Handler();
        mSelfDestruct.postDelayed(selfDestruct, 1000);
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            entryLayout.setVisibility(show ? View.GONE : View.VISIBLE);
            entryLayout.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    entryLayout.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            entryLayout.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.cancel();
        }
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class ClockEntryTask extends AsyncTask<Void, Void, ArrayOfClocking> {

        private final String mCode;
        private long mNow;  //timestamp of request

        ClockEntryTask(String code) {
            mCode = code;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            resetTTL();
        }

        @Override
        protected ArrayOfClocking doInBackground(Void... params) {

            //testRead();

            while(!isCancelled() && !MuTime.hasTheTime()){
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return null;
                }
            }
            if(!MuTime.hasTheTime())
                return null;

            long epoch = System.currentTimeMillis();
            try {
                epoch = MuTime.now();
            } catch (MissingTimeDataException e) {
                e.printStackTrace();
                return null;
            }
            this.mNow = epoch;
            Date now = new Date(epoch);
            Log.d(TAG, "Corrected time is now "+now);
            Calendar c = new GregorianCalendar();
            c.setTime(now);


            SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(ClockEntryActivity.this);
            final String ws_host = "http://192.168.0.18:8089"; // spref.getString("ws_host", "http://192.168.0.18:8089");
            final String vWsLogin = "wsuser"; // spref.getString("ws_username", "wsuser");
            final String vWsPassWord = "s0tub0"; // spref.getString("ws_password", "s0tub0");

            String vKelio = ws_host+ "/open/ClockingService";   //Kelio Server Address
            String vNomService = "ClockingService";                 //Web Service Name
            //String vWsLogin = getString(R.string.ws_username);          //Authentication Login
            //String vWsPassWord = getString(R.string.ws_password);   //Password


            /*
            String url = vKelio + "/services/" + vNomService + "";
            List<HeaderProperty> authHeaders = new ArrayList<>();
            try {
                byte[] data = String.format("%s:%s", vWsLogin, vWsPassWord ).getBytes("UTF-8");
                String base64 = Base64.encodeToString(data, Base64.DEFAULT);
                authHeaders.add(new HeaderProperty("Authorization", String.format("Basic %s", base64 )));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            */

            ClockingServiceSoapBinding mService = new ClockingServiceSoapBinding(null, ws_host + "/open/services/ClockingService", 30000 );
            try {
                mService.getHttpHeaders().add(new HeaderProperty("Authorization", "Basic " +
                        org.kobjects.base64.Base64.encode(String.format("%s:%s", vWsLogin, vWsPassWord ).getBytes("UTF-8"))));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }


            if(isCancelled())
                return null;


            ArrayOfClocking vArrayClocking = new ArrayOfClocking();
            Clocking vClocking = new Clocking();

            //Date now = new Date();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            vClocking.setEmployeeIdentificationNumber(mCode);
            //vClocking.employeeIdentificationNumber = mCode;
            vClocking.setDate(now);
            //vClocking.date = df.format(now);
            //vClocking.dateSpecified = true;

            df = new SimpleDateFormat("HH:mm:ss");
            vClocking.setTime(df.format(now));
            //vClocking.time = df.format(now);
            //vClocking.timeSpecified = true;

            vArrayClocking.add(vClocking);
            //Transfer to the Write Web Service of the list of Physical Clockings to be Added in Kelio
            ArrayOfClocking vListClock = null;
            try {
                vListClock = mService.importPhysicalClockings(vArrayClocking);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //ArrayOfClocking vErrorClocking = vServicePortType.importClockings(vArrayClocking);


            boolean is_error = false;
            if(vListClock != null) {
                /*** Error Verification when Writing Clocking **/
                Log.d(TAG, "Nb Error: " + vListClock.size());
                is_error = vListClock.size() > 0;
                for (Clocking vClock : vListClock) {
                    Log.e(TAG, vClock.getErrorMessage());
                }
            } else {
                is_error = true;
            }

            if(is_error || isCancelled()){
                return null;
            }




            df = new SimpleDateFormat("yyyy-MM-dd");
            //String endDate = df.format(c.getTime());
            //c.add(Calendar.DAY_OF_MONTH, -1);
            //String startDate = df.format(c.getTime());

            Date endDate = now;
            c.setTime(now);
            c.add(Calendar.DAY_OF_MONTH, -1);
            Date startDate = c.getTime();


            ArrayOfAskedEmployee employees = new ArrayOfAskedEmployee();
            AskedEmployee e = new AskedEmployee();
            e.setEmployeeIdentificationNumber(mCode);
            //e.employeeIdentificationNumber = mCode;
            c.setTime(now);
            e.setEndDate(endDate);
            //e.endDate = endDate;
            e.setStartDate(startDate);
            //e.startDate = startDate;
            //e.startDateSpecified = true;
            //e.endDateSpecified = true;

            /*
            e.employeeBadgeCode = null;
            e.technicalString = null;
            e.employeeBadgeCode = null;
            e.employeeKey = -1;
            e.employeeKeySpecified = false;
            */

            employees.add(e);

            if(!isCancelled()) {


                ArrayOfClocking vArrayOfClocking = null;
                try {
                    vArrayOfClocking = mService.exportClockingsByDateForEmployeeList(employees); //authheaders
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                return vArrayOfClocking;
            }

            return null;

        }

        @Override
        protected void onPostExecute(final ArrayOfClocking res) {

            resetTTL();
            if(res == null){
                onCancelled();
                return;
            }

            List<Clocking> clocks = new ArrayList<>();

            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            if(res != null) {
                String vValueBadge = "";
                for (Clocking vClock : res) {

                    Date dt = vClock.getDate();

                    if(!(vClock.getAutomatic() && (dt.getTime() <= this.mNow + 5000) )) { //discard automatic picking
                        clocks.add(0, vClock);
                    }



                    Log.d(TAG,"Surname: " + vClock.getEmployeeSurname() + ", First name: " + vClock.getEmployeeFirstName());
                    Log.d(TAG,"Id. number: " + vClock.getEmployeeIdentificationNumber());
                    Log.d(TAG,"Clocking date:" + vClock.getDate());
                    if (vClock.getInOutIndicator() == 1) vValueBadge = "In";
                    if (vClock.getInOutIndicator() == 2) vValueBadge = "Out";
                    Log.d(TAG,"Clocking time: " + vClock.getTime());
                    Log.d(TAG,"Clocking in/out: " + vValueBadge);
                    Log.d(TAG,"\n");

                }
            }

            if(clocks.size() <= 0){
                onCancelled();
                return;
            }

            Clocking last = clocks.get(0);
            textViewTime.setText(last.getTime().toString());
            if(last.getInOutIndicator() == 1){
                imageViewInOut.setImageResource(R.drawable.v_forward);
                textViewDescription.setText(R.string.txt_enter);
                textViewTime.setTextColor( getResources().getColor(android.R.color.holo_green_dark));
                textViewAction.setText(R.string.entrada);
                textViewAction.setTextColor( getResources().getColor(android.R.color.holo_green_dark));
            }else{
                imageViewInOut.setImageResource(R.drawable.v_backward);
                textViewDescription.setText(R.string.txt_leave);
                textViewTime.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                textViewAction.setText(R.string.saida);
                textViewAction.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }

            textViewEmployee.setText(String.format("%s :: %s %s", last.getEmployeeIdentificationNumber(), last.getEmployeeSurname(), last.getEmployeeFirstName()));


            entriesGridViewAdapter.setItems(clocks);

            showProgress(false);

            soundPool.play(leaveSoundId, 1.0f, 1.0f, 1, 0, 1f);

        }

        @Override
        protected void onCancelled() {


            AlertDialog.Builder builder = new AlertDialog.Builder(ClockEntryActivity.this);
            mAlertDialog = builder.setTitle(R.string.txt_error)
                    .setMessage(R.string.txt_error_creating_entry)
                    .setIcon(android.R.drawable.stat_notify_error)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ClockEntryActivity.this.finish();
                        }
                    })
                    .show();


            //showProgress(false);
        }
    }


    private class EntryRecyclerViewAdapter extends RecyclerView.Adapter<EntryRecyclerViewAdapter.RecyclerViewHolder> {


        private List<Clocking> mEntries;

        public EntryRecyclerViewAdapter(Context ctx) {
            setHasStableIds(false);
            mEntries = new ArrayList<>(0);
        }

        @Override
        public RecyclerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.clock_entry_list_item, parent, false));
        }


        @Override
        public void onBindViewHolder(final RecyclerViewHolder holder, int position) {
            Clocking clk = mEntries.get(position);

            boolean leave = false;
            if (clk.getInOutIndicator() == 1) leave = false;
            if (clk.getInOutIndicator() == 2) leave = true;

            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

            String date = df.format(clk.getDate());
            String time = clk.getTime();

            if (leave) {
                holder.imageViewDirection.setImageResource(R.drawable.v_backward);
                //holder.textViewDirection.setText(R.string.txt_leave);
                holder.layoutBackground.setBackgroundColor(getResources().getColor(R.color.colorBgRed));
            } else {
                holder.imageViewDirection.setImageResource(R.drawable.v_forward);
                //holder.textViewDirection.setText(R.string.txt_enter);
                holder.layoutBackground.setBackgroundColor(getResources().getColor(R.color.colorBgGreen));
            }



            holder.textViewTitle.setText(time);
            holder.textViewDescription.setText(date);

            holder.itemView.setTag(clk);

        }


        @Override
        public void onViewRecycled(RecyclerViewHolder holder) {
            holder.itemView.setTag(null);
            super.onViewRecycled(holder);
        }


        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        public void setItems(List<Clocking> entries) {
            setItems(entries, true);
        }

        public void setItems(List<Clocking> entries, boolean notify) {
            this.mEntries = entries;
            if (notify)
                notifyDataSetChanged();
        }


        class RecyclerViewHolder extends RecyclerView.ViewHolder {
            private View layoutBackground;
            private TextView textViewTitle;
            private TextView textViewDescription;
            private ImageView imageViewDirection;
            //private TextView textViewDirection;


            RecyclerViewHolder(View view) {
                super(view);

                layoutBackground = view.findViewById(R.id.layoutBackground);
                textViewTitle = view.findViewById(R.id.textViewTitle);
                textViewDescription = view.findViewById(R.id.textViewDescription);
                imageViewDirection = view.findViewById(R.id.imageViewDirection);
                //textViewDirection = view.findViewById(R.id.textViewDirection);
            }
        }
    }
//
//
//    private void testRead(){
//        String vKelio = "http://192.168.0.10:8089/open"; //Kelio Server Address
//        //String vKelio = "http://10.0.0.2:8089/open"; //Kelio Server Address
//        String vNomService = "ClockingService";       //Web Service Name
//        String vWsLogin = "wsuser";                   //Authentication Login
//        String vWsPassWord = "wsbodet";               //Password
//
//        String url = vKelio + "/services/" + vNomService + "";
//        List<HeaderProperty> authHeaders = new ArrayList<>();
//        try {
//            byte[] data = new byte[0];
//            data = String.format("%s:%s", vWsLogin, vWsPassWord ).getBytes("UTF-8");
//            String base64 = Base64.encodeToString(data, Base64.DEFAULT);
//            authHeaders.add(new HeaderProperty("Authorization", String.format("Basic %s", base64 )));
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//        }
//
//        ClockingService mService = new ClockingService();
//        mService.setUrl(url);
//        //mService.
//        boolean debug = true;
//
//        //String[] ids = new String[]{"096","071","006","125","067","079","030","178","045","106","051","080","054","057","134","018","138","092","048","039","075","133","060","093"};
//        //String[] ids = new String[]{"025", "088", "017", "010", "015", "138", "060", "052", "003", "127", "105", "082", "053", "014", "093", "039", "045"};
//        String[] ids = new String[]{"082"};
//
//        VectoraskedEmployee employees = new VectoraskedEmployee();
//        for(int i = 0; i < ids.length; i++)
//        {
//
//
//            askedEmployee e = new askedEmployee();
//            e.employeeIdentificationNumber = ids[i];
//            e.startDate = "2020-05-01";
//            e.endDate = "2020-05-20";
//            e.startDateSpecified = true;
//            e.endDateSpecified = true;
//
//            e.employeeBadgeCode = null;
//            e.technicalString = null;
//            e.employeeBadgeCode = null;
//            e.employeeKey = -1;
//            e.employeeKeySpecified = false;
//
//            employees.add(e);
//        }
//        VectorClocking vArrayOfClocking = mService.exportClockingsByDateForEmployeeList(employees, authHeaders);
//
//
//        String vValueBadge = "";
//        for (Clocking vClock : vArrayOfClocking) {
//            System.out.println("Surname: " + vClock.employeeSurname + ", First name: " + vClock.employeeFirstName);
//            System.out.println("Id. number: " + vClock.employeeIdentificationNumber);
//            System.out.println("Clocking date:" + vClock.date);
//            if (vClock.inOutIndicator == 1) vValueBadge = "In";
//            if (vClock.inOutIndicator == 2) vValueBadge = "Out";
//            System.out.println("Clocking time: " + vClock.time);
//            System.out.println("Clocking in/out: " + vValueBadge);
//            System.out.println("reader: " + vClock.readerDescription);
//            System.out.println("\n");
//        }
//
//    }
//
//
//    private void testWrite(){
//        String vKelio = "http://192.168.0.10:8089/open"; //Kelio Server Address
//        String vNomService = "ClockingService";       //Web Service Name
//        String vWsLogin = "wsuser";                   //Authentication Login
//        String vWsPassWord = "wsbodet";               //Password
//
//        String url = vKelio + "/services/" + vNomService + "";
//        List<HeaderProperty> authHeaders = new ArrayList<>();
//        try {
//            byte[] data = new byte[0];
//            data = String.format("%s:%s", vWsLogin, vWsPassWord ).getBytes("UTF-8");
//            String base64 = Base64.encodeToString(data, Base64.DEFAULT);
//            authHeaders.add(new HeaderProperty("Authorization", String.format("Basic %s", base64 )));
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//        }
//
//        ClockingService mService = new ClockingService();
//        mService.setUrl(url);
//
//        VectorClocking vArrayClocking = new VectorClocking();
//        Clocking vClocking = new Clocking();
//
//        vClocking.employeeIdentificationNumber = "082";
//        vClocking.date = "2018-09-21";
//        vClocking.dateSpecified = true;
//        vClocking.time = "11:51:00";
//        vClocking.timeSpecified = true;
//
//
//        vArrayClocking.add(vClocking);
//        //Transfer to the Write Web Service of the list of Physical Clockings to be Added in Kelio
//        VectorClocking vListClock = mService.importPhysicalClockings(vArrayClocking, authHeaders);
//        //ArrayOfClocking vErrorClocking = vServicePortType.importClockings(vArrayClocking);
//
//        if(vListClock != null) {
//            /*** Error Verification when Writing Clocking **/
//            System.out.println("Nb Error: " + vListClock.size());
//            for (Clocking vClock : vListClock) {
//                System.out.println(vClock.errorMessage);
//            }
//        }
//    }
//
//    private void testReadEmployees(){
//        String vKelio = "http://192.168.0.10:8089/open"; //Kelio Server Address
//        String vNomService = "EmployeeService";       //Web Service Name
//        String vWsLogin = "wsuser";                   //Authentication Login
//        String vWsPassWord = "wsbodet";               //Password
//
//        String url = vKelio + "/services/" + vNomService + "";
//        List<HeaderProperty> authHeaders = new ArrayList<>();
//        try {
//            byte[] data = new byte[0];
//            data = String.format("%s:%s", vWsLogin, vWsPassWord ).getBytes("UTF-8");
//            String base64 = Base64.encodeToString(data, Base64.DEFAULT);
//            authHeaders.add(new HeaderProperty("Authorization", String.format("Basic %s", base64 )));
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//        }
//
//        EmployeeService mService = new EmployeeService();
//        mService.setUrl(url);
//        mService.setTimeOut(30);
//        //mService.
//
//        VectorEmployee vArrayOfEmployee = mService.exportEmployees("","", authHeaders);
//
//        for (Employee employee : vArrayOfEmployee){
//            System.out.println("Surname: " + employee.surname + ", First name: " + employee.firstName);
//            System.out.println("Id. number: " + employee.identificationNumber);
//            System.out.println("Birthdate: " + employee.birthDate);
//            System.out.println("\n");
//        }
//    }



}
