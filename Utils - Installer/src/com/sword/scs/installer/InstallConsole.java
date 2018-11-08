package com.sword.scs.installer;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.File;
import java.io.PrintStream;

import sword.common.utils.EnvUtils;
import sword.common.utils.StringUtils;

public final class InstallConsole extends Installer {

	private final Console console;

	public InstallConsole(String installDest, String envPath) {
		super(installDest, envPath);
		console = System.console();
	}

	@Override
	public void start(boolean requestInstallDest) {
		if (requestInstallDest) {
			String ip = null;
			while (StringUtils.isNullOrEmpty(ip) || !Installer.isValidInstallDir(ip)) {
				if (!StringUtils.isNullOrEmpty(ip)) console.format("Invalid path%s", EnvUtils.CR);
				ip = console.readLine("Enter the path of the installation folder: ");
			}
			installDest = new File(ip).toPath();
		}
		
		try {
			doInstall();
		} catch (final Throwable e) {
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			try (PrintStream ps = new PrintStream(os)) { e.printStackTrace(ps); }
			console.format("Installation process aborted due to an unexpected error: %s", new String(os.toByteArray()));
		}
	}

}
