package com.ExciteOMeter.polarH10android;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;

import io.reactivex.disposables.Disposable;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.errors.PolarInvalidArgument;

import edu.ucsd.sccn.LSL;

public class HRActivity extends AppCompatActivity implements PlotterListener {

    private XYPlot plot;
    private TimePlotter plotter;

    TextView textViewHR, textViewFW;
    private String TAG = "Polar_HRActivity";
    public PolarBleApi api;
    private Disposable ecgDisposable = null;
    private Context classContext = this;
    private String DEVICE_ID;

    // LSL
    private static TextView tv;

    //// Outlet Heart Rate
    final String LSL_OUTLET_NAME_HR = "HeartRate";
    final String LSL_OUTLET_TYPE_HR = "ExciteOMeter";
    final int LSL_OUTLET_CHANNELS_HR = 1;
    final double LSL_OUTLET_NOMINAL_RATE_HR = LSL.IRREGULAR_RATE;
    final int LSL_OUTLET_CHANNEL_FORMAT_HR = LSL.ChannelFormat.int16;
    LSL.StreamInfo info_HR = null;
    LSL.StreamOutlet outlet_HR = null;
    int[] samples_HR = new int[1];

    //// Outlet R-R interval
    final String LSL_OUTLET_NAME_RR = "RRinterval";
    final String LSL_OUTLET_TYPE_RR = "ExciteOMeter";
    final int LSL_OUTLET_CHANNELS_RR = 1;
    final double LSL_OUTLET_NOMINAL_RATE_RR = LSL.IRREGULAR_RATE;
    final int LSL_OUTLET_CHANNEL_FORMAT_RR = LSL.ChannelFormat.float32;
    LSL.StreamInfo info_RR = null;
    LSL.StreamOutlet outlet_RR = null;
    float[] samples_RR = new float[1];

    // LSL Callbacks
    void showMessage(String string) {
        final String finalString = string;
        runOnUiThread(new Runnable(){
            @Override
            public void run(){
                tv.setText(finalString);
            }
        });
    }

    void sendDataHR(int data) {
        try{
            /*final String dataString = Integer.toString(data);
            runOnUiThread(new Runnable(){
                @Override
                public void run(){
                    showMessage("Now sending HR: " + dataString);
                }
            });*/
            samples_HR[0] = data;
            outlet_HR.push_sample(samples_HR);

            //Thread.sleep(5);
        } catch (Exception ex) {
            showMessage(ex.getMessage());
            outlet_HR.close();
            info_HR.destroy();
        }
    }

