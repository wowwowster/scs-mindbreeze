package com.sword.gsa.spis.scs.push.connector;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import sword.common.utils.StringUtils;

public final class URLManager {

	public static final String CONNECTORS_PATTERN = "regexp:https://connectors\\\\.sword-group\\\\.com/[A-Za-z0-9_\\\\-]+/doc\\\\?node=.+";
	public static final String CONNECTORS_HOST = "https://connectors.sword-group.com/%s/doc?node=%s";
	private static final String ALTERNATIVE_BASE_URL = System.getProperty("sword.indexer.AlternativeURL", "");

	public static String getSystemURL(final String dataSource, final String docId) throws UnsupportedEncodingException {
		final String un = StandardCharsets.UTF_8.name();
		
		final String did = URLEncoder.encode(docId, un);
		if (StringUtils.isNullOrEmpty(ALTERNATIVE_BASE_URL)) {
			final String ds = URLEncoder.encode(dataSource, un);
			return String.format(CONNECTORS_HOST, ds, did);
		} else {
			return ALTERNATIVE_BASE_URL + did;
		}
		
	}
}