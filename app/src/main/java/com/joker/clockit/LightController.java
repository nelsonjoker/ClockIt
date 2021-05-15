package com.joker.clockit;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public class LightController {

    private static final String TAG = LightController.class.getName();
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private Context mContext;
    private UsbManager mManager;
    private UsbDevice mDevice;
    private UsbInterface mInterface;

    private UsbDeviceConnection mConnection;
    private UsbSerialDriver mDriver;
    private UsbSerialPort mPort;

    private int[] iluminations;
    private double[] error_i;

    private static LightController theInstance = null;
    public static LightController getInstance(){
        if(theInstance == null){
            theInstance = new LightController();
        }
        return theInstance;
    }



    private LightController(){

        mContext = null;
        mManager = null;
        mDevice = null;
        mInterface = null;
        mConnection = null;
        mPort = null;
        iluminations = new int[4];
        error_i = new double[4];
    }

    public boolean init(Context ctx){

        mContext = ctx;
        mManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);


        close();

        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(1155, 22336, CdcAcmSerialDriver.class);

        UsbSerialProber prober = new UsbSerialProber(customTable);

        List<UsbSerialDriver> availableDrivers = prober.findAllDrivers(mManager);
        if (availableDrivers.isEmpty()) {
            return false;
        }

        mDriver = null;

        for(UsbSerialDriver drv : availableDrivers){
            UsbDevice device = drv.getDevice();
            if(device.getProductId() == 22336 && device.getVendorId() == 1155){
                mDriver = drv;
                break;
            }
        }

        if(mDriver == null)
            return false;

        mDevice = mDriver.getDevice();
        // Open a connection to the first available driver.

        mConnection = mManager.openDevice(mDevice);
        if (mConnection == null) {

            PendingIntent mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            mContext.registerReceiver(mUsbReceiver, filter);
            filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
            mContext.registerReceiver(mUsbReceiver, filter);

            mManager.requestPermission(mDevice, mPermissionIntent);


            // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
        }else{
            connect(mConnection);
        }


        return mDevice != null;
    }


    public void close(){
        if(mPort != null){
            try {
                mPort.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mPort = null;
        }

    }

    private void connect(UsbDeviceConnection connection){

        // Read some data! Most have just one port (port 0).
        UsbSerialPort port = mDriver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            mPort = port;

            //byte[] bytes = "AT+ON\r\n".getBytes();
            //port.write(bytes, 500);

        } catch (IOException e) {
            // Deal with error.
        }

    }


    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.equals(mDevice)) {
                   close();
                }
            }

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            mDevice = device;
                            mConnection = mManager.openDevice(mDevice);
                            connect(mConnection);
                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }



        }
    };

    public boolean isConnected(){
        return mPort != null;
    }

    private boolean command(String cmd){
        if(!isConnected())
            return false;

        byte[] bytes = cmd.getBytes();
        try {
            int res = mPort.write(bytes, 500);
            if(res == bytes.length){
                byte[] buffer = new byte[1024];
                res = mPort.read(buffer, 500);

                if(res >= 2){
                    if(buffer[0] == 'O' && buffer[1] == 'K'){
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }



        return false;

    }

    public boolean on(){

        if( command("AT+ON\r\n")){
            for(int i = 0; i < iluminations.length ; i++){
                iluminations[i] = 100;
            }
        }
        return false;

    }


    public boolean off(){

        if( command("AT+OFF\r\n")){
            for(int i = 0; i < iluminations.length ; i++){
                iluminations[i] = 0;
            }
        }
        return false;

    }


    public boolean adjust(Bitmap cp) {

        int w = cp.getWidth();
        int h = cp.getHeight();

        int qw = w /2;
        int qh = h/2;

        int spacing = Math.max(1, qw/10);

        int[] q = new int[4];
        q[0] = calculateBrightnessEstimate(cp, qw,0, qw,qh, spacing);
        q[1] = calculateBrightnessEstimate(cp, 0,0, qw,qh, spacing);
        q[2] = calculateBrightnessEstimate(cp, 0,qh, qw,qh, spacing);
        q[3] = calculateBrightnessEstimate(cp, qw,qh, qw,qh, spacing);

        int qs = 0;
        for(int i = 0; i < q.length ; i++){
            qs += q[i];
        }
        int avg = (qs) / q.length;

        int adj = 90 - avg;

        for(int i = 0; i < q.length ; i++){
            double error = (adj + 100*( 0.25 - q[i]/((double)qs)));
            error_i[i] += error;
            if(error_i[i] < -255)
                error_i[i] = -255;
            if(error_i[i] > 255)
                error_i[i] = 255;

            iluminations[i] += 0.6*error + 0.3*error_i[i];
            if(iluminations[i] < 0)
                iluminations[i] = 0;
            if(iluminations[i] > 100)
                iluminations[i] = 100;
        }


        String cmd = String.format("AT+LEVEL=%d,%d,%d,%d\r\n", iluminations[0], iluminations[1], iluminations[2], iluminations[3]);

        return command(cmd);

    }


    /*
    Calculates the estimated brightness of an Android Bitmap.
    pixelSpacing tells how many pixels to skip each pixel. Higher values result in better performance, but a more rough estimate.
    When pixelSpacing = 1, the method actually calculates the real average brightness, not an estimate.
    This is what the calculateBrightness() shorthand is for.
    Do not use values for pixelSpacing that are smaller than 1.
    */
    public int calculateBrightnessEstimate(android.graphics.Bitmap bitmap, int x, int y, int width, int height, int pixelSpacing) {
        int R = 0; int G = 0; int B = 0;
        int n = 0;
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, x, y, width, height);
        for (int i = 0; i < pixels.length; i += pixelSpacing) {
            int color = pixels[i];
            R += Color.red(color);
            G += Color.green(color);
            B += Color.blue(color);
            n++;
        }
        return (R + B + G) / (n * 3);
    }

    public boolean ring(){
        return command("AT+RING\r\n");
    }



}
