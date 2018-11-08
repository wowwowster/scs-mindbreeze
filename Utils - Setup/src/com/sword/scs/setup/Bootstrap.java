package com.sword.scs.setup;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;

import sword.common.utils.files.FileUtils;
import sword.common.utils.ui.UIUtils;

public class Bootstrap {

	public static final int EC_UNEXPECTED_ERROR = 1501;
	public static final int EC_NON_ROOT = 1502;
	public static final int EC_INVALID_ARGS = 1503;

	public static void main(final String[] args) {
		if (args != null && args.length > 0) {
			String envPath = "";
			boolean noGUI = false;
			String mode = "";
			String patchPath = "";
			for (int i = 0; i < args.length; i++) {
				if ("/EnvPath".equals(args[i])) {
					envPath = args[++i];
				} else if ("/NoGUI".equals(args[i])) {
					noGUI = true;
				} else if ("/Mode".equals(args[i])) {
					mode = args[++i];
					if ("ApplyPatch".equals(mode)) {
						patchPath = args[++i];
					}
				}
			}

			final Path rootDir = FileUtils.getJarFile(Bootstrap.class).getParentFile().getParentFile().getParentFile().toPath();

			final boolean headless = noGUI || GraphicsEnvironment.isHeadless();
			try {
				if ("SvcStart".equals(mode)) {
					new SetupConsole(rootDir, envPath).doStartSvc();
				} else if ("SvcStop".equals(mode)) {
					new SetupConsole(rootDir, envPath).doStopSvc();
				} else if ("SvcRestart".equals(mode)) {
					new SetupConsole(rootDir, envPath).doRestartSvc();
				} else if ("ApplyPatch".equals(mode)) {
					new SetupConsole(rootDir, envPath).applyPatch(new File(patchPath).toPath());
				} else {
					Setup ma;
					if (headless) {
						ma = new SetupConsole(rootDir, envPath);
					} else {
						ma = new SetupGUI(rootDir, envPath);
					}
					ma.start();
				}
			} catch (final Throwable t) {
				if (headless) {
					System.err.println("Manager application aborted due to an unexpected error:");
					t.printStackTrace(System.err);
					System.exit(EC_UNEXPECTED_ERROR);
					return;
				} else {
					UIUtils.displayError("Manager application aborted due to an unexpected error:", t);
				}
			}
		} else {
			System.exit(EC_INVALID_ARGS);
		}
	}

}
