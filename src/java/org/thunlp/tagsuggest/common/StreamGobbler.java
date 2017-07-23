package org.thunlp.tagsuggest.common;

import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.logging.Logger;



public class StreamGobbler extends Thread{
    private InputStream is;
    private String type;
    private Logger LOG;

    public StreamGobbler(InputStream is, String type,Logger LOG) {
        this.is = is;
        this.type = type;
        this.LOG = LOG;
    }

    public void run() {
        try {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            while ((line = br.readLine()) != null) {
                LOG.info(type + ">" + line);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
