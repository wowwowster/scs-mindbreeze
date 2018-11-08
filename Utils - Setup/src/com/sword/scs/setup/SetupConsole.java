package com.sword.scs.setup;

import java.io.ByteArrayInputStream;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.ConfigurationException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

import sword.common.utils.EnvUtils;
import sword.common.utils.StringUtils;

import com.sword.scs.Constants;
import com.sword.scs.utils.LicMgr;
import com.sword.scs.utils.LicMgr.LicDate;

public class SetupConsole extends Setup {

	private final String cr = EnvUtils.CR;
	private final Console console;

	public SetupConsole(final Path rootDir, final String envPath) throws Exception {
		super(rootDir, envPath);
		console = System.console();
	}

	@Override
	public void start() throws Exception {
		while (!mainMenu()) {
			;
		}
	}

	private boolean mainMenu() throws Exception {

		Configuration conf = loadCurrentConfig();
		if (conf.isDefault()) {
			console.format("SCS base configuration is empty - redirecting to base configuration menu%s", cr);
			conf = baseConfig(conf);
		} else {
			console.format("Menu:%s", cr);
			console.format("\t1- Base configuration%s", cr);
			console.format("\t2- License%s", cr);
			console.format("\t3- Certificates%s", cr);
			console.format("\t4- Service%s", cr);
			console.format("\t5- Apply patch%s", cr);
			console.format("\t6- Exit%s", cr);
			final String respStr = console.readLine(">: ");
			if ("1".equals(respStr)) {
				conf = baseConfig(conf);
			} else if ("2".equals(respStr)) return licenseConfig(conf);
			else if ("3".equals(respStr)) return certifConfig(conf);
			else if ("4".equals(respStr)) return serviceConfig(conf);
			else if ("5".equals(respStr)) return patchConfig();
			else if ("6".equals(respStr)) return true;
			else {
				console.format("Unknown command %s%s", respStr, cr);
			}
		}

		return false;

	}

	private boolean licenseConfig(final Configuration conf) throws Exception {
		String s = null;
		do {
			s = licenseMenu(conf);
		} while (!("backtomain".equals(s) || "exit".equals(s)));
		return "exit".equals(s);
	}

	private String licenseMenu(final Configuration conf) throws Exception {

		final String lv = getLicenseValue();
		LicDate licDate = LicDate.UNKNOWN_ERROR;
		try {
			licDate = LicMgr.checkLicense(lv);
			if (licDate==null) licDate = LicDate.UNKNOWN_ERROR;
		} catch (final Throwable t) {
			licDate = LicDate.UNKNOWN_ERROR;
		}
		final StringBuilder message = new StringBuilder();
		if (!licDate.isValid) {
			message.append("License problem: ").append(licDate.dateString);
		} else if (!licDate.expires) {
			message.append("Permanent license");
		} else {
			message.append("License valid until ").append(licDate.dateString);
		}

		console.format("%s%s", message.toString(), cr);

		console.format("License:%s", cr);
		console.format("\t1- Export Instance ID%s", cr);
		console.format("\t2- Import License%s", cr);
		console.format("\t3- Back to main menu%s", cr);
		console.format("\t4- Exit%s", cr);
		final String respStr = console.readLine(">: ");
		if ("1".equals(respStr)) {
			final String path = console.readLine("Enter the path of the file where the Instance ID will be saved: ");
			final File file = new File(path);
			Files.copy(new ByteArrayInputStream(LicMgr.getInstanceID().getBytes(StandardCharsets.UTF_8)), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			console.format("Instance ID file saved: %s%s", file.getAbsolutePath(), cr);
			return "backtomain";
		} else if ("2".equals(respStr)) {
			final String path = console.readLine("Enter the path of the license file: ");
			updateLicense(new File(path).toPath());
			console.format("License updated successfully%s", cr);
			return "backtomain";
		} else if ("3".equals(respStr)) return "backtomain";
		else if ("4".equals(respStr)) return "exit";
		else {
			console.format("Unknown command %s%s", respStr, cr);
		}

		return "";

	}

	private Configuration baseConfig(final Configuration conf) throws XPathExpressionException, DOMException, NoSuchAlgorithmException, ParserConfigurationException, TransformerFactoryConfigurationError, SAXException, IOException, TransformerException {

		final String curHN = conf.getHostname();
		boolean canReuse = curHN != null;
		String hn = null;
		while (hn == null) {
			hn = console.readLine("Hostname%s>: ", canReuse ? " [" + curHN + "]" : "");
			if (canReuse && StringUtils.isNullOrEmpty(hn)) {
				hn = curHN;
			} else {
				try {
					Setup.checkHostname(hn);
				} catch (final ConfigurationException e) {
					console.format(e.getExplanation() + cr);
					hn = null;
				}
			}
		}

		final int curHP = conf.getHttpPort();
		canReuse = curHP > 0;
		int hp = -1;
		while (hp < 1024) {
			final String hpStr = console.readLine("HTTP port%s>: ", canReuse ? " [" + curHP + "]" : "");
			if (canReuse && StringUtils.isNullOrEmpty(hpStr)) {
				hp = curHP;
			} else {
				try {
					Setup.checkPort(hpStr);
					hp = Integer.parseInt(hpStr);
				} catch (final ConfigurationException e) {
					console.format(e.getExplanation() + cr);
					hp = -1;
				}
			}
		}

		final int curHSP = conf.getHttpsPort();
		canReuse = curHSP > 0;
		int hsp = -1;
		while (hsp < 1024) {
			final String hspStr = console.readLine("HTTPS port%s>: ", canReuse ? " [" + curHSP + "]" : "");
			if (canReuse && StringUtils.isNullOrEmpty(hspStr)) {
				hsp = curHSP;
			} else {
				try {
					Setup.checkPort(hspStr);
					hsp = Integer.parseInt(hspStr);
				} catch (final ConfigurationException e) {
					console.format(e.getExplanation() + cr);
					hsp = -1;
				}
			}
		}

		final int curSDP = conf.getShutdownPort();
		final int sdp = curSDP > 0 ? curSDP : getRandomAvailablePort();

		canReuse = conf.getAdminPasswordHash() != null;

		String newPwdHash = null;
		while (true) {
			final char[] ap = console.readPassword("Administration password%s>: ", canReuse ? " [************]" : "");
			if (ap == null || ap.length < 1) {
				if (canReuse) {
					newPwdHash = Configuration.DO_NOT_CHANGE;
					break;
				}
			} else {
				newPwdHash = Configuration.hashPassword(new String(ap));
				break;
			}
		}

		final Configuration newConf = new Configuration(hn, "" + hp, "" + hsp, "" + sdp, newPwdHash);
		updateSCSConfig(newConf);
		return newConf;

	}

	private boolean certifConfig(final Configuration conf) throws IOException, InterruptedException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
		String s = null;
		do {
			s = certificatesMenu(conf);
		} while (!("8".equals(s) || "9".equals(s)));
		return "9".equals(s);
	}

