package org.jrd.backend.core;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jrd.backend.communication.CallDecompilerAgent;
import org.jrd.backend.communication.FsAgent;
import org.jrd.backend.communication.JrdAgent;
import org.jrd.backend.core.AgentRequestAction.RequestAction;
import org.jrd.backend.data.VmInfo;
import org.jrd.backend.data.VmManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.jrd.backend.decompiling.PluginManager.LAMBDA_FORM;
import static org.jrd.backend.decompiling.PluginManager.UNDECOMPILABLE_LAMBDA;

/**
 * This class manages the requests that are put in queue by the controller.
 */
public class DecompilerRequestReceiver {

    //private static final Logger logger = LoggingUtils.getLogger(DecompilerRequestReceiver.class);

    private final AgentAttachManager attachManager;
    private VmManager vmManager;

    public static final String ERROR_RESPONSE = "error";
    private static final String OK_RESPONSE = "ok";
    private static final int NOT_ATTACHED = -1;


    public DecompilerRequestReceiver(VmManager vmManager) {
        this.attachManager = new AgentAttachManager(vmManager);
        this.vmManager = vmManager;
    }

    public String processRequest(AgentRequestAction request) {
        String vmId = request.getParameter(AgentRequestAction.VM_ID_PARAM_NAME);
        String vmPidStr = request.getParameter(AgentRequestAction.VM_PID_PARAM_NAME);
        String hostname = request.getParameter(AgentRequestAction.HOSTNAME_PARAM_NAME);
        String portStr = request.getParameter(AgentRequestAction.LISTEN_PORT_PARAM_NAME);
        String actionStr = request.getParameter(AgentRequestAction.ACTION_PARAM_NAME);
        RequestAction action;
        int vmPid;
        int port;

        try {
            action = RequestAction.returnAction(actionStr);
        } catch (IllegalArgumentException e) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, new RuntimeException("Illegal action in request", e));
            return ERROR_RESPONSE;
        }
        port = tryParseInt(portStr, "Listen port is not an integer!");
        vmPid = tryParseInt(vmPidStr, "VM PID is not a number!");

        if (vmPid >= 0 || port >= 0) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, "Processing request. VM ID: " + vmId + ", PID: " + vmPid + ", action: " + action + ", port: " + portStr);
        } else {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, "Processing request. VM ID: " + vmId + ", action: " + action);
        }
        String response;
        switch (action) {
            case OVERWRITE:
                String classNameForOverwrite = request.getParameter(AgentRequestAction.CLASS_TO_DECOMPILE_NAME);
                String classFutureBody = request.getParameter(AgentRequestAction.CLASS_TO_OVERWRITE_BODY);
                response = getOverwriteAction(hostname, port, vmId, vmPid, classNameForOverwrite, classFutureBody);
                break;
            case BYTES:
                String className = request.getParameter(AgentRequestAction.CLASS_TO_DECOMPILE_NAME);
                response = getByteCodeAction(hostname, port, vmId, vmPid, className);
                break;
            case CLASSES:
                response = getAllLoadedClassesAction(hostname, port, vmId, vmPid);
                break;
            case CLASSINFOS:
                response = getAllLoadedClassInfosAction(hostname, port, vmId, vmPid);
                break;
            case HALT:
                response = getHaltAction(hostname, port, vmId, vmPid);
                break;
            default:
                OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, "Unknown action given: " + action);
                return ERROR_RESPONSE;
        }
        return response;

    }

    private int tryParseInt(String intStr, String msg) {
        try {
            return Integer.parseInt(intStr);
        } catch (NumberFormatException e) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, msg);
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, e);
            return NOT_ATTACHED;
        }
    }

    private int getPort(String hostname, int listenPort, String vmId, int vmPid) {
        int actualListenPort;
        if ("localhost".equals(hostname)) {
            try {
                actualListenPort = checkIfAgentIsLoaded(listenPort, vmId, vmPid);
            } catch (Exception ex) {
                throw ex;
            }
        } else {
            actualListenPort = listenPort;
        }
        if (actualListenPort == NOT_ATTACHED) {
            throw new RuntimeException("Failed to attach agent. On JDK 9 and higher, you must run the target process with '-Djdk.attach.allowAttachSelf=true'.");
        }
        return actualListenPort;
    }

    private static class ResponseWithPort {
        private final String response;
        private final int port;

        public ResponseWithPort(String response, int port) {
            this.response = response;
            this.port = port;
        }

    }

    private ResponseWithPort getResponse(String hostname, int listenPort, String vmId, int vmPid, String requestBody) {
        int actualListenPort = -1;
        JrdAgent nativeAgent;
        if (listenPort >= 0 || vmPid >= 0) {
            actualListenPort = getPort(hostname, listenPort, vmId, vmPid);
            nativeAgent = new CallDecompilerAgent(actualListenPort, hostname);
        } else {
            VmInfo vmInfo = vmManager.findVmFromPID(vmId);
            nativeAgent = new FsAgent(vmInfo.getCp());
        }
        String reply = nativeAgent.submitRequest(requestBody);
        if ("ERROR".equals(reply)) {
            throw new RuntimeException("Agent returned ERROR");

        }
        return new ResponseWithPort(reply, actualListenPort);
    }

    private String getOverwriteAction(String hostname, int listenPort, String vmId, int vmPid, String className, String nwBody) {
        try {
            ResponseWithPort reply = getResponse(hostname, listenPort, vmId, vmPid, "OVERWRITE\n" + className + "\n" + nwBody);
            VmDecompilerStatus status = new VmDecompilerStatus();
            status.setHostname(hostname);
            status.setListenPort(reply.port);
            status.setVmId(vmId);
            //note, that we have no reply from overwrite. Or better, nothing to do with reply
            vmManager.getVmInfoByID(vmId).replaceVmDecompilerStatus(status);

        } catch (Exception ex) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, ex);
            return ERROR_RESPONSE;
        }

        return OK_RESPONSE;
    }

    private String getByteCodeAction(String hostname, int listenPort, String vmId, int vmPid, String className) {
        try {
            ResponseWithPort reply = getResponse(hostname, listenPort, vmId, vmPid, "BYTES\n" + className);
            VmDecompilerStatus status = new VmDecompilerStatus();
            status.setHostname(hostname);
            status.setListenPort(reply.port);
            status.setVmId(vmId);
            status.setLoadedClassBytes(reply.response);
            vmManager.getVmInfoByID(vmId).replaceVmDecompilerStatus(status);

        } catch (Exception ex) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, ex);
            return ERROR_RESPONSE;
        }
        //logger.info("Request for bytecode sent");

        return OK_RESPONSE;
    }

    private String getAllLoadedClassesAction(String hostname, int listenPort, String vmId, int vmPid) {
        try {
            ResponseWithPort reply = getResponse(hostname, listenPort, vmId, vmPid, "CLASSES");

            String[] arrayOfClasses = parseClasses(reply.response);
            Arrays.sort(arrayOfClasses, new ClassesComparator());

            VmDecompilerStatus status = new VmDecompilerStatus();
            status.setHostname(hostname);
            status.setListenPort(reply.port);
            status.setVmId(vmId);
            status.setLoadedClassNames(arrayOfClasses);

            vmManager.getVmInfoByID(vmId).replaceVmDecompilerStatus(status);
        } catch (Exception ex) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, ex);
            return ERROR_RESPONSE;
        }
        return OK_RESPONSE;
    }

    private String getAllLoadedClassInfosAction(String hostname, int listenPort, String vmId, int vmPid){
        try {
            ResponseWithPort reply = getResponse(hostname, listenPort, vmId, vmPid, "CLASSINFOS");
            List<String[]> arrayOfClasses = parseClassInfos(reply.response);

            VmDecompilerStatus status = new VmDecompilerStatus();
            status.setHostname(hostname);
            status.setListenPort(reply.port);
            status.setVmId(vmId);
            status.setLoadedClassInfos(arrayOfClasses);
            vmManager.getVmInfoByID(vmId).replaceVmDecompilerStatus(status);
        } catch (Exception ex) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, ex);
            return ERROR_RESPONSE;
        }
        return OK_RESPONSE;
    }

    private String getHaltAction(String hostname, int listenPort, String vmId, int vmPid) {
        try {
            getResponse(hostname, listenPort, vmId, vmPid, "HALT");
        } catch (Exception e) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, new RuntimeException("Exception when calling halt action", e));
        } finally {
            vmManager.getVmInfoByID(vmId).removeVmDecompilerStatus();
        }
        return OK_RESPONSE;
    }

    private int checkIfAgentIsLoaded(int port, String vmId, int vmPid) {
        if (port != NOT_ATTACHED) {
            return port;
        }
        int actualListenPort = NOT_ATTACHED;
        VmDecompilerStatus status = attachManager.attachAgentToVm(vmId, vmPid);
        if (status != null) {
            actualListenPort = status.getListenPort();
        }

        return actualListenPort;
    }

    private String[] parseClasses(String classes) {
        String[] array = classes.split(";");
        List<String> list = new ArrayList<>(Arrays.asList(array));
        list.removeAll(Arrays.asList("", null));
        List<String> list1 = new ArrayList<>();
        for (String s : list) {
            list1.add(s);
        }
        java.util.Collections.sort(list1);
        return list1.toArray(new String[]{});

    }

    private static class ClassesComparator implements Comparator<String>, Serializable {

        @SuppressFBWarnings(
                value = "NP_NULL_ON_SOME_PATH_MIGHT_BE_INFEASIBLE",
                justification = "SpotBugs false reports possible NP dereference, even though both o1 & o2 are tested for nullness. "
        )
        @Override
        public int compare(String o1, String o2) {
            if (o1 == null && o2 == null) {
                return 0;
            }
            if (o1 == null && o2 != null) {
                return 1;
            }
            if (o1 != null && o2 == null) {
                return -1;
            }
            if (o1.startsWith("[") && !o2.startsWith("[")) {
                return 1;
            }
            if (!o1.startsWith("[") && o2.startsWith("[")) {
                return -1;
            }
            if (o1.contains(UNDECOMPILABLE_LAMBDA) && !o2.contains(UNDECOMPILABLE_LAMBDA)) {
                return 1;
            }
            if (!o1.contains(UNDECOMPILABLE_LAMBDA) && o2.contains(UNDECOMPILABLE_LAMBDA)) {
                return -1;
            }
            if (LAMBDA_FORM.matcher(o1).matches() && !LAMBDA_FORM.matcher(o2).matches()) {
                return 1;
            }
            if (!LAMBDA_FORM.matcher(o1).matches() && LAMBDA_FORM.matcher(o2).matches()) {
                return -1;
            }

            return o1.compareTo(o2);
        }
    }
    private List<String[]> parseClassInfos(String classInfos){
        String[] array = classInfos.split(";");
        List<String[]> list = new ArrayList<>();
        for(String s : array){
            String[] classInfo = s.split(",");
            list.add(classInfo);
        }
        return list;
    }

}
