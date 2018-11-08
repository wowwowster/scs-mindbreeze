package com.sword.scs.building.utils;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jdatepicker.DateModel;
import org.jdatepicker.DefaultComponentFactory;
import org.jdatepicker.impl.JDatePanelImpl;
import org.jdatepicker.impl.JDatePickerImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import sword.common.utils.files.FileUtils;
import sword.common.utils.files.visitors.FileTreeCopier;
import sword.common.utils.files.visitors.FileTreeDeleter;
import sword.common.utils.files.visitors.FileTreeZipper;
import sword.common.utils.runtime.RuntimeUtils;
import sword.common.utils.streams.StreamUtils;
import sword.common.utils.throwables.ExceptionDigester;

public class PatchGenerator extends ConnectorSelector implements ActionListener {

	private static final SimpleDateFormat DATES = new SimpleDateFormat("yyyy-MM-dd");
	private static final Date J7_TO_J8;
	static {
		Calendar cal = Calendar.getInstance();
		cal.set(2015, 3, 23);
		J7_TO_J8 = cal.getTime();
	}

	private static final Pattern REVISION_ENTRY = Pattern.compile("^r([\\d]+) \\| (.+?) \\| (.+?) \\| [\\d]+ lines?\\r?\\nChanged paths:\\r?\\n((?: {3}[ADM] /[^\\r\\n]+?\\r?\\n)*)\\r?\\n(.+?)\\r?\\n[\\-]{64,}", Pattern.MULTILINE|Pattern.DOTALL);
	private static final Pattern CHANGED_PATH_ENTRY = Pattern.compile("^ {3}([ADM]) /trunk/(.+?)/(.+)$", Pattern.MULTILINE);
	private static final Pattern COMMIT_COMMENTS_ELEMENTS = Pattern.compile("^(.+?)(?:-v| version )([0-9]+(?:\\.[0-9]+)*)\\:? ?$", Pattern.MULTILINE);

	private static final long serialVersionUID = 1L;
	
	public static void main(final String[] args) {
		new PatchGenerator(FileUtils.getJarFile(PatchGenerator.class).getParentFile().getParentFile().getAbsolutePath()).start();
	}
	
	private JDatePickerImpl from;
	private JDatePickerImpl to;

