package com.sword.gsa.spis.scs.ui;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.net.ssl.SSLHandshakeException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.xpath.XPathExpressionException;

import org.xml.sax.SAXException;

import sword.common.crypto.hash.Digester;
import sword.common.crypto.hash.HashAlgorithm;
import sword.common.security.PwdManager;
import sword.common.utils.HexUtils;
import sword.common.utils.StringUtils;
import sword.common.utils.dates.DateUtils;
import sword.common.utils.files.visitors.FileTreeDeleter;
import sword.common.utils.throwables.ExceptionDigester;
import sword.connectors.commons.config.CP;
import sword.connectors.commons.config.CPUtils;
import sword.gsa.xmlfeeds.builder.Authmethod;
import sword.gsa.xmlfeeds.builder.Metadata;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.sword.gsa.adminapi.ConnectorManagers;
import com.sword.gsa.adminapi.CrawlPatternsUpdater;
import com.sword.gsa.adminapi.DatasourceDeleter;
import com.sword.gsa.adminapi.FeedergateAccessManager;
import com.sword.gsa.adminapi.NonGdataHttpAdmin;
import com.sword.gsa.adminapi.gdatawrap.GSAClientBuilder;
import com.sword.gsa.adminapi.gdatawrap.GsaClientProxy;
import com.sword.gsa.adminapi.gdatawrap.ServiceException;
import com.sword.gsa.adminapi.objects.ConnectorManager;
import com.sword.gsa.adminapi.objects.PatternDef;
import com.sword.gsa.spis.scs.commons.acl.cache.FailedCache;
import com.sword.gsa.spis.scs.commons.acl.cache.GroupCache;
import com.sword.gsa.spis.scs.commons.acl.cache.NullCache;
import com.sword.gsa.spis.scs.commons.config.HttpParams;
import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.config.authn.AuthenticatorConf;
import com.sword.gsa.spis.scs.commons.config.authn.GroupRetrieverConf;
import com.sword.gsa.spis.scs.commons.config.authz.AuthorizerConf;
import com.sword.gsa.spis.scs.commons.config.authz.SAMLAuthzConf;
import com.sword.gsa.spis.scs.commons.config.indexing.IndexerConf;
import com.sword.gsa.spis.scs.commons.config.indexing.Schedule;
import com.sword.gsa.spis.scs.commons.config.indexing.Schedule.Period;
import com.sword.gsa.spis.scs.commons.connector.ConnectorContext;
import com.sword.gsa.spis.scs.commons.connector.GroupCacheManager;
import com.sword.gsa.spis.scs.commons.connector.IConnectorContext;
import com.sword.gsa.spis.scs.commons.connector.IndexingProcMgr;
import com.sword.gsa.spis.scs.commons.connector.IndexingProcWatcher;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.commons.connector.models.ICachableGroupRetriever;
import com.sword.gsa.spis.scs.commons.connector.models.Indexer;
import com.sword.gsa.spis.scs.commons.http.SCSSecureServlet;
import com.sword.gsa.spis.scs.commons.utils.HttpUtils;
import com.sword.gsa.spis.scs.gsaadmin.GSAConnectivityStatus;
import com.sword.gsa.spis.scs.gsaadmin.GSAInfo;
import com.sword.gsa.spis.scs.gsaadmin.GsaManager;
import com.sword.gsa.spis.scs.gsaadmin.ReloadableTrustManager;
import com.sword.gsa.spis.scs.push.config.PushConfig;
import com.sword.gsa.spis.scs.push.config.PushType;
import com.sword.gsa.spis.scs.push.connector.URLManager;
import com.sword.gsa.spis.scs.push.monitoring.History;
import com.sword.gsa.spis.scs.push.monitoring.Monitor;
import com.sword.gsa.spis.scs.push.monitoring.SerStats;
import com.sword.gsa.spis.scs.push.monitoring.Statistics;
import com.sword.scs.Constants;

@MultipartConfig
public class SCSConfigUI extends SCSSecureServlet {

	private static final long serialVersionUID = 1L;

	private GsaClientProxy gc = null;
	private NonGdataHttpAdmin gHttpc = null;

