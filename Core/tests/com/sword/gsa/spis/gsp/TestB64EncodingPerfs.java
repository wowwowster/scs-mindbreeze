package com.sword.gsa.spis.gsp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.junit.Assert;
import org.junit.Test;

import sword.common.crypto.hash.Digester;
import sword.common.crypto.hash.HashAlgorithm;
import sword.common.utils.streams.B64OutputStream;

public class TestB64EncodingPerfs {

	@SuppressWarnings({"static-method", "resource"})
	@Test
	public void testB64EncodingPerfs() {

		try {
			long timeApache = 0L;
			long timeSword = 0L;
			final File srcFile = new File("../Utils - Installer/resources/jdk/x64/linux/jdk.tar.gz");
			// new File("D:\\Users\\jpasquon\\Desktop\\ASCII.xlsx");
			final long fileSize = srcFile.length();
			final FileInputStream fis = new FileInputStream(srcFile);
			final File apacheB64 = new File("D:\\Users\\jpasquon\\Desktop\\test.encoded.apache.bin");
			final File swordB64 = new File("D:\\Users\\jpasquon\\Desktop\\test.encoded.sword.bin");
			final Base64OutputStream apacheB64os = new Base64OutputStream(new FileOutputStream(apacheB64));
			final B64OutputStream swordB64os = new B64OutputStream(new FileOutputStream(swordB64), true);
			long ref = 0L;
			try {
				final byte[] buf = new byte[4096];
				int r = -1;
				while ((r = fis.read(buf)) >= 0) {

					ref = System.currentTimeMillis();
					swordB64os.write(buf, 0, r);
					timeSword += System.currentTimeMillis() - ref;

					ref = System.currentTimeMillis();
					apacheB64os.write(buf, 0, r);
					timeApache += System.currentTimeMillis() - ref;

				}
			} finally {

				fis.close();

				ref = System.currentTimeMillis();
				apacheB64os.close();
				timeApache += System.currentTimeMillis() - ref;

				ref = System.currentTimeMillis();
				swordB64os.close();
				timeSword += System.currentTimeMillis() - ref;

			}

			System.out.println("Apache: " + timeApache + " <-> " + fileSize / (1000f * timeApache) + " MB/S");
			System.out.println("Sword: " + timeSword + " <-> " + fileSize / (1000f * timeSword) + " MB/S");

			try (// Apache b64 encoder appends a trailing CRLF - need to remove it
			FileOutputStream os = new FileOutputStream(apacheB64, true); FileChannel ch = os.getChannel();) {
				ch.truncate(ch.size() - 2);
			}

			final Digester d = new Digester(HashAlgorithm.SHA_256);
			byte[] a = null;
			byte[] s = null;
			try (final FileInputStream afis = new FileInputStream(apacheB64)) {
				a = d.digest(afis);
			}
			try (final FileInputStream sfis = new FileInputStream(swordB64)) {
				s = d.digest(sfis);
			}
			Assert.assertArrayEquals(a, s);

		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail();
		}

	}

}
