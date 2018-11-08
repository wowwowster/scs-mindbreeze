package com.sword.scs.installer;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import sword.common.utils.files.FileUtils;
import sword.common.utils.files.visitors.FileTreeDeleter;
import sword.common.utils.ui.Constant;
import sword.common.utils.ui.UIUtils;
import sword.common.utils.ui.components.FilePathTextField;
import sword.common.utils.ui.frames.SwpFrame;

public final class InstallUI extends Installer implements ActionListener {

	private static final Dimension WIN_MIN_SIZE = new Dimension(480, 230);
	private static final String AC_PREV_PAGE = "pp";
	private static final String AC_NEXT_PAGE = "np";
	private static final String AC_INSTALL_PAGE = "doInstall";
	private static final String AC_BROWSE = "bd";

	private final SwpFrame win;
	private final JPanel mainPane = new JPanel(new CardLayout());
	private final FilePathTextField destDir = UIUtils.toSwpStyle(new FilePathTextField(), false, true);
	private final JLabel destLbl = UIUtils.toSwpStyle(new JLabel(), false, true);
	private final JButton installBtn = UIUtils.toSwpStyle(new JButton("Install"), false, true);
	
	public InstallUI(String installDest, String envPath) {
		super(installDest, envPath);
		win = new SwpFrame("SCS Installer", Color.WHITE) {};
		win.getFrame().setMinimumSize(WIN_MIN_SIZE);
		win.getContentPane().setLayout(new BorderLayout());
	}

	@Override
	public void start(boolean requestInstallDest) throws BadLocationException {
		addCardsToMainPane(requestInstallDest);
		win.getContentPane().add(mainPane, BorderLayout.CENTER);
		win.show(false);
	}

	private void addCardsToMainPane(boolean requestInstallDest) throws BadLocationException {
		mainPane.add(buildWelcomeScreen());
		if (requestInstallDest) mainPane.add(buildBrowseDestScreen());
		mainPane.add(buildConfirmInstallScreen());
	}
	private JPanel buildWelcomeScreen() throws BadLocationException {
		final JPanel welcomeScreen = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);

		final JTextPane welcomeMessage = new JTextPane();
		welcomeMessage.setEditable(false);

		final StyledDocument doc = welcomeMessage.getStyledDocument();

		final Style baseStyle = welcomeMessage.addStyle("base", null);
		StyleConstants.setForeground(baseStyle, Constant.SWORD_BLUE);
		StyleConstants.setFontSize(baseStyle, 12);

		final Style titleStyle = welcomeMessage.addStyle("title", baseStyle);
		StyleConstants.setBold(titleStyle, true);
		StyleConstants.setFontSize(titleStyle, 18);

		final Style iStyle = welcomeMessage.addStyle("italic", baseStyle);
		StyleConstants.setItalic(iStyle, true);

		doc.insertString(doc.getLength(), "\tWelcome to the SCS setup\n\n", titleStyle);
		doc.insertString(doc.getLength(), "Setup will guide you through the installation of the Sword Connector Server (SCS).\n\n", baseStyle);
		doc.insertString(doc.getLength(), "Click ", baseStyle);
		doc.insertString(doc.getLength(), "Next", iStyle);
		doc.insertString(doc.getLength(), " to continue.", baseStyle);

		welcomeScreen.add(welcomeMessage, BorderLayout.CENTER);

