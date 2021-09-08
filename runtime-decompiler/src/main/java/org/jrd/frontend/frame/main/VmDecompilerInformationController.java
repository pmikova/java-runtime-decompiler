package org.jrd.frontend.frame.main;

import org.jrd.backend.core.AgentRequestAction;
import org.jrd.backend.core.AgentRequestAction.RequestAction;
import org.jrd.backend.core.DecompilerRequestReceiver;
import org.jrd.backend.core.OutputController;
import org.jrd.backend.core.VmDecompilerStatus;
import org.jrd.backend.data.Config;
import org.jrd.backend.data.Model;
import org.jrd.backend.data.VmInfo;
import org.jrd.backend.data.VmManager;
import org.jrd.backend.decompiling.DecompilerWrapperInformation;
import org.jrd.backend.decompiling.PluginManager;
import org.jrd.frontend.frame.remote.NewConnectionController;
import org.jrd.frontend.frame.remote.NewConnectionView;
import org.jrd.frontend.frame.filesystem.NewFsVmController;
import org.jrd.frontend.frame.filesystem.NewFsVmView;
import org.jrd.frontend.frame.plugins.PluginConfigurationEditorController;
import org.jrd.frontend.frame.plugins.PluginConfigurationEditorView;

import java.util.*;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;

/**
 * This class provides Action listeners and request handling for
 * the GUI.
 */
public class VmDecompilerInformationController {

    private final MainFrameView mainFrameView;
    private final BytecodeDecompilerView bytecodeDecompilerView;
    private NewConnectionView newConnectionDialog;
    private NewFsVmView newFsVmDialog;
    private PluginConfigurationEditorView pluginConfigurationEditorView;
    private PluginConfigurationEditorController pluginConfigurationEditorController;
    private LoadingDialog loadingDialog;
    private VmManager vmManager;
    private VmInfo vmInfo;
    private PluginManager pluginManager;

    // createRequest() uses array of strings on input. Those are most common, usually shared indexes
    public static final int CLASS_NAME = 0;
    public static final int CLASS_BODY = 1;

    public VmDecompilerInformationController(MainFrameView mainFrameView, Model model) {
        this.mainFrameView = mainFrameView;
        this.bytecodeDecompilerView = mainFrameView.getBytecodeDecompilerView();
        this.vmManager = model.getVmManager();
        this.pluginManager = model.getPluginManager();

        updateVmLists();

        vmManager.subscribeToVMChange(e -> updateVmLists());

        mainFrameView.setRefreshLocalVmsListener(e -> vmManager.updateLocalVMs());
        mainFrameView.setNewConnectionDialogListener(e -> createNewConnectionDialog());
        mainFrameView.setNewFsVmDialogListener(e -> createNewFsVMDialog());
        mainFrameView.setRemoveVmDialogListener(this::removeVmDialog);
        bytecodeDecompilerView.setClassesActionListener(e -> loadClassNames());
        bytecodeDecompilerView.setBytesActionListener(e -> loadClassBytecode(e.getActionCommand()));
        bytecodeDecompilerView.setRewriteActionListener(new ClassRewriter());

        mainFrameView.setVmChanging(this::changeVm);
        mainFrameView.setHaltAgentListener(e -> haltAgent());
        mainFrameView.setPluginConfigurationEditorListener(actionEvent -> createConfigurationEditor());
        bytecodeDecompilerView.refreshComboBox(pluginManager.getWrappers());
    }

    // Method for opening plugin configuration window
    private void createConfigurationEditor() {
        pluginConfigurationEditorView = new PluginConfigurationEditorView(mainFrameView);
        pluginConfigurationEditorController = new PluginConfigurationEditorController(pluginConfigurationEditorView,
                pluginManager);
        pluginConfigurationEditorController.setPluginsConfiguredListener(actionEvent -> {
            bytecodeDecompilerView.refreshComboBox(pluginManager.getWrappers());
        });
        pluginConfigurationEditorView.setVisible(true);
    }

