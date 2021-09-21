package org.jrd.frontend.frame.settings;

import org.jrd.backend.core.OutputController;
import org.jrd.backend.data.ArchiveManagerOptions;
import org.jrd.backend.data.Config;
import org.jrd.frontend.frame.main.BytecodeDecompilerView;
import org.jrd.frontend.frame.main.MainFrameView;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jrd.backend.data.Directories.getJrdLocation;
import static org.jrd.backend.data.Directories.isPortable;
import static org.jrd.frontend.frame.plugins.FileSelectorArrayRow.fallback;
import static org.jrd.frontend.frame.plugins.FileSelectorArrayRow.getTextFieldToolTip;

public class SettingsView extends JDialog {

    private JPanel mainPanel;
        private SettingsPanel settingsPanel;
        private JPanel okCancelPanel;
            private JButton okButton;
            private JButton cancelButton;

    private final Config config = Config.getConfig();

    public static class SettingsPanel extends JPanel {

        public JTextField agentPathTextField;
        public JLabel agentPathLabel;
        public JButton browseButton;
        public JLabel checkBoxSettings;
        public JCheckBox useHostSystemClassesCheckBox;

        public JFileChooser chooser;

        public JCheckBox useDefaults;
        public JTextField newExtensionsTextField;
        public JLabel nestedJars;
        public JButton addButton;
        public JButton removeButton;
        public DefaultListModel<String> defaultListModel;
        public JList<DefaultListModel<String>> currentExtensionsList;
        public JScrollPane scrollPane;


        SettingsPanel(String initialAgentPath, boolean initialUseHostSystemClasses) {

            this.agentPathTextField = new JTextField();
            this.agentPathTextField.setToolTipText(BytecodeDecompilerView.styleTooltip() + "Select a path to the Decompiler Agent.<br />" +
                    getTextFieldToolTip()
            );
            this.agentPathTextField.setText(initialAgentPath);

            this.agentPathLabel = new JLabel("Decompiler Agent path");
            this.browseButton = new JButton("Browse");

            this.checkBoxSettings = new JLabel("Settings");
            this.useHostSystemClassesCheckBox = new JCheckBox(
                    "Use host system classes during compilation phase of class overwrite",
                    initialUseHostSystemClasses
            );

            chooser = new JFileChooser();
            File dir;
            if (isPortable()) {
                dir = new File(getJrdLocation() + File.separator + "libs");
            } else {
                dir = new File(getJrdLocation() + File.separator + "decompiler_agent" + File.separator + "target");
            }
            chooser.setCurrentDirectory(fallback(dir));

            this.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.BOTH;

            gbc.insets = new Insets(20, 20, 0, 0);
            gbc.gridx = 1;
            this.add(this.agentPathLabel, gbc);

            gbc.insets = new Insets(5,20,0,0);
            gbc.weightx = 1;
            gbc.gridx = 1;
            this.add(agentPathTextField, gbc);

            gbc.insets = new Insets(0, 20, 0, 20);
            gbc.weightx = 0;
            gbc.gridx = 2;
            gbc.gridy = 1;
            this.add(browseButton, gbc);

            gbc.insets = new Insets(20, 20, 0, 0);
            gbc.gridx = 1;
            gbc.gridy = 3;
            this.add(checkBoxSettings, gbc);

            gbc.insets = new Insets(5, 20, 0, 0);
            gbc.gridx = 1;
            gbc.gridy = 4;
            this.add(useHostSystemClassesCheckBox, gbc);

            // Nested Jars
            nestedJars = new JLabel("Nested Jars Settings:");
            newExtensionsTextField = new JTextField();

            defaultListModel = new DefaultListModel<>();
            currentExtensionsList = new JList(defaultListModel);
            currentExtensionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            currentExtensionsList.setLayoutOrientation(JList.VERTICAL);
            currentExtensionsList.setVisibleRowCount(-1);

            scrollPane = new JScrollPane(currentExtensionsList);
            scrollPane.setPreferredSize(new Dimension(0, 200));

            addButton = new JButton("Add");
            addButton.addActionListener(actionEvent -> {
                for (String s : newExtensionsTextField.getText().split( "\\s")) {
                    if (s.equals("") || s.equals("\\s")) {
                        // Do nothing
                    } else {
                        defaultListModel.addElement(s);
                    }
                }
                newExtensionsTextField.setText("");
            });

            removeButton = new JButton("Remove");
            removeButton.addActionListener(actionEvent -> {
                defaultListModel.removeElementAt(currentExtensionsList.getSelectedIndex());
            });

            useDefaults = new JCheckBox("Use default extensions");
            useDefaults.addItemListener(itemEvent -> {
                newExtensionsTextField.setEnabled(!useDefaults.isSelected());
                addButton.setEnabled(!useDefaults.isSelected());
                removeButton.setEnabled(!useDefaults.isSelected());
                currentExtensionsList.setEnabled(!useDefaults.isSelected());
            });
            useDefaults.setToolTipText(BytecodeDecompilerView.styleTooltip() + "Default extensions that are searched are: .zip, .jar, .war, .ear");

            // Setup
            List<String> l = ArchiveManagerOptions.getInstance().getExtensions();
            if (l == null || l.isEmpty()) {
                useDefaults.setSelected(true);
            } else {
                defaultListModel.addAll(l);
            }

            gbc.insets = new Insets(30, 20, 0, 0);
            gbc.gridy = 5;
            this.add(nestedJars, gbc);

            gbc.insets = new Insets(10, 20, 0, 0);
            gbc.gridy = 6;
            this.add(useDefaults, gbc);

            gbc.insets = new Insets(5, 20, 0, 20);
            gbc.gridy = 7;
            gbc.weighty = 1.0;
            gbc.gridwidth = 3;
            this.add(scrollPane, gbc);

            gbc.insets = new Insets(5, 20, 0, 0);
            gbc.weighty = 0;
            gbc.gridy = 8;
            gbc.gridwidth = 1;
            this.add(newExtensionsTextField, gbc);

            gbc.insets = new Insets(5, 5, 0, 0);
            gbc.gridx = 2;
            this.add(addButton, gbc);

            gbc.insets = new Insets(5, 5, 0, 20);  //top padding
            gbc.gridx = 3;
            this.add(removeButton, gbc);

            this.setPreferredSize(new Dimension(0, 400));
        }
    }

