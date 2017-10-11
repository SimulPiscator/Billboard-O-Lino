package org.simulpiscator.billboardolino;

import android.os.SystemClock;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public class RootScript {
    private String mScript, mEncoding;
    private Integer mExitValue;
    private String mOutput = "", mErrorOutput = "";
    private static String sSuBinary = null;

    static boolean rootAvailable() {
        return !suBinary().isEmpty();
    }

    static String suBinary() {
        if(sSuBinary == null) {
            sSuBinary = "";
            for(String path : new String[] {"/system/bin/su", "/system/xbin/su"})
                if(new File(path).canExecute())
                    sSuBinary = path;
        }
        return sSuBinary;
    }

    static boolean askForRoot(int timeoutMs) {
        RootScript rs = new RootScript("");
        try {
            return rs.execute(timeoutMs) == 0;
        }
        catch(Exception e) {
        }
        return false;
    }

    RootScript(String script) {
        this(script, "UTF-8");
    }
    RootScript(String script, String enc) {
        mScript = script;
        mEncoding = enc;
    }

    String output() { return mOutput; }
    String errorOutput() { return mErrorOutput; }
    Integer exitValue() { return mExitValue; }

    Integer execute(int timeoutMs) throws Exception {
        return execute(null, timeoutMs, null);
    }

    Integer execute(String workingDirectory) throws Exception {
        return execute(workingDirectory, -1, null);
    }

    Integer execute(String workingDirectory, final int timeoutMs, String username) throws Exception {
        mExitValue = null;
        mOutput = "";
        mErrorOutput = "";
        if(!rootAvailable())
            throw new Exception("su binary not found");

        String[] argv = new String[] {
                suBinary(),
                username == null ? "root" : username,
                "-c", "/system/bin/sh"
        };

        File wd = null;
        if(workingDirectory != null)
            wd = new File(workingDirectory);
        final Process process = Runtime.getRuntime().exec(argv, null, wd);
        PrintStream stdin = new PrintStream(process.getOutputStream(), true, mEncoding);
        InputStream stdout = process.getInputStream();
        InputStream stderr = process.getErrorStream();

        String[] lines = mScript.split("\n");
        for (String line : lines) {
            stdin.println(line);
            mOutput += readInput(stdout);
            mErrorOutput += readInput(stderr);
        }
        stdin.close();
        if(timeoutMs > 0) {
            new Thread() {
                @Override
                public void run() {
                    SystemClock.sleep(timeoutMs);
                    process.destroy();
                }
            }.start();
        }
        try {
            mExitValue = process.waitFor();
        } catch(InterruptedException e) {
            mExitValue = null;
        }
        mOutput += readInput(stdout);
        mErrorOutput += readInput(stderr);
        return mExitValue;
    }

    private String readInput(InputStream is) throws IOException {
        int avail = is.available();
        byte[] buf = new byte[avail];
        int pos = 0, read = is.read(buf, 0, avail);
        while(read > 0 && avail > 0) {
            pos += read;
            avail -= read;
            read = is.read(buf, pos, avail);
        }
        return new String(buf, 0, pos, mEncoding);
    }

}
