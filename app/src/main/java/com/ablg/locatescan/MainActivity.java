package com.ablg.locatescan;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.budiyev.android.codescanner.DecodeCallback;
import com.budiyev.android.codescanner.ScanMode;
import com.google.zxing.Result;

import java.io.File;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity {

    private CodeScanner mCodeScanner;
    private CodeScannerView mCodeScannerView;
    private ScanSession scs;
    private SeekBar mZoom ;
    private long lastcantime=0;
    private int funcmode=1; // 0 =rafale 1=clicktoscan
    private boolean autofocusenabled=true;

    @Override
    protected void onResume() {
        super.onResume();
        if (mCodeScanner!=null) mCodeScanner.startPreview();
        //updateInfo();
    }

    @Override
    protected void onPause() {
        if (mCodeScanner!=null) mCodeScanner.releaseResources();
        super.onPause();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        File[] f;
        f = ContextCompat.getExternalFilesDirs(this,null);


        scs = new ScanSession(f[f.length-1]);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 123);
        } else {
            startScanning();
        }
    }

    private void updateInfo() {
        TextView hier= (TextView)  findViewById(R.id.hier);
        TextView latest= (TextView)  findViewById(R.id.latest);
        TextView items = (TextView) findViewById(R.id.items);
        TextView loc = (TextView) findViewById(R.id.location);
        latest.setText(scs.getLatestScan());
        loc.setText(scs.getLocation());
        items.setText(scs.get3LatestItems());
        hier.setText(scs.hierstr());
    }
    private void startScanning() {

        mCodeScannerView = findViewById(R.id.scanner_view);
        mCodeScanner = new CodeScanner(this, mCodeScannerView);
        mCodeScanner.startPreview();   // this line is very important, as you will not be able to scan your code without this, you will only get blank screen
        final ReentrantLock lock = new ReentrantLock();
        mCodeScanner.setDecodeCallback(new DecodeCallback() {
            @Override
            public void onDecoded(@NonNull final Result result) {
                Date date = new Date();
                long delta=date.getTime()-lastcantime;
                if (delta<750)  return ;
                lastcantime=date.getTime();
                //lock.lock();
                autofocusenabled=mCodeScanner.isAutoFocusEnabled();
                mCodeScanner.setScanMode(ScanMode.PREVIEW);
                CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
                sv.setMaskColor(Color.parseColor("#AAAAAAAA"));
                try {
                    if (scs.receive(result.getText(),funcmode)) {
                        updateInfo();
                        scandone();
                    }
                    else {
                        updateInfo();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //lock.unlock();
                pause500();




                //runOnUiThread(new Runnable() {
               //     @Override
                //    public void run() {
               //         Toast.makeText(MainActivity.this, result.getText(), Toast.LENGTH_SHORT).show();
               //    }
               // });
            }
        });

        mZoom = (SeekBar) findViewById(R.id.xzoom);
        mZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                mCodeScanner.setZoom(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }});


        //now if you want to scan again when you click on scanner then do this.
        mCodeScannerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
                if (!mCodeScanner.isPreviewActive()) {
                    mCodeScanner.startPreview();
                }
                if (funcmode==0) {
                    funcmode=-1;
                    mCodeScanner.setScanMode(ScanMode.PREVIEW);
                    mCodeScanner.setAutoFocusEnabled(autofocusenabled);
                    preparescan();
                }
                else if (funcmode==-1) {
                    funcmode=0;
                    preparescan();
                }
                else if (funcmode==1) {
                    mCodeScanner.setScanMode(ScanMode.CONTINUOUS);
                    mCodeScanner.setAutoFocusEnabled(autofocusenabled);
                    if (scs.getLocation()=="") sv.setMaskColor(Color.parseColor("#A0000080"));
                    else sv.setMaskColor(Color.parseColor("#A0800000"));

                }

            }
        });

        preparescan();
    }

    public void HierUp(View v) {
        scs.addLevel();
        updateInfo();
    }

    public void HierDown(View v) {
        scs.removeLevel();
        updateInfo();
    }

    public void RemoveLastItem(View v) {
        scs.removeLastItem();
        updateInfo();
    }

    public void scandone() {
        CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
        sv.setMaskColor(Color.parseColor("#A0FFFFFF"));
    }

    public void pause500() {
        CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
        sv.setMaskColor(Color.parseColor("#A0000000"));
        mCodeScanner.setScanMode(ScanMode.PREVIEW);
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                postscan();
            }
        }, 500);
    }
    public void postscan() {
        CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
        if (scs.getLocation()=="") sv.setMaskColor(Color.parseColor("#A0000020"));
        else sv.setMaskColor(Color.parseColor("#A0200000"));
        if (funcmode==0) preparescan();
    }

    public void preparescan() {
        CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
        if (funcmode==-1) {
            if (scs.getLocation()=="") sv.setMaskColor(Color.parseColor("#A0000020"));
            else sv.setMaskColor(Color.parseColor("#A0200000"));
            mCodeScanner.setScanMode(ScanMode.PREVIEW);
            mCodeScanner.setAutoFocusEnabled(autofocusenabled);
        }
        if (funcmode==0) {
            if (scs.getLocation()=="") sv.setMaskColor(Color.parseColor("#A0000060"));
            else sv.setMaskColor(Color.parseColor("#A0600000"));
            mCodeScanner.setScanMode(ScanMode.CONTINUOUS);
            mCodeScanner.setAutoFocusEnabled(autofocusenabled);
        }
        if (funcmode==1) {
            if (scs.getLocation()=="") sv.setMaskColor(Color.parseColor("#A0000020"));
            else sv.setMaskColor(Color.parseColor("#A0200000"));
            mCodeScanner.setScanMode(ScanMode.PREVIEW);
            mCodeScanner.setAutoFocusEnabled(autofocusenabled);
        }
    }
    public void ChangeMode(View v) {
        TextView mode = (TextView) findViewById(R.id.mode);
        CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
        if (funcmode!=1) {
            funcmode=1;
            mode.setText("MODE: Click To Scan");
        }
        else{
            funcmode=-1;
            mode.setText("MODE: Rafale");
        }
        preparescan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 123){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                startScanning();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}