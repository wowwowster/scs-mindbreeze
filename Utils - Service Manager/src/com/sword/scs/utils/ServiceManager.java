package com.sword.scs.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sword.common.utils.EnvUtils;
import sword.common.utils.StringUtils;
import sword.common.utils.runtime.RuntimeUtils;
import sword.common.utils.streams.StreamUtils;

public final class ServiceManager {

	private final Path scsRootDir;
	private final Path svcNameFile;
	private final String envPath;
	private final boolean isX64;
	private final boolean isWindows;

	public ServiceManager(Path scsRootDir, Path svcNameFile, String envPath, boolean isX64, boolean isWindows) {
		super();
		this.scsRootDir = scsRootDir;
		this.svcNameFile = svcNameFile;
		this.envPath = envPath;
		this.isX64 = isX64;
		this.isWindows = isWindows;
	}

	public String getServiceName() throws IOException {
		String svcName = null;
		if (svcNameFile.toFile().exists()) {
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Files.copy(svcNameFile, baos);
			svcName = new String(baos.toByteArray(), StandardCharsets.UTF_8);
		}
		return svcName;
	}

	public boolean isInstalled(final String serviceName) throws IOException, InterruptedException {
		if (isWindows) return RuntimeUtils.getProcessWithPath(new String[] {"SC", "query", serviceName}, scsRootDir.toFile(), envPath).waitFor() == 0;
		else return new File("/etc/init.d/" + serviceName).exists();
	}

	public boolean isRunning(final String serviceName) throws IOException, InterruptedException {
		if (isWindows) {
			final Process p = RuntimeUtils.getProcessWithPath(new String[] {"wmic", "service", "where", "Name='" + serviceName + "'", "Get", "ProcessId"}, scsRootDir.toFile(), envPath);
			p.waitFor();
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			StreamUtils.transferBytes(p.getInputStream(), baos);
			final String result = new String(baos.toByteArray());
			final Matcher m = Pattern.compile("ProcessId[ \\t\\r\\n]+([0-9]+)").matcher(result);
			if (m.find()) {
				final String pid = m.group(1);
				if (StringUtils.isInteger(pid)) return Integer.parseInt(pid) > 0;
				else return false;
			} else return false;
		} else {

			final File sf = new File("/etc/init.d/" + serviceName);
			final Process p = RuntimeUtils.getProcessWithPath(new String[] {"sh", sf.getAbsolutePath(), "status"}, scsRootDir.toFile(), envPath);
			final int rc = p.waitFor();
			if (rc != 0) return false;
			else return RuntimeUtils.readTerminatedProcessOutput(p).contains("Server is running");
		}
	}

