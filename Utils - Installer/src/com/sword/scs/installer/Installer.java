package com.sword.scs.installer;

import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;

import sword.common.utils.EnvUtils;
import sword.common.utils.StringUtils;
import sword.common.utils.files.FileUtils;
import sword.common.utils.files.visitors.FileTreeCopier;
import sword.common.utils.files.visitors.FileUnzipper;
import sword.common.utils.runtime.RuntimeUtils;

import com.sword.scs.Constants;
import com.sword.scs.utils.ServiceManager;

public abstract class Installer {

	public final Path installerRoot;
	public final String envPath;
	public Path installDest;

	public Installer(String installDest, String envPath) {
		installerRoot = FileUtils.getJarFile(Installer.class).getParentFile().getParentFile().toPath();
		this.installDest = StringUtils.isNullOrEmpty(installDest) ? null : new File(installDest).toPath();
		this.envPath = envPath;
	}

	public abstract void start(boolean requestInstallDest) throws Exception;

	void doInstall() throws Exception {
		try (FileUnzipper x = new FileUnzipper(new ZipInputStream(this.getClass().getResourceAsStream("/binary/SCS")), installDest)) {
			x.extract();
		}
		if (!installerRoot.equals(installDest)) {
			final FileTreeCopier ftc = new FileTreeCopier(installerRoot.resolve(Constants.JDK_DIR_NAME), installDest.resolve(Constants.JDK_DIR_NAME));
			Files.walkFileTree(installerRoot.resolve(Constants.JDK_DIR_NAME), ftc);
		}
		
		final ServiceManager svcMgr = new ServiceManager(installDest, installDest.resolve(Constants.REL_PATH_SCS_SVC_FILE), envPath, EnvUtils.isX64(installerRoot.resolve(Constants.JDK_DIR_NAME)), EnvUtils.IS_WINDOWS);
		int i = 1;
		while (svcMgr.isInstalled(Constants.SCS_SVC_BASENAME + i))
			i++;
		svcMgr.createSvc(Constants.SCS_SVC_BASENAME + i);
	}

	void startManager() throws IOException, InterruptedException {
		if (GraphicsEnvironment.isHeadless()) {
			Process p = RuntimeUtils.getProcessWithPath(new String[] {installDest.resolve(Constants.REL_PATH_JAVA).toString(), "-jar", installDest.resolve(Constants.REL_PATH_SCS_BIN).resolve("setup.jar").toString(), "/EnvPath", envPath}, installDest.toFile(), envPath);
			int rc = p.waitFor();
			if (rc != 0) System.out.println(RuntimeUtils.readTerminatedProcessOutput(p));
		} else {
			RuntimeUtils.getProcessWithPath(new String[] {installDest.resolve(Constants.REL_PATH_JAVAW).toString(), "-jar", installDest.resolve(Constants.REL_PATH_SCS_BIN).resolve("setup.jar").toString(), "/EnvPath", envPath}, installDest.toFile(), envPath);
		}
	}

	public static boolean isValidInstallDir(String dirPath) {
		final File tmp = new File(dirPath);
		if (tmp.exists()) return tmp.isDirectory();
		else return tmp.mkdirs();
	}

	public static String readTerminatedProcessOutput(Process p) throws IOException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (InputStream is = p.getInputStream()) {
			final byte[] buf = new byte[1024];
			int r = -1;
			while ((r = is.read(buf)) != -1)
				baos.write(buf, 0, r);
		}
		baos.write(EnvUtils.CR.getBytes());
		try (InputStream is = p.getErrorStream()) {
			final byte[] buf = new byte[1024];
			int r = -1;
			while ((r = is.read(buf)) != -1)
				baos.write(buf, 0, r);
		}
		return new String(baos.toByteArray());
	}

}
