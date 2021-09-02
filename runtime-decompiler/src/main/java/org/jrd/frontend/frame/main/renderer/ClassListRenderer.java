package org.jrd.frontend.frame.main.renderer;

import org.jrd.backend.core.ClassInfo;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;

public class ClassListRenderer extends JPanel implements ListCellRenderer<ClassInfo> {

    JLabel name;
    JLabel location;
    boolean doShowInfo;

    public ClassListRenderer() {
        name = new JLabel();
        location = new JLabel();
        location.setFont(location.getFont().deriveFont(10.0F));

        this.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(3, 3, 3, 3);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        this.add(name, gbc);

        gbc.weighty = 0.5;
        gbc.gridy = 1;
        this.add(location, gbc);

        this.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends ClassInfo> list, ClassInfo classInfo, int i, boolean isSelected, boolean cellHasFocus
    ) {
        name.setText(classInfo.getName());

        if (doShowInfo) {
            location.setText("Location: " + classInfo.getLocation() + " "); // trailing space to prevent font cutoff
            location.setVisible(true);

            this.setPreferredSize(null);
        } else {
            location.setVisible(false);

            this.setPreferredSize(null);
        }

        if (isSelected) {
            this.setBackground(list.getSelectionBackground());
            this.setForeground(list.getSelectionForeground());
        } else {
            this.setBackground(list.getBackground());
            this.setForeground(list.getForeground());
        }

        return this;
    }

    public void setDoShowInfo(boolean doShowInfo) {
        this.doShowInfo = doShowInfo;
    }


}
