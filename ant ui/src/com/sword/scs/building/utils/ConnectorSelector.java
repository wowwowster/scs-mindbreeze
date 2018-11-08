package com.sword.scs.building.utils;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public abstract class ConnectorSelector extends JFrame implements Runnable, ActionListener {

	static final String OK = "OK";
	static final String NONE = "None";
	static final String ALL = "All";
	private static final long serialVersionUID = 1L;
	
	final File rootDir;
	final List<JCheckBox> cbs = new ArrayList<>();
	final JPanel mainPan = new JPanel();

	public ConnectorSelector(String root) throws HeadlessException {
		super("Connector Selector");
		System.out.println("root: " + root);
		rootDir = new File(root);
		File[] fs = rootDir.listFiles();
		for (File f: fs) 
			if (f.isDirectory() && f.getName().startsWith("Connector - ") && new File(f, "dist").exists()) 
				cbs.add(new JCheckBox(f.getName().substring("Connector - ".length())));
		Collections.sort(cbs, new Comparator<JCheckBox>() {
			@Override
			public int compare(JCheckBox o1, JCheckBox o2) {
				return o1.getText().compareTo(o2.getText());
			}
		});
		
		mainPan.setBorder(new EmptyBorder(8, 8, 8, 8));
		mainPan.setLayout(new BoxLayout(mainPan, BoxLayout.Y_AXIS));

		JPanel lblPan = new JPanel(new BorderLayout());
		lblPan.add(new JLabel("Select connectors: "), BorderLayout.CENTER);
		mainPan.add(lblPan);
		
		JPanel cbPan = new JPanel(new GridLayout(0, 3));
		for (JCheckBox cb : cbs) cbPan.add(cb);
		mainPan.add(cbPan);

		JPanel selBtnPan = new JPanel();
		final JButton allBtn = new JButton(ALL);
		allBtn.setActionCommand(ALL);
		allBtn.addActionListener(this);
		selBtnPan.add(allBtn);
		final JButton noneBtn = new JButton(NONE);
		noneBtn.setActionCommand(NONE);
		noneBtn.addActionListener(this);
		selBtnPan.add(noneBtn);
		mainPan.add(selBtnPan);
		
		addSwingComponents();

		JPanel okBtnPan = new JPanel();
		final JButton okBtn = new JButton(OK);
		okBtn.setActionCommand(OK);
		okBtn.addActionListener(this);
		okBtnPan.add(okBtn);
		mainPan.add(okBtnPan);
		
		getContentPane().add(mainPan, BorderLayout.CENTER);
	}
	
	
	public void addSwingComponents() {
		//No additional component needed
	}
	
	public void start() {
		SwingUtilities.invokeLater(this);
	}

	@Override
	public void run() {
		pack();
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setVisible(true);
	}

}