	public void createSvc(String serviceName) throws IOException, InterruptedException {

		if (EnvUtils.IS_WINDOWS) {
			String execPath = "/binaries/x";
			if (isX64) execPath += "64/";
			else execPath += "86/";

			for (final String s : new String[] {"service.bat", "tcnative-1.dll", "tomcat8.exe", "tomcat8w.exe"})
				try (InputStream is = this.getClass().getResourceAsStream(execPath + s)) {
					Files.copy(is, scsRootDir.resolve("bin/" + s), StandardCopyOption.REPLACE_EXISTING);
				}
			final File svcExe = scsRootDir.resolve("bin/" + serviceName + "w.exe").toFile();
			if (svcExe.exists()) svcExe.delete();
			scsRootDir.resolve("bin/tomcat8w.exe").toFile().renameTo(svcExe);

			final Process p = RuntimeUtils.getProcessWithPath(new String[] {"cmd.exe", "/C", "scs\\bin\\createsvc.bat " + serviceName}, scsRootDir.toFile(), envPath);
			if (p.waitFor() != 0) throw new RuntimeException("Service installation failed: " + RuntimeUtils.readTerminatedProcessOutput(p));

		} else {
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			StreamUtils.transferBytes(this.getClass().getResourceAsStream("/binaries/initd-script.sh"), os);
			String initScript = new String(os.toByteArray(), StandardCharsets.UTF_8);
			initScript = initScript.replace("SVC_NAME_PLACEHOLDER", serviceName).replace("JDK_HOME_PLACEHOLDER", "./jdk").replace("CATALINA_HOME_PLACEHOLDER", scsRootDir.toString());

			Files.copy(new ByteArrayInputStream(initScript.getBytes(StandardCharsets.UTF_8)), new File("/etc/init.d/" + serviceName).toPath(), StandardCopyOption.REPLACE_EXISTING);

			final StringBuilder setenv = new StringBuilder("#!/bin/sh\n\nexport JAVA_OPTS=\"-server ").append(isX64 ? "-Xms512m -Xmx3072m -XX:PermSize=256m -XX:MaxPermSize=256m" : "-Xms256m -Xmx1536m -XX:PermSize=128m -XX:MaxPermSize=128m").append("\"\nexport CATALINA_PID=\"");
			setenv.append(scsRootDir.toString()).append("/work/tompid\"\n");
			Files.copy(new ByteArrayInputStream(setenv.toString().getBytes(StandardCharsets.UTF_8)), scsRootDir.resolve("bin/setenv.sh"), StandardCopyOption.REPLACE_EXISTING);

			final Process p = RuntimeUtils.getProcessWithPath(new String[] {"sh", "scs/bin/createsvc.sh", serviceName}, scsRootDir.toFile(), envPath);
			if (p.waitFor() != 0) throw new RuntimeException("Service installation failed: " + RuntimeUtils.readTerminatedProcessOutput(p));
		}

		Files.copy(new ByteArrayInputStream(serviceName.getBytes(StandardCharsets.UTF_8)), svcNameFile, StandardCopyOption.REPLACE_EXISTING);

	}

	public void deleteSvc(String serviceName) throws IOException, InterruptedException {

		if (EnvUtils.IS_WINDOWS) {
			final Process p = RuntimeUtils.getProcessWithPath(new String[] {"cmd.exe", "/C", "scs\\bin\\delsvc.bat " + serviceName}, scsRootDir.toFile(), envPath);
			if (p.waitFor() != 0) throw new RuntimeException("Service removal failed: " + RuntimeUtils.readTerminatedProcessOutput(p));
		} else {
			final Process p = RuntimeUtils.getProcessWithPath(new String[] {"sh", "scs/bin/delsvc.sh", serviceName}, scsRootDir.toFile(), envPath);
			if (p.waitFor() != 0) throw new RuntimeException("Service removal failed: " + RuntimeUtils.readTerminatedProcessOutput(p));
		}

		final File snf = svcNameFile.toFile();
		if (snf.exists()) snf.delete();
	}

	public void startSvc(String serviceName) throws IOException, InterruptedException {

		if (EnvUtils.IS_WINDOWS) {
			final Process p = RuntimeUtils.getProcessWithPath(new String[] {"net", "start", serviceName}, scsRootDir.toFile(), envPath);
			if (p.waitFor() != 0) throw new RuntimeException("Failed to start service: " + RuntimeUtils.readTerminatedProcessOutput(p));
		} else {
			final Process p = RuntimeUtils.getProcessWithPath(new String[] {"sh", "/etc/init.d/" + serviceName, "start"}, scsRootDir.toFile(), envPath);
			if (p.waitFor() != 0) throw new RuntimeException("Failed to start service: " + RuntimeUtils.readTerminatedProcessOutput(p));
		}

	}

	public void stopSvc(String serviceName) throws IOException, InterruptedException {
		if (EnvUtils.IS_WINDOWS) {
			final Process p = RuntimeUtils.getProcessWithPath(new String[] {"net", "stop", serviceName}, scsRootDir.toFile(), envPath);
			if (p.waitFor() != 0) throw new RuntimeException("Failed to stop service: " + RuntimeUtils.readTerminatedProcessOutput(p));
		} else {
			final Process p = RuntimeUtils.getProcessWithPath(new String[] {"sh", "/etc/init.d/" + serviceName, "stop"}, scsRootDir.toFile(), envPath);
			if (p.waitFor() != 0) throw new RuntimeException("Failed to stop service: " + RuntimeUtils.readTerminatedProcessOutput(p));
		}
	}

}
