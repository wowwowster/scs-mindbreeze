package com.sword.scs.setup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import sword.common.utils.files.visitors.FileTreeCopier;
import sword.common.utils.files.visitors.FileTreeDeleter;

public class PatchApplicator {

	private final Path rootDirPath;
	private final File rootDirFile;
	private final Path patchDirPath;
	private final String envPath;
	private final String svcName;
	private final boolean isWindows;
	private final String moveExecutablePath;

	public PatchApplicator(final Path rootDir, final Path patchDir, final String envPath, final String svcName, final boolean isWindows) throws IOException {
		rootDirPath = rootDir;
		rootDirFile = rootDir.toFile();
		patchDirPath = patchDir;
		this.envPath = envPath;
		this.svcName = svcName;
		this.isWindows = isWindows;

		if (isWindows) {
			final Path tmpBat = patchDirPath.resolve("tmp_move.bat");
			moveExecutablePath = tmpBat.toString();
			Files.copy(new ByteArrayInputStream("move /Y %1 %2".getBytes()), tmpBat, StandardCopyOption.REPLACE_EXISTING);
		} else {
			final Path tmpSh = patchDirPath.resolve("tmp_move.sh");
			moveExecutablePath = tmpSh.toString();
			Files.copy(new ByteArrayInputStream("mv -f \"$1\" \"$2\"".getBytes()), tmpSh, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public static void main(final String[] args) {

		final Path rootDir = new File(args[0]).toPath();
		final Path patchDir = new File(args[1]).toPath();
		final String envPath = args[2];
		final String svcName = args[3];
		final boolean isWindows = Boolean.parseBoolean(args[4]);

		try {
			final PatchApplicator pa = new PatchApplicator(rootDir, patchDir, envPath, svcName, isWindows);
			pa.run();
		} catch (final Exception e) {
			e.printStackTrace();
		}

	}

	private void run() throws Exception {

		final Object lockWait = new Object();
		synchronized (lockWait) {
			lockWait.wait(2000L);
		}

		final File setupSh = rootDirPath.resolve("setup.sh").toFile();
		final File hiddenSetupSh = patchDirPath.resolve("setup.sh").toFile();
		if (setupSh.exists()) moveFile(setupSh, hiddenSetupSh);

		final File setupExe = rootDirPath.resolve("setup.exe").toFile();
		final File hiddenSetupExe = patchDirPath.resolve("setup.exe").toFile();
		if (setupExe.exists()) moveFile(setupExe, hiddenSetupExe);

		synchronized (lockWait) {
			lockWait.wait(500L);
		}

		try {
			
			final Path patchBuildInfoPath = patchDirPath.resolve("buildinfo");
			if (patchBuildInfoPath.toFile().exists()) {
				ByteArrayOutputStream patchBuildInfo = new ByteArrayOutputStream();
				Files.copy(patchBuildInfoPath, patchBuildInfo);
				final Path buildInfoPath = rootDirPath.resolve("scs/bin/buildinfo");
				if (buildInfoPath.toFile().exists()) {
					ByteArrayOutputStream buildInfo = new ByteArrayOutputStream();
					Files.copy(buildInfoPath, buildInfo);
					buildInfo.write(patchBuildInfo.toByteArray());
					Files.copy(new ByteArrayInputStream(buildInfo.toByteArray()), buildInfoPath, StandardCopyOption.REPLACE_EXISTING);
				} else {
					Files.copy(new ByteArrayInputStream(patchBuildInfo.toByteArray()), buildInfoPath);
				}
				
			}
			
			final File remFile = patchDirPath.resolve("removal.xml").toFile();
			if (remFile.exists()) {

				checkIntegrity();

				final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(remFile);
				final NodeList nl = doc.getDocumentElement().getChildNodes();
				final int s = nl.getLength();
				for (int i = 0; i < s; i++)
					if (nl.item(i).getNodeType() == Node.ELEMENT_NODE && "delete".equals(nl.item(i).getNodeName())) {
						final Element e = (Element) nl.item(i);
						final String p = e.getAttribute("path");
						final File f = rootDirPath.resolve(p).toFile();
						if (f.exists()) {
							if (f.isFile()) {
								if (!f.delete()) throw new Exception("Failed to delete file " + f.getAbsolutePath() + " - Please try to delete file manually and apply patch again.");
							} else {
								Files.walkFileTree(f.toPath(), new FileTreeDeleter());
							}
						}
					}

				Files.walkFileTree(patchDirPath.resolve("binary"), new FileTreeCopier(patchDirPath.resolve("binary"), rootDirPath));
				if (!isWindows) {
					final Path tmpSh = patchDirPath.resolve("tmp_setperms.sh");
					Files.copy(new ByteArrayInputStream(("#!/bin/sh\n\n" + "svcName=$1\n\n" + "chown $svcName.$svcName -R .\n\n" + "find . -name \"*.sh\" -print0 | xargs -0 chmod u+x").getBytes()), tmpSh, StandardCopyOption.REPLACE_EXISTING);
					final ProcessBuilder pb = new ProcessBuilder(Arrays.asList(new String[] {"sh", tmpSh.toString(), svcName}));
					pb.environment().put("PATH", envPath);
					pb.directory(rootDirPath.toFile());
					final Process p = pb.start();
					p.waitFor();
				}

			} else throw new Exception("Invalid patch file");
		} finally {
			try {
				if (hiddenSetupSh.exists() && !setupSh.exists()) {
					moveFile(hiddenSetupSh, setupSh);
				}
			} catch (final Exception e) {
				e.printStackTrace();
			}
			try {
				if (hiddenSetupExe.exists() && !setupExe.exists()) {
					moveFile(hiddenSetupExe, setupExe);
				}
			} catch (final Exception e) {
				e.printStackTrace();
			}
			try {
				Files.walkFileTree(patchDirPath, new FileTreeDeleter());
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void checkIntegrity() throws IOException, InterruptedException {
		System.out.println("Checking integrity");
		{
			final File scsDir = rootDirPath.resolve("scs").toFile();
			final File scsDirOldName = rootDirPath.resolve("gsp").toFile();
			System.out.println(scsDirOldName.exists() + " ; " + scsDirOldName.isDirectory() + " ; " + scsDir.exists());
			if (scsDirOldName.exists() && scsDirOldName.isDirectory() && !scsDir.exists()) {
				moveFile(scsDirOldName, scsDir);
			}
		}

		{
			final File scsWebappDir = rootDirPath.resolve("webapps/SCS").toFile();
			final File scsWebappDirOldName = rootDirPath.resolve("webapps/GSP").toFile();
			System.out.println(scsWebappDirOldName.exists() + " ; " + scsWebappDirOldName.isDirectory() + " ; " + scsWebappDir.exists());
			if (scsWebappDirOldName.exists() && scsWebappDirOldName.isDirectory() && !scsWebappDir.exists()) {
				moveFile(scsWebappDirOldName, scsWebappDir);
			}
		}

		{
			final File scsConfOldName = rootDirPath.resolve("scs/conf/GSPConfig.xml").toFile();
			final File scsConf = rootDirPath.resolve("scs/conf/config.xml").toFile();
			System.out.println(scsConfOldName.exists() + " ; " + scsConfOldName.isDirectory() + " ; " + scsConf.exists());
			if (scsConfOldName.exists() && scsConfOldName.isFile()) {
				moveFile(scsConfOldName, scsConf);
			}
		}

		{
			final File scsBinDir = rootDirPath.resolve("scs/bin").toFile();
			final File[] scsBinFiles = scsBinDir.listFiles();
			if (scsBinFiles != null) {
				for (final File scsBinFile : scsBinFiles) {
					final String fn = scsBinFile.getName();
					if (fn.matches("gsp-.+\\.jar")) {
						moveFile(scsBinFile, new File(scsBinDir, fn.replaceFirst("gsp-(.+)\\.jar", "scs-$1.jar")));
					} else if ("GSP Manager.jar".equals(fn)) {
						moveFile(scsBinFile, new File(scsBinDir, "setup.jar"));
					}
				}
			}
		}

		{
			final File scsLibDir = rootDirPath.resolve("webapps/SCS/WEB-INF/lib").toFile();
			final File[] scsLibFiles = scsLibDir.listFiles();
			if (scsLibFiles != null) {
				for (final File scsLibFile : scsLibFiles) {
					final String fn = scsLibFile.getName();
					if (scsLibFile.getName().matches("gsp-.+\\.jar")) {
						moveFile(scsLibFile, new File(scsLibDir, fn.replaceFirst("gsp-(.+)\\.jar", "scs-$1.jar")));
					}
				}
			}
		}

		{
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			final Path f = rootDirPath.resolve("scs/conf/config.xml");
			Files.copy(f, os);
			String conf = new String(os.toByteArray(), StandardCharsets.UTF_8);
			conf = conf.replace(rootDirPath.resolve("gsp").toString(), rootDirPath.resolve("scs").toString());
			Files.copy(new ByteArrayInputStream(conf.getBytes(StandardCharsets.UTF_8)), f, StandardCopyOption.REPLACE_EXISTING);
		}

		{
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			final Path f = rootDirPath.resolve("scs/conf/jaas.conf");
			Files.copy(f, os);
			String conf = new String(os.toByteArray(), StandardCharsets.UTF_8);
			conf = conf.replace("${catalina.home}/gsp/conf/gsp.keytab", "${catalina.home}/scs/conf/scs.keytab");
			Files.copy(new ByteArrayInputStream(conf.getBytes(StandardCharsets.UTF_8)), f, StandardCopyOption.REPLACE_EXISTING);
		}

		{
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			final Path f = rootDirPath.resolve("conf/server.xml");
			Files.copy(f, os);
			String conf = new String(os.toByteArray(), StandardCharsets.UTF_8);
			conf = conf.replace(rootDirPath.resolve("gsp").toString(), rootDirPath.resolve("scs").toString());
			Files.copy(new ByteArrayInputStream(conf.getBytes(StandardCharsets.UTF_8)), f, StandardCopyOption.REPLACE_EXISTING);
		}

		{
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			final Path f = rootDirPath.resolve("webapps/SCS/WEB-INF/web.xml");
			Files.copy(f, os);
			String conf = new String(os.toByteArray(), StandardCharsets.UTF_8);
			conf = conf.replace("com.sword.gsa.spis.gsp.", "com.sword.gsa.spis.scs.");
			conf = conf.replace("GSPContextListener", "SCSContextListener");
			conf = conf.replace("GSPConfigUI", "SCSConfigUI");
			conf = conf.replace("GSPPublicConfigUI", "SCSPublicConfigUI");
			Files.copy(new ByteArrayInputStream(conf.getBytes(StandardCharsets.UTF_8)), f, StandardCopyOption.REPLACE_EXISTING);
		}

	}

	private void moveFile(final File orig, final File dest) throws IOException, InterruptedException {
		if (isWindows) {
			final ProcessBuilder pb = new ProcessBuilder(Arrays.asList(new String[] {"cmd.exe", "/C", moveExecutablePath, orig.getAbsolutePath(), dest.getAbsolutePath()}));
			pb.environment().put("PATH", envPath);
			pb.directory(rootDirFile);
			final Process p = pb.start();
			p.waitFor();
			try (InputStream is = p.getInputStream()) {
				final byte[] buf = new byte[1024];
				int r = -1;
				while ((r = is.read(buf)) != -1) {
					System.out.print(new String(buf, 0, r));
				}
			}
			System.out.println();
			try (InputStream is = p.getErrorStream()) {
				final byte[] buf = new byte[1024];
				int r = -1;
				while ((r = is.read(buf)) != -1) {
					System.out.print(new String(buf, 0, r));
				}
			}
		} else {
			final ProcessBuilder pb = new ProcessBuilder(Arrays.asList(new String[] {"sh", moveExecutablePath, orig.getAbsolutePath(), dest.getAbsolutePath()}));
			pb.environment().put("PATH", envPath);
			pb.directory(rootDirFile);
			final Process p = pb.start();
			p.waitFor();
			try (InputStream is = p.getInputStream()) {
				final byte[] buf = new byte[1024];
				int r = -1;
				while ((r = is.read(buf)) != -1) {
					System.out.print(new String(buf, 0, r));
				}
			}
			System.out.println();
			try (InputStream is = p.getErrorStream()) {
				final byte[] buf = new byte[1024];
				int r = -1;
				while ((r = is.read(buf)) != -1) {
					System.out.print(new String(buf, 0, r));
				}
			}
		}
	}

}