    void sendDataRR(float data) {
        try{
/*            final String dataString = Float.toString(data);
            runOnUiThread(new Runnable(){
                @Override
                public void run(){
                    showMessage("Now sending RR: " + dataString);
                }
            });*/
            samples_RR[0] = data;
            outlet_RR.push_sample(samples_RR);

            //Thread.sleep(5);
        } catch (Exception ex) {
            showMessage(ex.getMessage());
            outlet_RR.close();
            info_RR.destroy();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hr);
        DEVICE_ID = getIntent().getStringExtra("id");
        textViewHR = findViewById(R.id.info2);
        textViewFW = findViewById(R.id.fw2);

        plot = findViewById(R.id.plot2);

        // LSL
        tv = (TextView) findViewById(R.id.textViewLSL);
        showMessage( "Attempting to send LSL markers: ");
        System.out.println(LSL.local_clock());

        AsyncTask.execute(new Runnable() {
            public void run() {
                // configure HR
                showMessage("Creating a new StreamInfo HR...");
                info_HR = new LSL.StreamInfo(LSL_OUTLET_NAME_HR,
                                            LSL_OUTLET_TYPE_HR,
                                            LSL_OUTLET_CHANNELS_HR,
                                            LSL_OUTLET_NOMINAL_RATE_HR,
                                            LSL_OUTLET_CHANNEL_FORMAT_HR,
                                            DEVICE_ID);

                showMessage("Creating an outlet HR...");
                try {
                    outlet_HR = new LSL.StreamOutlet(info_HR);
                } catch(IOException ex) {
                    showMessage("Unable to open LSL outlet. Have you added <uses-permission android:name=\"android.permission.INTERNET\" /> to your manifest file?");
                    return;
                }
            }
        });

        AsyncTask.execute(new Runnable() {
            public void run() {
                // configure RR
                showMessage("Creating a new StreamInfo RR...");
                info_RR = new LSL.StreamInfo(LSL_OUTLET_NAME_RR,
                        LSL_OUTLET_TYPE_RR,
                        LSL_OUTLET_CHANNELS_RR,
                        LSL_OUTLET_NOMINAL_RATE_RR,
                        LSL_OUTLET_CHANNEL_FORMAT_RR,
                        DEVICE_ID);

                showMessage("Creating an outlet RR...");
                try {
                    outlet_RR = new LSL.StreamOutlet(info_RR);
                    showMessage("Sending data through LSL...");
                } catch(IOException ex) {
                    showMessage("Unable to open LSL outlet. Have you added <uses-permission android:name=\"android.permission.INTERNET\" /> to your manifest file?");
                    return;
                }
            }
        });

        // API
        api = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR);
        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged " + b);
            }

            @Override
            public void deviceConnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device connected " + s.deviceId);
                Toast.makeText(classContext, R.string.connected,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void deviceConnecting(PolarDeviceInfo polarDeviceInfo) {

            }

            @Override
            public void deviceDisconnected(PolarDeviceInfo s) {
                Log.d(TAG, "Device disconnected " + s);

            }

            @Override
            public void ecgFeatureReady(String s) {
                Log.d(TAG, "ECG Feature ready " + s);
            }

            @Override
            public void accelerometerFeatureReady(String s) {
                Log.d(TAG, "ACC Feature ready " + s);
            }

            @Override
            public void ppgFeatureReady(String s) {
                Log.d(TAG, "PPG Feature ready " + s);
            }

            @Override
            public void ppiFeatureReady(String s) {
                Log.d(TAG, "PPI Feature ready " + s);
            }

            @Override
            public void biozFeatureReady(String s) {

            }

            @Override
            public void hrFeatureReady(String s) {
                Log.d(TAG, "HR Feature ready " + s);
            }

            @Override
            public void disInformationReceived(String s, UUID u, String s1) {
                if( u.equals(UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb"))) {
                    String msg = "Firmware: " + s1.trim();
                    Log.d(TAG, "Firmware: " + s + " " + s1.trim());
                    textViewFW.append(msg + "\n");
                }
            }

            @Override
            public void batteryLevelReceived(String s, int i) {
                String msg = "ID: " + s + "\nBattery level: " + i;
                Log.d(TAG, "Battery level " + s + " " + i);
//                Toast.makeText(classContext, msg, Toast.LENGTH_LONG).show();
                textViewFW.append(msg + "\n");
            }

            @Override
            public void hrNotificationReceived(String s,
                                               PolarHrData polarHrData) {
                Log.d(TAG, "HR " + polarHrData.hr);

                // LSL Send Data
                sendDataHR(polarHrData.hr);

                List<Integer> rrsMs = polarHrData.rrsMs;
                String msg = String.valueOf(polarHrData.hr) + "\n";
                for (int i : rrsMs) {
                    // LSL Send Data
                    sendDataRR( (float) i);
                    msg += i + ",";
                }
                if (msg.endsWith(",")) {
                    msg = msg.substring(0, msg.length() - 1);
                }
                textViewHR.setText(msg);
                plotter.addValues(polarHrData);
            }

            @Override
            public void polarFtpFeatureReady(String s) {
                Log.d(TAG, "Polar FTP ready " + s);
            }
        });
        try {
            api.connectToDevice(DEVICE_ID);
        } catch (PolarInvalidArgument a){
            a.printStackTrace();
        }

        plotter = new TimePlotter(this, "HR/RR");
        plotter.setListener(this);

        plot.addSeries(plotter.getHrSeries(), plotter.getHrFormatter());
        plot.addSeries(plotter.getRrSeries(), plotter.getRrFormatter());
        plot.setRangeBoundaries(50, 100,
                BoundaryMode.AUTO);
        plot.setDomainBoundaries(0, 360000,
                BoundaryMode.AUTO);
        // Left labels will increment by 10
        plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 10);
        plot.setDomainStep(StepMode.INCREMENT_BY_VAL, 60000);
        // Make left labels be an integer (no decimal places)
        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).
                setFormat(new DecimalFormat("#"));
        // These don't seem to have an effect
        plot.setLinesPerRangeLabel(2);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        api.shutDown();

        outlet_RR.close();
        info_RR.destroy();

        outlet_HR.close();
        info_HR.destroy();
    }

    public void update() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                plot.redraw();
            }
        });
    }
}
