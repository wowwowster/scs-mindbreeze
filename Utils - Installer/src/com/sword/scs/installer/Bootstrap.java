package com.sword.scs.installer;

import java.awt.GraphicsEnvironment;

import javax.swing.JFrame;

import sword.common.utils.StringUtils;
import sword.common.utils.ui.UIUtils;

public class Bootstrap {

	public static final int EC_UNEXPECTED_ERROR = 1501;
	public static final int EC_NON_ROOT = 1502;
	public static final int EC_INVALID_ARGS = 1503;

	public static void main(String[] args) {

		if (args != null && args.length > 0) {
			if ("IsHeadless".equals(args[0])) {
				try {
					JFrame f = new JFrame();
					f.setVisible(true);
					f.dispose();
					System.out.println("0");
				} catch (Throwable t) {
					System.out.println("1");
				}
			} else {// Standard Installation process
				String installDest = null;
				String envPath = "";
				boolean noGUI = false;
				for (int i = 0; i < args.length; i++)
					if ("/DestDir".equals(args[i])) installDest = args[++i];
					else if ("/EnvPath".equals(args[i])) envPath = args[++i];
					else if ("/NoGUI".equals(args[i])) noGUI = true;

				final boolean requestInstallDest = StringUtils.isNullOrEmpty(installDest);
				final boolean headless = noGUI || GraphicsEnvironment.isHeadless();
				try {
					Installer i;
					if (headless) i = new InstallConsole(installDest, envPath);
					else i = new InstallUI(installDest, envPath);
					i.start(requestInstallDest);
				} catch (final Throwable t) {
					if (headless) {
						System.err.println("Installation process aborted due to an unexpected error:");
						t.printStackTrace(System.err);
						System.exit(EC_UNEXPECTED_ERROR);
						return;
					} else UIUtils.displayError("Installation process aborted due to an unexpected error:", t);
				}
			}
		} else System.exit(EC_INVALID_ARGS);
	}
}
