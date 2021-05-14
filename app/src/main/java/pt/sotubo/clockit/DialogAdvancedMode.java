package pt.sotubo.clockit;


import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.kelio.webservice.ArrayOfClocking;

public class DialogAdvancedMode extends Dialog implements
        android.view.View.OnClickListener {


    private static final String TAG = DialogAdvancedMode.class.getName();

    public interface OnDialogResultListener{
        void OnCancelClicked();
        void OnClockClicked();
        void OnAdminClicked();

    }

    private ProgressBar mProgress;
    private View mBody;
    private TextView textViewTime;
    private TextView textViewDescription;
    private ImageView imageViewInOut;


    private String mUserCode;
    private Button btnCancel, btnClock, btnAdmin;

    private OnDialogResultListener mListener;
    public void setOnDialogResultListener(OnDialogResultListener l){ mListener = l;}

    private ClockEntryReadTask mRunningTask;

    public DialogAdvancedMode(Context ctx) {
        super(ctx);
        mUserCode = "";
        mRunningTask = null;
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_advanced_mode_dialog);
        setContentView(R.layout.dialog_advanced);

        mProgress = findViewById(R.id.progressBar);
        mBody = findViewById(R.id.linearLayoutBody);
        textViewTime = findViewById(R.id.textViewTime);
        textViewDescription = findViewById(R.id.textViewDescription);
        imageViewInOut = findViewById(R.id.imageViewInOut);

        btnCancel = findViewById(R.id.btn_cancel);
        btnClock = findViewById(R.id.btn_clock);
        btnAdmin = findViewById(R.id.btn_admin);
        btnCancel.setOnClickListener(this);
        btnClock.setOnClickListener(this);
        btnAdmin.setOnClickListener(this);




    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_cancel:
                if(mRunningTask != null)
                    mRunningTask.cancel(true);
                mListener.OnCancelClicked();
                dismiss();
                break;
            case R.id.btn_clock:
                mListener.OnClockClicked();
                dismiss();
                break;
            case R.id.btn_admin:
                mListener.OnAdminClicked();
                dismiss();
                break;
            default:
                break;
        }
        dismiss();
    }

    private void showProgress(boolean show){
        if(show){
            mProgress.setVisibility(View.VISIBLE);
            mBody.setVisibility(View.GONE);
        }else{
            mProgress.setVisibility(View.GONE);
            mBody.setVisibility(View.VISIBLE);

        }

    }


    public void show(String userCode) {
        show();
        mUserCode = userCode;
        showProgress(true);
        mRunningTask = new ClockEntryReadTask(mUserCode);
        mRunningTask.execute((Void) null);
    }


    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class ClockEntryReadTask extends AsyncTask<Void, Void, ArrayOfClocking> {

        private final String mCode;

        ClockEntryReadTask(String code) {
            mCode = code;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected ArrayOfClocking doInBackground(Void... params) {

//            SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(getContext());
//            final String ws_host = spref.getString("ws_host", "http://192.168.0.10:8089");
//            final String vWsLogin = spref.getString("ws_username", "wsuser");
//            final String vWsPassWord = spref.getString("ws_password", "wsbodet");
//
//            String vKelio = ws_host + "/open";   //Kelio Server Address
//            String vNomService = "ClockingService";                 //Web Service Name
//
//            String url = vKelio + "/services/" + vNomService + "";
//            List<HeaderProperty> authHeaders = new ArrayList<>();
//            try {
//                byte[] data = String.format("%s:%s", vWsLogin, vWsPassWord ).getBytes("UTF-8");
//                String base64 = Base64.encodeToString(data, Base64.DEFAULT);
//                authHeaders.add(new HeaderProperty("Authorization", String.format("Basic %s", base64 )));
//            } catch (UnsupportedEncodingException e) {
//                e.printStackTrace();
//            }
//
//            ClockingService mService = new ClockingService();
//            mService.setUrl(url);
//            mService.setTimeOut(30000);
//
//
//            if(isCancelled())
//                return null;
//
//
//            Calendar c = Calendar.getInstance();
//
//            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
//            String endDate = df.format(c.getTime());
//            c.add(Calendar.DAY_OF_MONTH, -1);
//            String startDate = df.format(c.getTime());
//
//            VectoraskedEmployee employees = new VectoraskedEmployee();
//            askedEmployee e = new askedEmployee();
//            e.employeeIdentificationNumber = mCode;
//            e.startDate = startDate;
//            e.endDate = endDate;
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
//
//            if(!isCancelled()) {
//
//                VectorClocking vArrayOfClocking = mService.exportClockingsByDateForEmployeeList(employees, authHeaders);
//
//                return vArrayOfClocking;
//            }

            return null;

        }

        @Override
        protected void onPostExecute(final ArrayOfClocking res) {

            if(res == null){
                onCancelled();
                return;
            }

//            List<Clocking> clocks = new ArrayList<>();
//
//            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//
//            if(res != null) {
//                String vValueBadge = "";
//                for (Clocking vClock : res) {
//
//                    Date dt = new Date();
//                    try {
//                        dt = df.parse(vClock.date+" "+vClock.time);
//                    } catch (ParseException e) {
//                        e.printStackTrace();
//                    }
//
//                    if(!(vClock.automatic && (dt.getTime() > System.currentTimeMillis())))
//                        clocks.add(0, vClock);
//
//
//
//                    Log.d(TAG,"Surname: " + vClock.employeeSurname + ", First name: " + vClock.employeeFirstName);
//                    Log.d(TAG,"Id. number: " + vClock.employeeIdentificationNumber);
//                    Log.d(TAG,"Clocking date:" + vClock.date);
//                    if (vClock.inOutIndicator == 1) vValueBadge = "In";
//                    if (vClock.inOutIndicator == 2) vValueBadge = "Out";
//                    Log.d(TAG,"Clocking time: " + vClock.time);
//                    Log.d(TAG,"Clocking in/out: " + vValueBadge);
//                    Log.d(TAG,"\n");
//                }
//            }
//
//            if(clocks.size() <= 0){
//                onCancelled();
//                return;
//            }
//
//            Clocking last = clocks.get(0);
//            textViewTime.setText(last.time);
//            if(last.inOutIndicator == 1){
//                imageViewInOut.setImageResource( R.drawable.v_forward);
//                textViewDescription.setText(R.string.txt_enter);
//                textViewTime.setTextColor( getContext().getResources().getColor(android.R.color.holo_green_dark));
//            }else{
//                imageViewInOut.setImageResource(R.drawable.v_backward);
//                textViewDescription.setText(R.string.txt_leave);
//                textViewTime.setTextColor(getContext().getResources().getColor(android.R.color.holo_red_dark));
//            }
//
//            showProgress(false);
//            btnClock.setEnabled(true);
//            btnAdmin.setEnabled(true);



        }

        @Override
        protected void onCancelled() {


            AlertDialog.Builder builder = new AlertDialog.Builder(DialogAdvancedMode.this.getContext());
            builder.setTitle(R.string.txt_error)
                    .setMessage(R.string.txt_error_creating_entry)
                    .setIcon(android.R.drawable.stat_notify_error)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dismiss();
                        }
                    })
                    .show();

            //showProgress(false);
        }
    }

}