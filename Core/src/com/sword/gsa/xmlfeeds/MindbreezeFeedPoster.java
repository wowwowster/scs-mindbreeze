package com.sword.gsa.xmlfeeds;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.log4j.Logger;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.DocumentType;

import sword.gsa.xmlfeeds.builder.FeedType;
import sword.common.http.client.HttpClientHelper;
import sword.common.utils.files.FileUtils;

public final class MindbreezeFeedPoster implements AutoCloseable {

	private static final File JAR_DIR;
	private static final Logger LOG = Logger.getLogger(MindbreezeFeedPoster.class);
	private static final File XSL;
	private static final boolean TRANSFORM_XMLS;

	private final CloseableHttpClient hc;
	private final String mindbreezeDocFeedServlet;
	private final String mindbreezeGroupFeedServlet;

	static {

		/**
		 * Exemple de valeur : JAR_DIR => D:\Program_Files\SCS\webapps\SCS\WEB-INF\lib
		 */

		JAR_DIR = FileUtils.getJarFile(MindbreezeFeedPoster.class).getParentFile();
		XSL = new File(JAR_DIR, "FeedTransformation.xsl");
		TRANSFORM_XMLS = XSL.exists();
	}

	public MindbreezeFeedPoster() {
		this(!"abcdef".equals(System.getProperty("javax.net.ssl.trustStore", "abcdef")));
	}

	public MindbreezeFeedPoster(boolean ssl) {

		if (ssl) {
			mindbreezeDocFeedServlet = "https://%s:19902/xmlfeed";
			mindbreezeGroupFeedServlet = "https://%s:19902/xmlgroups";
		} else {
			mindbreezeDocFeedServlet = "http://%s:19900/xmlfeed";
			mindbreezeGroupFeedServlet = "http://%s:19900/xmlgroups";
		}
		final RequestConfig rc = HttpClientHelper.createRequestConfig(false, false, 2, 300_000, 300_000, 300_000);
		hc = HttpClientHelper.getHttpClient(HttpClientHelper.getMultithreadedConnMgr(120, 240), rc);
	}

	public void post(String mindbreezeHostName, String datasourceName, FeedType feedType, File xmlOrFolder,
			boolean deleteFiles, boolean checkBackLog) throws IOException {

		if (xmlOrFolder.isDirectory()) {
			LOG.trace(xmlOrFolder.getAbsolutePath() + " is a folder");
			File[] files = xmlOrFolder.listFiles();
			if (files != null) {
				File[] arr$ = files;
				int len$ = files.length;

				for (int i$ = 0; i$ < len$; ++i$) {
					File file = arr$[i$];
					if (file != null && file.getAbsolutePath().endsWith(".xml")) {
						File f2send = getTransformedXML(file);
						try {
							oneFilePost(f2send, datasourceName, feedType, mindbreezeHostName, deleteFiles, checkBackLog);
						} catch (InterruptedException | HttpException e) {
							throw new IOException(e.getCause());
						}
					}
				}
			}
		} else {
			LOG.trace(xmlOrFolder.getAbsolutePath() + " is a file");
			File f2send = getTransformedXML(xmlOrFolder);
			try {
				oneFilePost(f2send, datasourceName, feedType, mindbreezeHostName, deleteFiles, checkBackLog);
			} catch (InterruptedException | HttpException e) {
				throw new IOException(e.getCause());
			}
		}
	}

