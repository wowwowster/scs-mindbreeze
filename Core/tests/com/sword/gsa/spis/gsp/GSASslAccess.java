package com.sword.gsa.spis.gsp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import org.junit.Assert;
import org.junit.Test;

import sword.common.utils.files.FileUtils;

import com.sword.gsa.spis.scs.gsaadmin.GSAInfo;
import com.sword.gsa.spis.scs.gsaadmin.GsaManager;
import com.sword.gsa.spis.scs.gsaadmin.ReloadableTrustManager;

public class GSASslAccess {

	@SuppressWarnings("static-method")
	@Test
	public void testHandshakeError() throws NoSuchAlgorithmException, KeyStoreException, FileNotFoundException, CertificateException, IOException {
		final GsaManager gm = new GsaManager(FileUtils.getJarFile(String.class).getParentFile().toPath().resolve("security/cacerts"));
		try {
			gm.getGsaInfo(true, "swpgsa1.parisgsa.lan", "swpgsa1.parisgsa.lan");
			Assert.fail("SSL to swpgsa1.parisgsa.lan succeeded");
		} catch (final Exception e) {
			Assert.assertEquals(e.getClass(), SSLHandshakeException.class);
		}
		try {
			gm.getGsaInfo(true, "swpgsa4.parisgsa.lan", "swpgsa4.parisgsa.lan");
			Assert.fail("SSL to swpgsa4.parisgsa.lan succeeded");
		} catch (final Exception e) {
			Assert.assertEquals(e.getClass(), ConnectException.class);
		}
		try {
			Assert.assertNull(gm.getGsaInfo(true, "swpgsa3.parisgsa.lan", "swpgsa3.parisgsa.lan"));
		} catch (final Exception e) {
			Assert.fail("SSL to swpgsa3.parisgsa.lan failed: " + e.toString());
		}
	}

	@SuppressWarnings("static-method")
	@Test
	public void testParseCert() {
		GSAInfo partialGi = new GSAInfo(true, "swpgsa1.parisgsa.lan", "swpgsa1.parisgsa.lan", "", "", "", false);
		try {
			final List<X509Certificate> certs = GsaManager.getCertificates(partialGi);
			Assert.assertEquals(1, certs.size());
			Assert.assertEquals("swpgsa1.parisgsa.lan", ReloadableTrustManager.extractCnFromCert(certs.get(0)));
		} catch (final Exception e) {
			Assert.fail("SSL to swpgsa1.parisgsa.lan failed: " + e.toString());
		}
		partialGi = new GSAInfo(true, "swpgsa2.parisgsa.lan", "swpgsa2.parisgsa.lan", "", "", "", false);
		try {
			final List<X509Certificate> certs = GsaManager.getCertificates(partialGi);
			Assert.assertEquals(2, certs.size());
			Assert.assertEquals("swpgsa2.parisgsa.lan", ReloadableTrustManager.extractCnFromCert(certs.get(0)));
			Assert.assertEquals("PARISGSA CA", ReloadableTrustManager.extractCnFromCert(certs.get(1)));
		} catch (final Exception e) {
			Assert.fail("SSL to swpgsa2.parisgsa.lan failed: " + e.toString());
		}
	}

	@SuppressWarnings("static-method")
	@Test
	public void testTrustCert() throws NoSuchAlgorithmException, KeyStoreException, FileNotFoundException, CertificateException, IOException {
		final GsaManager gm = new GsaManager(FileUtils.getJarFile(String.class).getParentFile().toPath().resolve("security/cacerts"));
		try {
			try {
				gm.getGsaInfo(true, "swpgsa1.parisgsa.lan", "swpgsa1.parisgsa.lan");
				Assert.fail("SSL to swpgsa1.parisgsa.lan succeeded");
			} catch (final Exception e) {
				Assert.assertEquals(e.getClass(), SSLHandshakeException.class);
			}

			try {
				GSAInfo partialGi = new GSAInfo(true, "swpgsa1.parisgsa.lan", "swpgsa1.parisgsa.lan", "", "", "", false);
				final List<X509Certificate> certs = GsaManager.getCertificates(partialGi);
				Assert.assertEquals(1, certs.size());
				gm.trustCertificate(certs.get(0));
			} catch (final Exception e) {
				Assert.fail("SSL to swpgsa1.parisgsa.lan failed: " + e.toString());
			}
			try {
				Assert.assertNotNull(gm.getGsaInfo(true, "swpgsa1.parisgsa.lan", "swpgsa1.parisgsa.lan"));
			} catch (final Exception e) {
				Assert.fail("SSL to swpgsa1.parisgsa.lan failed: " + e.toString());
			}
		} finally {
			gm.untrustCertificate("swpgsa1.parisgsa.lan");
		}
	}

}
