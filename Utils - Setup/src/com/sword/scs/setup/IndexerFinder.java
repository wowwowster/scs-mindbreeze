package com.sword.scs.setup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import sword.common.utils.StringUtils;

public class IndexerFinder implements FileVisitor<Path> {

	private final List<Path> indexerInstances = new ArrayList<>();

	@Override
	public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
		if (dir.resolve("_dashboard").toFile().exists() && dir.resolve("_conf").toFile().exists() && dir.resolve("_local-db").toFile().exists()) {
			indexerInstances.add(dir);
			return FileVisitResult.SKIP_SUBTREE;
		} else return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
		return FileVisitResult.CONTINUE;
	}

	public List<Path> getRunningIndexers() {
		final List<Path> runningInstances = new ArrayList<>();
		for (final Path indexerInstance : indexerInstances) {
			final Path monPort = indexerInstance.resolve("_dashboard/port");
			if (monPort.toFile().exists()) {
				try {
					final ByteArrayOutputStream os = new ByteArrayOutputStream();
					Files.copy(monPort, os);
					int monitorPort = -1;
					final String monitorFileContents = new String(os.toByteArray(), StandardCharsets.UTF_8);
					if (StringUtils.isInteger(monitorFileContents)) {
						monitorPort = Integer.parseInt(monitorFileContents);
					} else {
						monitorPort = -1;
					}
					if (monitorPort > 0) {
						try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monitorPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
							socketOs.write("GET /ping.action HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
							socketOs.flush();
							final byte[] buf = new byte[4096];
							final int r = socketIs.read(buf);
							final String resp = new String(buf, 0, r, StandardCharsets.UTF_8);
							if (StringUtils.npeProofEquals(resp, indexerInstance.getFileName().toString())) {
								runningInstances.add(indexerInstance);
							}
						}
					}
				} catch (final Exception ignore) {}
			}
		}
		return runningInstances;
	}

	public static void killIndexers(final List<Path> runningIndexers) {
		for (final Path indexerInstance : runningIndexers) {
			final Path monPort = indexerInstance.resolve("_dashboard/port");
			if (monPort.toFile().exists()) {
				try {
					final ByteArrayOutputStream os = new ByteArrayOutputStream();
					Files.copy(monPort, os);
					int monitorPort = -1;
					final String monitorFileContents = new String(os.toByteArray(), StandardCharsets.UTF_8);
					if (StringUtils.isInteger(monitorFileContents)) {
						monitorPort = Integer.parseInt(monitorFileContents);
					} else {
						monitorPort = -1;
					}
					if (monitorPort > 0) {
						try (final Socket socket = new Socket(InetAddress.getLoopbackAddress(), monitorPort); final OutputStream socketOs = socket.getOutputStream(); final InputStream socketIs = socket.getInputStream()) {
							socketOs.write("GET /kill.action HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
							socketOs.flush();
						}
					}
				} catch (final Exception ignore) {}
			}
		}
	}

}
