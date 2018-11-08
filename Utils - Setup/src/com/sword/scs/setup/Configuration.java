package com.sword.scs.setup;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

import sword.common.crypto.hash.Digester;
import sword.common.crypto.hash.HashAlgorithm;
import sword.common.utils.HexUtils;
import sword.common.utils.StringUtils;

public class Configuration {

	public static final String DO_NOT_CHANGE = "sgqsdfgdqsd,ffjù,; qsdgfklm";

	private String hostname;
	private int httpPort;
	private int httpsPort;
	private int shutdownPort;
	private String adminPasswordHash;

	public Configuration() {
		super();
		hostname = null;
		httpPort = -1;
		httpsPort = -1;
		shutdownPort = -1;
		adminPasswordHash = null;
	}

	public Configuration(final String hostname, final String httpPort, final String httpsPort, final String shutdownPort, final String adminPasswordHash) {
		super();
		this.hostname = StringUtils.isNullOrEmpty(hostname) || "NULL".equals(hostname) ? null : hostname;
		this.httpPort = StringUtils.isNullOrEmpty(httpPort) || !StringUtils.isInteger(httpPort) ? -1 : Integer.parseInt(httpPort);
		this.httpsPort = StringUtils.isNullOrEmpty(httpsPort) || !StringUtils.isInteger(httpsPort) ? -1 : Integer.parseInt(httpsPort);
		this.shutdownPort = StringUtils.isNullOrEmpty(shutdownPort) || !StringUtils.isInteger(shutdownPort) ? -1 : Integer.parseInt(shutdownPort);
		this.adminPasswordHash = StringUtils.isNullOrEmpty(adminPasswordHash) ? null : adminPasswordHash;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(final String hostname) {
		this.hostname = hostname;
	}

	public int getHttpPort() {
		return httpPort;
	}

	public void setHttpPort(final int httpPort) {
		this.httpPort = httpPort;
	}

	public int getHttpsPort() {
		return httpsPort;
	}

	public void setHttpsPort(final int httpsPort) {
		this.httpsPort = httpsPort;
	}

	public int getShutdownPort() {
		return shutdownPort;
	}

	public void setShutdownPort(final int shutdownPort) {
		this.shutdownPort = shutdownPort;
	}

	public String getAdminPasswordHash() {
		return adminPasswordHash;
	}

	public void setAdminPasswordHash(final String adminPasswordHash) {
		this.adminPasswordHash = adminPasswordHash;
	}

	public boolean isDefault() {
		return hostname == null && httpPort == -1 && httpsPort == -1 && shutdownPort == -1;
	}

	static String hashPassword(final String clearText) throws NoSuchAlgorithmException {
		return HexUtils.toHexString(new Digester(HashAlgorithm.SHA_256).digest(clearText.getBytes(StandardCharsets.UTF_8)));
	}

}
