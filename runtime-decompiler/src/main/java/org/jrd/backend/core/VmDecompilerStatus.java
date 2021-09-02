package org.jrd.backend.core;


import java.util.Arrays;

/**
 * This class stores all information about the state of decompiler plugin on
 * each VM.
 */

public class VmDecompilerStatus {

    private String vmId;
    private String hostname;
    private int listenPort;
    private ClassInfo[] loadedClasses;
    private String loadedClassBytes;

    public VmDecompilerStatus() {
        this.loadedClassBytes = "";
        this.loadedClasses = new ClassInfo[]{};
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

    public void setLoadedClasses(ClassInfo[] loadedClassNames) {
        this.loadedClasses = Arrays.copyOf(loadedClassNames, loadedClassNames.length);
    }

    public String getLoadedClassBytes() {
        return loadedClassBytes;
    }

    public String[] getLoadedClassNames() {
        return Arrays.stream(loadedClasses).map(ClassInfo::getName).toArray(String[]::new);
    }

    public ClassInfo[] getLoadedClasses() {
        return Arrays.copyOf(loadedClasses, loadedClasses.length);
    }

    public void setLoadedClassBytes(String value) {
        loadedClassBytes = value;
    }
}