    public SettingsView(MainFrameView mainFrameView) {
        settingsPanel = new SettingsPanel(config.getAgentRawPath(), config.doUseHostSystemClasses());
        settingsPanel.browseButton.addActionListener(actionEvent -> {
            int dialogResult = settingsPanel.chooser.showOpenDialog(settingsPanel);
            if (dialogResult == JFileChooser.APPROVE_OPTION) {
                settingsPanel.agentPathTextField.setText(settingsPanel.chooser.getSelectedFile().getPath());
            }
        });

        okButton = new JButton("OK");
        okButton.addActionListener(actionEvent -> {
            config.setAgentPath(settingsPanel.agentPathTextField.getText());
            config.setUseHostSystemClasses(settingsPanel.useHostSystemClassesCheckBox.isSelected());

            try {
                config.saveConfigFile();
            } catch (IOException e) {
                OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, e);
            }

            if (settingsPanel.useDefaults.isSelected()) {
                ArchiveManagerOptions.getInstance().setExtension(new ArrayList<String>());
            } else {
                List<String> ext = Collections.list(settingsPanel.defaultListModel.elements());
                ArchiveManagerOptions.getInstance().setExtension(ext);
            }
            dispose();
        });
        okButton.setPreferredSize(new Dimension(90, 30));

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(actionEvent -> {
            dispose();
        });
        cancelButton.setPreferredSize(new Dimension(90, 30));

        okCancelPanel = new JPanel(new GridBagLayout());
        okCancelPanel.setBorder(new EtchedBorder());
        okCancelPanel.setPreferredSize(new Dimension(0, 60));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridy = 0;
        gbc.weightx = 1;
        okCancelPanel.add(Box.createHorizontalGlue(), gbc);

        gbc.weightx = 0;
        gbc.gridx = 1;
        okCancelPanel.add(okButton, gbc);

        gbc.gridx = 2;
        okCancelPanel.add(Box.createHorizontalStrut(15), gbc);

        gbc.gridx = 3;
        okCancelPanel.add(cancelButton, gbc);

        gbc.gridx = 4;
        okCancelPanel.add(Box.createHorizontalStrut(20), gbc);

        mainPanel = new JPanel(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;

        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        mainPanel.add(settingsPanel, gbc);

        gbc.gridy = 1;
        gbc.weighty = 1;
        mainPanel.add(Box.createVerticalGlue(), gbc);

        gbc.gridy = 2;
        gbc.weighty = 0;
        mainPanel.add(okCancelPanel, gbc);

        this.setTitle("Settings");
        this.setSize(new Dimension(800, 500));
        this.setMinimumSize(new Dimension(250, 500));
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.setLocationRelativeTo(mainFrameView.getMainFrame());
        this.setModalityType(ModalityType.APPLICATION_MODAL);
        this.add(mainPanel);
        this.setVisible(true);
    }
}