    private void createNewConnectionDialog() {
        newConnectionDialog = new NewConnectionView(mainFrameView);
        new NewConnectionController(newConnectionDialog, vmManager);
        newConnectionDialog.setVisible(true);

        mainFrameView.switchTabsToRemoteVms(); // for JMenuItem ActionEvent origin
    }

    private void createNewFsVMDialog() {
        newFsVmDialog = new NewFsVmView(mainFrameView);
        new NewFsVmController(newFsVmDialog, vmManager);
        newFsVmDialog.setVisible(true);
    }

    public class vmArrayList<T> extends ArrayList<VmInfo> {
        @Override
        public boolean add(VmInfo vmInfo) {
            super.add(vmInfo);
            this.sort(Comparator.comparingInt(VmInfo::getVmPid));
            return true;
        }
    }

    @SuppressWarnings("unchecked") // event.getSource() is always of type JList<VmInfo>
    private void removeVmDialog(ActionEvent event) {
        String vmType = event.getActionCommand();
        JList<VmInfo> sourceList = (JList<VmInfo>) event.getSource();
        VmInfo selectedVm = sourceList.getSelectedValue();

        if (selectedVm == null) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, "Attempted to remove " + vmType + " with none selected.");
            JOptionPane.showMessageDialog(mainFrameView.getMainFrame(), "No " + vmType + " selected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean isSavedFsVm = selectedVm.getType() == VmInfo.Type.FS && Config.getConfig().isSavedFsVm(selectedVm);
        String confirmMessage = "Are you sure you want to remove this " + vmType + "?";
        if (isSavedFsVm) {
            confirmMessage += "\nRemoving it will also no longer keep it saved between JRD instances.";
        }

        int dialogResult = JOptionPane.showConfirmDialog(mainFrameView.getMainFrame(), confirmMessage, "Warning", JOptionPane.OK_CANCEL_OPTION);
        if (dialogResult == JOptionPane.CANCEL_OPTION) {
            return;
        }

        if (!vmManager.removeVm(selectedVm)) {
            String removeFailMessage = "Failed to remove VM: " + selectedVm;
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, removeFailMessage);
            JOptionPane.showMessageDialog(mainFrameView.getMainFrame(), removeFailMessage, "Error", JOptionPane.ERROR_MESSAGE);
        }