	private String certificatesMenu(final Configuration conf) throws IOException, InterruptedException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {

		final X509Certificate sc = getCertificate(Constants.SERVER_KS_ENTRY_NAME);
		String cn = sc.getSubjectDN().getName();
		final Date notAfter = sc.getNotAfter();

		final Pattern cnExtract = Pattern.compile("CN=([^,]+)");
		final Matcher m = cnExtract.matcher(cn);
		if (m.find()) {
			cn = m.group(1);
		}

		console.format("Certificates:%s", cr);
		console.format("[Current certificate generated for %s and valid until %s]%s", cn, CERTIF_DATES.format(notAfter), cr);
		console.format("\tA - Currently installed server certificate%s", cr);
		console.format("\t\t1- Generate new%s", cr);
		console.format("\t\t2- Export public key%s", cr);
		console.format("\t\t3- Export a CSR%s", cr);
		console.format("\t\t4- Install CSR reply%s", cr);
		console.format("\t\t5- Import a JAVA keystore%s", cr);
		console.format("\tB - SAML Keys%s", cr);
		console.format("\t\t6- Generate new keys%s", cr);
		console.format("\t\t7- Export public key%s", cr);
		console.format("\tC - Misc%s", cr);
		console.format("\t\t8- Back to main menu%s", cr);
		console.format("\t\t9- Exit%s", cr);
		final String respStr = console.readLine(">: ");
		if ("1".equals(respStr)) {
			generateSelfSignedCertificate(conf.getHostname(), Constants.SERVER_KS_ENTRY_NAME);
			console.format("A new server certificate was successfully generated%s", cr);
		} else if ("2".equals(respStr)) {
			final String path = console.readLine("Enter the path of the file where the public key will be saved: ");
			final File file = new File(path);
			Files.copy(new ByteArrayInputStream(viewPublicKey(Constants.SERVER_KS_ENTRY_NAME).getBytes(StandardCharsets.UTF_8)), file.toPath());
			console.format("Success%s", cr);
		} else if ("3".equals(respStr)) {
			final String path = console.readLine("Enter the path of the file where the CSR will be saved: ");
			final File file = new File(path);
			exportCsr(Constants.SERVER_KS_ENTRY_NAME, file);
			console.format("Success%s", cr);
		} else if ("4".equals(respStr)) {
			final String path = console.readLine("Enter the path of the combined certificate chain file: ");
			final File file = new File(path);
			importCsrReply(Constants.SERVER_KS_ENTRY_NAME, file);
			console.format("Success%s", cr);
		} else if ("5".equals(respStr)) {
			final String path = console.readLine("Enter the path of the JAVA keystore: ");
			final File file = new File(path);
			importJavaKeystore(file);
			console.format("Success%s", cr);
		} else if ("6".equals(respStr)) {
			generateSelfSignedCertificate("saml.gsp", Constants.SAML_KS_ENTRY_NAME);
			console.format("A new SAML keypair was successfully generated%s", cr);
		} else if ("7".equals(respStr)) {
			final String path = console.readLine("Enter the path of the file where the public key will be saved: ");
			final File file = new File(path);
			Files.copy(new ByteArrayInputStream(viewPublicKey(Constants.SAML_KS_ENTRY_NAME).getBytes(StandardCharsets.UTF_8)), file.toPath());
			console.format("Success%s", cr);
		} else if ("8".equals(respStr) || "9".equals(respStr)) {
			// do nothing
		} else {
			console.format("Unknown command %s%s", respStr, cr);
		}
		return respStr;
	}