	public PatchGenerator(final String root) throws HeadlessException {
		super(root);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void addSwingComponents() {
		final JPanel lblPan = new JPanel(new BorderLayout());
		lblPan.add(new JLabel("Build patch for files modified between: "), BorderLayout.CENTER);
		mainPan.add(lblPan);

		final JPanel datePan = new JPanel(new GridLayout(1, 3));
		
		final Calendar calFrom = Calendar.getInstance();
		final Calendar calTo = Calendar.getInstance();
		
		this.from = new JDatePickerImpl(new JDatePanelImpl(new DefaultComponentFactory().createModel(calFrom)));
		calFrom.setTimeInMillis(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30));
		((DateModel<Calendar>)from.getModel()).setValue(calFrom);
		datePan.add(from);
		
		datePan.add(new JLabel(" and ", SwingConstants.CENTER));
		
		this.to = new JDatePickerImpl(new JDatePanelImpl(new DefaultComponentFactory().createModel(calTo)));
		datePan.add(to);
		calTo.setTimeInMillis(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
		((DateModel<Calendar>)to.getModel()).setValue(calTo);
		datePan.add(to);
		
		mainPan.add(datePan);
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		if (OK.equals(e.getActionCommand())) {
			((JButton) e.getSource()).setEnabled(false);
			final List<String> connectorNames = cbs.parallelStream().filter(cb -> cb.isSelected()).map(cb -> "Connector - " + cb.getText()).collect(Collectors.toList());
			try {
				buildPatch(connectorNames);
			} catch (final Exception ex) {
				JOptionPane.showMessageDialog(this, ExceptionDigester.toString(ex), "Runtime error", JOptionPane.ERROR_MESSAGE);
			}
			dispose();
		} else if (ALL.equals(e.getActionCommand())) {
			cbs.parallelStream().forEach(cb -> cb.setSelected(true));
		} else if (NONE.equals(e.getActionCommand())) {
			cbs.parallelStream().forEach(cb -> cb.setSelected(false));
		}
	}

	private void buildPatch(final List<String> connectorNames) throws IOException, InterruptedException, ParserConfigurationException, TransformerFactoryConfigurationError, TransformerException {

		@SuppressWarnings("unchecked")
		Date from = ((DateModel<Calendar>)this.from.getModel()).getValue().getTime();
		@SuppressWarnings("unchecked")
		Date to = ((DateModel<Calendar>)this.to.getModel()).getValue().getTime();
		
		System.out.println("Building patch for modifications that occured between " + from + " and " + to);
		
		if (from.before(J7_TO_J8) && to.after(J7_TO_J8)) {
			JOptionPane.showMessageDialog(this, "Patch cannot be built for this time range as it comprises a change from JAVA 7 to JAVA 8 which is not supported by the patch generator\nAn update manual JAVA update will be necessary before you can apply this patch.", "Warning", JOptionPane.WARNING_MESSAGE);
		}

		final List<String> commands = new ArrayList<>();
		commands.add("svn");
		commands.add("log");
		commands.add("http://swpvsvn.parisgsa.lan/svn/SCS/trunk");
		commands.add("-v");
		commands.add("-r");
		commands.add(String.format("{%s}:{%s}", DATES.format(from), DATES.format(to)));
		final ProcessBuilder pb = new ProcessBuilder(commands);
		final Process p = pb.start();

		final ProcessStreamsConsumer psc = this.new ProcessStreamsConsumer(p);

		final int rc = p.waitFor();

		if (rc == 0) {

			final File destDir = rootDir.toPath().resolve("Utils - Installer/patch").toFile();
			if (destDir.exists()) {
				if (destDir.isFile()) destDir.delete();
				else Files.walkFileTree(destDir.toPath(), new FileTreeDeleter());
			}
			destDir.mkdirs();

			final Path rootPath = rootDir.toPath();

			final File tmpDir = new File(destDir, "tmp");
			tmpDir.mkdir();
			final Path tmpDirPath = tmpDir.toPath();
			
			final File tmpBinDir = new File(tmpDir, "binary");
			tmpBinDir.mkdir();
			final Path tmpBinDirPath = tmpBinDir.toPath();

			final Set<String> modifiedConnectorClasses = new HashSet<>();
			
			final Map<String, Set<String>> deletedConnectorHelpPages = new HashMap<>();
			final Map<String, Set<String>> deletedConnectorLibs = new HashMap<>();
			final Map<String, Set<String>> deletedConnectorResources = new HashMap<>();

			final Set<String> deletedConstantLibs = new HashSet<>();

			final Set<String> deletedCoreLibs = new HashSet<>();

			final Set<String> deletedTomcatFiles = new HashSet<>();
			

			final Set<String> ignoredConnectors = new HashSet<>();
			boolean ignoreInstallerSrcWarningDone = false;
			boolean ignoreJdkWarningDone = false;

			boolean isKrb5LibUpdated = false;
			boolean isLicManagerUpdated = false;
			boolean areSCSSetupScriptsUpdated = false;
			boolean isSCSSetupUpdated = false;
			boolean isSCSSvcManagerUpdated = false;

			final String svnLog = new String(psc.i.toByteArray());
			final Map<String, Map<String, String>> releaseNotes = new HashMap<>();

			final Set<String> allConnectorsUsingGoogle3rdParty = new HashSet<>();
			allConnectorsUsingGoogle3rdParty.add("Google Apps");
			allConnectorsUsingGoogle3rdParty.add("Google Drive");
			allConnectorsUsingGoogle3rdParty.add("Google Sites");

			final Set<String> allConnectorsUsingHttpClient = new HashSet<>();
			allConnectorsUsingHttpClient.add("AlfrescoREST");
			allConnectorsUsingHttpClient.add("Drupal");
			allConnectorsUsingHttpClient.add("eRoom");
			allConnectorsUsingHttpClient.add("Google OAuth");
			allConnectorsUsingHttpClient.add("Jive");
			allConnectorsUsingHttpClient.add("Microsoft OAuth");
			allConnectorsUsingHttpClient.add("OneDrive");
			allConnectorsUsingHttpClient.add("POS");
			allConnectorsUsingHttpClient.add("SalesForce");
			allConnectorsUsingHttpClient.add("Sharepoint Online");
			
			int prefixLen = "Connector - ".length();
			
			final boolean processGoogle3rdParty = connectorNames.stream().anyMatch(cn -> allConnectorsUsingGoogle3rdParty.contains(cn.substring(prefixLen)));
			final boolean processSwordHttpClient = connectorNames.stream().anyMatch(cn -> allConnectorsUsingHttpClient.contains(cn.substring(prefixLen)));

			final Set<String> connectorsUsingGoogle3rdParty = allConnectorsUsingGoogle3rdParty.stream().filter(cn -> connectorNames.contains("Connector - " + cn)).collect(Collectors.toSet());
			final Set<String> connectorsUsingHttpClient = allConnectorsUsingHttpClient.stream().filter(cn -> connectorNames.contains("Connector - " + cn)).collect(Collectors.toSet());
			
			{//Always provide a fresh PatchApplicator class
				final Path dest = tmpDirPath.resolve("patcher");
				final Path src = rootPath.resolve("Utils - Setup/bin/com/sword/scs/setup/PatchApplicator.class");
				if (src.toFile().exists()) {
					if (src.toFile().isFile()) Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
				} else {
					System.err.println("Missing PatchApplicator class");
					return;
				}
			}
			
			Files.copy(
				new ByteArrayInputStream(("patch.build.date=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n").getBytes(StandardCharsets.UTF_8)), 
				tmpDirPath.resolve("buildinfo"), 
				StandardCopyOption.REPLACE_EXISTING
			);
			
			{//Always overwrite log4j config
				final Path dest = tmpBinDirPath.resolve("webapps/SCS/WEB-INF/classes/log4j.properties");
				final Path src = rootPath.resolve("Utils - Installer/resources/tomcat/webapps/SCS/WEB-INF/classes/log4j.properties");
				if (src.toFile().exists()) {
					if (src.toFile().isFile()) {
						if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
						Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
					}
				} else System.err.println("Missing log4j config file");
			}

			final Matcher revMatcher = REVISION_ENTRY.matcher(svnLog);
			while (revMatcher.find()) {
				System.out.println(String.format("Processing revision #%s committed by %s on %s", revMatcher.group(1), revMatcher.group(2), revMatcher.group(3)));
				String changedPaths = revMatcher.group(4);
				String commitComments = revMatcher.group(5);
				
				if (!(commitComments.startsWith("[non-release]") || commitComments.startsWith("[same-release]"))) {
					Matcher ct = COMMIT_COMMENTS_ELEMENTS.matcher(commitComments);
					StringBuffer sb = new StringBuffer();
					if (ct.find()) {
						ct.appendReplacement(sb, "");
						String connector = ct.group(1);
						if ("GSP Core".equals(connector)) connector = "SCS Core";
						else if ("Onedrive".equals(connector)) connector = "OneDrive";
						String connectorVersion = ct.group(2);
						if (!releaseNotes.containsKey(connector)) releaseNotes.put(connector, new HashMap<String, String>());
						Map<String, String> versionNotes = releaseNotes.get(connector);
						ct.appendTail(sb);
						versionNotes.put(connectorVersion, sb.toString().replaceFirst("^[\\r\\n]+", ""));
					} else {
						System.err.println("Ignoring malformed commit comment: " + commitComments);
					}
				}
				
				final Matcher m = CHANGED_PATH_ENTRY.matcher(changedPaths);
				while (m.find()) {

					final String action = m.group(1);
					final String dir = m.group(2);
					String path = m.group(3);
					if (path.contains(" (from /")) {
						path = path.substring(0, path.indexOf(" (from /"));
					}

					if (dir.startsWith("Connector - ")) {
						
						String connectorName = dir.substring("Connector - ".length());

						if (connectorNames.contains(dir)) {
							if (path.startsWith("tests/")) {
								//do nothing
							} else if (path.startsWith("src/")) {
								modifiedConnectorClasses.add(connectorName);
							} else if (path.startsWith("lib/")) {
								if ("D".equals(action)) {
									if (!deletedConnectorLibs.containsKey(connectorName)) deletedConnectorLibs.put(connectorName, new HashSet<String>());
									deletedConnectorLibs.get(connectorName).add(path.substring("lib/".length()));
								} else {
									final String jarPath = path.substring("lib/".length());
									final Path dest = tmpBinDirPath.resolve(String.format("scs/connectors/%s/lib/%s", connectorName, jarPath));
									if (!dest.toFile().exists()) {
										final Path src = rootPath.resolve(String.format("%s/dist/lib/%s", dir, jarPath));
										if (src.toFile().exists()) {
											if (src.toFile().isFile()) {
												if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
												Files.copy(src, dest);
											}
										} else System.err.println("Missing source lib: " + src);
									}
								}
							} else if (path.startsWith("help/")) {
								if ("D".equals(action)) {
									if (!deletedConnectorHelpPages.containsKey(connectorName)) deletedConnectorHelpPages.put(connectorName, new HashSet<String>());
									deletedConnectorHelpPages.get(connectorName).add(path.substring("help/".length()));
								} else {
									final Path dest = tmpBinDirPath.resolve(String.format("webapps/documentation/connectors/%s/%s", connectorName, path.substring("help/".length())));
									if (!dest.toFile().exists()) {
										final Path src = rootPath.resolve(String.format("%s/%s", dir, path));
										if (src.toFile().exists()) {
											if (src.toFile().isFile()) {
												if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
												Files.copy(src, dest);
											}
										} else System.err.println("Missing source help file: " + src);
									}
								}
							} else if (path.startsWith("resources/")) {
								if ("D".equals(action)) {
									if (!deletedConnectorResources.containsKey(connectorName)) deletedConnectorResources.put(connectorName, new HashSet<String>());
									deletedConnectorResources.get(connectorName).add(path.substring("resources/".length()));
								} else {
									final Path dest = tmpBinDirPath.resolve(String.format("scs/connectors/%s/%s", connectorName, path.substring("resources/".length())));
									if (!dest.toFile().exists()) {
										final Path src = rootPath.resolve(String.format("%s/%s", dir, path));
										if (src.toFile().exists()) {
											if (src.toFile().isFile()) {
												if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
												Files.copy(src, dest);
											}
										} else System.err.println("Missing resource file: " + src);
									}
								}
							} else if (!(".classpath".equals(path) || path.startsWith("tests/") || "tests".equals(path) || "help".equals(path))) System.err.println("Ignoring " + dir + " -> " + path);
						} else {
							if (!ignoredConnectors.contains(dir)) {
								System.err.println("Skipping " + dir);
								ignoredConnectors.add(dir);
							}
						}

					} else if ("Constants".equals(dir)) {
						if (path.startsWith("src/")) {
							Path dest = tmpBinDirPath.resolve("scs/bin/scs-constants.jar");
							final Path src = rootPath.resolve("Constants/dist/scs-constants.jar");
							if (!dest.toFile().exists()) {
								if (src.toFile().exists()) {
									if (src.toFile().isFile()) {
										if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
										Files.copy(src, dest);
									}
								} else System.err.println("Missing Constants source file: " + src);
							}
							dest = tmpBinDirPath.resolve("webapps/SCS/WEB-INF/lib/scs-constants.jar");
							if (!dest.toFile().exists()) {
								if (src.toFile().exists()) {
									if (src.toFile().isFile()) {
										if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
										Files.copy(src, dest);
									}
								} else System.err.println("Missing Constants source file: " + src);
							}
						} else if (path.startsWith("lib/")) {
							if ("D".equals(action)) {
								deletedConstantLibs.add(path.substring("lib/".length()));
							} else {
								final String jarPath = path.substring("lib/".length());
								Path dest = tmpBinDirPath.resolve("scs/bin/" + jarPath);
								final Path src = rootPath.resolve("Constants/dist/" + jarPath);
								if (!dest.toFile().exists()) {
									if (src.toFile().exists()) {
										if (src.toFile().isFile()) {
											if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
											Files.copy(src, dest);
										}
									} else System.err.println("Missing Constants lib file: " + src);
								}
								dest = tmpBinDirPath.resolve("webapps/SCS/WEB-INF/lib/" + jarPath);
								if (!dest.toFile().exists()) {
									if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
									if (src.toFile().exists()) {
										if (src.toFile().isFile()) Files.copy(src, dest);
									} else System.err.println("Missing Constants lib file: " + src);
								}
							}
						} else if (!(".classpath".equals(path) || path.startsWith("tests/") || "tests".equals(path) || "help".equals(path))) System.err.println("Ignoring " + dir + " -> " + path);
					} else if ("Core".equals(dir)) {
						if (path.startsWith("src/")) {
							final Path dest = tmpBinDirPath.resolve("webapps/SCS/WEB-INF/lib/scs-core.jar");
							if (!dest.toFile().exists()) {
								final Path src = rootPath.resolve("Core/dist/scs-core.jar");
								if (src.toFile().exists()) {
									if (src.toFile().isFile()) {
										if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
										Files.copy(src, dest);
									}
								} else System.err.println("Missing core source file: " + src);
							}
						} else if (path.startsWith("lib/")) {
							if ("D".equals(action)) {
								deletedCoreLibs.add(path.substring("lib/".length()));
							} else {
								final Path dest = tmpBinDirPath.resolve("webapps/SCS/WEB-INF/lib/" + path.substring("lib/".length()));
								if (!dest.toFile().exists()) {
									final Path src = rootPath.resolve("Core/dist/" + path.substring("lib/".length()));
									if (src.toFile().exists()) {
										if (src.toFile().isFile()) {
											if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
											Files.copy(src, dest);
										}
									} else System.err.println("Missing core lib file: " + src);
								}
							}
						} else if (!(".classpath".equals(path) || path.startsWith("tests/") || "tests".equals(path) || "help".equals(path))) System.err.println("Ignoring " + dir + " -> " + path);
					} else if ("ThirdParty - GoogleAPIWrap".equals(dir) && processGoogle3rdParty) {
						if (path.startsWith("src/")) {
							if ("D".equals(action)) {
								for (String cn: connectorsUsingGoogle3rdParty) {
									if (!deletedConnectorLibs.containsKey(cn)) deletedConnectorLibs.put(cn, new HashSet<String>());
									deletedConnectorLibs.get(cn).add("GoogleAPIWrap.jar");
								}
							} else {
								final Path src = rootPath.resolve("ThirdParty - GoogleAPIWrap/dist/GoogleAPIWrap.jar");
								for (String cn: connectorsUsingGoogle3rdParty) {
									Path dest = tmpBinDirPath.resolve("scs/connectors/" + cn + "/lib/GoogleAPIWrap.jar");
									if (!dest.toFile().exists()) {
										if (src.toFile().exists()) {
											if (src.toFile().isFile()) {
												if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
												Files.copy(src, dest);
											}
										} else System.err.println("Missing GoogleAPIWrap source file: " + src);
									}
								}
							}
						} else if (path.startsWith("lib/")) {
							if ("D".equals(action)) {
								for (String cn: connectorsUsingGoogle3rdParty) {
									if (!deletedConnectorLibs.containsKey(cn)) deletedConnectorLibs.put(cn, new HashSet<String>());
									deletedConnectorLibs.get(cn).add(path.substring("lib/".length()));
								}
							} else {
								final String jarPath = path.substring("lib/".length());
								final Path src = rootPath.resolve(String.format("ThirdParty - GoogleAPIWrap/lib/%s", jarPath));
								for (String cn: connectorsUsingGoogle3rdParty) {
									final Path dest = tmpBinDirPath.resolve(String.format("scs/connectors/%s/lib/%s", cn, jarPath));
									if (!dest.toFile().exists()) {
										if (src.toFile().exists()) {
											if (src.toFile().isFile()) {
												if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
												Files.copy(src, dest);
											}
										} else System.err.println("Missing GoogleAPIWrap lib file: " + src);
									}
								}
							}
						} else if (!(".classpath".equals(path) || path.startsWith("tests/") || "tests".equals(path) || "help".equals(path))) System.err.println("Ignoring " + dir + " -> " + path);
					} else if ("ThirdParty - SwordHttpClient".equals(dir) && processSwordHttpClient) {
						if (path.matches(".+\\.jar$")) {
							if ("D".equals(action)) {
								for (String cn: connectorsUsingHttpClient) {
									if (!deletedConnectorLibs.containsKey(cn)) deletedConnectorLibs.put(cn, new HashSet<String>());
									deletedConnectorLibs.get(cn).add(path);
								}
							} else {
								final Path src = rootPath.resolve(String.format("ThirdParty - SwordHttpClient/%s", path));
								for (String cn: connectorsUsingHttpClient) {
									Path dest = tmpBinDirPath.resolve(String.format("scs/connectors/%s/lib/%s", cn, path));
									if (!dest.toFile().exists()) {
										if (src.toFile().exists()) {
											if (src.toFile().isFile()) {
												if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
												Files.copy(src, dest);
											}
										} else System.err.println("Missing SwordHttpClient lib file: " + src);
									}
								}
							}
						} else if (!(".classpath".equals(path) || path.startsWith("tests/") || "tests".equals(path) || "help".equals(path))) System.err.println("Ignoring " + dir + " -> " + path);
					} else if ("Utils - Installer".equals(dir)) {
						if (path.startsWith("src/")) {
							if (!ignoreInstallerSrcWarningDone) {
								ignoreInstallerSrcWarningDone = true;
								System.err.println("Installer code won't be updated in patches - ignoring " + dir + "/" + path);
							}
						} else if (path.startsWith("resources")) {
							if (path.startsWith("resources/jdk/")) {
								if (!ignoreJdkWarningDone) {
									ignoreJdkWarningDone = true;
									System.err.println("JDK version changes not supported in patches - ignoring " + dir + "/" + path);
								}
							} else if (path.startsWith("resources/scripts/")) {
								if (!ignoreInstallerSrcWarningDone) {
									ignoreInstallerSrcWarningDone = true;
									System.err.println("Installer code won't be updated in patches - ignoring " + dir + "/" + path);
								}
							} else if (path.startsWith("resources/tomcat/")) {
								String resourcePath = path.substring("resources/tomcat/".length());
								if (resourcePath.startsWith("gsp/")) {
									resourcePath = "scs/" + resourcePath.substring("gsp/".length());
								} else if (resourcePath.startsWith("webapps/GSP/")) {
									resourcePath = "webapps/SCS/" + resourcePath.substring("webapps/GSP/".length());
								}
								if ("D".equals(action)) {
									if (!(
										resourcePath.startsWith("bin/") || resourcePath.startsWith("lib/") || //ignore tomcat binary
										resourcePath.startsWith("conf/") || //ignore tomcat conf
										resourcePath.startsWith("scs/conf/") || resourcePath.equals("webapps/SCS/WEB-INF/web.xml")//ignore scs conf
									)) {
										deletedTomcatFiles.add(path.substring("resources/tomcat/".length()));
									}
								} else {
									if (!(
										resourcePath.startsWith("bin/") || resourcePath.startsWith("lib/") || //ignore tomcat binary
										resourcePath.startsWith("conf/") || //ignore tomcat conf
										resourcePath.startsWith("scs/conf/") || resourcePath.equals("webapps/SCS/WEB-INF/web.xml")//ignore scs conf
									)) {
										if (resourcePath.startsWith("setup.exe") || resourcePath.startsWith("setup.sh")) {
											areSCSSetupScriptsUpdated = true;
										} else {
											final Path dest = tmpBinDirPath.resolve(resourcePath);
											if (!dest.toFile().exists()) {
												final Path src = rootPath.resolve("Utils - Installer/resources/tomcat/" + resourcePath);
												if (src.toFile().exists()) {
													if (src.toFile().isFile()) {
														if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
														Files.copy(src, dest);
													}
												} else System.err.println("Missing tomcat source file: " + src);
											}
										}
									}
								}
							} else if (!(".classpath".equals(path) || path.startsWith("tests/") || "tests".equals(path) || "help".equals(path))) System.err.println("Ignoring " + dir + " -> " + path);
						} else if (!(".classpath".equals(path) || path.startsWith("tests/") || "tests".equals(path) || "help".equals(path))) System.err.println("Ignoring " + dir + " -> " + path);
					} else if ("Utils - Krb5".equals(dir)) {
						if (path.startsWith("src/")) {
							isKrb5LibUpdated = true;
						} else if (!(".classpath".equals(path) || path.startsWith("tests/") || "tests".equals(path) || "help".equals(path))) System.err.println("Ignoring " + dir + " -> " + path);
					} else if ("Utils - License Manager".equals(dir)) {
						if (path.startsWith("src/")) {
							isLicManagerUpdated = true;
						} else if (!(".classpath".equals(path) || path.startsWith("tests/") || "tests".equals(path) || "help".equals(path))) System.err.println("Ignoring " + dir + " -> " + path);
					} else if ("Utils - Setup".equals(dir) || "Utils - Manager".equals(dir)) {
						if (path.startsWith("src/")) {
							isSCSSetupUpdated = true;
						} else if (!(".classpath".equals(path) || path.startsWith("tests/") || "tests".equals(path) || "help".equals(path))) System.err.println("Ignoring " + dir + " -> " + path);
					} else if ("Utils - Service Manager".equals(dir)) {
						if (path.startsWith("src/")) {
							isSCSSvcManagerUpdated = true;
						} else if (!(".classpath".equals(path) || path.startsWith("tests/") || "tests".equals(path) || "help".equals(path))) System.err.println("Ignoring " + dir + " -> " + path);
					} else {
						if (!("ThirdParty - GoogleAPIWrap".equals(dir) || "ThirdParty - SwordHttpClient".equals(dir) || "ant".equals(dir) || "ant ui".equals(dir))) System.err.println("Unknown dir: " + dir);
					}
				}
			}

			if (isKrb5LibUpdated) {
				final Path dest = tmpBinDirPath.resolve("webapps/SCS/WEB-INF/lib/scs-kerberos.jar");
				if (!dest.toFile().exists()) {
					final Path src = rootPath.resolve("Utils - Krb5/dist/scs-kerberos.jar");
					if (src.toFile().exists()) {
						if (src.toFile().isFile()) {
							if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
							Files.copy(src, dest);
						}
					} else System.err.println("Missing source file: " + src);
				}
			}

			if (isLicManagerUpdated) {
				final Path src = rootPath.resolve("Utils - License Manager/dist/jackson-ext-2.2.3.jar");
				Path dest = tmpBinDirPath.resolve("webapps/SCS/WEB-INF/lib/jackson-ext-2.2.3.jar");
				if (!dest.toFile().exists()) {
					if (src.toFile().exists()) {
						if (src.toFile().isFile()) {
							if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
							Files.copy(src, dest);
						}
					} else System.err.println("Missing source file: " + src);
				}
				dest = tmpBinDirPath.resolve("scs/bin/jackson-ext-2.2.3.jar");
				if (!dest.toFile().exists()) {
					if (src.toFile().exists()) {
						if (src.toFile().isFile()) {
							if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
							Files.copy(src, dest);
						}
					} else System.err.println("Missing source file: " + src);
				}
			}

			if (isSCSSetupUpdated) {
				final Path dest = tmpBinDirPath.resolve("scs/bin/setup.jar");
				if (!dest.toFile().exists()) {
					final Path src = rootPath.resolve("Utils - Setup/dist/setup.jar");
					if (src.toFile().exists()) {
						if (src.toFile().isFile()) {
							if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
							Files.copy(src, dest);
						}
					} else System.err.println("Missing source file: " + src);
				}
			}

			if (isSCSSvcManagerUpdated) {
				final Path dest = tmpBinDirPath.resolve("scs/bin/scs-svc-mgr.jar");
				if (!dest.toFile().exists()) {
					final Path src = rootPath.resolve("Utils - Service Manager/dist/scs-svc-mgr.jar");
					if (src.toFile().exists()) {
						if (src.toFile().isFile()) {
							if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
							Files.copy(src, dest);
						}
					} else System.err.println("Missing source file: " + src);
				}
			}

			if (areSCSSetupScriptsUpdated) {
				Path dest = tmpBinDirPath.resolve("setup.exe");
				if (!dest.toFile().exists()) {
					final Path src = rootPath.resolve("Utils - Installer/resources/tomcat/setup.exe");
					if (src.toFile().exists()) {
						if (src.toFile().isFile()) {
							if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
							Files.copy(src, dest);
						}
					} else System.err.println("Missing source file: " + src);
				}
				dest = tmpBinDirPath.resolve("setup.sh");
				if (!dest.toFile().exists()) {
					Path src = rootPath.resolve("Utils - Installer/resources/tomcat/setup.sh");
					if (src.toFile().exists()) {
						if (src.toFile().isFile()) {
							if (!dest.toFile().getParentFile().exists()) dest.toFile().getParentFile().mkdirs();
							Files.copy(src, dest);
						}
					} else System.err.println("Missing source file: " + src);
				}
			}

			final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			final Element root = doc.createElement("root");
			doc.appendChild(root);
			
			//For connectors with some modified classes - we remove all installed classes and copy all current ones
			for (final String connectorName : modifiedConnectorClasses) {
				//delete installed classes
				Element d = doc.createElement("delete");
				d.setAttribute("path", "scs/connectors/${ConnectorName}/classes".replace("${ConnectorName}", connectorName));
				root.appendChild(d);
				//copy current classes
				Path srcDir = rootPath.resolve(String.format("Connector - %s/dist/classes", connectorName));
				Path dstDir = tmpBinDirPath.resolve(String.format("scs/connectors/%s/classes", connectorName));
				Files.walkFileTree(srcDir, new FileTreeCopier(srcDir, dstDir));
			}
			
			processEntries("delete", "webapps/documentation/connectors/${ConnectorName}/${FileRelPath}", deletedConnectorHelpPages, doc, root);
			processEntries("delete", "scs/connectors/${ConnectorName}/lib/${FileRelPath}", deletedConnectorLibs, doc, root);
			processEntries("delete", "scs/connectors/${ConnectorName}/${FileRelPath}", deletedConnectorResources, doc, root);

			processEntries("delete", "scs/bin/${FileRelPath}", deletedConstantLibs, doc, root);
			processEntries("delete", "webapps/SCS/WEB-INF/lib/${FileRelPath}", deletedConstantLibs, doc, root);

			processEntries("delete", "webapps/SCS/WEB-INF/lib/${FileRelPath}", deletedCoreLibs, doc, root);

			processEntries("delete", "${FileRelPath}", deletedTomcatFiles, doc, root);

			final Transformer t = TransformerFactory.newInstance().newTransformer();
			t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			t.setOutputProperty(OutputKeys.INDENT, "yes");

			final File removalXml = new File(tmpDir, "removal.xml");
			try (FileOutputStream fos = new FileOutputStream(removalXml)) {
				t.transform(new DOMSource(doc), new StreamResult(fos));
			}

			try (FileTreeZipper ftz = new FileTreeZipper(tmpDirPath, destDir.toPath().resolve("scs-patch.bin"), false)) {
				Files.walkFileTree(tmpDirPath, ftz);
			}
			
			Files.walkFileTree(tmpDirPath, new FileTreeDeleter());
			
			try (PrintStream ps = new PrintStream(new FileOutputStream(destDir.toPath().resolve("release-notes.txt").toFile()))) {
				List<String> rnConnectors = new ArrayList<>(releaseNotes.keySet());
				List<String> rnConnectorVersions = new ArrayList<>();
				Collections.sort(rnConnectors, new Comparator<String>() {
					@Override
					public int compare(String o1, String o2) {
						if ("GSP Core".equals(o1) || "SCS Core".equals(o1)) return Integer.MIN_VALUE;
						return o1.toLowerCase().compareTo(o2.toLowerCase());
					}
				});
				for (String rnConnector : rnConnectors) {
					Map<String, String> versionNotes = releaseNotes.get(rnConnector);
					rnConnectorVersions.clear();
					rnConnectorVersions.addAll(versionNotes.keySet());
					Collections.sort(rnConnectorVersions, new Comparator<String>() {
						@Override
						public int compare(String o1, String o2) {
							String[] av1 = o1.split("\\.");
							String[] av2 = o2.split("\\.");
							int l1 = av1.length;
							int l2 = av2.length;
							int l = Math.min(l1, l2);
							for (int i=0; i<l; i++) {
								int i1 = Integer.parseInt(av1[i]);
								int i2 = Integer.parseInt(av2[i]);
								if (i1 != i2) {
									return i1 - i2;
								}
							}
							return l1 - l2;
						}
					});
					for (String rnConnectorVersion : rnConnectorVersions) {
						ps.print(rnConnector);
						ps.print("-v");
						ps.print(rnConnectorVersion);
						ps.println(":");
						ps.println(versionNotes.get(rnConnectorVersion));
						ps.println();
					}
					ps.println("#########################################");
					ps.println();
				}
			}
			

		} else throw new RuntimeException(RuntimeUtils.readTerminatedProcessOutput(p));

	}

	private static void processEntries(final String nodeName, final String pathFormat, final Map<String, Set<String>> connectorPaths, final Document doc, final Element root) {
		final Set<String> currentConnectorNames = connectorPaths.keySet();
		Set<String> paths = null;
		Element d = null;
		for (final String currentConnectorName : currentConnectorNames) {
			paths = connectorPaths.get(currentConnectorName);
			for (final String path : paths) {
				d = doc.createElement(nodeName);
				d.setAttribute("path", pathFormat.replace("${ConnectorName}", currentConnectorName).replace("${FileRelPath}", path));
				root.appendChild(d);
			}
		}
	}

	private static void processEntries(final String nodeName, final String pathFormat, final Set<String> paths, final Document doc, final Element root) {
		Element d = null;
		for (final String path : paths) {
			d = doc.createElement(nodeName);
			d.setAttribute("path", pathFormat.replace("${FileRelPath}", path));
			root.appendChild(d);
		}
	}

	private class ProcessStreamsConsumer implements Runnable {

		private final Process p;
		private boolean firstPass = true;

		final ByteArrayOutputStream i = new ByteArrayOutputStream();
		private final ByteArrayOutputStream e = new ByteArrayOutputStream();

		public ProcessStreamsConsumer(final Process p) {
			this.p = p;
			new Thread(this).start();
			new Thread(this).start();
		}

		@Override
		public void run() {

			boolean firstPass = false;
			synchronized (p) {
				firstPass = this.firstPass;
				if (firstPass) {
					this.firstPass = false;
				}
			}

			if (firstPass) {
				@SuppressWarnings("resource")
				final InputStream is = p.getInputStream();
				try {
					StreamUtils.transferBytes(is, i);
				} catch (final IOException e) {
					e.printStackTrace();
				}
			} else {
				@SuppressWarnings("resource")
				final InputStream es = p.getErrorStream();
				try {
					StreamUtils.transferBytes(es, e);
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}

		}

	}

}