        if (isSavedFsVm) {
            try {
                Config.getConfig().removeSavedFsVm(selectedVm);
                Config.getConfig().saveConfigFile();
            } catch (IOException e) {
                OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, "Unable to tag '" + vmInfo + "' as no longer to be saved. Cause:");
                OutputController.getLogger().log(e);
            }
        }

        cleanup();
    }

    private void updateVmLists() {
        ArrayList<VmInfo> localVms = new ArrayList<>();
        ArrayList<VmInfo> remoteVms = new ArrayList<>();
        ArrayList<VmInfo> fsVms = new ArrayList<>();

        vmManager.getVmInfoSet().forEach(info -> {
            if (info.getType() == VmInfo.Type.LOCAL) {
                localVms.add(info);
            } else if (info.getType() == VmInfo.Type.REMOTE) {
                remoteVms.add(info);
            } else if (info.getType() == VmInfo.Type.FS) {
                fsVms.add(info);
            }
        });

        localVms.sort(VmInfo.LOCAL_VM_COMPARATOR);
        remoteVms.sort(VmInfo.REMOTE_VM_COMPARATOR);
        fsVms.sort(VmInfo.FS_VM_COMPARATOR);

        mainFrameView.setLocalVmList(localVms.toArray(new VmInfo[0]));
        mainFrameView.setRemoteVmList(remoteVms.toArray(new VmInfo[0]));
        mainFrameView.setFsVmList(fsVms.toArray(new VmInfo[0]));
    }

    private void changeVm(ActionEvent event) {
        JList<VmInfo> vmList = (JList<VmInfo>) event.getSource();
        VmInfo selectedVmInfo = vmList.getSelectedValue();
        mainFrameView.switchPanel(selectedVmInfo != null);
        clearOtherList(vmList);
        haltAgent();
        if (selectedVmInfo != null) {
            new Thread(() -> {
                this.vmInfo = selectedVmInfo;
                loadClassNames();
                loadClassInfos();
            }).start();
        }
    }

    /**
     * If selected list is remoteVmList clears localVmList and vice versa.<br>
     * Effectively merging them into one.
     *
     * @param vmList list that doesn't get cleared containing the VM that user
     *               wants to attach.
     */
    private void clearOtherList(JList<VmInfo> vmList) {
        switch (vmList.getName()) {
            case "remoteVmList":
                mainFrameView.clearLocalListSelection();
                break;
            case "localVmList":
                mainFrameView.clearRemoteListSelection();
                break;
            case "localFsVmList":
                mainFrameView.clearLocalListSelection();
                mainFrameView.clearRemoteListSelection();
                break;
            default:
                throw new RuntimeException("Unknown list requested cleaning.");
        }
    }

    private void showLoadingDialog() {
        SwingUtilities.invokeLater(() -> {
            loadingDialog = new LoadingDialog();
            loadingDialog.setAbortActionListener(e
                    -> abortClassLoading());
            loadingDialog.setVisible(true);
        });
    }

    private void hideLoadingDialog() {
        // Avoid race-conditions by queueing closing after opening
        SwingUtilities.invokeLater(() -> loadingDialog.dispose());
    }

    private void cleanup() {
        mainFrameView.switchPanel(false);
        mainFrameView.getBytecodeDecompilerView().reloadClassList(new String[0]);
        mainFrameView.getBytecodeDecompilerView().reloadTextField("", "", new byte[16]);
        haltAgent();
    }

    private void abortClassLoading() {
        cleanup();

        updateVmLists();
        mainFrameView.clearLocalListSelection();
        mainFrameView.clearRemoteListSelection();
        hideLoadingDialog();
    }

    public static final String CLASSES_NOPE = "Classes couldn't be loaded. Do you have agent configured? On JDK 9 and higher, did you run the target process with '-Djdk.attach.allowAttachSelf=true'?";

    /**
     * Sends request for classes. If "ok" response is received updates classes list.
     * If "error" response is received shows an error dialog.
     */
    private void loadClassNames() {
        OutputController.getLogger().log("Loading Class Names");
        showLoadingDialog();
        AgentRequestAction request = createRequest(RequestAction.CLASSES, "");
        String response = submitRequest(request);
        if (response.equals("ok")) {
            VmDecompilerStatus vmStatus = vmInfo.getVmDecompilerStatus();
            String[] classes = vmStatus.getLoadedClassNames();
            bytecodeDecompilerView.reloadClassList(classes);
        }
        hideLoadingDialog();
        if (response.equals(DecompilerRequestReceiver.ERROR_RESPONSE)) {
            JOptionPane.showMessageDialog(mainFrameView.getMainFrame(),
                    CLASSES_NOPE,
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }


	private void loadClassInfos() {
        OutputController.getLogger().log("Loading Class Infos");
        showLoadingDialog();
        AgentRequestAction request = createRequest(RequestAction.CLASSINFOS, "");
        String response = submitRequest(request);
        if (response.equals("ok")) {
            VmDecompilerStatus vmStatus = vmInfo.getVmDecompilerStatus();
            List<String[]> classInfos = vmStatus.getLoadedClassInfos();
            bytecodeDecompilerView.writeClassInfos(classInfos);
        }
        hideLoadingDialog();
        if (response.equals("error")) {
            JOptionPane.showMessageDialog(mainFrameView.getMainFrame(), CLASSES_NOPE, "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadClassBytecode(String name) {
        AgentRequestAction request = createRequest(RequestAction.BYTES, name);
        String response = submitRequest(request);
        String decompiledClass = "";
        if (response.equals(DecompilerRequestReceiver.ERROR_RESPONSE)) {
            JOptionPane.showMessageDialog(mainFrameView.getMainFrame(),
                    "Bytecode couldn't be loaded.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        VmDecompilerStatus vmStatus = vmInfo.getVmDecompilerStatus();
        String bytesInString = vmStatus.getLoadedClassBytes();
        byte[] bytes = Base64.getDecoder().decode(bytesInString);
        try {
            decompiledClass = pluginManager.decompile(bytecodeDecompilerView.getSelectedDecompilerWrapperInformation(),name,  bytes, null, vmInfo, vmManager);
        } catch (Exception e) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, e);
        }
        bytecodeDecompilerView.reloadTextField(name, decompiledClass,  bytes);
    }

    private static LatestPaths lastLoaded = new LatestPaths();

    class ClassRewriter {

        void rewriteClass(DecompilerWrapperInformation selectedDecompiler, String name, String buffer, byte[] binBuffer, int supperSelect) {
            if (name == null || name.trim().isEmpty())
                name = "???";

            final RewriteClassDialog rewriteClassDialog = new RewriteClassDialog(name, lastLoaded, buffer, binBuffer, vmInfo, vmManager, pluginManager, selectedDecompiler, supperSelect);
            ScreenFinder.centerWindowsToCurrentScreen(rewriteClassDialog);
            rewriteClassDialog.setVisible(true);

            lastLoaded.lastManualUpload = rewriteClassDialog.getManualUploadPath();
            lastLoaded.lastSaveSrc = rewriteClassDialog.getSaveSrcPath();
            lastLoaded.lastSaveBin = rewriteClassDialog.getSaveBinPath();
            lastLoaded.filesToCompile = rewriteClassDialog.getFilesToCompile();
            lastLoaded.outputExternalFilesDir = rewriteClassDialog.getOutputExternalFilesDir();
            lastLoaded.outputBinaries  = rewriteClassDialog.getOutputBinaries();
        }
    }

    public static String fileToBase64(String path) {
        try {
            return bytesToBase64(fileToBytes(path));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String stdinToBase64() {
        try {
            return bytesToBase64(stdinToBytes());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static byte[] fileToBytes(String path) throws IOException {
        return Files.readAllBytes(new File(path).toPath());
    }

    public static byte[] stdinToBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[32 * 1024];
        int bytesRead;
        while ((bytesRead = System.in.read(buffer)) > 0) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }

    public static String bytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private void haltAgent() {
        if (vmInfo == null || vmInfo.getType() == VmInfo.Type.REMOTE || vmInfo.getType() == VmInfo.Type.FS) {
            return;
        }
        try {
            AgentRequestAction request = createRequest(RequestAction.HALT, "");
            String response = submitRequest(request);
            if (response.equals("ok")) {
                OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG,
                        "Agent closing socket and exiting");
            }
        } catch (Exception e) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL,
                    new RuntimeException("Error when sending request to halt agent", e));
        }
    }

    private AgentRequestAction createRequest(RequestAction action, String... commands) {
        return createRequest(vmInfo, action, commands);
    }

    public static AgentRequestAction createRequest(VmInfo vmInfo, RequestAction action, String... commands) {
        VmDecompilerStatus status = vmInfo.getVmDecompilerStatus();
        int listenPort = AgentRequestAction.NOT_ATTACHED_PORT;
        String hostname = "localhost";
        if (status != null) {
            listenPort = status.getListenPort();
            hostname = status.getHostname();
        }

        AgentRequestAction request;
        if (null == action) {
            throw new AssertionError("Unknown null action");
        }

        switch (action) {
            case CLASSINFOS:
            case CLASSES:
            case HALT:
                request = AgentRequestAction.create(vmInfo, hostname, listenPort, action);
                break;
            case BYTES:
                request = AgentRequestAction.create(vmInfo, hostname, listenPort, action, commands[CLASS_NAME]);
                break;
            case OVERWRITE:
                try {
                    request = AgentRequestAction.create(vmInfo, hostname, listenPort, action,
                            commands[CLASS_NAME], commands[CLASS_BODY]);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                break;
            default:
                throw new AssertionError("Unknown action: " + action);
        }

        return request;
    }

    private String submitRequest(AgentRequestAction request) {
        return submitRequest(vmManager, request);
    }

    public static String submitRequest(VmManager vmManager, AgentRequestAction request) {
        // DecompilerAgentRequestResponseListener listener = new
        // DecompilerAgentRequestResponseListener(latch);
        DecompilerRequestReceiver receiver = new DecompilerRequestReceiver(vmManager);
        // wait for the request processing

        return receiver.processRequest(request); // listener
    }

}