	private boolean serviceConfig(final Configuration conf) throws Exception {
		String s = null;
		do {
			s = serviceMenu(conf);
		} while (!("backtomain".equals(s) || "exit".equals(s)));
		return "exit".equals(s);
	}

	private String serviceMenu(final Configuration conf) throws Exception {
		final String svcName = svcMgr.getServiceName();
		boolean isInstalled = false;
		if (svcName != null) {
			isInstalled = svcMgr.isInstalled(svcName);
		}
		boolean isStarted = false;
		if (isInstalled) {
			isStarted = svcMgr.isRunning(svcName);
		}

		if (isInstalled) {
			if (isStarted) {
				console.format("SCS Service:%s", cr);
				console.format("\t1- View configuration page URL%s", cr);
				console.format("\t2- Restart%s", cr);
				console.format("\t3- Stop%s", cr);
				console.format("\t4- Back to main menu%s", cr);
				console.format("\t5- Exit%s", cr);
				final String respStr = console.readLine(">: ");
				if ("1".equals(respStr)) {
					console.format("\t-> SCS configuration page: %s%s", getConfigurationURL(conf), cr);
				} else if ("2".equals(respStr) || "3".equals(respStr)) {
					final List<Path> runningIndexers = getRunningIndexers();
					if (runningIndexers.size() > 0) {
						final String yn = console.readLine("%d indexers are running - do you want to %s them too? (y/N)>: ", runningIndexers.size(), "2".equals(respStr) ? "restart" : "stop");
						if ("y".equals(yn) || "Y".equals(yn)) {
							killIndexers(runningIndexers);
						}
					}
					if ("2".equals(respStr)) {
						restartService(svcName);
					} else {
						svcMgr.stopSvc(svcName);
					}
				} else if ("4".equals(respStr)) return "backtomain";
				else if ("5".equals(respStr)) return "exit";
				else {
					console.format("Unknown command %s%s", respStr, cr);
				}
				return "";
			} else {
				console.format("SCS Service:%s", cr);
				console.format("\t1- Delete%s", cr);
				console.format("\t2- Start%s", cr);
				console.format("\t3- Back to main menu%s", cr);
				console.format("\t4- Exit%s", cr);
				final String respStr = console.readLine(">: ");
				if ("1".equals(respStr)) {
					svcMgr.deleteSvc(svcName);
				} else if ("2".equals(respStr)) {
					svcMgr.startSvc(svcName);
				} else if ("3".equals(respStr)) return "backtomain";
				else if ("4".equals(respStr)) return "exit";
				else {
					console.format("Unknown command %s%s", respStr, cr);
				}
				return "";
			}
		} else {
			console.format("SCS Service:%s", cr);
			console.format("\t1- Create%s", cr);
			console.format("\t2- Back to main menu%s", cr);
			console.format("\t3- Exit%s", cr);
			final String respStr = console.readLine(">: ");
			if ("1".equals(respStr)) {
				int i = 1;
				while (svcMgr.isInstalled(Constants.SCS_SVC_BASENAME + i)) {
					i++;
				}
				svcMgr.createSvc(Constants.SCS_SVC_BASENAME + i);
				console.format("Service created successfully%s", cr);
			} else if ("2".equals(respStr)) return "backtomain";
			else if ("3".equals(respStr)) return "exit";
			else {
				console.format("Unknown command %s%s", respStr, cr);
			}
			return "";
		}
	}

	private boolean patchConfig() throws Exception {
		final String patchFilePath = console.readLine("Enter patch file path >: ");
		applyPatch(new File(patchFilePath).toPath());
		return true;
	}

	@Override
	protected void notifyAppClosing() {
		console.readLine("SCS service will be stopped, setup application will close and the patch will be applied - press the return key to continue...%s", cr);
	}

	@Override
	protected void closeApp() {}

}