		final JPanel nextBtnPane = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);
		nextBtnPane.add(getNextPageBtn(this), BorderLayout.EAST);
		welcomeScreen.add(nextBtnPane, BorderLayout.SOUTH);

		return welcomeScreen;
	}

	private JPanel buildBrowseDestScreen() throws BadLocationException {
		final JPanel browseScreen = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);

		final JPanel browsePane = UIUtils.toSwpStyle(new JPanel(), true, false);
		browsePane.setBorder(new EmptyBorder(14, 4, 4, 4));
		browsePane.setLayout(new BoxLayout(browsePane, BoxLayout.Y_AXIS));

		final JTextPane browseMessage = new JTextPane();
		browseMessage.setPreferredSize(new Dimension(360, 60));
		browseMessage.setEditable(false);
		final StyledDocument doc = browseMessage.getStyledDocument();
		final Style baseStyle = browseMessage.addStyle("base", null);
		StyleConstants.setForeground(baseStyle, Constant.SWORD_BLUE);
		StyleConstants.setFontSize(baseStyle, 12);
		final Style iStyle = browseMessage.addStyle("italic", baseStyle);
		StyleConstants.setItalic(iStyle, true);
		doc.insertString(doc.getLength(), "Choose the directory where the SCS will be installed.\nDrag and drop the destination folder into the textbox or click on the ", baseStyle);
		doc.insertString(doc.getLength(), "Browse", iStyle);
		doc.insertString(doc.getLength(), " button to navigate through your file system and locate the destination folder.", baseStyle);
		browsePane.add(browseMessage);

		final TitledBorder tb = new TitledBorder("Destination folder");
		tb.setTitleColor(Constant.SWORD_BLUE);
		final GridBagLayout gbl = new GridBagLayout();
		final GridBagConstraints gbc = new GridBagConstraints();
		final JPanel browseArea = UIUtils.toSwpStyle(new JPanel(gbl), true, false);
		gbc.gridwidth = 5;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbl.setConstraints(destDir, gbc);
		browseArea.add(destDir);
		final JButton browseBtn = UIUtils.toSwpStyle(new JButton("Browse"), false, true);
		browseBtn.setMnemonic(KeyEvent.VK_B);
		browseBtn.setActionCommand(AC_BROWSE);
		browseBtn.addActionListener(this);
		gbc.gridwidth = 1;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		gbl.setConstraints(browseBtn, gbc);
		browseArea.add(browseBtn);
		browsePane.add(browseArea);

		browseScreen.add(browsePane, BorderLayout.CENTER);

		final JPanel prevNext = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);
		prevNext.add(getPrevPageBtn(this), BorderLayout.WEST);
		prevNext.add(getNextPageBtn(this), BorderLayout.EAST);
		browseScreen.add(prevNext, BorderLayout.SOUTH);

		return browseScreen;
	}

	private JPanel buildConfirmInstallScreen() {
		final JPanel ciScreen = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);

		final JPanel confirmPane = UIUtils.toSwpStyle(new JPanel(), true, false);
		confirmPane.setLayout(new BoxLayout(confirmPane, BoxLayout.Y_AXIS));

		final String fontElem = "<html><span style=\"font:10px arial,sans-serif\" >%s</span></html>";
		confirmPane.add(UIUtils.toSwpStyle(new JLabel(String.format(fontElem, "Installation process is about to begin.")), false, true));
		confirmPane.add(UIUtils.toSwpStyle(new JLabel(String.format(fontElem, "SCS will be installed in")), false, true));
		destLbl.setText("");
		confirmPane.add(destLbl);
		confirmPane.add(UIUtils.toSwpStyle(new JLabel(String.format(fontElem, "Click on the <i>Install</i> button to proceed with the installation.")), false, true));

		ciScreen.add(confirmPane, BorderLayout.CENTER);

		final JPanel prevNext = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);
		prevNext.add(getPrevPageBtn(this), BorderLayout.WEST);
		prevNext.add(getNextPageBtn(this), BorderLayout.EAST);
		installBtn.setMnemonic(KeyEvent.VK_I);
		installBtn.setActionCommand(AC_INSTALL_PAGE);
		installBtn.addActionListener(this);
		prevNext.add(installBtn, BorderLayout.EAST);
		ciScreen.add(prevNext, BorderLayout.SOUTH);

		return ciScreen;
	}

	private static JButton getPrevPageBtn(ActionListener al) {
		final JButton prev = UIUtils.toSwpStyle(new JButton("Previous"), false, true);
		prev.setMnemonic(KeyEvent.VK_P);
		prev.setActionCommand(AC_PREV_PAGE);
		prev.addActionListener(al);
		return prev;
	}

	private static JButton getNextPageBtn(ActionListener al) {
		final JButton prev = UIUtils.toSwpStyle(new JButton("Next"), false, true);
		prev.setMnemonic(KeyEvent.VK_N);
		prev.setActionCommand(AC_NEXT_PAGE);
		prev.addActionListener(al);
		return prev;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (AC_NEXT_PAGE.equals(e.getActionCommand())) {
			if (destDir.isShowing()) {
				if (Installer.isValidInstallDir(destDir.getText())) destLbl.setText("<html>&nbsp;&nbsp;&nbsp;<I>" + destDir.getText() + "</I></html>");
				else {
					JOptionPane.showMessageDialog(mainPane, "Invalid installation path: " + destDir.getText(), "Invalid path", JOptionPane.WARNING_MESSAGE);
					return;
				}
			}
			((CardLayout) mainPane.getLayout()).next(mainPane);
		} else if (AC_PREV_PAGE.equals(e.getActionCommand())) ((CardLayout) mainPane.getLayout()).previous(mainPane);
		else if (AC_BROWSE.equals(e.getActionCommand())) {
			final JFileChooser jfc = UIUtils.toSwpStyle(new JFileChooser(FileUtils.getJarFile(this.getClass()).getParentFile()), false, true);
			jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			final int ret = jfc.showDialog(mainPane, "Select");
			if (JFileChooser.APPROVE_OPTION == ret) destDir.setText(jfc.getSelectedFile().getAbsolutePath());
		} else if (AC_INSTALL_PAGE.equals(e.getActionCommand())) {
			installBtn.setEnabled(false);
			win.getFrame().setCursor(new Cursor(Cursor.WAIT_CURSOR));
			try {
				installDest = new File(destDir.getText()).toPath();
				doInstall();
				JOptionPane.showMessageDialog(mainPane, "Installation completed successfully - SCS Setup application with start now.", "Installation complete", JOptionPane.INFORMATION_MESSAGE);
				win.dispose();
				startManager();
			} catch (final Throwable ex) {
				win.getFrame().setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				UIUtils.displayError("Installation process aborted due to an unexpected error: ", ex);
				try {
					Files.walkFileTree(installDest, new FileTreeDeleter());
				} catch (final IOException ignore) {}
			}
		}
	}

}
