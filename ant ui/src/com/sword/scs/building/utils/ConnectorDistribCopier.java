package com.sword.scs.building.utils;

import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JButton;
import javax.swing.JCheckBox;

import com.sword.scs.Constants;

import sword.common.utils.files.visitors.FileTreeCopier;

public class ConnectorDistribCopier extends ConnectorSelector implements ActionListener {

	private static final long serialVersionUID = 1L;

	public static void main(String[] args) {
		new ConnectorDistribCopier(args[0]).start();
	}

	public ConnectorDistribCopier(String root) throws HeadlessException {
		super(root);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (OK.equals(e.getActionCommand())) {
			((JButton)e.getSource()).setEnabled(false);
			cbs.parallelStream().filter(cb -> cb.isSelected()).forEach(cb -> copyDistrib(cb));
			dispose();
		} else if (ALL.equals(e.getActionCommand())) {
			cbs.parallelStream().forEach(cb -> cb.setSelected(true));
		} else if (NONE.equals(e.getActionCommand())) {
			cbs.parallelStream().forEach(cb -> cb.setSelected(false));
		}
	}

	private void copyDistrib(JCheckBox cb) {

		final Path connectorDir = rootDir.toPath().resolve("Connector - " + cb.getText() + "/dist");
		try {
			Files.walkFileTree(connectorDir, new FileTreeCopier(connectorDir, rootDir.toPath().resolve("Utils - Installer/temp/" + Constants.SCS_WORK_DIR_NAME + "/connectors/" + cb.getText())));
		} catch (IOException e1) {
			e1.printStackTrace();
			System.exit(-1);
		}
		final Path docDir = rootDir.toPath().resolve("Connector - " + cb.getText() + "/help");
		if (docDir.toFile().exists()) {
			System.out.println("Copying " + docDir + " to " + rootDir.toPath().resolve("Utils - Installer/temp/webapps/documentation/connectors/" + cb.getText()));
			try {
				Files.walkFileTree(docDir, new FileTreeCopier(docDir, rootDir.toPath().resolve("Utils - Installer/temp/webapps/documentation/connectors/" + cb.getText())));
			} catch (IOException e1) {
				e1.printStackTrace();
				System.exit(-1);
			}
		}
		
	}

}
