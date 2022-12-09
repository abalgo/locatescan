package com.ablg.locatescan;

import android.media.AudioManager;
import android.media.ToneGenerator;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.LinkedList;
import java.io.FileWriter;   // Import the FileWriter class
import java.io.IOException;  // Import the IOException class to handle errors
import java.util.Objects;

import static android.os.SystemClock.sleep;



public class ScanSession {
    private String name;
    private LinkedList<ScannedItem> scanlist;
    private LinkedList<LocatedItem> loclist;
    private LinkedList<String> hier;
    private String location;
    private String lastqr;
    private boolean confwait;
    private File outdir;

    public String getLocation() {
        return location;
    }
    public String get3LatestItems() {
        String rtn="";
        int i;
        i=loclist.size();
        i=i-3;
        if (i<1) i=1;
        while(i<=loclist.size()) {
            rtn=rtn+loclist.get(i-1).pretty()+"\n";
            i++;
        }
        return rtn;
    }
    public ScanSession(File d) {
        outdir = d;
        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");
        name = formatter.format(date)+"_QRScanSession";
        scanlist = new LinkedList<ScannedItem>();
        loclist = new LinkedList<LocatedItem>();
        hier = new LinkedList<String>();
        location="";
        confwait=false;
        lastqr="";
    }
    public boolean receive(String qr) {
        ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        switch(scanreceive(qr)) {
            case -1:
                toneGen1.startTone(ToneGenerator.TONE_PROP_NACK, 300);
                break;
            case 1:
                toneGen1.startTone(ToneGenerator.TONE_PROP_PROMPT, 100);
                break;
            case 2:
                toneGen1.startTone(ToneGenerator.TONE_CDMA_NETWORK_CALLWAITING, 100);
                break;
            case 8:
                toneGen1.startTone(ToneGenerator.TONE_SUP_PIP, 400);
                break;
            case 9:
                toneGen1.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 400);
                break;
            case 11:
                toneGen1.startTone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 400);
                break;
        }
        save();
        sleep(500);
        toneGen1.release();
        lastqr=qr;
        return true;
    }
    private int scanreceive(String qr) {
        int rtn=0;
        if (hier.size() == 0) {
            // No hier level
            scanlist.add(new ScannedItem(qr));
            if (Objects.equals(location, "")) {
                location = qr;
                rtn=1;
            }
            else if (Objects.equals(qr, location)) {
                hier.add(location);
                rtn=11;
            }
            else {
                loclist.add(new LocatedItem(location, qr));
                location = "";
                rtn=2;
            }
        } else {
            // hier levels
            if (confwait) {
                confwait = false;
                if (Objects.equals(location, qr)) {
                    scanlist.add(new ScannedItem(qr));
                    scanlist.add(new ScannedItem(qr));
                    hier.removeLast();
                    rtn=9;
                    if (hier.size() == 0) location = "";
                    else location = hier.getLast();
                } else {
                    lastqr = "";
                    return -1;
                }
            } else {
                if (hier.contains(qr))
                    if (Objects.equals(qr, location)) {
                        confwait = true;
                        rtn=8;
                    }
                    else {
                        lastqr = "";
                        return -1;
                    }
                else {
                    scanlist.add(new ScannedItem(qr));
                    if (Objects.equals(qr, lastqr)) {
                        location = qr;
                        hier.add(location);
                        rtn=11;
                    } else {
                        loclist.add(new LocatedItem(location, qr));
                        rtn=2;
                    }
                }
            }
        }
        lastqr = qr;
        return rtn;

        //Note: special case when hierarchy contains qr
        //multiple ways to work
        //1. close everything until this level
        //2. wait second scan to validate and close until this level
        //3. Only authorize closing last level.
        //to be coherent with project, the point 3 is used
        //shortcut key on screen can be use to faster way to
        //close hierarchy levels
    }
    public boolean addLevel() {
        ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        if (lastqr=="") return false;
        if (hier.contains(lastqr)) return false;
        scanlist.add(new ScannedItem(lastqr));
        scanlist.add(new ScannedItem(lastqr));
        location=lastqr;
        hier.add(location);
        toneGen1.startTone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 400);
        sleep(500);
        toneGen1.release();
        return true;
    }

    public boolean removeLevel() {
        ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        if (hier.size()==0) return false;
        lastqr="";
        hier.removeLast();
        scanlist.add(new ScannedItem(lastqr));
        scanlist.add(new ScannedItem(lastqr));
        if (hier.size()==0) location="";
        else location=hier.getLast();
        toneGen1.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 500);
        sleep(500);
        toneGen1.release();
        return true;
    }

    public boolean removeLastItem() {
        ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        if (loclist.size()<1) return false;
        loclist.removeLast();
        saveLocated();
        toneGen1.startTone(ToneGenerator.TONE_CDMA_CALLDROP_LITE, 150);
        sleep(500);
        toneGen1.release();
        return true;
    }

    public void saveScanned() {
        FileWriter fw;
        try {
            //fw = new FileWriter(Environment.getExternalStorageDirectory()
            //        +"/Android/data/"
            //        +BuildConfig.APPLICATION_ID
            //       +"/"+name+"_scans.csv");
            fw = new FileWriter(outdir+"/"+name+"_scans.csv");

            Iterator it = scanlist.iterator();
            while(it.hasNext()){
                fw.write(it.next()+"\n");
            }
            fw.close();            }
        catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void saveLocated() {
        FileWriter fw;
        try {
            //fw = new FileWriter(Environment.getExternalStorageDirectory()
            //       +"/Android/data/"
            //       +BuildConfig.APPLICATION_ID
            //       +"/"+name+"_items.csv");
            fw = new FileWriter(outdir+"/"+name+"_items.csv");
            Iterator it = loclist.iterator();
            while (it.hasNext()) {
                fw.write(it.next() + "\n");
            }
            fw.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        savePairScan();
    }

    public void savePairScan() {
        FileWriter fw;
        try {
            LocatedItem l;
            //fw = new FileWriter(Environment.getExternalStorageDirectory()
            //        +"/Android/data/"
            //        +BuildConfig.APPLICATION_ID
            //        +"/"+name+"_pairscan.csv");
            fw = new FileWriter(outdir+"/"+name+"_pairscan.csv");

            Iterator<LocatedItem> it = loclist.iterator();
            while (it.hasNext()) {
                l=it.next();
                fw.write(l.pairscan() + "\n");
            }
            fw.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void save() {
        saveLocated();
        saveScanned();
    }

    public int hiersize() {
        return hier.size();
    }
    public String hierstr() {
        String rtn="";
        String nxt;
        String indent="";
        int i=0;
        Iterator it = hier.iterator();
        while(it.hasNext()){
            i++;
            nxt=it.next().toString();
            if (i>hier.size()-5) rtn=rtn+indent+nxt+"\n";
            indent=indent+"   ";
        }
        return rtn;
    }

    public String getLatestScan() {return lastqr;}
    public static class LocatedItem {
        public String tstp;
        public String location;
        public String item;

        public LocatedItem(String l, String i) {
            LocalDateTime date = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy,HH:mm:ss");
            tstp = formatter.format(date);
            location = l;
            item = i;
        }

        public String str() {
            return "com.learntodroid.androidqrcodescanner.ScanSession.LocatedItem{" +
                    "tstp='" + tstp + '\'' +
                    ", location='" + location + '\'' +
                    ", item='" + item + '\'' +
                    '}';
        }

        @Override
        public String toString() {
            return tstp + ',' + location + ',' + item;
        }

        public String pretty() { return location + " <== " + item;}

        public String pairscan() {return tstp + ',' + location  + '\n' + tstp + ',' + item; }
    }

    public static class ScannedItem {
        public String tstp;
        public String qr;

        public ScannedItem(String s) {
            LocalDateTime date = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy,HH:mm:ss");
            tstp = formatter.format(date);
            qr = s;
        }


        public String str() {
            return "com.learntodroid.androidqrcodescanner.ScanSession.ScannedItem{" +
                    "tstp='" + tstp + '\'' +
                    ", qr='" + qr + '\'' +
                    '}';
        }
        @Override
        public String toString() {
            return tstp + ',' + qr;
        }
    }
}


