package com.sword.scs.setup;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.ConfigurationException;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.xpath.XPathExpressionException;

import org.xml.sax.SAXException;

import sword.common.utils.StringUtils;
import sword.common.utils.streams.StreamUtils;
import sword.common.utils.ui.Constant;
import sword.common.utils.ui.UIUtils;
import sword.common.utils.ui.components.WebHelpBtn;
import sword.common.utils.ui.frames.SwpFrame;

import com.sword.scs.Constants;
import com.sword.scs.utils.LicMgr;
import com.sword.scs.utils.LicMgr.LicDate;

public class SetupGUI extends Setup implements ActionListener {

	private static final Dimension WIN_MIN_SIZE = new Dimension(600, 300);
	private static final Dimension WIN_MAX_SIZE = new Dimension(680, 420);
	private static final Dimension LBL_SIZE = new Dimension(160, 22);
	private static final Dimension HELP_BTN_SIZE = new Dimension(24, 20);

	private static final String AC_SAVE_CONFIG = "ManagerGUI01";
	private static final String AC_SRV_CERT_NEW = "ManagerGUI02";
	private static final String AC_SRV_CERT_EXPORT = "ManagerGUI03";
	private static final String AC_SAML_KEYS_NEW = "ManagerGUI04";
	private static final String AC_SAML_KEYS_EXPORT = "ManagerGUI05";
	private static final String AC_SVC_START = "ManagerGUI06";
	private static final String AC_SVC_RESTART = "ManagerGUI07";
	private static final String AC_SVC_STOP = "ManagerGUI08";
	private static final String AC_SVC_CR_DEL = "ManagerGUI09";
	private static final String AC_GOTO_CONF = "ManagerGUI10";
	private static final String AC_SAVE_KRB_CONFIG = "ManagerGUI11";
	private static final String AC_UPLOAD_KRB_KEYTAB = "ManagerGUI12";
	private static final String AC_SRV_CERT_CSR = "ManagerGUI13";
	private static final String AC_SRV_CERT_REPLY = "ManagerGUI14";
	private static final String AC_EXPORT_IID = "ManagerGUI15";
	private static final String AC_IMPORT_LIC = "ManagerGUI16";
	private static final String AC_UPLOAD_PATCH = "ManagerGUI17";
	private static final String AC_SRV_KS_IMPORT = "ManagerGUI18";

	final JTabbedPane mainPane = UIUtils.toSwpStyle(new JTabbedPane(), false, true);

	final JTextField hostnameTF = UIUtils.toSwpStyle(new JTextField(), true, true);
	final JTextField httpPortTF = UIUtils.toSwpStyle(new JTextField(), true, true);
	final JTextField httpsPortTF = UIUtils.toSwpStyle(new JTextField(), true, true);
	final JPasswordField adminPwdTF = UIUtils.toSwpStyle(new JPasswordField(), true, true);

	final JButton saveBtn = UIUtils.toSwpStyle(new JButton("Save"), false, true);

	final JLabel licStateLbl = UIUtils.toSwpStyle(new JLabel("<html><span>Unknown</span></html>", SwingConstants.CENTER), false, true);

	private final JLabel serverCertHost = UIUtils.toSwpStyle(new JLabel("", SwingConstants.LEFT), false, true);
	private final JLabel serverCertDate = UIUtils.toSwpStyle(new JLabel("", SwingConstants.LEFT), false, true);
	private final JButton serverCertGenNew = UIUtils.toSwpStyle(new JButton("Generate new"), false, true);
	private final JButton serverCertExport = UIUtils.toSwpStyle(new JButton("Export public key"), false, true);
	private final JButton serverCertCsr = UIUtils.toSwpStyle(new JButton("Export a CSR"), false, true);
	private final JButton serverCertReply = UIUtils.toSwpStyle(new JButton("Install CSR reply"), false, true);
	private final JButton serverImportKS = UIUtils.toSwpStyle(new JButton("Import JAVA Keystore"), false, true);

	private final JButton samlKeysGenNew = UIUtils.toSwpStyle(new JButton("Generate new keys"), false, true);
	private final JButton samlKeysExport = UIUtils.toSwpStyle(new JButton("Export public key"), false, true);

	final JTextField krbRealm = UIUtils.toSwpStyle(new JTextField(), true, true);
	final JTextField krbHostname = UIUtils.toSwpStyle(new JTextField(), true, true);
	final JTextField kdcHostname = UIUtils.toSwpStyle(new JTextField(), true, true);
	final JTextField keytabPath = UIUtils.toSwpStyle(new JTextField(), false, true);
	private final JButton keytabBBtn = UIUtils.toSwpStyle(new JButton("Browse"), false, true);

	private final JButton svcCrDel = UIUtils.toSwpStyle(new JButton("Delete"), false, true);
	final JButton svcStart = UIUtils.toSwpStyle(new JButton("Start"), false, true);
	private final JButton svcRestart = UIUtils.toSwpStyle(new JButton("Restart"), false, true);
	private final JButton svcStop = UIUtils.toSwpStyle(new JButton("Stop"), false, true);
	private final JButton goToConf = UIUtils.toSwpStyle(new JButton("Open configuration page"), false, true);

	private final JButton exportIidBtn = UIUtils.toSwpStyle(new JButton("Browse"), false, true);
	private final JButton importLicBtn = UIUtils.toSwpStyle(new JButton("Browse"), false, true);

	private final JButton uploadPatchBtn = UIUtils.toSwpStyle(new JButton("Browse"), false, true);

	private final ImageIcon helpIcon;

	final SwpFrame win;

	Configuration lastKnownConf = null;

