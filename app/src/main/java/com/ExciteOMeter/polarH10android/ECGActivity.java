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
import com.androidplot.xy.XYPlot;

import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarEcgData;
import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.model.PolarSensorSetting;
import polar.com.sdk.api.errors.PolarInvalidArgument;

import edu.ucsd.sccn.LSL;

public class ECGActivity extends AppCompatActivity implements PlotterListener {

    private XYPlot plot;
    private Plotter plotter;

    TextView textViewHR, textViewFW;
    private String TAG = "Polar_ECGActivity";
    public PolarBleApi api;
    private Disposable ecgDisposable = null;
    private Context classContext = this;
    private String DEVICE_ID;

    // LSL
    private static TextView tv;

    //// Outlet Heart Rate
    final String LSL_OUTLET_NAME_ECG = "RawECG";
    final String LSL_OUTLET_TYPE_ECG = "ExciteOMeter";
    final int LSL_OUTLET_CHANNELS_ECG = 1;
    final double LSL_OUTLET_NOMINAL_RATE_ECG = LSL.IRREGULAR_RATE;
    final int LSL_OUTLET_CHANNEL_FORMAT_ECG = LSL.ChannelFormat.int32;
    LSL.StreamInfo info_ECG = null;
    LSL.StreamOutlet outlet_ECG = null;
    int[] samples_ECG = null;

    // LSL callbacks
    void showMessage(String string) {
        final String finalString = string;
        runOnUiThread(new Runnable(){
            @Override
            public void run(){
                tv.setText(finalString);
            }
        });
    }

    void sendDataECG(List<Integer> data) {
        try{
            if (samples_ECG == null || samples_ECG.length < data.size()) {
                samples_ECG = new int[data.size()];
                final int newSize = data.size();
                runOnUiThread(new Runnable(){
                                  @Override
                                  public void run(){
                                      showMessage("New outlet chunk_size = " + newSize);
                                  }
                });
            }

            // Fill array
            int i = 0;
            for (Integer value : data)
            {
                samples_ECG[i++] = value;
            }

            outlet_ECG.push_chunk(samples_ECG);

            //Thread.sleep(5);
        } catch (Exception ex) {
            showMessage(ex.getMessage());
            outlet_ECG.close();
            info_ECG.destroy();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecg);
        DEVICE_ID = getIntent().getStringExtra("id");
        textViewHR = findViewById(R.id.info);
        textViewFW = findViewById(R.id.fw);

        plot = findViewById(R.id.plot);

        // LSL
        tv = (TextView) findViewById(R.id.textViewLSL);
        System.out.println(LSL.local_clock());
        AsyncTask.execute(new Runnable() {
            public void run() {
                showMessage("Creating a new StreamInfo...");
                info_ECG = new LSL.StreamInfo(LSL_OUTLET_NAME_ECG,
                        LSL_OUTLET_TYPE_ECG,
                        LSL_OUTLET_CHANNELS_ECG,
                        LSL_OUTLET_NOMINAL_RATE_ECG,
                        LSL_OUTLET_CHANNEL_FORMAT_ECG,
                        DEVICE_ID);

                showMessage("Creating an outlet...");
                try {
                    outlet_ECG = new LSL.StreamOutlet(info_ECG);
                } catch(IOException ex) {
                    showMessage("Unable to open LSL outlet. Have you added <uses-permission android:name=\"android.permission.INTERNET\" /> to your manifest file?");
                    return;
                }
            }
        });

        // API
        api = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING |
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
                streamECG();
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
                textViewHR.setText(String.valueOf(polarHrData.hr));
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

        plotter = new Plotter(this, "ECG");
        plotter.setListener(this);

        plot.addSeries(plotter.getSeries(), plotter.getFormatter());
        plot.setRangeBoundaries(-3.3, 3.3, BoundaryMode.FIXED);
        plot.setRangeStep(StepMode.INCREMENT_BY_FIT, 0.55);
        plot.setDomainBoundaries(0, 500, BoundaryMode.GROW);
        plot.setLinesPerRangeLabel(2);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        api.shutDown();

        outlet_ECG.close();
        info_ECG.destroy();
    }

    public void streamECG() {
        if (ecgDisposable == null) {
            ecgDisposable =
                    api.requestEcgSettings(DEVICE_ID).toFlowable().flatMap(new Function<PolarSensorSetting, Publisher<PolarEcgData>>() {
                        @Override
                        public Publisher<PolarEcgData> apply(PolarSensorSetting sensorSetting) throws Exception {
                            return api.startEcgStreaming(DEVICE_ID,
                                    sensorSetting.maxSettings());
                        }
                    }).observeOn(AndroidSchedulers.mainThread()).subscribe(
                            new Consumer<PolarEcgData>() {
                                @Override
                                public void accept(PolarEcgData polarEcgData) throws Exception {
                                    Log.d(TAG, "ecg update");

                                    // LSL Send Data
                                    sendDataECG(polarEcgData.samples);

                                    for (Integer data : polarEcgData.samples) {
                                        plotter.sendSingleSample((float) ((float) data / 1000.0));
                                    }
                                }
                            },
                            new Consumer<Throwable>() {
                                @Override
                                public void accept(Throwable throwable) throws Exception {
                                    Log.e(TAG,
                                            "" + throwable.getLocalizedMessage());
                                    ecgDisposable = null;
                                }
                            },
                            new Action() {
                                @Override
                                public void run() throws Exception {
                                    Log.d(TAG, "complete");
                                }
                            }
                    );
        } else {
            // NOTE stops streaming if it is "running"
            ecgDisposable.dispose();
            ecgDisposable = null;
        }
    }

    @Override
    public void update() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                plot.redraw();
            }
        });
    }
}
