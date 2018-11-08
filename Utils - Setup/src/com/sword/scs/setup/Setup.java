package com.sword.scs.setup;

import static sword.common.utils.EnvUtils.IS_WINDOWS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import javax.naming.ConfigurationException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import sword.common.utils.EnvUtils;
import sword.common.utils.StringUtils;
import sword.common.utils.dates.ThreadSafeDateFormat;
import sword.common.utils.files.visitors.FileTreeCopier;
import sword.common.utils.files.visitors.FileTreeDeleter;
import sword.common.utils.files.visitors.FileTreeZipper;
import sword.common.utils.files.visitors.FileUnzipper;
import sword.common.utils.runtime.RuntimeUtils;

import com.sword.scs.Constants;
import com.sword.scs.utils.ServiceManager;

public abstract class Setup {

	private static final String PATCH_DIR_PREFIX = "_scspatch_";

	static final ThreadSafeDateFormat CERTIF_DATES = new ThreadSafeDateFormat(new SimpleDateFormat("MMM dd, yyyy"));

	protected final Runtime rt = Runtime.getRuntime();
	protected final Path rootDir;
	protected final Path scsBin;
	protected final Path keystore;
	protected final Path truststore;
	protected final Path jaasConf;
	protected final Path krbConf;
	protected final Path keytab;
	protected final Path svcNameFile;
	protected final String envPath;
	protected final Path jdkHomePath;
	protected final boolean isX64;
	protected final ServiceManager svcMgr;

	public Setup(final Path rootDir, final String envPath) throws IOException {
		this.rootDir = rootDir;
		scsBin = this.rootDir.resolve(Constants.REL_PATH_SCS_BIN);
		keystore = this.rootDir.resolve(Constants.REL_PATH_SCS_KEYSTORE);
		truststore = this.rootDir.resolve(Constants.REL_PATH_SCS_TRUSTSTORE);
		jaasConf = this.rootDir.resolve(Constants.REL_PATH_SCS_JAAS_CONF);
		krbConf = this.rootDir.resolve(Constants.REL_PATH_SCS_KRB_CONF);
		keytab = this.rootDir.resolve(Constants.REL_PATH_SCS_KRB_KEYTAB);
		Path _svcNameFile = this.rootDir.resolve(Constants.REL_PATH_SCS_SVC_FILE);
		if (!_svcNameFile.toFile().exists()) {
			_svcNameFile = this.rootDir.resolve(Constants.REL_PATH_SCS_OLD_SVC_FILE);
		}
		svcNameFile = _svcNameFile;
		this.envPath = envPath;
		jdkHomePath = this.rootDir.resolve(Constants.JDK_DIR_NAME);
		isX64 = EnvUtils.isX64(jdkHomePath);
		svcMgr = new ServiceManager(this.rootDir, svcNameFile, this.envPath, isX64, IS_WINDOWS);

		final Path envStoragePath = scsBin.resolve(Constants.ENVPATH_STORE_FILE);
		if (scsBin.toFile().exists()) {
			Files.copy(new ByteArrayInputStream(this.envPath.getBytes(StandardCharsets.UTF_8)), envStoragePath, StandardCopyOption.REPLACE_EXISTING);
		}

		final File tmpDir = this.rootDir.resolve("temp").toFile();
		final File[] tmpDirFiles = tmpDir.listFiles();
		if (tmpDirFiles != null) {
			for (final File tmpDirFile : tmpDirFiles)
				if (tmpDirFile.getName().startsWith(PATCH_DIR_PREFIX)) {
					Files.walkFileTree(tmpDirFile.toPath(), new FileTreeDeleter());
				}
		}

	}

	public abstract void start() throws Exception;

	protected abstract void notifyAppClosing();

	protected abstract void closeApp();

	protected Configuration loadCurrentConfig() throws ParserConfigurationException, TransformerConfigurationException, TransformerFactoryConfigurationError, SAXException, IOException, XPathExpressionException {
		final DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		final Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final Document webXml = db.parse(rootDir.resolve("webapps_SCS_WEB-INF_web.xml".replace('_', File.separatorChar)).toFile());

		String passwordHash = xpath.evaluate("/web-app/context-param[param-name='AdministrationPassword']/param-value/text()", webXml);
		if (StringUtils.isNullOrEmpty(passwordHash)) {
			passwordHash = null;
		}

		final Document serverConf = db.parse(rootDir.resolve("conf_server.xml".replace('_', File.separatorChar)).toFile());
		final String hostname = xpath.evaluate("/Server/Service/Engine/@defaultHost", serverConf);
		final String httpPortStr = xpath.evaluate("/Server/Service/Connector[1]/@port", serverConf);
		final String httpsPortStr = xpath.evaluate("/Server/Service/Connector[2]/@port", serverConf);
		final String sdPortStr = xpath.evaluate("/Server/@port", serverConf);

		return new Configuration(hostname, httpPortStr, httpsPortStr, sdPortStr, passwordHash);
	}