	public SetupGUI(final Path rootDir, final String envPath) throws IOException, TransformerConfigurationException, XPathExpressionException, ParserConfigurationException, TransformerFactoryConfigurationError, SAXException, InterruptedException, GeneralSecurityException {
		super(rootDir, envPath);
		lastKnownConf = loadCurrentConfig();
		helpIcon = _getHelpIcon();
		win = new SwpFrame("SCS Setup", Color.WHITE) {};
		win.getFrame().setMinimumSize(WIN_MIN_SIZE);
		win.getFrame().setMaximumSize(WIN_MAX_SIZE);
		win.getContentPane().setLayout(new BorderLayout());
		mainPane.addTab("Base configuration", getBaseConfigPan());
		mainPane.addTab("License", getLicensePan());
		mainPane.addTab("Certificates", getCertificatesPan());
		mainPane.addTab("Kerberos", getKerberosPan());
		mainPane.addTab("Service", getServicePan());
		mainPane.addTab("Patch", getPatchPan());
		if (!StringUtils.isNullOrEmpty(lastKnownConf.getHostname())) {
			hostnameTF.setText(lastKnownConf.getHostname());
		}
		if (lastKnownConf.getHttpPort() > 0) {
			httpPortTF.setText("" + lastKnownConf.getHttpPort());
		}
		if (lastKnownConf.getHttpsPort() > 0) {
			httpsPortTF.setText("" + lastKnownConf.getHttpsPort());
		}
		if (!StringUtils.isNullOrEmpty(lastKnownConf.getAdminPasswordHash())) {
			adminPwdTF.setText(Configuration.DO_NOT_CHANGE);
		}
	}

	private static ImageIcon _getHelpIcon() throws IOException {
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		StreamUtils.transferBytes(SetupGUI.class.getResourceAsStream("/images/help.png"), os);
		return new ImageIcon(os.toByteArray(), "Help");
	}

	private JPanel getBaseConfigPan() {
		final JPanel p0 = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);

