package com.joker.clockit;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.joker.clockit.face.OpenFaceClient;


public class TSNEFragment extends PreferenceFragment implements OpenFaceClient.OnTSNEReceivedCallback {


    private OpenFaceClient mOpenFaceClient;

    private ImageView imageViewTSNE;

    public TSNEFragment() {
        // Required empty public constructor
    }

    public static TSNEFragment newInstance() {
        TSNEFragment fragment = new TSNEFragment();
        Bundle args = new Bundle();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final String open_face_server = spref.getString("open_face_server", "wss://faceid.sotubo.pt:9000");

        OpenFaceClient client = new OpenFaceClient();
        client.connect(open_face_server);
        mOpenFaceClient = client;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_tsne, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imageViewTSNE = view.findViewById(R.id.imageViewTSNE);

        mOpenFaceClient.setOnTSNEListener(this);

        mOpenFaceClient.getTSNE();

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
    public void OnFrameReceived(final Bitmap bmp) {


        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Drawable drw = imageViewTSNE.getDrawable();
                BitmapDrawable prev = drw instanceof BitmapDrawable ? ((BitmapDrawable)drw) : null;
                imageViewTSNE.setImageBitmap(bmp);
                if(prev != null && prev.getBitmap() != null){
                    prev.getBitmap().recycle();
                }

            }
        });


    }
}
