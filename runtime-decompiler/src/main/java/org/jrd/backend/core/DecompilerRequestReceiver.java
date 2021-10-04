package org.jrd.backend.core;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jrd.backend.communication.CallDecompilerAgent;
import org.jrd.backend.communication.ErrorCandidate;
import org.jrd.backend.communication.FsAgent;
import org.jrd.backend.communication.JrdAgent;
import org.jrd.backend.communication.TopLevelErrorCandidate;
import org.jrd.backend.core.AgentRequestAction.RequestAction;
import org.jrd.backend.data.VmInfo;
import org.jrd.backend.data.VmManager;
import org.jrd.backend.decompiling.PluginManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * This class manages the requests that are put in queue by the controller.
 */
public class DecompilerRequestReceiver {

    private final AgentAttachManager attachManager;
    private VmManager vmManager;

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
            Logger.getLogger().log(Logger.Level.DEBUG, new RuntimeException("Illegal action in request", e));
            return TopLevelErrorCandidate.toError(e);
        }
        port = tryParseInt(portStr, "Listen port is not an integer!");
        vmPid = tryParseInt(vmPidStr, "VM PID is not a number!");

        if (vmPid >= 0 || port >= 0) {
            Logger.getLogger().log(Logger.Level.DEBUG, "Processing request. VM ID: " + vmId + ", PID: " + vmPid + ", action: " + action + ", port: " + portStr);
        } else {
            Logger.getLogger().log(Logger.Level.DEBUG, "Processing request. VM ID: " + vmId + ", action: " + action);
        }
        String response;
        switch (action) {
            case OVERWRITE:
                String classNameForOverwrite = request.getParameter(AgentRequestAction.CLASS_NAME_PARAM);
                String classFutureBody = request.getParameter(AgentRequestAction.CLASS_TO_OVERWRITE_BODY);
                response = getOverwriteAction(hostname, port, vmId, vmPid, classNameForOverwrite, classFutureBody);
                break;
            case REMOVE_OVERRIDES:
                String patern = request.getParameter(AgentRequestAction.CLASS_NAME_PARAM);
                response = getRemoveOverrideAction(hostname, port, vmId, vmPid, patern);
                break;
            case INIT_CLASS:
                String fqn = request.getParameter(AgentRequestAction.CLASS_NAME_PARAM);
                response = getInitAction(hostname, port, vmId, vmPid, fqn);
                break;
            case BYTES:
                String className = request.getParameter(AgentRequestAction.CLASS_NAME_PARAM);
                response = getByteCodeAction(hostname, port, vmId, vmPid, className);
                break;
            case OVERRIDES:
            case CLASSES:
                response = getListAction(hostname, port, vmId, vmPid, action);
                break;
            case HALT:
                response = getHaltAction(hostname, port, vmId, vmPid);
                break;
            default:
                String s = "Unknown action given: " + action;
                Logger.getLogger().log(Logger.Level.DEBUG, s);
                return TopLevelErrorCandidate.toError(s);
        }
        return response;

    }

    private int tryParseInt(String intStr, String msg) {
        try {
            return Integer.parseInt(intStr);
        } catch (NumberFormatException e) {
            Logger.getLogger().log(Logger.Level.DEBUG, msg);
            Logger.getLogger().log(Logger.Level.ALL, e);
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

        ResponseWithPort(String response, int port) {
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
            VmInfo vmInfo = vmManager.findVmFromPid(vmId);
            nativeAgent = new FsAgent(vmInfo.getCp());
        }
        String reply = nativeAgent.submitRequest(requestBody);
        ErrorCandidate errorCandidate = new ErrorCandidate(reply);
        if (errorCandidate.isError()) {
            throw new RuntimeException("Agent returned ERROR: " + errorCandidate.getErrorMessage());
        }
        return new ResponseWithPort(reply, actualListenPort);
    }

    private String getOverwriteAction(
            String hostname, int listenPort, String vmId, int vmPid, String className, String newBody
    ) {
        try {
            ResponseWithPort reply = getResponse(
                    hostname, listenPort, vmId, vmPid, "OVERWRITE\n" + className + "\n" + newBody
            );
            VmDecompilerStatus status = new VmDecompilerStatus();
            status.setHostname(hostname);
            status.setListenPort(reply.port);
            status.setVmId(vmId);
            // Note that we have no reply from overwrite. Or better, nothing to do with reply
            vmManager.getVmInfoByID(vmId).replaceVmDecompilerStatus(status);
        } catch (Exception ex) {
            Logger.getLogger().log(Logger.Level.ALL, ex);
            return TopLevelErrorCandidate.toError(ex);
        }
        return OK_RESPONSE;
    }

    private String getRemoveOverrideAction(String hostname, int listenPort, String vmId, int vmPid, String fqn) {
        return getNoReplyValue(hostname, listenPort, vmId, vmPid, fqn, RequestAction.REMOVE_OVERRIDES);
    }

    private String getInitAction(String hostname, int listenPort, String vmId, int vmPid, String fqn) {
        return getNoReplyValue(hostname, listenPort, vmId, vmPid, fqn, RequestAction.INIT_CLASS);
    }

    private String getNoReplyValue(String hostname, int listenPort, String vmId, int vmPid, String argument, RequestAction action) {
        try {
            ResponseWithPort reply = getResponse(hostname, listenPort, vmId, vmPid, action + "\n" + argument);
            VmDecompilerStatus status = new VmDecompilerStatus();
            status.setHostname(hostname);
            status.setListenPort(reply.port);
            status.setVmId(vmId);
            vmManager.getVmInfoByID(vmId).replaceVmDecompilerStatus(status);
        } catch (Exception ex) {
            Logger.getLogger().log(Logger.Level.ALL, ex);
            return TopLevelErrorCandidate.toError(ex);
        }
        return OK_RESPONSE;
    }

    private String getByteCodeAction(String hostname, int listenPort, String vmId, int vmPid, String className) {
        try {
            ResponseWithPort reply = getResponse(hostname, listenPort, vmId, vmPid, RequestAction.BYTES + "\n" + className);
            VmDecompilerStatus status = new VmDecompilerStatus();
            status.setHostname(hostname);
            status.setListenPort(reply.port);
            status.setVmId(vmId);
            status.setLoadedClassBytes(reply.response);
            vmManager.getVmInfoByID(vmId).replaceVmDecompilerStatus(status);
        } catch (Exception ex) {
            Logger.getLogger().log(Logger.Level.ALL, ex);
            return TopLevelErrorCandidate.toError(ex);
        }
        return OK_RESPONSE;
    }

    private String getListAction(String hostname, int listenPort, String vmId, int vmPid, RequestAction type) {
        try {
            ResponseWithPort reply = getResponse(hostname, listenPort, vmId, vmPid, type.toString());
            String[] arrayOfClasses = parseClasses(reply.response);
            Arrays.sort(arrayOfClasses, new ClassesComparator());
            VmDecompilerStatus status = new VmDecompilerStatus();
            status.setHostname(hostname);
            status.setListenPort(reply.port);
            status.setVmId(vmId);
            status.setLoadedClassNames(arrayOfClasses);
            vmManager.getVmInfoByID(vmId).replaceVmDecompilerStatus(status);
        } catch (Exception ex) {
            Logger.getLogger().log(Logger.Level.ALL, ex);
            return TopLevelErrorCandidate.toError(ex);
        }
        return OK_RESPONSE;
    }

    private String getHaltAction(String hostname, int listenPort, String vmId, int vmPid) {
        try {
            getResponse(hostname, listenPort, vmId, vmPid, "HALT");
        } catch (Exception e) {
            Logger.getLogger().log(Logger.Level.ALL, new RuntimeException("Exception when calling halt action", e));
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

        @SuppressWarnings({"ReturnCount", "CyclomaticComplexity"}) // comparator syntax
        @SuppressFBWarnings(
                value = "NP_NULL_ON_SOME_PATH_MIGHT_BE_INFEASIBLE",
                justification = "False report of possible NP dereference, despite testing both o1 & o2 for nullness."
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
            if (o1.contains(PluginManager.UNDECOMPILABLE_LAMBDA) && !o2.contains(PluginManager.UNDECOMPILABLE_LAMBDA)) {
                return 1;
            }
            if (!o1.contains(PluginManager.UNDECOMPILABLE_LAMBDA) && o2.contains(PluginManager.UNDECOMPILABLE_LAMBDA)) {
                return -1;
            }
            if (PluginManager.LAMBDA_FORM.matcher(o1).matches() && !PluginManager.LAMBDA_FORM.matcher(o2).matches()) {
                return 1;
            }
            if (!PluginManager.LAMBDA_FORM.matcher(o1).matches() && PluginManager.LAMBDA_FORM.matcher(o2).matches()) {
                return -1;
            }

            return o1.compareTo(o2);
        }
    }
}
