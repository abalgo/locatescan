package com.ablg.locatescan;



import android.util.Log;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.util.List;

import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class JCreateZIP {

    private static final int BUFFER = 80000;

    public int zip(List<String> _files, String zipFileName) {
        try {
            BufferedInputStream origin = null;
            FileOutputStream dest = new FileOutputStream(zipFileName);
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
                    dest));
            byte data[] = new byte[BUFFER];

            for (int i = 0; i < _files.size(); i++) {
                Log.v("Compress", "Adding: " + _files.get(i));
                FileInputStream fi = new FileInputStream(_files.get(i));
                origin = new BufferedInputStream(fi, BUFFER);

                ZipEntry entry = new ZipEntry(_files.get(i).substring(_files.get(i).lastIndexOf(File.separator) + 1));
                out.putNextEntry(entry);
                int count;

                while ((count = origin.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
            }

            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }

    private void dirChecker(String dir) {
        File f = new File(dir);
        if (!f.isDirectory()) {
            f.mkdirs();
        }
    }

    public List<String> getListFiles(File file, List<String> files) {
        File[] dirs = file.listFiles();
        String name = "";
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir.isFile()) {
                    name = dir.getName().toLowerCase();
                    if(name.endsWith(".csv")) {
                        files.add(dir.getAbsolutePath());
                    }
                    else if(name.endsWith(".zip")) {
                        dir.delete();
                    }
                } else files = getListFiles(dir, files);
            }
        }
        return files;
    }

}
