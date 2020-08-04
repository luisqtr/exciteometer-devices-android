package com.ExciteOMeter.polarH10android;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

import edu.ucsd.sccn.LSL;

public class MainActivity extends AppCompatActivity {

    private String TAG = "Polar_MainActivity";
    private String sharedPrefsKey = "polar_device_id";
    private String DEVICE_ID;
    SharedPreferences sharedPreferences;

    // LSL
    private static TextView tv;

    void showMessage(String string) {
        final String finalString = string;
        runOnUiThread(new Runnable(){
            @Override
            public void run(){
                tv.setText(finalString);
            }
        });
    }

    private String markertypes[] = { "Test", "Blah", "Marker", "XXX", "Testtest", "Test-1-2-3" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);
        checkBT();

        // LSL
        tv = (TextView) findViewById(R.id.textViewLSL);
        showMessage( "Attempting to send LSL markers: ");


        AsyncTask.execute(new Runnable() {
            public void run() {
                System.out.println(LSL.local_clock());
                java.util.Random rand = new java.util.Random();
                showMessage("Creating a new StreamInfo...");
                LSL.StreamInfo info = new LSL.StreamInfo("MyMarkers","Markers",1,LSL.IRREGULAR_RATE,LSL.ChannelFormat.string,"myuid4563");

                showMessage("Creating an outlet...");
                LSL.StreamOutlet outlet = null;
                try {
                    outlet = new LSL.StreamOutlet(info);
                } catch(IOException ex) {
                    showMessage("Unable to open LSL outlet. Have you added <uses-permission android:name=\"android.permission.INTERNET\" /> to your manifest file?");
                    return;
                }

                // send random marker strings
                while (true) {
                    try{

                        final String mrk = markertypes[Math.abs(rand.nextInt()) % markertypes.length];
                        runOnUiThread(new Runnable(){
                            @Override
                            public void run(){
                                showMessage("Now sending: " + mrk);
                            }
                        });
                        String[] sample = new String[1];
                        sample[0] = mrk;
                        outlet.push_sample(sample);

                        Thread.sleep(1000);
                    } catch (Exception ex) {
                        showMessage(ex.getMessage());
                        outlet.close();
                        info.destroy();
                    }

                }


            }
        });

    }

    public void onClickConnect(View view) {
        checkBT();
        DEVICE_ID = sharedPreferences.getString(sharedPrefsKey,"");
        Log.d(TAG,DEVICE_ID);
        if(DEVICE_ID.equals("")){
            showDialog(view);
        } else {
            Toast.makeText(this,getString(R.string.connecting) + " " + DEVICE_ID,Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ECGActivity.class);
            intent.putExtra("id", DEVICE_ID);
            startActivity(intent);
        }
    }

    public void onClickConnect2(View view) {
        checkBT();
        DEVICE_ID = sharedPreferences.getString(sharedPrefsKey,"");
        Log.d(TAG,DEVICE_ID);
        if(DEVICE_ID.equals("")){
            showDialog(view);
        } else {
            Toast.makeText(this,getString(R.string.connecting) + " " + DEVICE_ID,Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, HRActivity.class);
            intent.putExtra("id", DEVICE_ID);
            startActivity(intent);
        }
    }

    public void onClickChangeID(View view) {
        showDialog(view);
    }

    public void showDialog(View view){
        AlertDialog.Builder dialog = new AlertDialog.Builder(this, R.style.PolarTheme);
        dialog.setTitle("Enter your Polar device's ID");

        View viewInflated = LayoutInflater.from(getApplicationContext()).inflate(R.layout.device_id_dialog_layout,(ViewGroup) view.getRootView() , false);

        final EditText input = viewInflated.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        dialog.setView(viewInflated);

        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DEVICE_ID = input.getText().toString();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(sharedPrefsKey, DEVICE_ID);
                editor.apply();
            }
        });
        dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        dialog.show();
    }

    public void checkBT(){
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 2);
        }

        //requestPermissions() method needs to be called when the build SDK version is 23 or above
        if(Build.VERSION.SDK_INT >= 23){
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},1);
        }
    }
}