package org.jrd.frontend.frame.main.renderer;

import org.jrd.backend.data.VmInfo;
import org.jrd.frontend.frame.main.BytecodeDecompilerView;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import java.awt.*;

public class VmListRenderer extends JPanel implements ListCellRenderer<VmInfo> {

    JLabel pid;
    JLabel name;
    JLabel cp;

    public VmListRenderer() {
        pid = new JLabel();
        name = new JLabel();
        cp = new JLabel();
        cp.setForeground(Color.GRAY);
        cp.setFont(cp.getFont().deriveFont(Font.ITALIC));

        this.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        this.add(name, gbc);

        gbc.weighty = 0.5;
        gbc.gridy = 1;
        this.add(cp, gbc);

        gbc.weighty = 0.75;
        gbc.gridy = 2;
        this.add(pid, gbc);

        this.setBorder(BorderFactory.createCompoundBorder(
                new EtchedBorder(EtchedBorder.LOWERED),
                new EmptyBorder(0, 10, 0, 0)
        ));
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends VmInfo> list, VmInfo vmInfo, int index, boolean isSelected, boolean cellHasFocus
    ) {
        switch (vmInfo.getType()) {
            case LOCAL:
                name.setText(vmInfo.getVmName().split(" ")[0]);
                pid.setText("PID: " + vmInfo.getVmPid());
                cp.setVisible(false);

                this.setToolTipText(BytecodeDecompilerView.styleTooltip() +
                        "NAME: " + vmInfo.getVmName().split(" ")[0] + "<br />" +
                        "PID: " + vmInfo.getVmPid() +
                        "</html>");
                break;
            case REMOTE:
                name.setText("Hostname: " + vmInfo.getVmName());
                pid.setText("Port: " + vmInfo.getVmDecompilerStatus().getListenPort());
                cp.setVisible(false);

                this.setToolTipText(BytecodeDecompilerView.styleTooltip() +
                        "Hostname: " + vmInfo.getVmName() + "<br />" +
                        "Port: " + vmInfo.getVmDecompilerStatus().getListenPort() +
                        "</html>");
                break;
            case FS:
                if (vmInfo.hasName()) {
                    name.setText(vmInfo.getVmName());
                    cp.setText(vmInfo.getCpString() + " "); // trailing space to prevent italics font cutoff
                    cp.setVisible(true);
                } else {
                    name.setText(vmInfo.getCpString());
                    cp.setVisible(false);
                }
                pid.setText("ID: " + vmInfo.getVmPid());

                this.setToolTipText(
                        BytecodeDecompilerView.styleTooltip() +
                        (vmInfo.hasName() ? "Name: " + vmInfo.getVmName() + "<br>" : "") +
                        "Classpath: " + vmInfo.getCpString() + "<br>" +
                        "ID: " + vmInfo.getVmPid() + "</html>"
                );
                break;
            default:
                break;
        }

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }
        return this;
    }
}