	protected void updateSCSConfig(final Configuration scsConf) throws ParserConfigurationException, TransformerFactoryConfigurationError, SAXException, IOException, XPathExpressionException, DOMException, TransformerException {
		final DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		final Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		final XPath xpath = XPathFactory.newInstance().newXPath();

		final File webXmlFile = rootDir.resolve("webapps_SCS_WEB-INF_web.xml".replace('_', File.separatorChar)).toFile();
		final Document webXml = db.parse(webXmlFile);

		final String aph = scsConf.getAdminPasswordHash();
		if (StringUtils.isNullOrEmpty(aph)) {
			final Element elem = (Element) xpath.evaluate("/web-app/context-param[param-name='AdministrationPassword']/param-value", webXml, XPathConstants.NODE);
			elem.setTextContent("");
		} else if (!Configuration.DO_NOT_CHANGE.equals(aph)) {
			final Element elem = (Element) xpath.evaluate("/web-app/context-param[param-name='AdministrationPassword']/param-value", webXml, XPathConstants.NODE);
			elem.setTextContent(aph);
		}

		final File tomConfFile = rootDir.resolve("conf_server.xml".replace('_', File.separatorChar)).toFile();
		final Document serverConf = db.parse(tomConfFile);
		final String hostname = scsConf.getHostname();
		if (!StringUtils.isNullOrEmpty(hostname)) {
			Element elem = (Element) xpath.evaluate("/Server/Service/Engine", serverConf, XPathConstants.NODE);
			elem.setAttribute("defaultHost", hostname);
			elem = (Element) xpath.evaluate("/Server/Service/Engine/Host", serverConf, XPathConstants.NODE);
			elem.setAttribute("name", hostname);
		}
		final Element httpConnectorElem = (Element) xpath.evaluate("/Server/Service/Connector[1]", serverConf, XPathConstants.NODE);
		final Element httpsConnectorElem = (Element) xpath.evaluate("/Server/Service/Connector[2]", serverConf, XPathConstants.NODE);

		httpsConnectorElem.setAttribute("keystoreFile", keystore.toString());
		httpsConnectorElem.setAttribute("keystorePass", Constants.STORES_PWD);
		httpConnectorElem.setAttribute("truststoreFile", truststore.toString());
		httpConnectorElem.setAttribute("truststorePass", Constants.STORES_PWD);
		httpsConnectorElem.setAttribute("truststoreFile", truststore.toString());
		httpsConnectorElem.setAttribute("truststorePass", Constants.STORES_PWD);

		if (scsConf.getHttpPort() > 0) {
			httpConnectorElem.setAttribute("port", Integer.toString(scsConf.getHttpPort()));
		}

		if (scsConf.getHttpsPort() > 0) {
			httpConnectorElem.setAttribute("redirectPort", Integer.toString(scsConf.getHttpsPort()));
			httpsConnectorElem.setAttribute("port", Integer.toString(scsConf.getHttpsPort()));
			httpsConnectorElem.setAttribute("redirectPort", Integer.toString(scsConf.getHttpsPort()));
		}

		if (scsConf.getShutdownPort() > 0) {
			((Element) xpath.evaluate("/Server", serverConf, XPathConstants.NODE)).setAttribute("port", Integer.toString(scsConf.getShutdownPort()));
		}

		transformer.transform(new DOMSource(webXml), new StreamResult(webXmlFile));
		transformer.transform(new DOMSource(serverConf), new StreamResult(tomConfFile));

	}

	protected void generateSelfSignedCertificate(final String hostname, final String alias) throws IOException, InterruptedException {
		final String keytool = rootDir.resolve(Constants.REL_PATH_KEYTOOL).toString();
		final String ks = keystore.toString();
		RuntimeUtils.getProcessWithPath(new String[] {keytool, "-delete", "-alias", alias, "-keystore", ks, "-storepass", Constants.STORES_PWD}, rootDir.toFile(), envPath).waitFor();
		final Process p = RuntimeUtils.getProcessWithPath(new String[] {keytool, "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650", "-dname",
			"CN=" + hostname + ", O=Sword Group, OU=Sword Connect, L=Paris, S=France, C=FR", "-keystore", ks, "-storepass", Constants.STORES_PWD, "-keypass", Constants.STORES_PWD}, rootDir.toFile(), envPath);
		final int rc = p.waitFor();
		if (rc != 0) throw new RuntimeException("Process exited with code " + rc + " - Process output: " + RuntimeUtils.readTerminatedProcessOutput(p));
	}

