package com.ablg.locatescan;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
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

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;


public class MainActivity extends AppCompatActivity {

    private CodeScanner mCodeScanner;
    private CodeScannerView mCodeScannerView;
    private ScanSession scs;
    private SeekBar mZoom ;
    private long lastcantime=0;
    private int funcmode=1; // 0 =rafale 1=clicktoscan
    private boolean autofocusenabled=true;
    private boolean idle=true;
    private File outdir;

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
        File[] outdirs;
        outdirs = ContextCompat.getExternalFilesDirs(this,null);
        //outdir=outdirs[0];
        outdir=outdirs[outdirs.length-1];
        scs = new ScanSession(outdir);
        idle=true;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 123);
        } else {
            startScanning();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.threesdots, menu);
        return true;
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        File fileWithinMyDir;
        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");
        String zipFileName = formatter.format(date)+"_QRScanSessions.zip";
        int id =item.getItemId();
        Idle();
        switch(id) {
            case R.id.newsession:
                scs.save();
                Idle();
                scs = new ScanSession(outdir);
                updateInfo();
                Toast.makeText(MainActivity.this, "New session started:"+scs.getName(), Toast.LENGTH_LONG).show();
                break;
            case R.id.about:
                Toast.makeText(MainActivity.this, "LocATE Scan v"+BuildConfig.VERSION_NAME+"\nA. Bertrand 2022", Toast.LENGTH_LONG).show();
                break;
            case R.id.export:
                Intent ShareIntent = new Intent(Intent.ACTION_SEND);
                Context context;
                context=this;
                if (ShareIntent.resolveActivity(getPackageManager()) == null) return false;
                ShareIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

                String folderPath = outdir + File.separator;

                File dir = new File(folderPath);
                List<String> listFiles;
                File zipFile = new File(folderPath + zipFileName);
                JCreateZIP zipManager = new JCreateZIP();
                listFiles = zipManager.getListFiles(dir, new ArrayList<String>());

                if (zipManager.zip(listFiles, folderPath + zipFileName) == 1) {
                    Toast.makeText(getApplicationContext(), "zipfile created!", Toast.LENGTH_LONG).show();

                    Uri apkURI = FileProvider.getUriForFile(
                            context,
                            context.getApplicationContext()
                                    .getPackageName() + ".provider", zipFile);
                    ShareIntent.setType("application/zip").putExtra(Intent.EXTRA_STREAM, apkURI);
                    ShareIntent.putExtra(Intent.EXTRA_SUBJECT, zipFileName);
                    ShareIntent.putExtra(Intent.EXTRA_TEXT, scs.strPairScan());
                    ShareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(ShareIntent, "Sharing session file using..."));
                }
                break;
        }
        return true;
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
                if (funcmode==0) {
                    if (delta < 750) return;
                    if (Objects.equals(scs.getLatestScan(),result.getText()) &&
                            (delta<1500)) return;
                }
                lastcantime=date.getTime();
                //lock.lock();
                autofocusenabled=mCodeScanner.isAutoFocusEnabled();
                mCodeScanner.setScanMode(ScanMode.PREVIEW);
                CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
                sv.setMaskColor(Color.parseColor("#AAAAAAAA"));
                try {
                    scs.receive(result.getText());
                    updateInfo();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //lock.unlock();
                if (funcmode==1) Idle();
                else Arm();




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
                if (idle) Arm();
                else Idle();
            }
        });

        Idle();
    }

    public void HierUp(View v) {
        scs.addLevel();
        Idle();
        updateInfo();
    }

    public void HierDown(View v) {
        scs.removeLevel();
        Idle();
        updateInfo();
    }

    public void RemoveLastItem(View v) {
        scs.removeLastItem();
        Idle();
        updateInfo();
    }

    public void scandone() {
        CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
        sv.setMaskColor(Color.parseColor("#A0FFFFFF"));
    }


    private void Idle() {
        CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
        mCodeScanner.setScanMode(ScanMode.PREVIEW);
        mCodeScanner.setAutoFocusEnabled(autofocusenabled);
        if (scs.getLocation()=="") sv.setMaskColor(Color.parseColor("#A0000020"));
        else sv.setMaskColor(Color.parseColor("#A0200000"));
        idle=true;
    }

    private void Arm() {
        CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
        if (scs.getLocation()=="") sv.setMaskColor(Color.parseColor("#A0000060"));
        else sv.setMaskColor(Color.parseColor("#A0600000"));
        mCodeScanner.setScanMode(ScanMode.CONTINUOUS);
        mCodeScanner.setAutoFocusEnabled(autofocusenabled);
        idle=false;
    }

    public void ChangeMode(View v) {
        TextView mode = (TextView) findViewById(R.id.mode);
        CodeScannerView sv = (CodeScannerView) findViewById(R.id.scanner_view);
        if (funcmode!=1) {
            funcmode=1;
            mode.setText("MODE: Click To Scan");
        }
        else{
            funcmode=0;
            mode.setText("MODE: Auto Scan");
        }
        Idle();
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