package com.sword.scs.setup;

public final class KrbConf {

	public final String realm;
	public final String hostname;
	public final String kdc;

	public KrbConf(final String realm, final String hostname, final String kdc) {
		super();
		this.realm = realm;
		this.hostname = hostname;
		this.kdc = kdc;
	}

}
