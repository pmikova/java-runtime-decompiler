package org.jrd.backend.core;

import java.util.*;
/**
 * This class stores all information about the state of decompiler plugin on 
 * each VM.
 */

public class VmDecompilerStatus{

    private String vmId;
    private String hostname;
    private int listenPort;
    private String[] loadedClassNames;
    private List<String[]> loadedClassInfos;
    private String loadedClassBytes;

    public VmDecompilerStatus() {
        this.loadedClassBytes = "";
        this.loadedClassNames = new String[]{};
    }
    
    public String getVmId() {
        return vmId;
    }

    
    public void setVmId(String vmId) {
        this.vmId = vmId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    
    public void setListenPort(int port) {
        this.listenPort = port;
    }

    
    public int getListenPort() {
        return listenPort;
    }

    public void setLoadedClassNames(String[] loadedClassNames) {
        this.loadedClassNames = Arrays.copyOf(loadedClassNames, loadedClassNames.length);

    }

    public void setLoadedClassInfos(List<String[]> loadedClassInfos){
        this.loadedClassInfos = loadedClassInfos;
    }

    public List<String[]> getLoadedClassInfos(){
        return loadedClassInfos;
    }

    
    public String getLoadedClassBytes() {
        return loadedClassBytes;
    }

    
    public String[] getLoadedClassNames() {
        return Arrays.copyOf(loadedClassNames, loadedClassNames.length);
    }

    
    public void setLoadedClassBytes(String value) {
        loadedClassBytes = value;
    }

}
