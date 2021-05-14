package pt.sotubo.clockit.pt.sotubo.clockit.face;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import pt.sotubo.clockit.TSNEFragment;

public class OpenFaceClient extends WebSocketListener{


    public static final int TRAINING_STATUS_STOPPED = 0;
    public static final int TRAINING_STATUS_RUNNING = 1;
    public static final int TRAINING_STATUS_CANCELLED = 2;


    private OnTSNEReceivedCallback mTSNEListener;
    public void setOnTSNEListener(OnTSNEReceivedCallback l) {
        mTSNEListener = l;
    }



    public interface OnTSNEReceivedCallback{
        void OnFrameReceived(Bitmap bmp);
    }

    public interface OnFrameReceivedCallback{
        void OnFrameReceived(Bitmap bmp);
        void OnFrameIdentityReceived(String id, double confidence);
        void OnTrainingStatus(int status);
    }

    private static final String TAG = OpenFaceClient.class.getName();

    private static final int NORMAL_CLOSURE_STATUS = 1000;
    private OkHttpClient mClient;
    private  WebSocket mWebSocket;
    private Semaphore mWaitProcessed;


    private BlockingQueue<String> mTxFrames;
    public void addFrame(Bitmap bitmap){
        String dataURL;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(32 * 1024);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);

        bitmap.recycle();