	protected String viewPublicKey(final String alias) throws IOException, InterruptedException {
		final String keytool = rootDir.resolve(Constants.REL_PATH_KEYTOOL).toString();
		final String ks = keystore.toString();
		final Process p = RuntimeUtils.getProcessWithPath(new String[] {keytool, "-exportcert", "-rfc", "-alias", alias, "-keystore", ks, "-storepass", Constants.STORES_PWD, "-keypass", Constants.STORES_PWD}, rootDir.toFile(), envPath);
		final int rc = p.waitFor();
		if (rc == 0) return RuntimeUtils.readTerminatedProcessOutput(p);
		else throw new RuntimeException("Process exited with code " + rc + " - Process output: " + RuntimeUtils.readTerminatedProcessOutput(p));
	}

	protected void exportCsr(final String alias, final File file) throws IOException, InterruptedException {
		final String keytool = rootDir.resolve(Constants.REL_PATH_KEYTOOL).toString();
		final String ks = keystore.toString();
		final Process p = RuntimeUtils.getProcessWithPath(new String[] {keytool, "-certreq", "-alias", alias, "-keystore", ks, "-storepass", Constants.STORES_PWD, "-file", file.getAbsolutePath(), "-keypass", Constants.STORES_PWD}, rootDir.toFile(), envPath);
		final int rc = p.waitFor();
		if (rc != 0) throw new RuntimeException("Process exited with code " + rc + " - Process output: " + RuntimeUtils.readTerminatedProcessOutput(p));
	}

	protected void importJavaKeystore(final File file) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
		final KeyStore currentKS = KeyStore.getInstance("JKS");
		final char[] ksPwd = Constants.STORES_PWD.toCharArray();
		final char[] newKsPwd = "changeit".toCharArray();
		try (FileInputStream is = new FileInputStream(keystore.toFile())) {
			currentKS.load(is, ksPwd);
		}
		final KeyStore newKS = KeyStore.getInstance("JKS");
		try (FileInputStream is = new FileInputStream(file)) {
			newKS.load(is, newKsPwd);
		}
		final Enumeration<String> aliases = newKS.aliases();
		while (aliases.hasMoreElements()) {
			final String alias = aliases.nextElement();
			if (newKS.isKeyEntry(alias)) {
				if (currentKS.containsAlias(alias)) {
					currentKS.deleteEntry(alias);
				}
				final Key key = newKS.getKey(alias, newKsPwd);
				final Certificate cert = newKS.getCertificate(alias);
				if (cert == null) {
					currentKS.setKeyEntry(alias, key, ksPwd, null);
				} else {
					currentKS.setKeyEntry(alias, key, ksPwd, new Certificate[] {cert});
				}
			}
		}