		final JPanel help = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), true, false);
		final WebHelpBtn helpBtn = UIUtils.toSwpStyle(new WebHelpBtn(helpIcon, rootDir.resolve("webapps/documentation/pages/redir/BaseHttpConf.html").toUri()), false, true);
		helpBtn.setPreferredSize(HELP_BTN_SIZE);
		help.add(helpBtn, BorderLayout.EAST);
		p0.add(help, BorderLayout.NORTH);

		final JPanel p = UIUtils.toSwpStyle(new JPanel(), false, false);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		{
			final GridBagLayout gbl = new GridBagLayout();
			final GridBagConstraints gbc = new GridBagConstraints();

			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(gbl), true, false);

			final JLabel lbl = UIUtils.toSwpStyle(new JLabel("Hostname: ", SwingConstants.RIGHT), false, true);
			lbl.setPreferredSize(LBL_SIZE);
			gbc.gridwidth = 1;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			gbl.setConstraints(lbl, gbc);
			p1.add(lbl);

			gbc.gridwidth = 5;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbl.setConstraints(hostnameTF, gbc);
			p1.add(hostnameTF);

			// final JButton help = UIUtils.toSwpStyle(new HelpBtn(_getHelpIcon(), new JEditorPane("text/html", String.format(HELP_BASE,
			// "For instance if the server host name is <i>myhost</i> and the domain suffix is <i>mydomain.com</i>, the fully qualified domain name of the server is <i>myhost.domain.com</i>."))),
			// false, false);
			// help.setPreferredSize(HELP_BTN_SIZE);
			// gbc.gridwidth = 1;
			// gbc.weightx = 0;
			// gbc.fill = GridBagConstraints.NONE;
			// gbl.setConstraints(help, gbc);
			// p1.add(help);

			p.add(p1);
		}
		{
			final GridBagLayout gbl = new GridBagLayout();
			final GridBagConstraints gbc = new GridBagConstraints();

			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(gbl), true, false);

			final JLabel lbl = UIUtils.toSwpStyle(new JLabel("HTTP port: ", SwingConstants.RIGHT), false, true);
			lbl.setPreferredSize(LBL_SIZE);
			gbc.gridwidth = 1;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			gbl.setConstraints(lbl, gbc);
			p1.add(lbl);

			gbc.gridwidth = 5;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbl.setConstraints(httpPortTF, gbc);
			p1.add(httpPortTF);

			// final JButton help = UIUtils.toSwpStyle(new HelpBtn(_getHelpIcon(), new JEditorPane("text/html", String.format(HELP_BASE,
			// "HTTP port number.<br>On Linux systems, ports lower than 1024 cannot be bound without root privileges and running a web server as root is <b>strongly</b> discouraged.<br>If you want the SCS to use port 80 for HTTP, it is recommended to change the routing table configuration to re-route port 80 to the SCS HTTP port."))),
			// false, false);
			// help.setPreferredSize(HELP_BTN_SIZE);
			// gbc.gridwidth = 1;
			// gbc.weightx = 0;
			// gbc.fill = GridBagConstraints.NONE;
			// gbl.setConstraints(help, gbc);
			// p1.add(help);

			p.add(p1);
		}
		{
			final GridBagLayout gbl = new GridBagLayout();
			final GridBagConstraints gbc = new GridBagConstraints();

			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(gbl), true, false);

			final JLabel lbl = UIUtils.toSwpStyle(new JLabel("HTTPS port: ", SwingConstants.RIGHT), false, true);
			lbl.setPreferredSize(LBL_SIZE);
			gbc.gridwidth = 1;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			gbl.setConstraints(lbl, gbc);
			p1.add(lbl);

			gbc.gridwidth = 5;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbl.setConstraints(httpsPortTF, gbc);
			p1.add(httpsPortTF);

			// final JButton help = UIUtils.toSwpStyle(new HelpBtn(_getHelpIcon(), new JEditorPane("text/html", String.format(HELP_BASE,
			// "HTTPS port number.<br>On Linux systems, ports lower than 1024 cannot be bound without root privileges and running a web server as root is <b>strongly</b> discouraged.<br>If you want the SCS to use port 443 for HTTPS, it is recommended to change the routing table configuration to re-route port 443 to the SCS HTTPS port."))),
			// false, false);
			// help.setPreferredSize(HELP_BTN_SIZE);
			// gbc.gridwidth = 1;
			// gbc.weightx = 0;
			// gbc.fill = GridBagConstraints.NONE;
			// gbl.setConstraints(help, gbc);
			// p1.add(help);

			p.add(p1);
		}
		{
			final GridBagLayout gbl = new GridBagLayout();
			final GridBagConstraints gbc = new GridBagConstraints();

			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(gbl), true, false);

			final JLabel lbl = UIUtils.toSwpStyle(new JLabel("Administration password: ", SwingConstants.RIGHT), false, true);
			lbl.setPreferredSize(LBL_SIZE);
			gbc.gridwidth = 1;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			gbl.setConstraints(lbl, gbc);
			p1.add(lbl);

			gbc.gridwidth = 5;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbl.setConstraints(adminPwdTF, gbc);
			p1.add(adminPwdTF);

			// final JButton help = UIUtils.toSwpStyle(new HelpBtn(_getHelpIcon(), new JEditorPane("text/html", String.format(HELP_BASE,
			// "This password will be necessary to access the SCS administration web interface."))), false, false);
			// help.setPreferredSize(HELP_BTN_SIZE);
			// gbc.gridwidth = 1;
			// gbc.weightx = 0;
			// gbc.fill = GridBagConstraints.NONE;
			// gbl.setConstraints(help, gbc);
			// p1.add(help);

			p.add(p1);
		}
		{
			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(), true, false);
			saveBtn.setPreferredSize(new Dimension(120, 24));
			saveBtn.setActionCommand(AC_SAVE_CONFIG);
			saveBtn.addActionListener(this);
			p1.add(saveBtn);
			p.add(p1);

		}
		p0.add(p, BorderLayout.CENTER);
		return p0;
	}

	private JPanel getCertificatesPan() throws KeyStoreException, FileNotFoundException, NoSuchAlgorithmException, CertificateException, IOException {
		final JPanel p0 = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);
		{
			final JPanel help = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), true, false);
			final WebHelpBtn helpBtn = UIUtils.toSwpStyle(new WebHelpBtn(helpIcon, rootDir.resolve("webapps/documentation/pages/redir/KeysConf.html").toUri()), false, true);
			helpBtn.setPreferredSize(HELP_BTN_SIZE);
			help.add(helpBtn, BorderLayout.EAST);
			p0.add(help, BorderLayout.NORTH);
		}
		final Dimension lblSize = new Dimension(90, 22);
		final Dimension btnSize = new Dimension(160, 18);
		{
			final GridBagLayout glb = new GridBagLayout();
			final GridBagConstraints gbc = new GridBagConstraints();
			final JPanel p = UIUtils.toSwpStyle(new JPanel(glb), false, false);
			final TitledBorder tb = new TitledBorder("Currently installed server certificate");
			tb.setTitleColor(Constant.SWORD_BLUE);
			p.setBorder(tb);

			final JLabel hostLbl = UIUtils.toSwpStyle(new JLabel("Host: ", SwingConstants.RIGHT), true, true);
			hostLbl.setPreferredSize(lblSize);
			gbc.weightx = 0;
			gbc.gridwidth = 1;
			glb.setConstraints(hostLbl, gbc);
			p.add(hostLbl);

			gbc.weightx = 1;
			gbc.gridwidth = GridBagConstraints.REMAINDER;
			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), true, false);
			p1.setPreferredSize(new Dimension(300, 22));
			p1.add(serverCertHost, BorderLayout.WEST);
			glb.setConstraints(p1, gbc);
			p.add(p1);

			final JLabel dateLbl = UIUtils.toSwpStyle(new JLabel("Valid until: ", SwingConstants.RIGHT), true, true);
			dateLbl.setPreferredSize(lblSize);
			gbc.weightx = 0;
			gbc.gridwidth = 1;
			glb.setConstraints(dateLbl, gbc);
			p.add(dateLbl);

			gbc.weightx = 1;
			gbc.gridwidth = GridBagConstraints.REMAINDER;
			final JPanel p2 = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), true, false);
			p2.setPreferredSize(new Dimension(300, 22));
			p2.add(serverCertDate, BorderLayout.WEST);
			glb.setConstraints(p2, gbc);
			p.add(p2);

			final JPanel btnPan = UIUtils.toSwpStyle(new JPanel(), true, false);
			serverCertGenNew.setPreferredSize(btnSize);
			serverCertGenNew.setActionCommand(AC_SRV_CERT_NEW);
			serverCertGenNew.addActionListener(this);
			btnPan.add(serverCertGenNew);
			// final JButton helpGenNew = UIUtils.toSwpStyle(new HelpBtn(_getHelpIcon(), new JEditorPane("text/html", String.format(HELP_BASE,
			// "Will create a new self-signed certificate and a private key for the server. This certificate will then be used by the server for all HTTPS connections."))), false, false);
			// helpGenNew.setPreferredSize(HELP_BTN_SIZE);
			// btnPan.add(helpGenNew);
			serverCertExport.setPreferredSize(btnSize);
			serverCertExport.setActionCommand(AC_SRV_CERT_EXPORT);
			serverCertExport.addActionListener(this);
			btnPan.add(serverCertExport);
			// final JButton helpExport = UIUtils.toSwpStyle(new HelpBtn(_getHelpIcon(), new JEditorPane("text/html", String.format(HELP_BASE,
			// "Will save the certificate in a PEM format. If you do not plan to have this certificate signed by a certificate authority, you can import the file in the GSA Certificate Authorities list in order to allow SSL communications between the GSA and the connector."))),
			// false, false);
			// helpExport.setPreferredSize(HELP_BTN_SIZE);
			// btnPan.add(helpExport);
			gbc.weightx = 0;
			gbc.gridwidth = GridBagConstraints.REMAINDER;
			glb.setConstraints(btnPan, gbc);
			p.add(btnPan);

			final JPanel btnPan2 = UIUtils.toSwpStyle(new JPanel(), true, false);
			serverCertCsr.setPreferredSize(btnSize);
			serverCertCsr.setActionCommand(AC_SRV_CERT_CSR);
			serverCertCsr.addActionListener(this);
			btnPan2.add(serverCertCsr);
			// final JButton helpCsr = UIUtils.toSwpStyle(new HelpBtn(_getHelpIcon(), new JEditorPane("text/html", String.format(HELP_BASE,
			// "Will save a CSR file that you can send to your Certification Authority for signing. Once you obtain a signed certificate from your CA, you can install the certificate reply for the server to use the newly signed certificate."))),
			// false, false);
			// helpCsr.setPreferredSize(HELP_BTN_SIZE);
			// btnPan2.add(helpCsr);
			serverCertReply.setPreferredSize(btnSize);
			serverCertReply.setActionCommand(AC_SRV_CERT_REPLY);
			serverCertReply.addActionListener(this);
			btnPan2.add(serverCertReply);
			// final JButton helpReply = UIUtils.toSwpStyle(new HelpBtn(_getHelpIcon(), new JEditorPane("text/html", String.format(WIDE_HELP_BASE,
			// "Installs the signed certificate supplied by your CA. The CSR reply needs to contain the entire certificate chain in base64 encoded PEM format - for this you need to combine the host certificate, intermediate CA certificates, and the root CA certificate in a single file. The certificates must be in the following order:<div style=\"padding-left: 32px; color: black\" ><br/>-----BEGIN CERTIFICATE-----<br/>Host certificate<br/>-----END CERTIFICATE-----<br/>-----BEGIN CERTIFICATE-----<br/>Intermediate certificate-1<br/>-----END CERTIFICATE-----<br/>-----BEGIN CERTIFICATE-----<br/>Intermediate certificate-2<br/>-----END CERTIFICATE-----<br/>-----BEGIN CERTIFICATE-----<br/>Root certificate<br/>-----END CERTIFICATE-----</div>"))),
			// false, false);
			// helpReply.setPreferredSize(HELP_BTN_SIZE);
			// btnPan2.add(helpReply);
			serverImportKS.setPreferredSize(btnSize);
			serverImportKS.setActionCommand(AC_SRV_KS_IMPORT);
			serverImportKS.addActionListener(this);
			btnPan2.add(serverImportKS);
			gbc.weightx = 0;
			gbc.gridwidth = GridBagConstraints.REMAINDER;
			glb.setConstraints(btnPan2, gbc);
			p.add(btnPan2);

			p0.add(p, BorderLayout.CENTER);

		}
		{
			final JPanel p = UIUtils.toSwpStyle(new JPanel(), false, false);
			final TitledBorder tb = new TitledBorder("SAML Keys");
			tb.setTitleColor(Constant.SWORD_BLUE);
			p.setBorder(tb);

			samlKeysGenNew.setActionCommand(AC_SAML_KEYS_NEW);
			samlKeysGenNew.addActionListener(this);
			samlKeysGenNew.setPreferredSize(btnSize);
			samlKeysExport.setActionCommand(AC_SAML_KEYS_EXPORT);
			samlKeysExport.addActionListener(this);
			samlKeysExport.setPreferredSize(btnSize);
			p.add(samlKeysGenNew);
			p.add(samlKeysExport);

			p0.add(p, BorderLayout.SOUTH);
		}

		updateCertInfo();

		return p0;
	}

	private JPanel getKerberosPan() throws IOException {
		final JPanel p0 = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);

		final JPanel help = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), true, false);
		final WebHelpBtn helpBtn = UIUtils.toSwpStyle(new WebHelpBtn(helpIcon, rootDir.resolve("webapps/documentation/pages/redir/KrbConf.html").toUri()), false, true);
		helpBtn.setPreferredSize(HELP_BTN_SIZE);
		help.add(helpBtn, BorderLayout.EAST);
		p0.add(help, BorderLayout.NORTH);

		final JPanel p = UIUtils.toSwpStyle(new JPanel(), false, false);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		{
			final GridBagLayout gbl = new GridBagLayout();
			final GridBagConstraints gbc = new GridBagConstraints();

			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(gbl), true, false);

			final JLabel lbl = UIUtils.toSwpStyle(new JLabel("Kerberos REALM: ", SwingConstants.RIGHT), false, true);
			lbl.setPreferredSize(LBL_SIZE);
			gbc.gridwidth = 1;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			gbl.setConstraints(lbl, gbc);
			p1.add(lbl);

			gbc.gridwidth = 5;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbl.setConstraints(krbRealm, gbc);
			p1.add(krbRealm);

			// final JButton help = UIUtils.toSwpStyle(new HelpBtn(_getHelpIcon(), new JEditorPane("text/html", String.format(HELP_BASE,
			// "Kerberos REALM usually has the form of an upper case domain suffix (without its leading dot) e.g.: GOOGLE.COM"))), false, false);
			// help.setPreferredSize(HELP_BTN_SIZE);
			// gbc.gridwidth = 1;
			// gbc.weightx = 0;
			// gbc.fill = GridBagConstraints.NONE;
			// gbl.setConstraints(help, gbc);
			// p1.add(help);

			p.add(p1);
		}
		{
			final GridBagLayout gbl = new GridBagLayout();
			final GridBagConstraints gbc = new GridBagConstraints();

			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(gbl), true, false);

			final JLabel lbl = UIUtils.toSwpStyle(new JLabel("SPN Hostname: ", SwingConstants.RIGHT), false, true);
			lbl.setPreferredSize(LBL_SIZE);
			gbc.gridwidth = 1;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			gbl.setConstraints(lbl, gbc);
			p1.add(lbl);

			gbc.gridwidth = 5;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbl.setConstraints(krbHostname, gbc);
			p1.add(krbHostname);

			// final JButton help = UIUtils.toSwpStyle(new HelpBtn(_getHelpIcon(), new JEditorPane("text/html", String.format(HELP_BASE,
			// "The hostname that has been utilized during the SPN creation. It is usually the fully qualified domain name configured in the base configuration section unless the SCS is being used behind a load balancer ; in which case it is the load balancer F.Q.D.N."))),
			// false, false);
			// help.setPreferredSize(HELP_BTN_SIZE);
			// gbc.gridwidth = 1;
			// gbc.weightx = 0;
			// gbc.fill = GridBagConstraints.NONE;
			// gbl.setConstraints(help, gbc);
			// p1.add(help);

			p.add(p1);
		}
		{
			final GridBagLayout gbl = new GridBagLayout();
			final GridBagConstraints gbc = new GridBagConstraints();

			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(gbl), true, false);

			final JLabel lbl = UIUtils.toSwpStyle(new JLabel("KDC Hostname: ", SwingConstants.RIGHT), false, true);
			lbl.setPreferredSize(LBL_SIZE);
			gbc.gridwidth = 1;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			gbl.setConstraints(lbl, gbc);
			p1.add(lbl);

			gbc.gridwidth = 5;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbl.setConstraints(kdcHostname, gbc);
			p1.add(kdcHostname);

			p.add(p1);
		}
		{
			final GridBagLayout gbl = new GridBagLayout();
			final GridBagConstraints gbc = new GridBagConstraints();

			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(gbl), true, false);

			final JLabel lbl = UIUtils.toSwpStyle(new JLabel("Keytab file: ", SwingConstants.RIGHT), false, true);
			lbl.setPreferredSize(LBL_SIZE);
			gbc.gridwidth = 1;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			gbl.setConstraints(lbl, gbc);
			p1.add(lbl);

			gbc.gridwidth = 5;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbl.setConstraints(keytabPath, gbc);

			p1.add(keytabPath);

			keytabBBtn.setActionCommand(AC_UPLOAD_KRB_KEYTAB);
			keytabBBtn.addActionListener(this);
			keytabBBtn.setPreferredSize(new Dimension(90, 20));
			gbc.gridwidth = 1;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			gbl.setConstraints(keytabBBtn, gbc);
			p1.add(keytabBBtn);

			p.add(p1);
		}
		{
			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(), true, false);
			final JButton krbSave = UIUtils.toSwpStyle(new JButton("Save"), false, true);
			krbSave.setPreferredSize(new Dimension(120, 24));
			krbSave.setActionCommand(AC_SAVE_KRB_CONFIG);
			krbSave.addActionListener(this);
			p1.add(krbSave);
			p.add(p1);
		}
		final KrbConf kc = getKrbConfig();
		if (kc != null) {
			kdcHostname.setText(kc.kdc);
			krbRealm.setText(kc.realm);
			krbHostname.setText(kc.hostname);
		}
		p0.add(p, BorderLayout.CENTER);
		return p0;
	}

	void updateCertInfo() throws KeyStoreException, FileNotFoundException, NoSuchAlgorithmException, CertificateException, IOException {

		final X509Certificate sc = getCertificate(Constants.SERVER_KS_ENTRY_NAME);
		String cn = sc.getSubjectDN().getName();
		final Date notAfter = sc.getNotAfter();

		final Pattern cnExtract = Pattern.compile("CN=([^,]+)");
		final Matcher m = cnExtract.matcher(cn);
		if (m.find()) {
			cn = m.group(1);
		}

		serverCertHost.setText(cn);
		if (!cn.equals(lastKnownConf.getHostname())) {
			serverCertHost.setToolTipText("Certificate host does not match the hostname configured in the server " + cn + " != " + lastKnownConf.getHostname() + " .");
			serverCertHost.setForeground(new Color(255, 127, 33));
		} else {
			serverCertHost.setToolTipText("");
			serverCertHost.setForeground(Constant.SWORD_BLUE);
		}

		serverCertDate.setText(CERTIF_DATES.format(notAfter));
		if (notAfter.before(new Date())) {
			serverCertDate.setForeground(new Color(255, 127, 33));
		} else {
			serverCertDate.setForeground(Constant.SWORD_BLUE);
		}

	}

	private JPanel getServicePan() throws IOException, InterruptedException {
		final JPanel p0 = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);

		final JPanel help = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), true, false);
		final WebHelpBtn helpBtn = UIUtils.toSwpStyle(new WebHelpBtn(helpIcon, rootDir.resolve("webapps/documentation/pages/redir/SCSSvc.html").toUri()), false, true);
		helpBtn.setPreferredSize(HELP_BTN_SIZE);
		help.add(helpBtn, BorderLayout.EAST);
		p0.add(help, BorderLayout.NORTH);

		final JPanel p = UIUtils.toSwpStyle(new JPanel(new GridLayout(6, 1)), false, false);
		{
			goToConf.setPreferredSize(new Dimension(200, 22));
			goToConf.setActionCommand(AC_GOTO_CONF);
			goToConf.addActionListener(this);
			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(), true, false);
			p1.add(goToConf);
			p.add(p1);
		}

		{
			svcCrDel.setPreferredSize(new Dimension(120, 22));
			svcCrDel.setActionCommand(AC_SVC_CR_DEL);
			svcCrDel.addActionListener(this);
			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(), true, false);
			p1.add(svcCrDel);
			p.add(p1);
		}

		{
			svcStart.setPreferredSize(new Dimension(120, 22));
			svcStart.setActionCommand(AC_SVC_START);
			svcStart.addActionListener(this);
			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(), true, false);
			p1.add(svcStart);
			p.add(p1);
		}

		{
			svcRestart.setPreferredSize(new Dimension(120, 22));
			svcRestart.setActionCommand(AC_SVC_RESTART);
			svcRestart.addActionListener(this);
			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(), true, false);
			p1.add(svcRestart);
			p.add(p1);
		}

		{
			svcStop.setPreferredSize(new Dimension(120, 22));
			svcStop.setActionCommand(AC_SVC_STOP);
			svcStop.addActionListener(this);
			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(), true, false);
			p1.add(svcStop);
			p.add(p1);
			updateServiceInfo();
		}
		p0.add(p, BorderLayout.CENTER);
		return p0;
	}

	private JPanel getLicensePan() throws IOException {
		final JPanel p0 = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);

		final JPanel help = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), true, false);
		final WebHelpBtn helpBtn = UIUtils.toSwpStyle(new WebHelpBtn(helpIcon, rootDir.resolve("webapps/documentation/pages/redir/LicenseMgt.html").toUri()), false, true);
		helpBtn.setPreferredSize(HELP_BTN_SIZE);
		help.add(helpBtn, BorderLayout.EAST);
		p0.add(help, BorderLayout.NORTH);

		final JPanel p = UIUtils.toSwpStyle(new JPanel(), false, false);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		{

			final String message = getLicenseStateMessage();

			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(), true, false);
			licStateLbl.setText(message);
			p1.add(licStateLbl);
			p.add(p1);

		}
		{
			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(), true, false);

			final JLabel lbl = UIUtils.toSwpStyle(new JLabel("Export Instance ID: ", SwingConstants.RIGHT), false, true);
			lbl.setPreferredSize(LBL_SIZE);
			p1.add(lbl);

			exportIidBtn.setActionCommand(AC_EXPORT_IID);
			exportIidBtn.addActionListener(this);
			exportIidBtn.setPreferredSize(new Dimension(90, 20));
			p1.add(exportIidBtn);

			p.add(p1);
		}
		{
			final JPanel p1 = UIUtils.toSwpStyle(new JPanel(), true, false);

			final JLabel lbl = UIUtils.toSwpStyle(new JLabel("Import License: ", SwingConstants.RIGHT), false, true);
			lbl.setPreferredSize(LBL_SIZE);
			p1.add(lbl);

			importLicBtn.setActionCommand(AC_IMPORT_LIC);
			importLicBtn.addActionListener(this);
			importLicBtn.setPreferredSize(new Dimension(90, 20));
			p1.add(importLicBtn);

			p.add(p1);
		}
		p0.add(p, BorderLayout.CENTER);
		return p0;
	}

	String getLicenseStateMessage() throws IOException {
		final String lv = getLicenseValue();
		LicDate licDate = LicDate.UNKNOWN_ERROR;
		try {
			licDate = LicMgr.checkLicense(lv);
			if (licDate==null) licDate = LicDate.UNKNOWN_ERROR;
		} catch (final Throwable t) {
			licDate = LicDate.UNKNOWN_ERROR;
		}
		final StringBuilder message = new StringBuilder("<html>");
		if (!licDate.isValid) {
			message.append("<span style=\"color: red\" >License problem: ").append(licDate.dateString).append("</span>");
		} else if (!licDate.expires) {
			message.append("<span style=\"color: green\" >Permanent license</span>");
		} else {
			message.append("<span style=\"color: green\" >License valid until ").append(licDate.dateString).append("</span>");
		}
		message.append("</html>");
		return message.toString();
	}

	void updateServiceInfo() throws IOException, InterruptedException {
		final String svcName = svcMgr.getServiceName();
		if (StringUtils.isNullOrEmpty(svcName) || !svcMgr.isInstalled(svcName)) {
			svcCrDel.setEnabled(true);
			svcCrDel.setText("Create");
			svcStart.setEnabled(false);
			svcRestart.setEnabled(false);
			svcStop.setEnabled(false);
			goToConf.setEnabled(false);
		} else if (svcMgr.isRunning(svcName)) {
			svcCrDel.setEnabled(false);
			svcCrDel.setText("Delete");
			svcStart.setEnabled(false);
			svcRestart.setEnabled(true);
			svcStop.setEnabled(true);
			goToConf.setEnabled(true);
		} else {
			svcCrDel.setEnabled(true);
			svcCrDel.setText("Delete");
			svcStart.setEnabled(true);
			svcRestart.setEnabled(false);
			svcStop.setEnabled(false);
			goToConf.setEnabled(false);
		}
	}

	// private void grayOutSvcButtons(){
	// svcCrDel.setEnabled(false);
	// svcStart.setEnabled(false);
	// svcRestart.setEnabled(false);
	// svcStop.setEnabled(false);
	// goToConf.setEnabled(false);
	// }

	private JPanel getPatchPan() {
		final JPanel p0 = UIUtils.toSwpStyle(new JPanel(new BorderLayout()), false, false);

		final JPanel p1 = UIUtils.toSwpStyle(new JPanel(), true, false);

		final JLabel lbl = UIUtils.toSwpStyle(new JLabel("Upload patch: ", SwingConstants.RIGHT), false, true);
		lbl.setPreferredSize(LBL_SIZE);
		p1.add(lbl);

		uploadPatchBtn.setActionCommand(AC_UPLOAD_PATCH);
		uploadPatchBtn.addActionListener(this);
		uploadPatchBtn.setPreferredSize(new Dimension(90, 20));
		p1.add(uploadPatchBtn);

		p0.add(p1, BorderLayout.CENTER);
		return p0;
	}

	@Override
	public void start() {
		win.getContentPane().add(mainPane, BorderLayout.CENTER);
		win.show(false);
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		if (win.getFrame().getCursor().getType() == Cursor.WAIT_CURSOR) return;
		saveBtn.setEnabled(false);
		win.getFrame().setCursor(new Cursor(Cursor.WAIT_CURSOR));
		new Thread(this.new ActionPerformer(e)).start();
	}

	private class ActionPerformer implements Runnable {

		private final ActionEvent e;

		public ActionPerformer(final ActionEvent e) {
			this.e = e;
		}

		@Override
		public void run() {

			try {
				final String ac = e.getActionCommand();
				if (AC_SAVE_CONFIG.equals(ac)) {

					final String hn = hostnameTF.getText();
					try {
						Setup.checkHostname(hn);
					} catch (final ConfigurationException e1) {
						JOptionPane.showMessageDialog(mainPane, e1.getExplanation(), "Invalid FQDN", JOptionPane.WARNING_MESSAGE);
						return;
					}
					final String hp = httpPortTF.getText();
					try {
						Setup.checkPort(hp);
					} catch (final ConfigurationException e1) {
						JOptionPane.showMessageDialog(mainPane, e1.getExplanation(), "Invalid port number", JOptionPane.WARNING_MESSAGE);
						return;
					}
					final String hsp = httpsPortTF.getText();
					try {
						Setup.checkPort(hsp);
					} catch (final ConfigurationException e1) {
						JOptionPane.showMessageDialog(mainPane, e1.getExplanation(), "Invalid port number", JOptionPane.WARNING_MESSAGE);
						return;
					}

					final int curSDP = lastKnownConf.getShutdownPort();
					final int sdp = curSDP > 0 ? curSDP : getRandomAvailablePort();

					final String clearText = new String(adminPwdTF.getPassword());
					String pwdHash = null;
					if (Configuration.DO_NOT_CHANGE.equals(clearText)) {
						pwdHash = Configuration.DO_NOT_CHANGE;
					} else if (!StringUtils.isNullOrEmpty(clearText)) {
						pwdHash = Configuration.hashPassword(clearText);
					} else {
						JOptionPane.showMessageDialog(mainPane, "Password must not be empty", "Invalid password", JOptionPane.WARNING_MESSAGE);
						return;
					}
					updateSCSConfig(new Configuration(hn, hp, hsp, "" + sdp, pwdHash));
					lastKnownConf = loadCurrentConfig();

				} else if (AC_SRV_CERT_NEW.equals(ac)) {
					if (!StringUtils.npeProofEquals(hostnameTF.getText(), lastKnownConf.getHostname())) {
						final int resp = JOptionPane.showConfirmDialog(win.getFrame(),
							String.format("The hostname in the Base configuration tab (%s) has not bee saved. New certificate will be generated for %s - are you sure you want to proceed?", hostnameTF.getText(), lastKnownConf.getHostname()), "Unsaved config",
							JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
						if (resp != JOptionPane.YES_OPTION) return;
					}
					generateSelfSignedCertificate(lastKnownConf.getHostname(), Constants.SERVER_KS_ENTRY_NAME);
					updateCertInfo();
				} else if (AC_SRV_CERT_EXPORT.equals(ac)) {
					final JFileChooser jfc = new JFileChooser(rootDir.toFile());
					jfc.setDragEnabled(true);
					jfc.setSelectedFile(rootDir.resolve("scs-certificate.crt").toFile());
					final int returnVal = jfc.showSaveDialog(win.getFrame());
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						Files.copy(new ByteArrayInputStream(viewPublicKey(Constants.SERVER_KS_ENTRY_NAME).getBytes(StandardCharsets.UTF_8)), jfc.getSelectedFile().toPath());
					}
				} else if (AC_SRV_CERT_CSR.equals(ac)) {
					final JFileChooser jfc = new JFileChooser(rootDir.toFile());
					jfc.setDragEnabled(true);
					jfc.setSelectedFile(rootDir.resolve("scs-signing-request.csr").toFile());
					final int returnVal = jfc.showSaveDialog(win.getFrame());
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						exportCsr(Constants.SERVER_KS_ENTRY_NAME, jfc.getSelectedFile());
					}
				} else if (AC_SRV_CERT_REPLY.equals(ac)) {
					final JFileChooser jfc = new JFileChooser(rootDir.toFile());
					jfc.setDragEnabled(true);
					jfc.setSelectedFile(rootDir.resolve("cert-chain.crt").toFile());
					final int returnVal = jfc.showOpenDialog(win.getFrame());
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						importCsrReply(Constants.SERVER_KS_ENTRY_NAME, jfc.getSelectedFile());
					}
				} else if (AC_SRV_KS_IMPORT.equals(ac)) {
					final JFileChooser jfc = new JFileChooser(rootDir.toFile());
					jfc.setDragEnabled(true);
					jfc.setSelectedFile(rootDir.resolve("keystore.jks").toFile());
					final int returnVal = jfc.showOpenDialog(win.getFrame());
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						importJavaKeystore(jfc.getSelectedFile());
					}
				} else if (AC_SAML_KEYS_NEW.equals(ac)) {
					generateSelfSignedCertificate(Constants.SAML_KEY_CN, Constants.SAML_KS_ENTRY_NAME);
				} else if (AC_SAML_KEYS_EXPORT.equals(ac)) {
					final JFileChooser jfc = new JFileChooser(rootDir.toFile());
					jfc.setDragEnabled(true);
					jfc.setSelectedFile(rootDir.resolve("scs-saml-key.crt").toFile());
					final int returnVal = jfc.showSaveDialog(win.getFrame());
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						Files.copy(new ByteArrayInputStream(viewPublicKey(Constants.SAML_KS_ENTRY_NAME).getBytes(StandardCharsets.UTF_8)), jfc.getSelectedFile().toPath());
					}
				} else if (AC_SVC_CR_DEL.equals(ac)) {
					String sn = svcMgr.getServiceName();
					if (StringUtils.isNullOrEmpty(sn) || !svcMgr.isInstalled(sn)) {
						if (StringUtils.isNullOrEmpty(sn)) {
							int i = 1;
							while (svcMgr.isInstalled(Constants.SCS_SVC_BASENAME + i)) {
								i++;
							}
							sn = Constants.SCS_SVC_BASENAME + i;
						}
						svcMgr.createSvc(sn);
					} else {
						svcMgr.deleteSvc(sn);
					}
					synchronized (sn) {
						sn.wait(3000);
					}
					updateServiceInfo();
				} else if (AC_SVC_RESTART.equals(ac)) {
					svcStart.setEnabled(false);
					final String sn = svcMgr.getServiceName();
					final List<Path> runningIndexers = getRunningIndexers();
					if (runningIndexers.size() > 0) {
						final int resp = JOptionPane.showConfirmDialog(win.getFrame(), String.format("%d indexers are running - do you want to restart them too?", runningIndexers.size()), "Restart running indexers", JOptionPane.YES_NO_OPTION,
							JOptionPane.QUESTION_MESSAGE);
						if (resp != JOptionPane.YES_OPTION) {
							killIndexers(runningIndexers);
						}
					}
					restartService(sn);
					synchronized (sn) {
						sn.wait(3000);
					}
					updateServiceInfo();
				} else if (AC_SVC_START.equals(ac)) {
					final String sn = svcMgr.getServiceName();
					svcMgr.startSvc(sn);
					synchronized (sn) {
						sn.wait(3000);
					}
					updateServiceInfo();
				} else if (AC_SVC_STOP.equals(ac)) {
					final String sn = svcMgr.getServiceName();
					final List<Path> runningIndexers = getRunningIndexers();
					if (runningIndexers.size() > 0) {
						final int resp = JOptionPane.showConfirmDialog(win.getFrame(), String.format("%d indexers are running - do you want to stop them too?", runningIndexers.size()), "Stop running indexers", JOptionPane.YES_NO_OPTION,
							JOptionPane.QUESTION_MESSAGE);
						if (resp != JOptionPane.YES_OPTION) {
							killIndexers(runningIndexers);
						}
					}
					svcMgr.stopSvc(sn);
					synchronized (sn) {
						sn.wait(3000);
					}
					updateServiceInfo();
				} else if (AC_GOTO_CONF.equals(ac)) {
					Desktop.getDesktop().browse(new URI(getConfigurationURL(lastKnownConf)));
				} else if (AC_SAVE_KRB_CONFIG.equals(ac)) {
					updateKrbConfig(krbRealm.getText(), krbHostname.getText(), kdcHostname.getText(), keytabPath.getText());
				} else if (AC_UPLOAD_KRB_KEYTAB.equals(ac)) {
					final JFileChooser jfc = new JFileChooser(rootDir.toFile());
					jfc.setDragEnabled(true);
					jfc.setSelectedFile(rootDir.resolve("scs.keytab").toFile());
					final int returnVal = jfc.showOpenDialog(win.getFrame());
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						keytabPath.setText(jfc.getSelectedFile().toPath().toString());
					}
				} else if (AC_EXPORT_IID.equals(ac)) {
					final JFileChooser jfc = new JFileChooser(rootDir.toFile());
					jfc.setDragEnabled(true);
					jfc.setSelectedFile(rootDir.resolve("scs.iid").toFile());
					final int returnVal = jfc.showSaveDialog(win.getFrame());
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						Files.copy(new ByteArrayInputStream(LicMgr.getInstanceID().getBytes(StandardCharsets.UTF_8)), jfc.getSelectedFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
					}
				} else if (AC_IMPORT_LIC.equals(ac)) {
					final JFileChooser jfc = new JFileChooser(rootDir.toFile());
					jfc.setDragEnabled(true);
					jfc.setSelectedFile(rootDir.resolve("scs.lic").toFile());
					final int returnVal = jfc.showOpenDialog(win.getFrame());
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						updateLicense(jfc.getSelectedFile().toPath());
						licStateLbl.setText(getLicenseStateMessage());
					}
				} else if (AC_UPLOAD_PATCH.equals(ac)) {
					final JFileChooser jfc = new JFileChooser(rootDir.toFile());
					jfc.setDragEnabled(true);
					final int returnVal = jfc.showOpenDialog(win.getFrame());
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						applyPatch(jfc.getSelectedFile().toPath());
					}
				}
			} catch (final Exception e1) {
				UIUtils.displayError("Error", e1);
			} finally {
				win.getFrame().setCursor(Cursor.getDefaultCursor());
				saveBtn.setEnabled(true);
				saveBtn.setForeground(new Color(34, 177, 76));
				synchronized (saveBtn) {
					try {
						saveBtn.wait(250);
					} catch (final InterruptedException e) {}
				}
				saveBtn.setForeground(Constant.SWORD_BLUE);
			}

		}

	}

	@Override
	protected void notifyAppClosing() {
		JOptionPane.showMessageDialog(mainPane, "SCS service will be stopped, setup application will close and the patch will be applied", "Patching SCS", JOptionPane.INFORMATION_MESSAGE);
	}

	@Override
	protected void closeApp() {
		win.dispose();
	}

}