	@Override
	protected boolean checkConfig(final ServletConfig config) {
		return true;
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		try {
			initGsaClients(req, pwdMgr);
			final String pi = req.getPathInfo();
			if (StringUtils.isNullOrEmpty(pi)) jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(0): " + req.getRequestURL());
			if ("/logout".equals(pi)) {
				clearSession(req);
				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
				resp.setContentType("text/plain");
				resp.setContentLength(0);
			} else if (pi.startsWith("/scs")) {
				if (pi.startsWith("/scs/config")) getScsConfig(req, resp);
				else if (pi.startsWith("/scs/misc")) getScsMiscInfo(req, resp);
				else if (pi.startsWith("/scs/threads")) getScsThreads(resp);
				else if (pi.startsWith("/scs/log")) getLog(conf.scsCtx.tomcatRoot, "SCS.log", req, resp);
				else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(1): " + req.getRequestURL());
			} else if (pi.startsWith("/connector")) {
				if (pi.startsWith("/connector/config")) {
					final String connectorClass = req.getParameter("class");
					final String connectorId = req.getParameter("id");
					if (!StringUtils.isNullOrEmpty(connectorClass)) try {
						getConnectorConfig(resp, conf, conf.scsCtx.getConnectorCtx(connectorClass), null);
					} catch (final IllegalArgumentException e) {
						jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown connector class: " + connectorClass);
					}
					else if (!StringUtils.isNullOrEmpty(connectorId)) {// Request for edition of an existing connector
						if (conf.configuredConnectors.containsKey(connectorId)) {
							final AConnector c = conf.configuredConnectors.get(connectorId);
							try {
								getConnectorConfig(resp, conf, conf.scsCtx.getConnectorCtx(c), c);
							} catch (final IllegalArgumentException e) {
								jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown connector class: " + c.getClass().getName());
							}
						} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown connector ID: " + connectorId);
						return;
					} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(2): " + req.getRequestURL());
				} else if (pi.startsWith("/connector/cache")) {
					final String connectorId = req.getParameter("id");
					if (StringUtils.isNullOrEmpty(connectorId)) jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(3): " + req.getRequestURL());
					else {
						if (conf.configuredConnectors.containsKey(connectorId)) {
							final AConnector c = conf.configuredConnectors.get(connectorId);
							if (gcm == null) jsonError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "No GSA registered");
							else if (c instanceof ICachableGroupRetriever) getConnectorGroupRetrCacheStatus(resp, gcm, connectorId);
							else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid connector ID: " + connectorId);
						} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown connector ID: " + connectorId);
						return;
					}
				} else if (pi.startsWith("/connector/indexer")) {
					final String connectorId = req.getParameter("id");
					if (StringUtils.isNullOrEmpty(connectorId)) jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(4): " + req.getRequestURL());
					else if (conf.configuredConnectors.containsKey(connectorId)) {
						final AConnector c = conf.configuredConnectors.get(connectorId);
						if (c instanceof Indexer) {
							final IConnectorContext cc = conf.scsCtx.getConnectorCtx(c);
							if (pi.startsWith("/connector/indexer/state")) getIndexerStatus(resp, conf, c, cc);
							else if (pi.startsWith("/connector/indexer/stats")) getIndexerStats(resp, conf, c, cc);
							else if (pi.startsWith("/connector/indexer/LastKnownStats")) getIndexerLastKnownStats(resp, conf, c);
							else if (pi.startsWith("/connector/indexer/LastKnownHistory")) getIndexerLastKnownHistory(resp, conf, c);
							else if (pi.startsWith("/connector/indexer/history")) getIndexerHistory(resp, conf, c, cc);
							else if (pi.startsWith("/connector/indexer/log")) getLog(conf.scsCtx.tomcatRoot, "indexer-" + connectorId + ".log", req, resp);
							else if (pi.startsWith("/connector/indexer/internaldb/childnodes")) {
								String num = req.getParameter("num");
								if (!StringUtils.isInteger(num)) num = "25";
								String pid = req.getParameter("parent");
								if (StringUtils.isNullOrEmpty(pid)) pid = null;
								String page = req.getParameter("page");
								if (!StringUtils.isInteger(page)) page = "0";
								String states = req.getParameter("states");
								if (StringUtils.isNullOrEmpty(states)) states = null;
								getChildNodes(resp, conf, c, cc, pid, Integer.parseInt(num), Integer.parseInt(page), states);
							} else if (pi.startsWith("/connector/indexer/internaldb/updatestate")) {
								final String nid = req.getParameter("nid");
								if (StringUtils.isNullOrEmpty(nid)) jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown node id");
								else {
									String ntype = req.getParameter("ntype");
									if (StringUtils.isNullOrEmpty(ntype)) ntype = null;
									String action = req.getParameter("action");
									if (StringUtils.isNullOrEmpty(action)) action = null;
									boolean isDir = false;
									if ("dir".equals(ntype)) isDir = true;
									else if ("doc".equals(ntype)) isDir = false;
									else {
										jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown node type");
										return;
									}
									boolean exclude = false;
									if ("exclude".equals(action)) exclude = true;
									else if ("reindex".equals(action)) exclude = false;
									else {
										jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown action type");
										return;
									}
									reloadNodeState(resp, conf, c, cc, nid, isDir, exclude);
								}
							} else if (pi.startsWith("/connector/indexer/internaldb/search")) {
								final String nid = req.getParameter("nid");
								if (StringUtils.isNullOrEmpty(nid)) jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown node id");
								else {
									String pid = req.getParameter("parent");
									if (StringUtils.isNullOrEmpty(pid)) pid = null;
									findChild(resp, conf, c, cc, pid, nid);
								}
							} else jsonError(resp, HttpServletResponse.SC_NOT_IMPLEMENTED, "Yet");
						}
					} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown connector ID: " + connectorId);
				} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(5): " + req.getRequestURL());
			} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(6): " + req.getRequestURL());
		} catch (final Exception e) {
			jsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unhandled exception", e);
		}
	}

	private void initGsaClients(final HttpServletRequest req, final PwdManager pwdMgr) {
		try {
			final GSAClientBuilder gcb = (GSAClientBuilder) req.getSession(false).getAttribute(SESSION_ATTR_GSA_CLIENT_BUILDER);
			if (gcb != null) {
				gc = gcb.buildGSAClient(pwdMgr, 15_000);
				gHttpc = gcb.buildGSAHttpClient(pwdMgr, !conf.gsa.is72orHigher);
			}
		} catch (Exception e) {
			LOG.warn("GSA connection error: ", e);
		}
	}

	@Override
	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		try {
			initGsaClients(req, pwdMgr);
			final String pi = req.getPathInfo();
			if (StringUtils.isNullOrEmpty(pi)) jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(7): " + req.getRequestURL());
			else if (pi.startsWith("/scs")) {
				if (pi.startsWith("/scs/authent")) {
					try {
						postAuthentConfig(req, resp, getServletContext(), conf);
						reloadConfig();
					} catch (final IllegalArgumentException e) {
						LOG.warn("Invalid config: ", e);
						jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid config");
					}
				} else if (pi.startsWith("/scs/gsa/feedergate")) {
					try {
						grantFeederGateAccess(req, resp, conf);
					} catch (final IllegalStateException e) {
						LOG.warn("Unable to grant access to feedergate: ", e);
						jsonError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Unable to grant access to feedergate");
					}
				} else if (pi.startsWith("/scs/gsa/cm")) {
					try {
						registerConnectorManager(req, resp, conf);
					} catch (final IllegalStateException e) {
						LOG.warn("Unable to grant access to feedergate: ", e);
						jsonError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Unable to grant access to feedergate");
					}
				} else if (pi.startsWith("/scs/gsa/url-patterns")) {
					try {
						addConnectorPatterns(req, resp, conf);
					} catch (final IllegalStateException e) {
						LOG.warn("Unable to grant access to feedergate: ", e);
						jsonError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Unable to grant access to feedergate");
					}
				} else if (pi.startsWith("/scs/gsa")) {
					try {
						if (postGsaHostname(req, resp, conf)) reloadConfig();
					} catch (final IllegalArgumentException e) {
						LOG.warn("Invalid config: ", e);
						jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid config");
					}
				} else if (pi.startsWith("/scs/ftp")){
					try {
						if (postftpInfo(req, resp, conf)) reloadConfig();
					} catch (final IllegalArgumentException e) {
						LOG.warn("Invalid config: ", e);
						jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid config");
					}
				}else jsonError(resp, HttpServletResponse.SC_NOT_IMPLEMENTED, "Yet");

			} else if (pi.startsWith("/connector")) {
				if (pi.startsWith("/connector/config")) try {
					postConnectorConfig(req, resp, getServletContext(), conf, pwdMgr);
					reloadConfig();
				} catch (final IllegalArgumentException e1) {
					LOG.warn("Invalid connector: ", e1);
					jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid connector: ", e1);
				}
				else if (pi.startsWith("/connector/delete")) {// Did not implement DELETE HTTP method because i'm not sure Ajax supports it in all browsers
					final String connectorId = req.getParameter("id");
					if (!StringUtils.isNullOrEmpty(connectorId)) {// Request for deletion of an existing connector
						if (conf.configuredConnectors.containsKey(connectorId)) {
							final Path confFile = conf.scsCtx.tomcatRoot.resolve(Constants.REL_PATH_SCS_CONF_FILE);
							SCSConfiguration.deleteConnectorConfig(confFile, connectorId);
							final Path chd = conf.scsCtx.getConnectorCtx(conf.configuredConnectors.get(connectorId)).getInstanceHomeDir(connectorId);
							Files.walkFileTree(chd, new FileTreeDeleter());
							resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
							resp.setContentType("text/plain");
							resp.setContentLength(0);
							reloadConfig();
						} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown connector ID: " + connectorId);
						return;
					} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(8): " + req.getRequestURL());
				} else if (pi.startsWith("/connector/cache/reload")) {
					if (conf.gsa == null) {
						resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
						resp.setContentType("text/plain");
						resp.setCharacterEncoding("UTF-8");
						resp.setContentLength(0);
					} else {

						final String connectorId = req.getParameter("id");
						if (StringUtils.isNullOrEmpty(connectorId)) jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(9): " + req.getRequestURL());
						else {
							if (conf.configuredConnectors.containsKey(connectorId)) {
								final AConnector c = conf.configuredConnectors.get(connectorId);
								if (c instanceof ICachableGroupRetriever) {
									gcm.reloadCache(connectorId);
									resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
									resp.setContentType("text/plain");
									resp.setCharacterEncoding("UTF-8");
									resp.setContentLength(0);
								} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid connector ID: " + connectorId);
							} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown connector ID: " + connectorId);
							return;
						}

					}
				} else if (pi.startsWith("/connector/indexer")) {
					final String connectorId = req.getParameter("id");
					if (StringUtils.isNullOrEmpty(connectorId)) jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(a): " + req.getRequestURL());
					else if (conf.configuredConnectors.containsKey(connectorId)) {
						final AConnector c = conf.configuredConnectors.get(connectorId);
						if (c != null && c instanceof Indexer) {
							if (ipm == null){
								jsonError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "No GSA configured");
							}
							else {
								final IConnectorContext cc = conf.scsCtx.getConnectorCtx(c);
								if (pi.startsWith("/connector/indexer/startdbbrowsing")) postDBBrowsingStartRequest(resp, conf, c, ipm);
								else if (pi.startsWith("/connector/indexer/stopdbbrowsing")) postDBBrowsingStopRequest(resp, conf, c, cc, ipm);
								else if (pi.startsWith("/connector/indexer/start")) postIndexerStartReq(resp, conf, c, ipm);
								else if (pi.startsWith("/connector/indexer/stop")) postIndexerStopReq(resp, conf, c, ipm);
								else if (pi.startsWith("/connector/indexer/reset")) postIndexerResetReq(req, resp, conf, c, ipm, gc, gHttpc);
								else if (pi.startsWith("/connector/indexer/kill")) postIndexerKillReq(resp, conf, c, ipm);
								else if (pi.startsWith("/connector/indexer/applydbchanges")) postDBCommitRequest(resp, conf, c, cc);
								else jsonError(resp, HttpServletResponse.SC_NOT_IMPLEMENTED, "Yet");
							}
						} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid connector ID: " + connectorId);
					} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown connector ID: " + connectorId);
				} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(b): " + req.getRequestURL());
			} else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(c): " + req.getRequestURL());
		} catch (final Exception e) {
			jsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unhandled exception", e);
		}
	}



	@Override
	protected void doPut(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		doPost(req, resp);
	}

	private void getScsConfig(final HttpServletRequest req, final HttpServletResponse resp) throws IOException, JsonGenerationException {

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");

		final HttpSession session = req.getSession(false);
		final String currentUser = (String) session.getAttribute(SESSION_ATTR_USERNAME); 

		try (OutputStream os = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8)) {
			jg.setPrettyPrinter(new DefaultPrettyPrinter());
			jg.writeStartObject();

			jg.writeStringField("CurrentUser", currentUser);
			if (conf.ftpMode){
				jg.writeStringField("FtpHost", conf.ftpHost);
				jg.writeStringField("FtpMode", conf.ftpMode ? "true" : "false");
				jg.writeStringField("FtpUsername", conf.ftpUsername);
				jg.writeStringField("FtpPassword", conf.ftpPassword);
			}

			if (conf.gsa != null) GSAInfo.toJson(conf.gsa, jg);

			jg.writeObjectFieldStart("installedConnectors");
			writeInstalledConnectors(jg, conf.scsCtx.installedConnectors);
			jg.writeEndObject();

			jg.writeObjectFieldStart("configuredConnectors");
			final Collection<AConnector> configuredConnectors = conf.configuredConnectors.values();
			final List<AConnector> sortedConfiguredConnectors = new ArrayList<>();
			sortedConfiguredConnectors.addAll(configuredConnectors);
			Collections.sort(sortedConfiguredConnectors, new Comparator<AConnector>() {

				@Override
				public int compare(final AConnector o1, final AConnector o2) {
					return o2.uniqueId.compareTo(o1.uniqueId);
				}
			});
			for (final AConnector c : sortedConfiguredConnectors)
				jg.writeStringField(c.uniqueId, c.getClass().getName());
			jg.writeEndObject();

			if (conf.authnConf == null) jg.writeNullField("authnConf");
			else {
				jg.writeObjectFieldStart("authnConf");
				writeAuthnConf(jg, conf);
				jg.writeEndObject();
			}

			if (conf.authzConf == null) jg.writeNullField("authzConf");
			else {
				jg.writeObjectFieldStart("authzConf");
				writeAuthzConf(jg, conf.authzConf);
				jg.writeEndObject();
			}

			if (conf.indexers == null) jg.writeNullField("indexers");
			else {
				jg.writeArrayFieldStart("indexers");
				for (final IndexerConf ic : conf.indexers)
					jg.writeString(ic.connectorId);
				jg.writeEndArray();
			}

			jg.writeEndObject();
		}
	}

	private void getScsMiscInfo(final HttpServletRequest req, final HttpServletResponse resp) throws IOException, JsonGenerationException, ReflectiveOperationException {

		final GSAConnectivityStatus gcs = (GSAConnectivityStatus) getServletContext().getAttribute(CTX_ATTR_GSA_CONN_STATUS);

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");

		try (OutputStream os = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8)) {
			jg.setPrettyPrinter(new DefaultPrettyPrinter());
			jg.writeStartObject();
			outputScsInfo(req, jg);
			outGsaInfo(gcs, jg);
			jg.writeEndObject();
		}
	}

	private void outputScsInfo(final HttpServletRequest req, final JsonGenerator jg) throws IOException {
		jg.writeObjectFieldStart("scs");
		jg.writeStringField("LocalIP", req.getLocalAddr());
		jg.writeStringField("Uptime", DateUtils.toReadableTimeSpan(System.currentTimeMillis() - conf.creationTime));
		jg.writeNumberField("ServerDate", System.currentTimeMillis());
		final String scsHttpBase = buildScsBaseURL(httpDefaultHost, false);
		final String scsHttpsBase = buildScsBaseURL(httpDefaultHost, true);
		jg.writeStringField("SamlAuthNHttpEndpoint", scsHttpBase + "/authenticate");
		jg.writeStringField("SamlAuthNHttpsEndpoint", scsHttpsBase + "/authenticate");
		jg.writeStringField("SamlAuthNResponderHttpEndpoint", scsHttpBase + "/responder");
		jg.writeStringField("SamlAuthNResponderHttpsEndpoint", scsHttpsBase + "/responder");
		jg.writeStringField("SamlAuthZHttpEndpoint", scsHttpBase + "/authorize");
		jg.writeStringField("SamlAuthZHttpsEndpoint", scsHttpsBase + "/authorize");
		jg.writeStringField("CookieHttpEndpoint", scsHttpBase + "/authenticate");
		jg.writeStringField("CookieHttpsEndpoint", scsHttpsBase + "/authenticate");
		jg.writeStringField("ConnMgrHttpEndpoint", scsHttpBase + "/cm");
		jg.writeStringField("ConnMgrHttpsEndpoint", scsHttpsBase + "/cm");
		jg.writeEndObject();
	}

	private static String buildScsBaseURL(final HttpParams httpDefaultHost, boolean https) {
		if (https) return String.format("https://%s%s/SCS", httpDefaultHost.defaultHost, httpDefaultHost.httpsPort == 443 ? "" : ":" + httpDefaultHost.httpsPort);
		else return String.format("http://%s%s/SCS", httpDefaultHost.defaultHost, httpDefaultHost.httpPort == 80 ? "" : ":" + httpDefaultHost.httpPort);
	}

	private void outGsaInfo(final GSAConnectivityStatus gcs, final JsonGenerator jg) throws IOException, MalformedURLException, ReflectiveOperationException {
		jg.writeObjectFieldStart("gsa");
		gcs.toJson(jg, gc != null);
		if (gc != null) {
			jg.writeNumberField("status", 1);

			boolean feedergateAccessOK = false;
			try {
				feedergateAccessOK = GsaManager.checkFeederGateAccess(conf.gsa);
			} catch (final Exception e) {
				feedergateAccessOK = false;
			}
			jg.writeBooleanField("FeedergateAccessOK", feedergateAccessOK);

			final String cmHttpUrl = String.format("http://%s%s/SCS/cm", httpDefaultHost.defaultHost, httpDefaultHost.httpPort == 80 ? "" : ":" + httpDefaultHost.httpPort);
			final String cmHttpsUrl = String.format("https://%s%s/SCS/cm", httpDefaultHost.defaultHost, httpDefaultHost.httpsPort == 443 ? "" : ":" + httpDefaultHost.httpsPort);

			jg.writeObjectFieldStart("cm");
			boolean found = false;
			try {
				final ConnectorManagers cmClient = new ConnectorManagers(gc);
				final List<ConnectorManager> cms = cmClient.getConnectorManagers();
				for (final ConnectorManager cm : cms)
					if (cmHttpUrl.equals(cm.url)) {
						found = true;
						jg.writeStringField("name", cm.entryID);
						jg.writeStringField("url", cm.url);
						break;
					} else if (cmHttpsUrl.equals(cm.url)) {
						found = true;
						jg.writeStringField("name", cm.entryID);
						jg.writeStringField("url", cm.url);
						break;
					}

			} catch (final ServiceException e) {
				LOG.warn("Failed to connect to GSA: ", e);
			}
			if (!found) {
				jg.writeNullField("name");
				jg.writeNullField("url");
			}
			jg.writeEndObject();

			try {
				final CrawlPatternsUpdater cpClient = new CrawlPatternsUpdater(gc);
				final PatternDef pd = cpClient.getCrawlPatterns();
				jg.writeBooleanField("urlpatterns", pd.follow.contains(URLManager.CONNECTORS_PATTERN));
			} catch (final ServiceException e) {
				LOG.warn("Failed to connect to GSA: ", e);
				jg.writeBooleanField("urlpatterns", false);
			}

		}
		jg.writeEndObject();
	}

	private static void getScsThreads(final HttpServletResponse resp) throws IOException, JsonGenerationException {

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");

		try (OutputStream os = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8)) {

			final Map<Thread, StackTraceElement[]> stMap = Thread.getAllStackTraces();
			final List<Thread> threads = new ArrayList<>();
			threads.addAll(stMap.keySet());
			Collections.sort(threads, new Comparator<Thread>() {

				@Override
				public int compare(final Thread o1, final Thread o2) {
					return (int) (o1.getId() - o2.getId());
				}
			});

			jg.setPrettyPrinter(new DefaultPrettyPrinter());
			jg.writeStartObject();

			for (final Thread t : threads) {

				jg.writeStartObject();

				jg.writeStringField("Name", t.getName());
				jg.writeNumberField("ID", t.getId());
				jg.writeArrayFieldStart("Stack");
				final StackTraceElement[] aste = stMap.get(t);
				if (aste != null) for (final StackTraceElement ste : aste) {
					jg.writeStartObject();
					jg.writeStringField("Class", ste.getClassName());
					jg.writeStringField("File", ste.getFileName());
					jg.writeStringField("Method", ste.getMethodName());
					jg.writeNumberField("ln", ste.getLineNumber());
					jg.writeEndObject();
				}
				jg.writeEndArray();

				jg.writeEndObject();
			}

			jg.writeEndObject();
		}

	}

	private static void getLog(final Path tomcatRoot, final String logFileName, final HttpServletRequest req, final HttpServletResponse resp) throws IOException {

		final File logDir = tomcatRoot.resolve("logs").toFile();
		final File[] logFiles = logDir.listFiles();
		for (final File logFile : logFiles)
			if (logFile.getName().equals(logFileName)) {
				final long lmd = logFile.lastModified();
				final long lmdSec = lmd / 1000;
				final long now = System.currentTimeMillis();

				final long expires = lmd + 3_600_000L > now ? lmd + 3_600_000L : now + 10_000L;

				final String cc = req.getHeader("Cache-Control");
				final long ims = req.getDateHeader("If-Modified-Since");
				final long imsSec = ims / 1000;
				final boolean noCache = "no-cache".equals(cc);

				if (!noCache && ims > 0 && lmdSec <= imsSec) {

					resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
					resp.addDateHeader("Expires", expires);
					resp.addDateHeader("Date", now);
					resp.addHeader("Age", Long.toString((now - lmd) / 1000L));

				} else {

					final String ae = req.getHeader("Accept-Encoding");
					final boolean supportsGZ = !StringUtils.isNullOrEmpty(ae) && ae.contains("gzip");

					resp.setStatus(HttpServletResponse.SC_OK);
					resp.addDateHeader("Last-Modified", lmd);
					resp.addDateHeader("Date", now);
					resp.addDateHeader("Expires", expires);
					resp.addHeader("Age", Long.toString((now - lmd) / 1000L));
					resp.setContentType("text/plain");
					if (supportsGZ) resp.addHeader("Content-Encoding", "gzip");
					resp.setCharacterEncoding(Charset.defaultCharset().name());
					if (!supportsGZ) resp.setContentLengthLong(logFile.length());

					try (OutputStream os = new BufferedOutputStream(supportsGZ ? new GZIPOutputStream(resp.getOutputStream()) : resp.getOutputStream(), 8192)) {
						Files.copy(logFile.toPath(), os);
					}

				}
				return;
			}
		resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid file name: " + logFileName);

	}

	private static void getConnectorConfig(final HttpServletResponse resp, final SCSConfiguration conf, final IConnectorContext iConnectorContext, final AConnector c) throws IOException, JsonGenerationException {

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");

		try (OutputStream os = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8)) {
			jg.setPrettyPrinter(new DefaultPrettyPrinter());
			jg.writeStartObject();

			final boolean isExistingConnector = c != null;

			jg.writeStringField("className", iConnectorContext.getClassName());
			if (isExistingConnector) {
				jg.writeStringField("id", c.uniqueId);
				if (c.namespace == null) jg.writeNullField("namespace");
				else jg.writeStringField("namespace", c.namespace);
				if (StringUtils.isNullOrEmpty(c.nameTransformer)) jg.writeNullField("nameTransformer");
				else jg.writeStringField("nameTransformer", c.nameTransformer);
			}

			if (iConnectorContext.isCachableGroupRetriever()) {
				jg.writeObjectFieldStart("CachableGroupRetriever");
				jg.writeStringField("name", ICachableGroupRetriever.CP_CACHE_REFRESH_INTERVAL.name);
				jg.writeStringField("label", ICachableGroupRetriever.CP_CACHE_REFRESH_INTERVAL.label);
				jg.writeStringField("description", ICachableGroupRetriever.CP_CACHE_REFRESH_INTERVAL.description);
				jg.writeStringField("type", ICachableGroupRetriever.CP_CACHE_REFRESH_INTERVAL.type.name());
				if (isExistingConnector) {
					boolean isAlreadyGR = false;
					for (final GroupRetrieverConf grc : conf.groupRetrievers)
						if (grc.connector.uniqueId.equals(c.uniqueId)) {
							isAlreadyGR = true;
							jg.writeNumberField("value", TimeUnit.MILLISECONDS.toMinutes(grc.cacheRefreshInterval));
						}
					if (!isAlreadyGR) jg.writeNumberField("value", TimeUnit.MILLISECONDS.toMinutes(GroupRetrieverConf.DEFAULT_CRI));
				} else jg.writeNumberField("value", TimeUnit.MILLISECONDS.toMinutes(GroupRetrieverConf.DEFAULT_CRI));
				jg.writeEndObject();
			}

			if (iConnectorContext.isIndexer()) {
				PushConfig pc = null;
				if (isExistingConnector) for (final IndexerConf ic : conf.indexers)
					if (ic.connectorId.equals(c.uniqueId)) {
						try {
							pc = PushConfig.readConfiguration(iConnectorContext, c.uniqueId, conf.gsa.ssl, conf.gsa.defaultHost, conf.ftpMode, conf.ftpUsername, conf.ftpPassword, PushType.UPDATE, c.namespace, c.uniqueId, -1, false);
						} catch (final Exception e) {
							LOG.error("Invalid push config", e);
							pc = null;
						}

						jg.writeNumberField("interval", TimeUnit.MILLISECONDS.toHours(ic.interval));
						jg.writeNumberField("throughput", ic.throughput / 1000);

						if (ic.schedule == null) jg.writeNullField("schedule");
						else {
							jg.writeObjectFieldStart("schedule");
							jg.writeArrayFieldStart("periods");
							for (final Period p : ic.schedule.periods) {
								jg.writeStartObject();
								jg.writeNumberField("day", p.day);
								jg.writeNumberField("start", p.startHour);
								jg.writeNumberField("duration", p.duration);
								jg.writeEndObject();
							}
							jg.writeEndArray();
							jg.writeEndObject();
						}

					}

				jg.writeArrayFieldStart("IndexerConf");
				for (final CP cp : PushConfig.CONFIGURATION_PARAMETERS) {
					jg.writeStartObject();

					jg.writeStringField("name", cp.name);
					jg.writeStringField("label", cp.label);
					jg.writeStringField("description", cp.description);
					jg.writeStringField("type", cp.type.name());
					final boolean isEncrypted = CP.isEncrypted(cp);
					final boolean isMandatory = CP.isMandatory(cp);
					final boolean isMultivalue = CP.isMultivalue(cp);
					jg.writeBooleanField("isEncrypted", isEncrypted);
					jg.writeBooleanField("isMandatory", isMandatory);
					jg.writeBooleanField("isMultivalue", isMultivalue);
					if (cp.permittedValues != null && cp.permittedValues.length > 0) {
						jg.writeArrayFieldStart("permittedValues");
						for (final String pv : cp.permittedValues)
							jg.writeString(pv);
						jg.writeEndArray();
					}

					if (isExistingConnector && pc != null) {
						if (PushConfig.ACL_SUPER_USER_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", pc.aclSuperUserGroup);
						else if (PushConfig.ACL_USERS_AS_GROUPS_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", Boolean.toString(pc.aclUsersAsGroups));
						else if (PushConfig.AUTH_METHOD_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", pc.authMethod.name());
						else if (PushConfig.CONSTANT_META_TAGS.equals(cp.name)) {
							jg.writeArrayFieldStart("values");
							for (final Metadata md : pc.constants)
								jg.writeString(md.name + "=" + md.value);
							jg.writeEndArray();
						} else if (PushConfig.META_TAGS_RENAMING.equals(cp.name)) {
							jg.writeArrayFieldStart("values");
							for (final Metadata md : pc.metaRenaming)
								jg.writeString(md.name + "=" + md.value);
							jg.writeEndArray();
						} else if (PushConfig.FEED_DATES_FORMAT_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", pc.feedDatesFormat.toPattern());
						else if (PushConfig.FEEDS_CONSERVATION_PERIOD_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", Integer.toString(pc.feedsConservationPeriod));
						else if (PushConfig.MAX_CONTENT_SIZE_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", Long.toString(pc.maxContentSize / 1_000_000L));
						else if (PushConfig.MAX_FEED_SIZE_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", Long.toString(pc.maxFeedSize / 1_000_000L));
						else if (PushConfig.MAX_INDEXING_THREADS.equals(cp.name)) jg.writeStringField("value", Integer.toString(pc.maxIndexingThreads));
						else if (PushConfig.SO_TIMEOUT_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", Long.toString(TimeUnit.MILLISECONDS.toSeconds(pc.httpClientTimeout)));
						else if (PushConfig.UNSUPPORTED_MIMES.equals(cp.name)) {
							jg.writeArrayFieldStart("values");
							for (final String mt : pc.unsupportedMimeTypes)
								jg.writeString(mt);
							jg.writeEndArray();
						} else if (PushConfig.JAVA_OPTIONS.equals(cp.name)) {
							jg.writeArrayFieldStart("values");
							for (final String jo : pc.javaOptions)
								jg.writeString(jo);
							jg.writeEndArray();
						}else if (PushConfig.ACL_PUBLIC_GROUPS.equals(cp.name)) {
							jg.writeArrayFieldStart("values");
							for (final String jo : pc.publicGroups)
								jg.writeString(jo);
							jg.writeEndArray();

						} 

						else if (PushConfig.AUTH_METHOD_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", PushConfig.DEFAULT_AUTH_MTHD.name());
						else if (PushConfig.FEED_DATES_FORMAT_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", PushConfig.DEFAULT_FEED_DATES_FORMAT);
						else if (PushConfig.FEEDS_CONSERVATION_PERIOD_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", Integer.toString(PushConfig.DEFAULT_CONS_PERIOD));
						else if (PushConfig.MAX_CONTENT_SIZE_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", Long.toString(PushConfig.DEFAULT_MAX_CONTENT_SIZE / 1_000_000L));
						else if (PushConfig.MAX_FEED_SIZE_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", Long.toString(PushConfig.DEFAULT_MAX_FEED_SIZE / 1_000_000L));
						else if (PushConfig.MAX_INDEXING_THREADS.equals(cp.name)) jg.writeStringField("value", Integer.toString(PushConfig.DEFAULT_MAX_THREADS));
						else if (PushConfig.SO_TIMEOUT_PARAM_NAME.equals(cp.name)) jg.writeStringField("value", Long.toString(TimeUnit.MILLISECONDS.toSeconds(PushConfig.DEFAULT_TIMEOUT)));
						else if (PushConfig.ACL_PUBLIC_GROUPS.equals(cp.name)) jg.writeStringField("value", "");
						else if (PushConfig.JAVA_OPTIONS.equals(cp.name)) jg.writeStringField("value", "");
					}
					jg.writeEndObject();
				}
				jg.writeEndArray();
			}

			jg.writeArrayFieldStart("confParams");
			for (final CP cp : iConnectorContext.getConfParams()) {
				jg.writeStartObject();

				jg.writeStringField("name", cp.name);
				jg.writeStringField("label", cp.label);
				jg.writeStringField("description", cp.description);
				jg.writeStringField("type", cp.type.name());
				final boolean isEncrypted = CP.isEncrypted(cp);
				final boolean isMandatory = CP.isMandatory(cp);
				final boolean isMultivalue = CP.isMultivalue(cp);
				jg.writeBooleanField("isEncrypted", isEncrypted);
				jg.writeBooleanField("isMandatory", isMandatory);
				jg.writeBooleanField("isMultivalue", isMultivalue);
				if (cp.permittedValues != null && cp.permittedValues.length > 0) {
					jg.writeArrayFieldStart("permittedValues");
					for (final String pv : cp.permittedValues)
						jg.writeString(pv);
					jg.writeEndArray();
				}

				if (isExistingConnector && c.cpMap.containsKey(cp.name)) {
					final String s = c.cpMap.get(cp.name);
					if (isMultivalue) {
						jg.writeArrayFieldStart("values");
						if (!StringUtils.isNullOrEmpty(s)) for (final String v : CPUtils.stringToList(s))
							jg.writeString(isEncrypted ? Constants.DO_NOT_CHANGE : v);
						jg.writeEndArray();
					} else if (isEncrypted) jg.writeStringField("value", Constants.DO_NOT_CHANGE);
					else if (s != null) jg.writeStringField("value", s);
					else jg.writeNullField("value");
				}

				jg.writeEndObject();
			}
			jg.writeEndArray();
			jg.writeEndObject();
		}
	}

	private static void getConnectorGroupRetrCacheStatus(final HttpServletResponse resp, final GroupCacheManager gcm, final String connectorId) throws IOException {

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");

		final GroupCache groupCache = gcm.getGroupCache(connectorId);
		final boolean isReloading = gcm.getIsReloading(connectorId);

		try (OutputStream os = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8)) {
			jg.setPrettyPrinter(new DefaultPrettyPrinter());
			jg.writeStartObject();
			final long cct = groupCache.creationTime;
			jg.writeBooleanField("IsFailed", groupCache instanceof FailedCache);
			jg.writeBooleanField("IsLoading", isReloading);
			jg.writeBooleanField("HasNoCache", groupCache instanceof NullCache);

			String cacheInfo = "";
			try { cacheInfo = groupCache.getCacheInfo(); }
			catch (final Exception e) { cacheInfo = "Failed to get cache info: " + e.toString(); }
			jg.writeStringField("info", cacheInfo);

			if (cct > 0) jg.writeStringField("CacheAge", DateUtils.toReadableTimeSpan(System.currentTimeMillis() - cct));
			else jg.writeStringField("CacheAge", "N/A");

			jg.writeEndObject();
		}
	}

	private static void getIndexerStatus(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IConnectorContext cc) throws IOException {

		final boolean isStarted = Monitor.answersPing(cc, c.uniqueId, LOG);

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");

		try (OutputStream jsonOs = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(jsonOs, JsonEncoding.UTF8)) {
			jg.setPrettyPrinter(new DefaultPrettyPrinter());
			jg.writeStartObject();

			jg.writeBooleanField("IsStarted", isStarted);

			if (isStarted) {
				jg.writeBooleanField("IsConnected", true);
				jg.writeBooleanField("IsDBBrowsingMode", Monitor.isDBBrowsingMode(cc, c.uniqueId));
			} else jg.writeBooleanField("IsConnected", false);

			jg.writeEndObject();
		}
	}

	private static void getIndexerStats(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IConnectorContext cc) throws IOException {
		if (Monitor.answersPing(cc, c.uniqueId, LOG)) {
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");
			resp.addHeader("Cache-Control", "no-cache");
			try (OutputStream os = resp.getOutputStream()) {
				Monitor.getIndexerStats(cc, c.uniqueId, os);
				os.flush();
			}
		} else jsonError(resp, HttpServletResponse.SC_NOT_FOUND, "Connector is not running");
	}

	private static void getIndexerLastKnownStats(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c) throws IOException {

		final IConnectorContext cc = scsConf.scsCtx.getConnectorCtx(c);
		final SerStats ss = Monitor.reloadStats(cc, c.uniqueId);

		if (ss == null) jsonError(resp, HttpServletResponse.SC_NOT_FOUND, "No stats backup found");
		else {
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");

			resp.addHeader("Cache-Control", "no-cache");

			final Statistics stats = new Statistics(ss);

			try (OutputStream os = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8)) {
				jg.writeStartObject();
				stats.outputStats(jg, false);
				jg.writeEndObject();
				jg.flush();
			}
		}

	}

	private static void getIndexerLastKnownHistory(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c) throws IOException {
		final IConnectorContext cc = scsConf.scsCtx.getConnectorCtx(c);
		final List<String> lkh = Monitor.reloadHistory(cc, c.uniqueId);
		if (lkh == null) jsonError(resp, HttpServletResponse.SC_NOT_FOUND, "No history found");
		else {
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");
			resp.addHeader("Cache-Control", "no-cache");
			try (OutputStream os = resp.getOutputStream()) {
				History.outputHistory(lkh, os);
			}
		}
	}

	private static void getIndexerHistory(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IConnectorContext cc) throws IOException {
		if (Monitor.answersPing(cc, c.uniqueId, LOG)) {
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");
			resp.addHeader("Cache-Control", "no-cache");
			try (OutputStream os = resp.getOutputStream()) {
				Monitor.getIndexerHistory(cc, c.uniqueId, os);
				os.flush();
			}
		} else jsonError(resp, HttpServletResponse.SC_NOT_FOUND, "Connector is not running");
	}

	private static void getChildNodes(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IConnectorContext cc, final String parentNodeId, final int pageSize, final int page, final String states) throws IOException {
		if (Monitor.answersPing(cc, c.uniqueId, LOG)) {
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");
			resp.addHeader("Cache-Control", "no-cache");
			try (OutputStream os = resp.getOutputStream()) {
				Monitor.getDBChildNodes(cc, c.uniqueId, os, parentNodeId, pageSize, page, states);
				os.flush();
			}
		} else jsonError(resp, HttpServletResponse.SC_NOT_FOUND, "Connector is not running");
	}

	private static void reloadNodeState(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IConnectorContext cc, final String nodeId, final boolean isDir, final boolean exclude) throws IOException {
		if (Monitor.answersPing(cc, c.uniqueId, LOG)) {
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");
			resp.addHeader("Cache-Control", "no-cache");
			try (OutputStream os = resp.getOutputStream()) {
				Monitor.reloadDBNodeState(cc, c.uniqueId, os, nodeId, isDir, exclude);
			}
		} else jsonError(resp, HttpServletResponse.SC_NOT_FOUND, "Connector is not running");
	}

	private static void findChild(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IConnectorContext cc, final String parentNodeId, final String nodeId) throws IOException {
		if (Monitor.answersPing(cc, c.uniqueId, LOG)) {
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");
			resp.addHeader("Cache-Control", "no-cache");
			try (OutputStream os = resp.getOutputStream()) {
				Monitor.findDBNode(cc, c.uniqueId, os, parentNodeId, nodeId);
			}
		} else jsonError(resp, HttpServletResponse.SC_NOT_FOUND, "Connector is not running");
	}

	private static void postAuthentConfig(final HttpServletRequest req, final HttpServletResponse resp, final ServletContext sc, final SCSConfiguration conf) throws IOException, JsonParseException, SAXException, ParserConfigurationException, TransformerConfigurationException, TransformerFactoryConfigurationError, TransformerException, XPathExpressionException {
		LOG.info("Received an authent config submit request.");

		String n = null, type = null, trustDuration = null, entityId = null, cmMainConnectorName = null;
		boolean kerberosAuthN = false;
		boolean groupRetrieval = false;
		try (InputStream is = req.getInputStream(); JsonParser jp = new JsonFactory().createParser(is)) {
			JsonToken tok = null;
			while ((tok = jp.nextToken()) != null)
				if (tok != JsonToken.FIELD_NAME) if (tok == JsonToken.START_ARRAY) while ((tok = jp.nextToken()) != JsonToken.END_ARRAY);
				else if (tok == JsonToken.VALUE_FALSE || tok == JsonToken.VALUE_TRUE) {
					n = jp.getCurrentName();
					if ("kerberosAuthN".equals(n)) kerberosAuthN = jp.getBooleanValue();
					else if ("groupRetrieval".equals(n)) groupRetrieval = jp.getBooleanValue();
				} else if (tok == JsonToken.VALUE_STRING) {
					n = jp.getCurrentName();
					if ("type".equals(n)) type = jp.getValueAsString();
					else if ("trustDuration".equals(n)) trustDuration = jp.getValueAsString();
					else if ("entityId".equals(n)) entityId = jp.getValueAsString();
					else if ("cmMainConnectorName".equals(n)) cmMainConnectorName = jp.getValueAsString();
				}
		}

		LOG.info(String.format("Config received: type=%s ; trustDuration=%s ; entityId=%s ; kerberosAuthN=%b ; groupRetrieval=%b", type, trustDuration, entityId, kerberosAuthN, groupRetrieval));

		final Path confFile = conf.scsCtx.tomcatRoot.resolve(Constants.REL_PATH_SCS_CONF_FILE);
		SCSConfiguration.updateAuthentConfig(confFile, type, trustDuration, entityId, kerberosAuthN, groupRetrieval, cmMainConnectorName, conf.authnConf.authenticator == null ? null : ((AConnector) conf.authnConf.authenticator).uniqueId);

		resp.sendRedirect("/SCS/secure/manager.html?status=updated");
	}

	private boolean postftpInfo(HttpServletRequest req, HttpServletResponse resp, SCSConfiguration conf) {
		final String ftpHost = req.getParameter("FTPHost");
		final String ftpUsername = req.getParameter("FTPUsername");
		final String ftpPassword = req.getParameter("FTPPassword");
		final String mode = req.getParameter("mode");
		if ("confirm".equals(mode)) {
			try {
				final Path confFile = conf.scsCtx.tomcatRoot.resolve(Constants.REL_PATH_SCS_CONF_FILE);
				SCSConfiguration.updateFTP(confFile, ftpHost, ftpUsername, ftpPassword);
				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
				resp.setContentType("text/plain");
				resp.setCharacterEncoding("UTF-8");
				resp.setContentLength(0);
				clearSession(req);
				return true;
			} catch(final Exception e1){

			}
		}
		return false;
	}

	private static boolean postGsaHostname(final HttpServletRequest req, final HttpServletResponse resp, final SCSConfiguration conf) throws IOException, TransformerFactoryConfigurationError {
		final String mode = req.getParameter("mode");
		final String sslStr = req.getParameter("ssl");
		final boolean ssl = "true".equals(sslStr);
		final String defaultGsaHost = req.getParameter("defaultgsahost");
		String adminGsaHost = req.getParameter("admingsahost");
		if (StringUtils.isNullOrEmpty(adminGsaHost)) adminGsaHost = defaultGsaHost;
		GSAInfo partialGi = new GSAInfo(ssl, defaultGsaHost, adminGsaHost, "", "", "", false);
		if (StringUtils.isNullOrEmpty(defaultGsaHost)) jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid GSA hostname");
		else if ("probe".equals(mode)) {
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");
			resp.addHeader("Cache-Control", "no-cache");
			try (OutputStream os = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8)) {
				try {
					final Path trustStore = conf.scsCtx.tomcatRoot.resolve(Constants.REL_PATH_CACERTS);
					final GsaManager gm = new GsaManager(trustStore);
					final GSAInfo gi = gm.getGsaInfo(ssl, defaultGsaHost, adminGsaHost);
					if (gi == null) throw new IllegalStateException("Unable to retrieve GSA information");
					else {
						jg.writeStartObject();
						jg.writeNumberField("status", 0);
						jg.writeStringField("DefaultGsaHost", defaultGsaHost);
						jg.writeStringField("AdminGsaHost", adminGsaHost);
						jg.writeBooleanField("ssl", ssl);
						GSAInfo.toJson(gi, jg);
						jg.writeEndObject();
					}
				} catch (final SSLHandshakeException e1) {
					try {
						final List<X509Certificate> certs = GsaManager.getCertificates(partialGi);
						jg.writeStartObject();
						jg.writeNumberField("status", 1);
						jg.writeStringField("DefaultGsaHost", defaultGsaHost);
						jg.writeStringField("AdminGsaHost", adminGsaHost);
						jg.writeBooleanField("ssl", true);
						jg.writeArrayFieldStart("certs");
						final int numCerts = certs.size();
						for (int i = 0; i < numCerts; i++) {
							final X509Certificate c = certs.get(i);
							jg.writeStartObject();
							jg.writeStringField("cn", ReloadableTrustManager.extractCnFromCert(c));
							jg.writeStringField("issued_to", ReloadableTrustManager.principalToHTML(c.getSubjectX500Principal()));
							final String hexSerial = HexUtils.toHexString(c.getSerialNumber().toByteArray());
							jg.writeStringField("raw_sn", hexSerial);
							jg.writeStringField("sn", hexToBreakableHtml(hexSerial));
							jg.writeStringField("issued_by", ReloadableTrustManager.principalToHTML(c.getIssuerDN()));
							jg.writeStringField("valnb", c.getNotBefore().toString());
							jg.writeStringField("valna", c.getNotAfter().toString());
							jg.writeStringField("fingerprint_sha256", hexToBreakableHtml(HexUtils.toHexString(new Digester(HashAlgorithm.SHA_256).digest(c.getEncoded()))));
							jg.writeStringField("fingerprint_sha1", hexToBreakableHtml(HexUtils.toHexString(new Digester(HashAlgorithm.SHA_1).digest(c.getEncoded()))));
							jg.writeNumberField("version", c.getVersion());
							jg.writeStringField("pubkey", hexToBreakableHtml(HexUtils.toHexString(c.getPublicKey().getEncoded())));
							jg.writeStringField("sigalg", c.getSigAlgName());
							jg.writeStringField("signature", hexToBreakableHtml(HexUtils.toHexString(c.getSignature())));
							jg.writeEndObject();
						}
						jg.writeEndArray();
						jg.writeEndObject();
					} catch (final Exception e2) {
						LOG.error("error", e2);
						jg.writeStartObject();
						jg.writeNumberField("status", -1);
						jg.writeStringField("DefaultGsaHost", defaultGsaHost);
						jg.writeStringField("AdminGsaHost", adminGsaHost);
						jg.writeBooleanField("ssl", true);
						jg.writeStringField("error_type", e2.getClass().getName());
						jg.writeStringField("error_message", e2.getMessage());
						jg.writeEndObject();
					}
				} catch (final Exception e) {
					LOG.error("error", e);
					jg.writeStartObject();
					jg.writeNumberField("status", -1);
					jg.writeStringField("DefaultGsaHost", defaultGsaHost);
					jg.writeStringField("AdminGsaHost", adminGsaHost);
					jg.writeBooleanField("ssl", ssl);
					jg.writeStringField("error_type", e.getClass().getName());
					jg.writeStringField("error_message", e.getMessage());
					jg.writeEndObject();
				}
			}
		} else if ("trustcert".equals(mode)) {
			final String certSN = req.getParameter("sn");
			if (StringUtils.isNullOrEmpty(certSN)) jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid certificate serial number");
			else try {
				final List<X509Certificate> certs = GsaManager.getCertificates(partialGi);
				X509Certificate cert = null;
				for (final X509Certificate c : certs)
					if (certSN.equals(HexUtils.toHexString(c.getSerialNumber().toByteArray()))) {
						cert = c;
						break;
					}
				if (cert == null) jsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Certificate not found");
				else {
					final Path trustStore = conf.scsCtx.tomcatRoot.resolve(Constants.REL_PATH_CACERTS);
					new GsaManager(trustStore).trustCertificate(cert);
					resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
					resp.setContentType("text/plain");
					resp.setCharacterEncoding("UTF-8");
					resp.setContentLength(0);
				}
			} catch (final Exception e) {
				jsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to trust certificate", e);
			}

		} else if ("confirm".equals(mode)) try {
			final Path trustStore = conf.scsCtx.tomcatRoot.resolve(Constants.REL_PATH_CACERTS);
			final GsaManager gm = new GsaManager(trustStore);
			final GSAInfo gi = gm.getGsaInfo(ssl, defaultGsaHost, adminGsaHost);
			if (gi == null) throw new IllegalStateException("Failed to retrieve information from GSA " + defaultGsaHost + "/" + adminGsaHost);
			else {
				final Path confFile = conf.scsCtx.tomcatRoot.resolve(Constants.REL_PATH_SCS_CONF_FILE);
				SCSConfiguration.updateGsa(confFile, gi);
				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
				resp.setContentType("text/plain");
				resp.setCharacterEncoding("UTF-8");
				resp.setContentLength(0);
				clearSession(req);
				return true;
			}

		} catch (final Exception e) {
			jsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to register GSA", e);
		}
		else if ("delete".equals(mode)) try {
			final Path confFile = conf.scsCtx.tomcatRoot.resolve(Constants.REL_PATH_SCS_CONF_FILE);
			SCSConfiguration.updateGsa(confFile, null);
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			resp.setContentType("text/plain");
			resp.setCharacterEncoding("UTF-8");
			resp.setContentLength(0);
			return true;
		} catch (final Exception e) {
			jsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to register GSA", e);
		}
		else jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown mode");

		return false;
	}

	private void grantFeederGateAccess(final HttpServletRequest req, final HttpServletResponse resp, final SCSConfiguration conf) throws Exception {
		if (gc == null) {
			throw new IllegalStateException("No GSA client available");
		} else {
			FeedergateAccessManager fgam = new FeedergateAccessManager(gc);
			fgam.addTrustedIPAddresses(req.getLocalAddr());
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			resp.setContentType("text/plain");
			resp.setContentLength(0);
		}
	}

	private void registerConnectorManager(final HttpServletRequest req, final HttpServletResponse resp, final SCSConfiguration conf) throws SecurityException, IllegalArgumentException, MalformedURLException, ServiceException, IOException, ReflectiveOperationException {
		if (gc == null) {
			throw new IllegalStateException("No GSA client available");
		} else {
			ConnectorManagers cmApp = new ConnectorManagers(gc);
			List<ConnectorManager> cms = cmApp.getConnectorManagers();
			final String cmUrl = buildScsBaseURL(httpDefaultHost, conf.gsa.ssl) + "/cm";
			boolean found = false;
			for (ConnectorManager cm : cms) {
				if (cm.entryID.equals("scs-cm")) {
					found = true;
					break;
				}
			}
			if (found) {
				int i = 2;
				while (found) {
					found = false;
					for (ConnectorManager cm : cms) {
						if (cm.entryID.equals("scs-cm-" + i)) {
							found = true;
							break;
						}
					}
					if (found) i++;
				}
				cmApp.addConnectorManager(new ConnectorManager("scs-cm-" + i, null, "Sword SCS on " + req.getHeader("Host"), cmUrl));
			} else {
				cmApp.addConnectorManager(new ConnectorManager("scs-cm", null, "Sword SCS on " + req.getHeader("Host"), cmUrl));
			}
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			resp.setContentType("text/plain");
			resp.setContentLength(0);
		}
	}

	private void addConnectorPatterns(final HttpServletRequest req, final HttpServletResponse resp, final SCSConfiguration conf) throws SecurityException, IllegalArgumentException, MalformedURLException, ServiceException, IOException, ReflectiveOperationException {
		if (gc == null) {
			throw new IllegalStateException("No GSA client available");
		} else {
			CrawlPatternsUpdater cpu = new CrawlPatternsUpdater(gc);
			PatternDef pd = cpu.getCrawlPatterns();
			if (!pd.follow.contains(URLManager.CONNECTORS_PATTERN)) {
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				try (
					Scanner sc = new Scanner(new ByteArrayInputStream(pd.follow.getBytes(StandardCharsets.UTF_8))); 
					PrintStream ps = new PrintStream(os, false, StandardCharsets.UTF_8.name())
					) {
					ps.println("# Sword SCS indexers URL patterns - start");
					ps.println(URLManager.CONNECTORS_PATTERN);
					ps.println("# Sword SCS indexers URL patterns - end");
					while (sc.hasNextLine()) ps.println(sc.nextLine());
				}
				PatternDef newPd = new PatternDef(pd.start, new String(os.toByteArray(), StandardCharsets.UTF_8), pd.doNotFollow);
				cpu.replacePatterns(newPd);
			}
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			resp.setContentType("text/plain");
			resp.setContentLength(0);
		}
	}


	public static String hexToBreakableHtml(final String hex) {
		final StringBuilder sb = new StringBuilder("<div>");
		final char[] chars = hex.toCharArray();
		final int l = chars.length;
		for (int i = 1; i <= l; i++) {
			sb.append(chars[i - 1]);
			if (i % 32 == 0) {
				sb.append("</div>");
				if (i != l) sb.append("<div>");
			} else if (i % 2 == 0 && i != l) sb.append(' ');
		}
		if (l % 32 != 0) sb.append("</div>");
		else if (l == 0) sb.append("</div>");
		return sb.toString();
	}

	private static void postConnectorConfig(final HttpServletRequest req, final HttpServletResponse resp, final ServletContext sc, final SCSConfiguration conf, final PwdManager pwdMgr) throws IOException, ServletException, XPathExpressionException, SAXException, ParserConfigurationException, IllegalBlockSizeException, BadPaddingException, TransformerConfigurationException, TransformerException, TransformerFactoryConfigurationError {
		LOG.info("Received a config submit request.");

		final String ce = req.getCharacterEncoding();
		LOG.info("Request charset: " + ce);
		final Charset cs = HttpUtils.getRequestCharset(req);

		final String connectorClass = HttpUtils.partToString(cs, req.getPart("class"));
		final IConnectorContext cc = conf.scsCtx.getConnectorCtx(connectorClass);

		final Path confFile = conf.scsCtx.tomcatRoot.resolve(Constants.REL_PATH_SCS_CONF_FILE);
		final String cid = HttpUtils.partToString(cs, req.getPart("cid"));
		String mid = HttpUtils.partToString(cs, req.getPart("nameTransformer"));
		if ("notaconnector".equals(mid)) mid = null;
		final String ns = HttpUtils.partToString(cs, req.getPart("ns"));

		{
			final boolean useForAuthN = "on".equals(HttpUtils.partToString(cs, req.getPart("UseForAuthN")));
			LOG.info("Use for authN: " + useForAuthN);
			final String curAuthnString = conf.authnConf.authenticator == null ? null : ((AConnector) conf.authnConf.authenticator).uniqueId;
			final String newAuthnString = useForAuthN ? cid : curAuthnString;
			if (!StringUtils.npeProofEquals(curAuthnString, newAuthnString)) {
				SCSConfiguration.updateAuthentConfig(confFile, conf.authnConf.samlMode.name(), Long.toString(TimeUnit.MILLISECONDS.toMinutes(conf.authnConf.trustDuration)), conf.authnConf.samlEntityId, conf.authnConf.kerberosAuthN,
					conf.authnConf.groupRetrieval, conf.authnConf.cmMainConnectorName, newAuthnString);
				LOG.info("Updating authent config.");
			}

		}

		{
			final boolean useForGR = "on".equals(HttpUtils.partToString(cs, req.getPart("UseForGR")));
			LOG.info("Use for Group Retrieval: " + useForGR);
			if (useForGR) {
				final String criStr = HttpUtils.partToString(cs, req.getPart(ICachableGroupRetriever.CP_CACHE_REFRESH_INTERVAL.name));
				final long cri = StringUtils.isInteger(criStr) ? TimeUnit.MINUTES.toMillis(Integer.parseInt(criStr)) : GroupRetrieverConf.DEFAULT_CRI;
				SCSConfiguration.addOrUpdateGroupRetriever(confFile, cid, cri);
				LOG.info("Updating group retrievers config.");
			} else SCSConfiguration.removeGroupRetriever(confFile, cid);
		}

		{
			final boolean useForPush = "on".equals(HttpUtils.partToString(cs, req.getPart("UseForPush")));
			LOG.info("Use for Indexing: " + useForPush);

			if (useForPush) {

				final String feedsConservationPeriodStr = HttpUtils.partToString(cs, req.getPart(PushConfig.FEEDS_CONSERVATION_PERIOD_PARAM_NAME));
				int feedsConservationPeriod = PushConfig.DEFAULT_CONS_PERIOD;
				if (StringUtils.isInteger(feedsConservationPeriodStr)) feedsConservationPeriod = Integer.parseInt(feedsConservationPeriodStr);

				final String maxFeedSizeStr = HttpUtils.partToString(cs, req.getPart(PushConfig.MAX_FEED_SIZE_PARAM_NAME));
				long maxFeedSize = PushConfig.DEFAULT_MAX_FEED_SIZE;
				if (StringUtils.isInteger(maxFeedSizeStr)) maxFeedSize = Long.parseLong(maxFeedSizeStr) * 1_000_000L;

				final String maxContentSizeStr = HttpUtils.partToString(cs, req.getPart(PushConfig.MAX_CONTENT_SIZE_PARAM_NAME));
				long maxContentSize = PushConfig.DEFAULT_MAX_CONTENT_SIZE;
				if (StringUtils.isInteger(maxContentSizeStr)) maxContentSize = Long.parseLong(maxContentSizeStr) * 1_000_000L;

				final String feedDatesFormatStr = HttpUtils.partToString(cs, req.getPart(PushConfig.FEED_DATES_FORMAT_PARAM_NAME));
				SimpleDateFormat feedDatesFormat = new SimpleDateFormat(PushConfig.DEFAULT_FEED_DATES_FORMAT);
				try {
					feedDatesFormat = new SimpleDateFormat(feedDatesFormatStr);
				} catch (NullPointerException | IllegalArgumentException e) {
					feedDatesFormat = new SimpleDateFormat(PushConfig.DEFAULT_FEED_DATES_FORMAT);
				}

				final String maxIndexingThreadsStr = HttpUtils.partToString(cs, req.getPart(PushConfig.MAX_INDEXING_THREADS));
				int maxIndexingThreads = PushConfig.DEFAULT_MAX_THREADS;
				if (StringUtils.isInteger(maxIndexingThreadsStr)) maxIndexingThreads = Integer.parseInt(maxIndexingThreadsStr);

				final String unsupportedMimeTypesStr = HttpUtils.partToString(cs, req.getPart(PushConfig.UNSUPPORTED_MIMES));
				final List<String> unsupportedMimeTypes = new ArrayList<>();
				if (!StringUtils.isNullOrEmpty(unsupportedMimeTypesStr)) unsupportedMimeTypes.addAll(CPUtils.stringToList(unsupportedMimeTypesStr));

				final String constantsStr = HttpUtils.partToString(cs, req.getPart(PushConfig.CONSTANT_META_TAGS));
				final List<String> constants = new ArrayList<>();
				if (!StringUtils.isNullOrEmpty(constantsStr)) constants.addAll(CPUtils.stringToList(constantsStr));

				final String metaRenamingStr = HttpUtils.partToString(cs, req.getPart(PushConfig.META_TAGS_RENAMING));
				final List<String> metaRenaming = new ArrayList<>();
				if (!StringUtils.isNullOrEmpty(metaRenamingStr)) metaRenaming.addAll(CPUtils.stringToList(metaRenamingStr));

				final String httpClientTimeoutStr = HttpUtils.partToString(cs, req.getPart(PushConfig.SO_TIMEOUT_PARAM_NAME));
				long httpClientTimeout = PushConfig.DEFAULT_TIMEOUT;
				if (StringUtils.isInteger(httpClientTimeoutStr)) httpClientTimeout = TimeUnit.SECONDS.toMillis(Long.parseLong(httpClientTimeoutStr));

				final String aclSuperUserGroup = HttpUtils.partToString(cs, req.getPart(PushConfig.ACL_SUPER_USER_PARAM_NAME));

				final String aclUsersAsGroupsStr = HttpUtils.partToString(cs, req.getPart(PushConfig.ACL_USERS_AS_GROUPS_PARAM_NAME));
				final boolean aclUsersAsGroups = "on".equals(aclUsersAsGroupsStr);

				final String authMethodStr = HttpUtils.partToString(cs, req.getPart(PushConfig.AUTH_METHOD_PARAM_NAME));
				Authmethod authMethod = Authmethod.httpsso;
				if (!StringUtils.isNullOrEmpty(authMethodStr)) authMethod = Authmethod.resolve(authMethodStr);

				final String publicGroups = HttpUtils.partToString(cs, req.getPart(PushConfig.ACL_PUBLIC_GROUPS));
				final String javaOpts = HttpUtils.partToString(cs, req.getPart(PushConfig.JAVA_OPTIONS));

				final PushConfig pc = new PushConfig(cc, cid, false, feedsConservationPeriod, maxFeedSize, maxContentSize, feedDatesFormat, unsupportedMimeTypes, constants, metaRenaming, httpClientTimeout, maxIndexingThreads, authMethod, ns, aclSuperUserGroup, aclUsersAsGroups, conf.gsa.ssl, conf.gsa.defaultHost, conf.ftpMode, conf.ftpUsername, conf.ftpPassword, cid, PushType.UPDATE, -1, CPUtils.stringToList(javaOpts),CPUtils.stringToList(publicGroups));
				PushConfig.saveAllConfig(cc.getIndexerConfFile(cid).toFile(), pc);
				LOG.info("Updating indexer config.");

				final String intervalStr = HttpUtils.partToString(cs, req.getPart("interval"));
				long interval = TimeUnit.HOURS.toMillis(3);
				if (!StringUtils.isNullOrEmpty(intervalStr)) try {
					interval = TimeUnit.HOURS.toMillis(Integer.parseInt(intervalStr));
				} catch (final NumberFormatException nfe) {
					interval = TimeUnit.HOURS.toMillis(3);
				}

				final String throughputStr = HttpUtils.partToString(cs, req.getPart("throughput"));
				long throughput = -1L;
				if (!StringUtils.isNullOrEmpty(throughputStr)) try {
					throughput = Integer.parseInt(throughputStr) * 1_000L;// transform kB/S to B/S
				} catch (final NumberFormatException nfe) {
					throughput = -1L;
				}

				Schedule schedule = null;
				final String scheduleJson = HttpUtils.partToString(cs, req.getPart("schedule"));
				if (!StringUtils.isNullOrEmpty(scheduleJson)) {
					final List<Period> periods = parseIndexerSchedule(scheduleJson);
					if (!periods.isEmpty()) {
						schedule = new Schedule();
						schedule.periods.addAll(periods);
					}
				}

				final IndexerConf indexer = new IndexerConf(cid, interval, throughput, schedule);
				SCSConfiguration.addOrUpdateIndexer(confFile, indexer);
			} else SCSConfiguration.removeIndexer(confFile, cid);
		}

		LOG.info(String.format("Connector: ID: %s ; Name Transformer ID: %s ; Namespace: %s", cid, mid, ns));

		final Map<CP, Part> confParams = new HashMap<>();
		for (final CP cp : cc.getConfParams())
			confParams.put(cp, req.getPart(cp.name));

		SCSConfiguration.updateConnectorConfig(cs, pwdMgr, confFile, cc, cid, ns, mid, confParams);

		resp.addCookie(new Cookie("scstab", "CONF_" + cid));
		resp.sendRedirect("/SCS/secure/manager.html?status=updated");

	}

	private static List<Period> parseIndexerSchedule(final String scheduleJson) throws IOException, JsonParseException {
		final List<Period> periods = new ArrayList<>();

		try (JsonParser jp = new JsonFactory().createParser(scheduleJson)) {

			boolean running = false;
			JsonToken tok = null;
			int day = -2;
			int start = -2;
			int duration = -2;
			String n;
			while ((tok = jp.nextToken()) != null)
				if (tok == JsonToken.START_ARRAY) running = true;
				else if (tok == JsonToken.END_ARRAY) running = false;
				else if (tok == JsonToken.START_OBJECT) {
					day = -2;
					start = -2;
					duration = -2;
				} else if (tok == JsonToken.END_OBJECT) {
					if (running) if (!(day == -2 || start == -2 || duration == -2L)) periods.add(new Period(day, start, duration));
				} else if (tok == JsonToken.VALUE_NUMBER_INT) {
					n = jp.getCurrentName();
					if ("day".equals(n)) day = jp.getValueAsInt();
					else if ("start".equals(n)) start = jp.getValueAsInt();
					else if ("duration".equals(n)) duration = jp.getValueAsInt();
				}
		}
		return periods;
	}

	private static void postIndexerStartReq(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IndexingProcMgr ipm) throws IOException, InterruptedException {
		IndexerConf indexerConf = null;
		for (final IndexerConf ic : scsConf.indexers)
			if (ic.connectorId.equals(c.uniqueId)) indexerConf = ic;

		if (indexerConf == null) {
			jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid connector ID: " + c.uniqueId);
			return;
		} else {
			final long startRequestTime = System.currentTimeMillis();
			final IndexingProcWatcher ipw = ipm.start(indexerConf, false);
			final boolean hasStarted = IndexingProcMgr.waitStart(ipw, indexerConf, startRequestTime, TimeUnit.SECONDS.toMillis(30));
			if (hasStarted) {
				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
				resp.setContentType("text/plain");
				resp.setContentLength(0);
			} else jsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to start process");
		}
	}

	private static void postDBBrowsingStartRequest(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IndexingProcMgr ipm) throws IOException, InterruptedException {
		IndexerConf indexerConf = null;
		for (final IndexerConf ic : scsConf.indexers)
			if (ic.connectorId.equals(c.uniqueId)) indexerConf = ic;

		if (indexerConf == null) {
			jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid connector ID: " + c.uniqueId);
			return;
		} else {
			final long startRequestTime = System.currentTimeMillis();
			final IndexingProcWatcher ipw = ipm.start(indexerConf, true);
			final boolean hasStarted = IndexingProcMgr.waitStart(ipw, indexerConf, startRequestTime, TimeUnit.SECONDS.toMillis(30));
			if (hasStarted) {
				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
				resp.setContentType("text/plain");
				resp.setContentLength(0);
			} else jsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to start process");
		}
	}

	private static void postIndexerStopReq(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IndexingProcMgr ipm) throws IOException {
		IndexerConf ic = null;
		for (final IndexerConf _ic : scsConf.indexers)
			if (_ic.connectorId.equals(c.uniqueId)) {
				ic = _ic;
				break;
			}

		if (ic == null) ic = new IndexerConf(c.uniqueId, TimeUnit.HOURS.toMillis(3), -1, null);

		ipm.stop(ic);
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		resp.setContentType("text/plain");
		resp.setContentLength(0);
	}

	private static void postIndexerResetReq(HttpServletRequest req, final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IndexingProcMgr ipm, GsaClientProxy gc, NonGdataHttpAdmin gHttpc) throws IOException {

		IndexerConf ic = null;
		for (final IndexerConf _ic : scsConf.indexers)
			if (_ic.connectorId.equals(c.uniqueId)) {
				ic = _ic;
				break;
			}

		if (ic == null) ic = new IndexerConf(c.uniqueId, TimeUnit.HOURS.toMillis(3), -1, null);

		final String dds = req.getParameter("deleteDatasource");

		try {
			ipm.reset(ic);
			if ((gc != null) && Boolean.parseBoolean(dds)) {
				try (DatasourceDeleter dsd = new DatasourceDeleter(gHttpc)) {
					dsd.deleteDatasource(c.uniqueId);
				}
			}
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			resp.setContentType("text/plain");
			resp.setContentLength(0);
		} catch (final Exception e) {
			jsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to start process", e);
		}

	}

	private static void postIndexerKillReq(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IndexingProcMgr ipm) throws InterruptedException {

		IndexerConf ic = null;
		for (final IndexerConf _ic : scsConf.indexers)
			if (_ic.connectorId.equals(c.uniqueId)) {
				ic = _ic;
				break;
			}

		if (ic == null) ic = new IndexerConf(c.uniqueId, TimeUnit.HOURS.toMillis(3), -1, null);

		ipm.kill(ic);
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		resp.setContentType("text/plain");
		resp.setContentLength(0);

	}

	private static void postDBBrowsingStopRequest(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IConnectorContext cc, final IndexingProcMgr ipm) throws IOException, InterruptedException {
		IndexerConf ic = null;
		for (final IndexerConf _ic : scsConf.indexers)
			if (_ic.connectorId.equals(c.uniqueId)) {
				ic = _ic;
				break;
			}

		Monitor.stopDBBrowsing(cc, c.uniqueId);
		if (ipm.waitStop(ic, TimeUnit.SECONDS.toMillis(90))) {
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			resp.setContentType("text/plain");
			resp.setContentLength(0);
		} else jsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Process did not complete in time");
	}

	private static void postDBCommitRequest(final HttpServletResponse resp, final SCSConfiguration scsConf, final AConnector c, final IConnectorContext cc) throws IOException {
		Monitor.commitDBBrowsingChanges(cc, c.uniqueId);
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		resp.setContentType("text/plain");
		resp.setContentLength(0);
	}

	private static void writeAuthnConf(final JsonGenerator jg, final SCSConfiguration scsConf) throws JsonGenerationException, IOException {
		final AuthenticatorConf authnConf = scsConf.authnConf;
		jg.writeStringField("type", authnConf.samlMode.name());
		jg.writeStringField("entityId", authnConf.samlEntityId);
		jg.writeNumberField("trustDuration", TimeUnit.MILLISECONDS.toMinutes(authnConf.trustDuration));
		jg.writeBooleanField("kerberosAuthN", authnConf.kerberosAuthN);
		jg.writeBooleanField("groupRetrieval", authnConf.groupRetrieval);
		jg.writeStringField("cmMainConnectorName", authnConf.cmMainConnectorName);

		if (authnConf.authenticator == null) jg.writeNullField("authenticator");
		else jg.writeStringField("authenticator", ((AConnector) authnConf.authenticator).uniqueId);

		jg.writeArrayFieldStart("groupRetrievers");
		for (final GroupRetrieverConf grc : scsConf.groupRetrievers)
			jg.writeString(grc.connector.uniqueId);
		jg.writeEndArray();

	}

	private static void writeAuthzConf(final JsonGenerator jg, final AuthorizerConf authzConf) throws JsonGenerationException, IOException {
		jg.writeNumberField("globalAuthZTimeout", TimeUnit.MILLISECONDS.toSeconds(authzConf.globalAuthZTimeout));
		jg.writeStringField("unknownURLsDefaultAccess", authzConf.unknownURLsDefaultAccess.name());

		jg.writeObjectFieldStart("authorizers");
		for (final SAMLAuthzConf sazc : authzConf.authorizers) {
			jg.writeObjectFieldStart(sazc.authorizer.uniqueId);
			jg.writeStringField("urlPattern", sazc.urlPattern.pattern());
			if (sazc.nameTransformer != null) jg.writeStringField("nameTransformer", sazc.nameTransformer.uniqueId);
			jg.writeEndObject();
		}
		jg.writeEndObject();
	}

	/**
	 * Write the className as the JSON object key so that JavaScript can find it faster
	 */
	private static void writeInstalledConnectors(final JsonGenerator jg, final List<ConnectorContext> installedConnectors) throws JsonGenerationException, IOException {
		for (final ConnectorContext cc : installedConnectors) {
			jg.writeObjectFieldStart(cc.getClassName());
			jg.writeStringField("name", cc.getSpec().name());
			jg.writeStringField("version", cc.getSpec().version());
			jg.writeBooleanField("IsAuthNDelegator", cc.isAuthNDelegator);
			jg.writeBooleanField("IsAuthNFormData", cc.isAuthNFormData);
			jg.writeBooleanField("IsAuthNHeaders", cc.isAuthNHeaders);
			jg.writeBooleanField("IsAuthorizer", cc.isAuthorizer);
			jg.writeBooleanField("IsCachableGroupRetriever", cc.isCachableGroupRetriever);
			jg.writeBooleanField("IsGroupRetriever", cc.isGroupRetriever);
			jg.writeBooleanField("IsNameTransformer", cc.isNameTransformer);
			jg.writeBooleanField("IsIndexer", cc.isIndexer);
			jg.writeEndObject();
		}
	}

	static void jsonError(final HttpServletResponse resp, final int httpStatusCode, final String message) throws IOException {
		resp.setStatus(httpStatusCode);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		resp.addHeader("Cache-Control", "no-cache");
		try (OutputStream os = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8)) {
			jg.writeStartObject();
			jg.writeStringField("ERROR", message);
			jg.writeEndObject();
		}

	}

	static void jsonError(final HttpServletResponse resp, final int httpStatusCode, final String message, final Throwable t) throws IOException {
		resp.setStatus(httpStatusCode);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		resp.addHeader("Cache-Control", "no-cache");
		try (OutputStream os = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8)) {
			jg.writeStartObject();
			jg.writeStringField("ERROR", message);
			if (t instanceof IllegalStateException) jg.writeStringField("ERROR_STACK", t.getMessage());
			else jg.writeStringField("ERROR_STACK", ExceptionDigester.toString(t));
			jg.writeEndObject();
		}

	}

}