		try (FileOutputStream os = new FileOutputStream(keystore.toFile())) {
			currentKS.store(os, ksPwd);
		}
	}

	protected void importCsrReply(final String alias, final File file) throws IOException, InterruptedException {
		final String keytool = rootDir.resolve(Constants.REL_PATH_KEYTOOL).toString();
		final String ks = keystore.toString();
		final Process p = RuntimeUtils.getProcessWithPath(new String[] {keytool, "-import", "-alias", alias, "-keystore", ks, "-storepass", Constants.STORES_PWD, "-trustcacerts", "-noprompt", "-file", file.getAbsolutePath(), "-keypass", Constants.STORES_PWD},
			rootDir.toFile(), envPath);
		final int rc = p.waitFor();
		if (rc != 0) throw new RuntimeException("Process exited with code " + rc + " - Process output: " + RuntimeUtils.readTerminatedProcessOutput(p));
	}

	protected X509Certificate getCertificate(final String alias) throws KeyStoreException, FileNotFoundException, IOException, NoSuchAlgorithmException, CertificateException {
		final KeyStore ks = KeyStore.getInstance("JKS");
		try (FileInputStream is = new FileInputStream(keystore.toFile())) {
			ks.load(is, Constants.STORES_PWD.toCharArray());
		}
		return (X509Certificate) ks.getCertificate(alias);
	}

	protected KrbConf getKrbConfig() throws IOException {

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Files.copy(jaasConf, os);
		final String jaasConf = new String(os.toByteArray(), StandardCharsets.UTF_8);

		final Matcher m = Pattern.compile("\tprincipal=\"HTTP/(.+?)@(.+?)\"").matcher(jaasConf);
		if (m.find()) {
			final String hostname = m.group(1);
			final String realm = m.group(2);
			if ("scs.domain.com".equals(hostname)) return null;
			if ("DOMAIN.COM".equals(realm)) return null;

			os = new ByteArrayOutputStream();
			Files.copy(krbConf, os);
			final String krbConf = new String(os.toByteArray(), StandardCharsets.UTF_8);
			final Matcher m2 = Pattern.compile("\t\tkdc = (.+)").matcher(krbConf);
			if (m2.find()) {
				final String kdc = m2.group(1);
				if ("kdc.domain.com".equals(hostname)) return null;
				return new KrbConf(realm, hostname, kdc);
			}
		}

		return null;

	}

	protected void updateKrbConfig(final String krbRealm, final String krbHostname, final String kdcHostname, final String keytabPath) throws IOException {

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Files.copy(jaasConf, os);
		String jaasConf = new String(os.toByteArray(), StandardCharsets.UTF_8);
		jaasConf = jaasConf.replaceFirst("\tprincipal=\".+", "\tprincipal=\"HTTP/" + krbHostname + "@" + krbRealm + "\"");
		Files.copy(new ByteArrayInputStream(jaasConf.getBytes(StandardCharsets.UTF_8)), this.jaasConf, StandardCopyOption.REPLACE_EXISTING);

		os = new ByteArrayOutputStream();
		Files.copy(krbConf, os);
		final StringBuilder krbConf = new StringBuilder("[libdefaults]\n");
		krbConf.append("\tdefault_realm = ").append(krbRealm).append("\n");
		krbConf.append("\tforwardable = true\n");
		krbConf.append("\tdefault_tkt_enctypes = rc4-hmac\n");
		krbConf.append("\tdefault_tgs_enctypes = rc4-hmac\n");
		krbConf.append("\tpermitted_enctypes = rc4-hmac\n\n");
		krbConf.append("[realms]\n");
		krbConf.append("\t").append(krbRealm).append(" = {\n");
		krbConf.append("\t\tkdc = ").append(kdcHostname).append("\n");
		krbConf.append("\t\tdefault_domain = ").append(krbRealm).append("\n");
		krbConf.append("\t\tadmin_server = ").append(kdcHostname).append("\n");
		krbConf.append("\t}\n\n");
		krbConf.append("[domain_realm]\n");
		krbConf.append("\t.").append(krbRealm.toLowerCase()).append(" = ").append(krbRealm).append("\n");
		krbConf.append("\t").append(krbRealm.toLowerCase()).append(" = ").append(krbRealm).append("\n\n");
		krbConf.append("[logging]\n\t#kdc = CONSOLE");
		Files.copy(new ByteArrayInputStream(krbConf.toString().getBytes(StandardCharsets.UTF_8)), this.krbConf, StandardCopyOption.REPLACE_EXISTING);

		Files.copy(new File(keytabPath).toPath(), keytab, StandardCopyOption.REPLACE_EXISTING);

	}

	protected void restartService(final String svcName) throws IOException, InterruptedException {
		svcMgr.stopSvc(svcName);
		final Object lock = new Object();
		while (svcMgr.isRunning(svcName)) {
			synchronized (lock) {
				lock.wait(250);
			}
		}
		zipLog(rootDir);
		svcMgr.startSvc(svcName);
	}

	protected static String getConfigurationURL(final Configuration conf) {
		return String.format("https://%s:%d/SCS/secure/manager.html", conf.getHostname(), conf.getHttpsPort());
	}

	protected void updateLicense(final Path path) throws IOException, InterruptedException {

		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Files.copy(path, os);
		final String newLic = removeNonLicBytes(os.toByteArray());

		os = new ByteArrayOutputStream();
		final Path webxml = rootDir.resolve("webapps_SCS_WEB-INF_web.xml".replace('_', File.separatorChar));
		Files.copy(webxml, os);
		final String webXml = new String(os.toByteArray(), StandardCharsets.UTF_8);

		final Matcher m = Pattern.compile("<context-param>[\r\n\t ]*<param-name>License</param-name>[\r\n\t ]*((<param-value>([^<]+)</param-value>)|(<param-value/>))[\r\n\t ]*</context-param>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(webXml);
		final StringBuffer sb = new StringBuffer();
		if (m.find()) {
			m.appendReplacement(sb, "<context-param>\n\t\t<param-name>License</param-name>\n\t\t<param-value>" + StringUtils.toXMLString(newLic) + "</param-value>\n\t</context-param>");
		}
		m.appendTail(sb);

		Files.copy(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)), webxml, StandardCopyOption.REPLACE_EXISTING);

		final String svcName = svcMgr.getServiceName();
		if (svcMgr.isRunning(svcName)) {
			restartService(svcName);
		}

	}

	private static String removeNonLicBytes(final byte[] licBin) {
		final String lic = new String(licBin, StandardCharsets.UTF_8);
		final StringBuilder filtered = new StringBuilder();
		for (final char c : lic.toCharArray())
			if (c >= '0' && c <= '9' || c >= 'A' && c <= 'G' || c >= 'a' && c <= 'g') {
				filtered.append(c);
			}
		return filtered.toString();
	}

	protected String getLicenseValue() throws IOException {

		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		final Path webxml = rootDir.resolve("webapps_SCS_WEB-INF_web.xml".replace('_', File.separatorChar));
		Files.copy(webxml, os);
		final String webXml = new String(os.toByteArray(), StandardCharsets.UTF_8);

		final Matcher m = Pattern.compile("<context-param>[\r\n\t ]*<param-name>License</param-name>[\r\n\t ]*<param-value>([^<]+)</param-value>[\r\n\t ]*</context-param>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(webXml);
		if (m.find()) return m.group(1);
		else return null;

	}

	protected List<Path> getRunningIndexers() throws IOException {
		final Path conDir = rootDir.resolve(Constants.REL_PATH_SCS_CONNECTORS);
		final IndexerFinder indexerFinder = new IndexerFinder();
		Files.walkFileTree(conDir, indexerFinder);
		return indexerFinder.getRunningIndexers();
	}

	protected static void killIndexers(final List<Path> runningIndexers) {
		IndexerFinder.killIndexers(runningIndexers);
	}

	public void applyPatch(final Path patchFilePath) throws Exception {
		final String patchDirRelPath = "temp/" + PATCH_DIR_PREFIX + Long.toHexString(System.currentTimeMillis());
		final Path patchDir = rootDir.resolve(patchDirRelPath);
		patchDir.toFile().mkdirs();
		try (FileUnzipper x = new FileUnzipper(new ZipInputStream(new FileInputStream(patchFilePath.toFile())), patchDir)) {
			x.extract();
		}

		if (!patchDir.resolve("patcher").toFile().exists()) throw new Exception("Invalid patch file");

		String classRelPath = FileTreeCopier.class.getName().replace('.', '/') + ".class";
		Path classDest = patchDir.resolve(classRelPath);
		classDest.toFile().getParentFile().mkdirs();
		try (final InputStream is = FileTreeCopier.class.getResourceAsStream("/" + classRelPath)) {
			Files.copy(is, classDest, StandardCopyOption.REPLACE_EXISTING);
		}

		classRelPath = FileTreeDeleter.class.getName().replace('.', '/') + ".class";
		classDest = patchDir.resolve(classRelPath);
		classDest.toFile().getParentFile().mkdirs();
		try (final InputStream is = FileTreeCopier.class.getResourceAsStream("/" + classRelPath)) {
			Files.copy(is, classDest, StandardCopyOption.REPLACE_EXISTING);
		}

		classRelPath = PatchApplicator.class.getName().replace('.', '/') + ".class";
		classDest = patchDir.resolve(classRelPath);
		classDest.toFile().getParentFile().mkdirs();
		Files.copy(patchDir.resolve("patcher"), classDest, StandardCopyOption.REPLACE_EXISTING);

		notifyAppClosing();

		final List<Path> ris = getRunningIndexers();
		if (!ris.isEmpty()) {
			killIndexers(ris);
		}
		final String svcName = svcMgr.getServiceName();
		if (svcMgr.isRunning(svcName)) {
			svcMgr.stopSvc(svcName);
		}
		zipLog(rootDir);

		final StringBuilder command = new StringBuilder(rootDir.resolve(Constants.REL_PATH_JAVA).toString());
		command.append(" -cp \".");
		if (IS_WINDOWS) {
			command.append(";");
		} else {
			command.append(":");
		}
		command.append(patchDirRelPath).append("\" ").append(PatchApplicator.class.getName());
		command.append(" \"").append(rootDir.toString()).append("\"");
		command.append(" \"").append(patchDir.toString()).append("\"");
		command.append(" \"").append(envPath).append("\"");
		command.append(" \"").append(svcMgr.getServiceName()).append("\"");
		command.append(" ").append(Boolean.toString(IS_WINDOWS));

		if (IS_WINDOWS) {
			Files.copy(new ByteArrayInputStream(command.toString().getBytes()), patchDir.resolve("tmp.bat"), StandardCopyOption.REPLACE_EXISTING);
			RuntimeUtils.getProcessWithPath(new String[] {"cmd.exe", "/C", patchDir.resolve("tmp.bat").toString(), ">" + rootDir.resolve("logs\\patch-post-op.log").toString()}, rootDir.toFile(), envPath);
		} else {
			Files.copy(new ByteArrayInputStream(command.toString().getBytes()), patchDir.resolve("tmp.sh"), StandardCopyOption.REPLACE_EXISTING);
			RuntimeUtils.getProcessWithPath(new String[] {"sh", "-c", "sh " + patchDir.resolve("tmp.sh").toString() + " >" + rootDir.resolve("logs/patch-post-op.log").toString()}, rootDir.toFile(), envPath);
		}

		closeApp();

	}

	public void doStartSvc() throws IOException, InterruptedException {
		System.out.print("Starting service ..... ");
		svcMgr.startSvc(svcMgr.getServiceName());
		System.out.println("OK");
	}

	public void doStopSvc() throws IOException, InterruptedException {
		System.out.print("Stopping service ..... ");
		svcMgr.stopSvc(svcMgr.getServiceName());
		System.out.println("OK");
	}

	public void doRestartSvc() throws IOException, InterruptedException {
		System.out.print("Restarting service ..... ");
		restartService(svcMgr.getServiceName());
		System.out.println("OK");
	}

	public static void checkHostname(final String hostname) throws ConfigurationException {
		if (hostname.length() > 255) throw new ConfigurationException("Hostnames must not contain more than 255 characters");
		else if (hostname.length() < 1) throw new ConfigurationException("Hostnames must contain more than 1 character");
		else {
			final String[] labels = hostname.split("\\.");
			if (labels.length < 3) throw new ConfigurationException("Fully qualified domain names must contain at least two dots (e.g.: myhost.mydomain.com)");
			else {
				for (final String lbl : labels)
					if (!lbl.matches("^[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?$")) throw new ConfigurationException(String.format(
						"Invalid hostname label: \"%s\" ; an hostname label must start and end with a digit or an ASCII letter and may only contain digits, ASCII letters or hyphen characters", lbl));
			}
		}
	}

	public static void checkPort(final String portStr) throws ConfigurationException {
		if (!StringUtils.isInteger(portStr)) throw new ConfigurationException(portStr + " is not a number");
		else {
			final int p = Integer.parseInt(portStr);
			if (IS_WINDOWS) {
				if (p < 80 || p > 65535) throw new ConfigurationException("Port number must be greater than 79 and lower than 65536");
			} else if (p < 1024 || p > 65535) throw new ConfigurationException("Port number must be greater than 1023 and lower than 65536");
		}
	}

	public static void zipLog(final Path rootDir) throws IOException {
		final Path logs = rootDir.resolve("logs");
		final File logsBU = rootDir.resolve("logs-backup").toFile();
		if (!logsBU.exists()) {
			logsBU.mkdir();
		}
		try (FileTreeZipper ftz = new FileTreeZipper(logs, logsBU.toPath().resolve(String.format("backup_%s.zip", new SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(new Date()))))) {
			Files.walkFileTree(logs, ftz);
		}
		final File[] logFiles = logs.toFile().listFiles();
		if (logFiles != null) {
			for (final File logFile : logFiles)
				if (logFile.isFile() && !logFile.getName().startsWith("indexer-")) {
					logFile.delete();
				}
		}
	}

	public static boolean isAvailablePort(final int pn) {
		try (Socket s = new Socket(InetAddress.getLocalHost(), pn)) {
			return false;
		} catch (final ConnectException ce) {
			return true;
		} catch (final Throwable t) {
			return false;
		}
	}

	public static int getRandomAvailablePort() throws IOException {
		try (ServerSocket ss = new ServerSocket(0)) {
			return ss.getLocalPort();
		}
	}

}
