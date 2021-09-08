package org.jrd.frontend.frame.nestedJars;

import org.jrd.backend.data.ArchiveManagerOptions;
import org.jrd.frontend.frame.main.BytecodeDecompilerView;
import org.jrd.frontend.frame.main.MainFrameView;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NestedJarsView extends JDialog {
	private JPanel mainPanel;
	private NestedPanel nestedPanel;
	private JPanel okCancelPanel;
	private JButton okButton;
	private JButton cancelButton;

	public static class NestedPanel extends JPanel {

		public JCheckBox useDefaults;
		public JTextField textField;
		public JButton addButton;
		public JButton removeButton;
		public JList list;
		public JScrollPane scrollPane;
		DefaultListModel dList;

		NestedPanel() {
			textField = new JTextField();

			dList = new DefaultListModel();
			list = new JList(dList);
			list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			list.setLayoutOrientation(JList.VERTICAL);
			list.setVisibleRowCount(-1);

			scrollPane = new JScrollPane(list);
			scrollPane.setPreferredSize(new Dimension(0, 200));

			addButton = new JButton("ADD");
			addButton.addActionListener(actionEvent -> {
				dList.addElement(textField.getText());
				textField.setText("");
			});

			removeButton = new JButton("REMOVE");
			removeButton.addActionListener(actionEvent -> {
				int index = list.getSelectedIndex();
				dList.removeElementAt(index);
			});

			useDefaults = new JCheckBox("Use default extensions");
			useDefaults.addItemListener(itemEvent -> {
				if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
					textField.setEnabled(false);
					addButton.setEnabled(false);
					removeButton.setEnabled(false);
					list.setEnabled(false);
				} else {
					textField.setEnabled(true);
					addButton.setEnabled(true);
					removeButton.setEnabled(true);
					list.setEnabled(true);
				}
			});
			useDefaults.setToolTipText(BytecodeDecompilerView.styleTooltip() + "Default extensions that are searched are: .zip, .jar, .war, .ear");

			// Setup
			List<String> l = ArchiveManagerOptions.getInstance().getExtensions();
			if (l == null || l.isEmpty()) {
				useDefaults.setSelected(true);
			} else {
				dList.addAll(l);
			}

			this.setLayout(new GridBagLayout());
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.anchor = GridBagConstraints.WEST;
			gbc.fill = GridBagConstraints.BOTH;

			this.add(useDefaults, gbc);

			gbc.gridy = 1;
			gbc.weighty = 1.0;
			gbc.gridwidth = 3;
			this.add(scrollPane, gbc);

			gbc.insets = new Insets(5,0,0,0);  //top padding
			gbc.weighty = 0;
			gbc.gridy = 2;
			gbc.gridwidth = 1;
			this.add(textField, gbc);

			gbc.insets = new Insets(5,5,0,0);  //top padding
			gbc.gridx = 1;
			this.add(addButton, gbc);

			gbc.gridx = 2;
			this.add(removeButton, gbc);

			this.setPreferredSize(new Dimension(0, 150));
		}
	}

	public NestedJarsView(MainFrameView mainFrameView) {

		nestedPanel = new NestedPanel();

		okButton = new JButton("OK");
		okButton.addActionListener(actionEvent -> {
			if (nestedPanel.useDefaults.isSelected()) {
				ArchiveManagerOptions.getInstance().setExtension(new ArrayList<String>());
			} else {
				List<String> ext = Collections.list(nestedPanel.dList.elements());
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
		gbc.weighty = 1;
		gbc.gridx = 0;
		gbc.gridy = 0;
		mainPanel.add(nestedPanel, gbc);

		gbc.gridy = 1;
		gbc.weighty = 0;
		mainPanel.add(Box.createVerticalGlue(), gbc);

		gbc.gridy = 2;
		gbc.weighty = 0;
		mainPanel.add(okCancelPanel, gbc);

		this.setTitle("Nested Jars Extension Settings");
		this.setSize(new Dimension(400, 400));
		this.setMinimumSize(new Dimension(250, 300));
		this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		this.setLocationRelativeTo(mainFrameView.getMainFrame());
		this.setModalityType(ModalityType.APPLICATION_MODAL);
		this.add(mainPanel);
		this.setVisible(true);
	}
}