	public void oneFilePost(final File feedFile, final String datasourceName, final FeedType feedType,
			final String mindbreezeHostName, final boolean delFiles, final boolean checkBackLog)
			throws IOException, InterruptedException, HttpException {

		String FEEDTYPE_PARAM = "feedtype";
		String DATASOURCE_PARAM = "datasource";
		String XMLFILE_PARAM = "data";

		String mindbreezeServer = mindbreezeHostName;

		HttpClient client = HttpClientBuilder.create().build();
		HttpPost postPageRequest = new HttpPost(String.format(mindbreezeDocFeedServlet, mindbreezeServer));

		InputStream is = new FileInputStream(feedFile);
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();

		builder.addTextBody(FEEDTYPE_PARAM, datasourceName, ContentType.TEXT_PLAIN);
		builder.addTextBody(DATASOURCE_PARAM, feedType.toString(), ContentType.TEXT_PLAIN);
		builder.addBinaryBody(XMLFILE_PARAM, is);
		HttpEntity multipartEntity = builder.build();

		postPageRequest.setEntity(multipartEntity);

		HttpResponse postPageResponse = client.execute(postPageRequest);

		/**
		 * TODO rs = status inspire = gsa
		 */
		int status = -1;
		try {
			final FileBody contentFb = new FileBody(feedFile, ContentType.TEXT_XML, feedFile.getName());
			final StringBody dsSb = new StringBody(datasourceName, ContentType.TEXT_PLAIN);
			final StringBody ftSb = new StringBody(feedType.toString(), ContentType.TEXT_PLAIN);

			final HttpEntity reqEntity;
			if (feedType == FeedType.GROUPS)
				reqEntity = MultipartEntityBuilder.create().addPart("groupsource", dsSb).addPart("data", contentFb)
						.build();
			else
				reqEntity = MultipartEntityBuilder.create().addPart("data", contentFb).addPart("datasource", dsSb)
						.addPart("feedtype", ftSb).build();

			final String[] aMindbreeze = mindbreezeHostName.split(",");
			for (final String inspire : aMindbreeze) {
				final HttpPost hp;
				if (feedType == FeedType.GROUPS) {
					hp = new HttpPost(String.format(mindbreezeGroupFeedServlet, inspire));
				} else {
					hp = new HttpPost(String.format(mindbreezeDocFeedServlet, inspire));
				}
				hp.setEntity(reqEntity);
				try (CloseableHttpResponse r = HttpClientHelper.executeWithRetry(hc, hp)) {
					status = postPageResponse.getStatusLine().getStatusCode();
				}
				if (200 != status)
					throw new IOException("Feed POST failed ; GSA answered with HTTP status code: " + status);
				LOG.debug("Feed file successfully sent to " + inspire);
			}
		} finally {
			if (delFiles && feedFile.exists()) {
				LOG.debug("Deleting feed file " + feedFile + " - HTTP POST to GSA obtained: " + status);
				if (!feedFile.delete())
					feedFile.deleteOnExit();
			}
		}

		final String[] aMindbreeze = mindbreezeHostName.split(",");
		for (final String inspire : aMindbreeze) {
			status = postPageResponse.getStatusLine().getStatusCode();

			if (200 != status)
				throw new IOException("Feed POST failed ; GSA answered with HTTP status code: " + status);
			LOG.debug("Feed file successfully sent to " + inspire);
		}
	}

	private static File getTransformedXML(File originalXML) {
		File f2send = originalXML;
		if (TRANSFORM_XMLS && originalXML.length() < 104857600L) {
			LOG.info("Applying XSL transformation to file " + originalXML.getAbsolutePath());

			try {
				StreamSource ss = new StreamSource(originalXML);
				f2send = new File(originalXML.getAbsolutePath().replaceFirst("(\\.[^.]+)$", "_transformed$1"));
				StreamResult result = new StreamResult(f2send);
				DOMImplementation domImpl = DocumentBuilderFactory.newInstance().newDocumentBuilder()
						.getDOMImplementation();
				DocumentType docType = domImpl.createDocumentType("gsafeed", "-//Google//DTD GSA Feeds//EN",
						"gsafeed.dtd");
				Transformer transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(XSL));
				transformer.setOutputProperty("doctype-system", docType.getSystemId());
				transformer.setOutputProperty("doctype-public", docType.getPublicId());
				transformer.transform(ss, result);
				if (originalXML != null && originalXML.exists() && !originalXML.delete()) {
					originalXML.deleteOnExit();
				}
			} catch (Exception var7) {
				LOG.info("XSL transformation failed: ", var7);
				f2send = originalXML;
			}
		}

		return f2send;
	}

	@Override
	public void close() throws IOException {
		hc.close();
	}

}
