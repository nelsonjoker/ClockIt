package com.medavox.library.mutime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;



/**
 * A {@link BroadcastReceiver} which listens for device reboots and clock changes by the user.
 * Register this class as a broadcast receiver for {@link Intent#ACTION_BOOT_COMPLETED BOOT_COMPLETED}
 * and {@link Intent#ACTION_TIME_CHANGED TIME_CHANGED},
 * to allow MuTime to correct its offsets against these events.
 */
public final class TimeDataPreserver extends BroadcastReceiver {
    private Persistence persistence;
    private static final String TAG = BroadcastReceiver.class.getSimpleName();

    //private long lastBroadcastEvent;

    public interface OnUpdateRequiredListener{
        public void OnUpdateRequired();
    }

    private OnUpdateRequiredListener mOnUpdateRequiredListener;

    public TimeDataPreserver(Persistence p, OnUpdateRequiredListener listener) {
        this.persistence = p;
        this.mOnUpdateRequiredListener = listener;
    }

    /**Detects when one of the stored time stamps have been invalidated by user actions,
     * and repairs it using the intact timestamp
     *
     * <p>
     *
     * For instance, if the user changes the system clock manually,
     * then the uptime timestamp is used to calculate a new value for the system clock time stamp.
     * */
    @Override
    public void onReceive(Context context, Intent intent) {
        TimeData old = persistence.getTimeData();
        Log.i(TAG, "action \""+intent.getAction()+"\" detected. Repairing TimeData...");
        if(old == null){
            Log.d(TAG, "no old data available running the reload task");
            if(this.mOnUpdateRequiredListener != null) {
                this.mOnUpdateRequiredListener.OnUpdateRequired();
            }
            return;
        }
        long clockNow = System.currentTimeMillis();
        long uptimeNow = SystemClock.elapsedRealtime();
        long trueTime;
        TimeData.Builder builder = new TimeData.Builder(old);
        try {
            switch (intent.getAction()) {
                case Intent.ACTION_BOOT_COMPLETED:
                    //uptime can no longer be trusted

                    trueTime = clockNow + old.getClockOffset();
                    long newUptimeOffset = trueTime - uptimeNow;

                    TimeData fixedUptime = builder
                            .uptimeOffset(newUptimeOffset)
                            .build();
                    persistence.onSntpTimeData(fixedUptime);

                    break;

                case Intent.ACTION_TIME_CHANGED:
                    //offset from system clock can no longer be trusted

                    trueTime = uptimeNow + old.getUptimeOffset();
                    long newClockOffset = trueTime - clockNow;

                    TimeData fixedSystemClockTime = builder
                            .systemClockOffset(newClockOffset)
                            .build();
                    persistence.onSntpTimeData(fixedSystemClockTime);
            }
        }catch(Exception e){
            e.printStackTrace();;
            Log.e(TAG, e.getMessage());
        }
    }
}