        byte[] byteArray = byteArrayOutputStream.toByteArray();
        String head = "data:image/jpeg;base64,";
        dataURL = head + Base64.encodeToString(byteArray, Base64.DEFAULT);
        mTxFrames.add(dataURL);
    }
    public void clearFrames(){
            mTxFrames.clear();
    }

    private OnFrameReceivedCallback mOnFrameReceivedCallback;
    public void setOnFrameReceivedCallback(OnFrameReceivedCallback cb) { mOnFrameReceivedCallback = cb;}



    public OpenFaceClient(){
        mTxFrames = new LinkedBlockingQueue<>(10);
        mClient = trustAllSslClient();
        mWaitProcessed = new Semaphore(1);

    }

    /*
     * This is very bad practice and should NOT be used in production.
     */
    private static final TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }
    };
    private static final SSLContext trustAllSslContext;
    static {
        try {
            trustAllSslContext = SSLContext.getInstance("SSL");
            trustAllSslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }
    private static final SSLSocketFactory trustAllSslSocketFactory = trustAllSslContext.getSocketFactory();

    /*
     * This should not be used in production unless you really don't care
     * about the security. Use at your own risk.
     */
    private static OkHttpClient trustAllSslClient() {
        Log.d(TAG,"Using the trustAllSslClient is highly discouraged and should not be used in Production!");
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.sslSocketFactory(trustAllSslSocketFactory, (X509TrustManager)trustAllCerts[0]);
        builder.hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
        //builder.socketFactory(new RestrictedSocketFactory(64*1024));
        return builder.build();
    }

    public void connect(String server) {




        Request request = new Request.Builder().url(server).build();
        mWebSocket = mClient.newWebSocket(request, this);

        try {
            JSONObject check = new JSONObject();
            check.put("type", "NULL");
            mWebSocket.send(check.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }



    }


    private static final int defaultNumNulls = 20;
    private static final int defaultTok = 1;

    private int numNulls = 0;
    private int tok = 0;

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(TAG, "Connected to server status :" + response.code());
        mTxFrames.clear();
        numNulls = 0;
        tok = defaultTok;

    }



    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Log.d(TAG, "Message: " + text);
        JSONObject r;
        try {
            r = new JSONObject(text);

            String type = r.optString("type", "");
            //Log.d(TAG, "RX :"+type);

            if("NULL".equals(type)){
                numNulls++;
                if(numNulls >= defaultNumNulls){
                    //numNulls = 0;
                    JSONObject msg ; //= sendState();
                    //webSocket.send(msg.toString());
                    msg = sendFrameLoop("");
                    if(msg != null){
                        webSocket.send(msg.toString());
                    }
                    mWaitProcessed.release();

                }else{
                    JSONObject check = new JSONObject();
                    check.put("type", "NULL");
                    webSocket.send(check.toString());
                }

            }else if ("PROCESSED".equals(type)) {
                tok++;
                mWaitProcessed.release();
            }else if ("NEW_IMAGE".equals(type)) {

                OnFrameReceivedCallback cb = mOnFrameReceivedCallback;
                if(cb != null) {

                    String hash = r.getString("hash");
                    int identity = r.getInt("identity");

                    JSONArray reps = r.getJSONArray("representation");
                    double[] representation = new double[reps.length()];
                    for(int i = 0; i < reps.length(); i++)
                        representation[i] = reps.getDouble(i);


                    String encodedImage = r.getString("content");
                    encodedImage = encodedImage.split(",")[1];

                    //byte[] decodedString = decode(encodedImage); //Base64.decode(encodedImage, Base64.URL_SAFE | Base64.NO_PADDING);
                    try {
                        encodedImage = URLDecoder.decode(encodedImage, "UTF-8");
                        byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
                        Bitmap image = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        if(image != null)
                            cb.OnFrameReceived(image);

                    }catch(Exception e){
                        e.printStackTrace();
                    }

                }

/*
                String encodedImage = r.getString("image");
                encodedImage = encodedImage.split(",")[1];

                try {
                    encodedImage = URLDecoder.decode(encodedImage, "UTF-8");
                    byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
                    msg.image = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                }catch(Exception e){
                    e.printStackTrace();
                }
                */



            }else if ("IDENTITIES".equals(type)) {
                JSONArray ids = r.getJSONArray("identities");
                JSONArray confidences = r.getJSONArray("confidences");
                List<String> identities = new ArrayList<>(ids.length());

                OnFrameReceivedCallback cb = mOnFrameReceivedCallback;

                for(int i = 0; i < ids.length(); i++){
                    String id = ids.getString(i);
                    double confidence = confidences.getDouble(i);
                    if(cb != null) {
                        cb.OnFrameIdentityReceived(id, confidence);
                    }
                }

            }else if ("ANNOTATED".equals(type)) {

                OnFrameReceivedCallback cb = mOnFrameReceivedCallback;
                if(cb != null) {
                    String encodedImage = r.getString("content");
                    encodedImage = encodedImage.split(",")[1];

                    //byte[] decodedString = decode(encodedImage); //Base64.decode(encodedImage, Base64.URL_SAFE | Base64.NO_PADDING);
                    try {
                        encodedImage = URLDecoder.decode(encodedImage, "UTF-8");
                        byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
                        Bitmap image = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        cb.OnFrameReceived(image);

                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }

            }else if ("TSNE_DATA".equals(type)) {

                OnTSNEReceivedCallback cb = mTSNEListener;
                if(cb != null) {
                    String encodedImage = r.getString("content");
                    encodedImage = encodedImage.split(",")[1];

                    //byte[] decodedString = decode(encodedImage); //Base64.decode(encodedImage, Base64.URL_SAFE | Base64.NO_PADDING);
                    try {
                        encodedImage = URLDecoder.decode(encodedImage, "UTF-8");
                        byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
                        Bitmap image = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        cb.OnFrameReceived(image);

                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }

            }else if ("TRAINING".equals(type)) {
                int val = r.getInt("val");
                OnFrameReceivedCallback cb = mOnFrameReceivedCallback;
                if(cb != null) {
                    cb.OnTrainingStatus(val);
                }

            }else{
                Log.e(TAG, "Unrecognized msg type "+type);
            }


        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }


    }
    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        Log.d(TAG, "Message bytes :" + bytes.hex());
    }
    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        webSocket.close(NORMAL_CLOSURE_STATUS, null);
        mWebSocket = null;
        mTxFrames.clear();
        Log.d(TAG,"Closing : " + code + " / " + reason);
    }
    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG,"Error : " + t.getMessage());
    }



    protected JSONObject sendState() throws JSONException {

        JSONObject msg = new JSONObject();
        msg.put("type", "ALL_STATE");

        JSONArray images = new JSONArray();

        msg.put("images", images);

        JSONArray people = new JSONArray();

        msg.put("people", people);
        msg.put("training", false);

        return msg;

    }




    protected JSONObject sendFrameLoop(String target) throws JSONException {

        if (tok > 0 && !mTxFrames.isEmpty())
        {

            JSONObject msg = new JSONObject();
            msg.put("type", "FRAME");
            msg.put("identity", target);
            msg.put("debug", true);

            JSONArray urls = new JSONArray();

            while(!mTxFrames.isEmpty()){
                try {
                    String dataURL = mTxFrames.take();
                    urls.put(dataURL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if(urls.length() > 1){
                Log.d(TAG, "multi frame not supported");
            }

            msg.put("dataURL", urls);
            tok--;
            return msg;

        }

        return null;
    }

    public void close() {

        mTxFrames.clear();
        mWebSocket.close(NORMAL_CLOSURE_STATUS, null);
        mWebSocket = null;

    }

    public boolean sendFrames(String target) {
        boolean res = false;
        if(mWebSocket == null || mTxFrames.isEmpty()){
            return res;
        }

        JSONObject msg = null;
        try {
            msg = sendFrameLoop(target);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if(msg != null) {
            res = mWebSocket.send(msg.toString());
            Log.d(TAG, "Frames sent...");
        }

        return res;
    }

    public boolean sendFrame(Bitmap frame, String code, long timeout) throws JSONException {

        try {
            if(!mWaitProcessed.tryAcquire(timeout, TimeUnit.MILLISECONDS)){
                return false;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        mTxFrames.clear();
        addFrame(frame);

        JSONObject msg = new JSONObject();
        msg.put("type", "FRAME");
        msg.put("identity", code);
        msg.put("debug", true);

        JSONArray urls = new JSONArray();

        while(!mTxFrames.isEmpty()){
            try {
                String dataURL = mTxFrames.take();
                urls.put(dataURL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(urls.length() > 1){
            Log.d(TAG, "multi frame not supported");
        }

        msg.put("dataURL", urls);
        tok--;
        return mWebSocket.send(msg.toString());
    }

    public void trainStop(boolean commit) {
        if(mWebSocket == null){
            return;
        }

        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "TRAINING");
            msg.put("val", commit ? 0 : 2);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mWebSocket.send(msg.toString());
    }

    public void trainStart(String name) {

        if(mWebSocket == null){
            return;
        }

        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "ADD_PERSON");
            msg.put("val", name);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mWebSocket.send(msg.toString());



        msg = new JSONObject();
        try {
            msg.put("type", "TRAINING");
            msg.put("val", true);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mWebSocket.send(msg.toString());



    }


    public void getTSNE() {

        if(mWebSocket == null){
            return;
        }

        JSONObject msg = new JSONObject();

        msg = new JSONObject();
        try {
            msg.put("type", "REQ_TSNE");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mWebSocket.send(msg.toString());

    }


}
