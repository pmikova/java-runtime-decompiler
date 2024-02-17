package org.jrd.frontend.frame.settings;

import org.jrd.frontend.frame.main.decompilerview.BytecodeDecompilerView;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;

public class CompilationSettingsPanel extends JPanel implements ChangeReporter {

    private JLabel compilationSettingsLabel;
    private JCheckBox useHostSystemClassesCheckBox;
    private JCheckBox useHostJavaLangObjectCheckBox;
    private JLabel compilerArgsLabel;
    private JLabel compilerArgsLabelNit;
    private JTextField compilerArgsTextField;
    private JCheckBox overwriteSourceTargetOnRuntime;

    public CompilationSettingsPanel(
            boolean initialUseHostSystemClasses, String initialCompilerArgs, boolean initialUseHostJavaObject,
            boolean initialOverwriteStValue
    ) {
        compilationSettingsLabel = new JLabel("Compilation settings");
        this.setName(compilationSettingsLabel.getText());
        useHostSystemClassesCheckBox =
                new JCheckBox("Use host system classes during compilation phase of class overwrite", initialUseHostSystemClasses);
        useHostJavaLangObjectCheckBox =
                new JCheckBox("Always use host class java.lang.Object (e.g. DCEVM requires this to work)", initialUseHostJavaObject);
        useHostSystemClassesCheckBox.setToolTipText(
                BytecodeDecompilerView.styleTooltip() + "<b>very tricky switch</b><br>" +
                        "If true, then (should be default) then system classes (like java.lang) are loaded from THIS jvm<br>" +
                        "If false, then all classes are onl from remote vm. Where this is more correct, it slower and may have issues<br>" +
                        "Note, that even true, may bring some unexpected behavior, and is hard to determine what is better." +
                        " With false on FS, you have to provide also system classes to cp!"
        );
        compilerArgsLabel = new JLabel("Compiler arguments");
        compilerArgsLabelNit = new JLabel(
                "<html>Note, that --patch-module as is, have no sense in filesystem less environment" +
                        " ` --patch-module module=file(:file)*\n` .<br>" +
                        "So we are parsing it and reusing in a bit 'our' way. You can use:<br>" +
                        " <b>--patch-module <module>=pkgOrClassFqn(:pkgOrClassFqn)* </b>instead."
        );
        compilerArgsTextField = new JTextField(initialCompilerArgs);
        compilerArgsTextField.setToolTipText(
                BytecodeDecompilerView.styleTooltip() +
                        "Arguments that get passed to the compiler, eg. '-source 5 -target 8 -release 9 -Xlint'."
        );
        compilerArgsLabel.setToolTipText(compilerArgsTextField.getToolTipText());

        this.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridwidth = 2;
        gbc.weightx = 1; // required or else contents are centered

        this.add(compilationSettingsLabel, gbc);

        gbc.gridy = 1;
        this.add(useHostSystemClassesCheckBox, gbc);
        gbc.gridy = 2;
        this.add(useHostJavaLangObjectCheckBox, gbc);

        gbc.insets = new Insets(5, 5 + useHostSystemClassesCheckBox.getInsets().left, 5, 5);
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.gridy = 3;
        this.add(compilerArgsLabel, gbc);

        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.gridx = 0;
        gbc.gridy = 4;
        this.add(compilerArgsTextField, gbc);
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.gridx = 0;
        gbc.gridy = 5;
        this.add(compilerArgsLabelNit, gbc);
        gbc.gridx = 0;
        gbc.gridy = 6;
        overwriteSourceTargetOnRuntime = new JCheckBox("Overwrite source/target per class");
        overwriteSourceTargetOnRuntime.setToolTipText(
                BytecodeDecompilerView.styleTooltip() + "If selected, source target will be always set<br>" +
                        "even if you set it manually, it will be overwritten<br>" + "to the value obtained from original bytecode"
        );
        overwriteSourceTargetOnRuntime.setSelected(initialOverwriteStValue);
        this.add(overwriteSourceTargetOnRuntime, gbc);
    }

    public boolean shouldUseHostSystemClassesCheckBox() {
        return useHostSystemClassesCheckBox.isSelected();
    }

    public boolean shouldUseHostJavaLangObjectCheckBox() {
        return useHostJavaLangObjectCheckBox.isSelected();
    }

    public boolean shouldOverwriteStCheckBox() {
        return overwriteSourceTargetOnRuntime.isSelected();
    }

    public String getCompilerArgs() {
        return compilerArgsTextField.getText();
    }

    @Override
    public void setChangeReporter(ActionListener listener) {
        ChangeReporter.addCheckboxListener(listener, useHostSystemClassesCheckBox);
        ChangeReporter.addCheckboxListener(listener, useHostJavaLangObjectCheckBox);
        ChangeReporter.addTextChangeListener(listener, compilerArgsTextField);
    }
}
