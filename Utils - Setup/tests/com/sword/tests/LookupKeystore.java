package com.sword.tests;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import sword.common.utils.streams.B64OutputStream;

import com.sword.scs.Constants;

public class LookupKeystore {

	public static void main(String[] args) {
		try {
			File f = new File("C:\\Users\\jpasquon\\Desktop\\keystore.jks");
			final KeyStore newKS = KeyStore.getInstance("JKS");
			try (FileInputStream is = new FileInputStream(f)) {
				newKS.load(is, Constants.STORES_PWD.toCharArray());
			}
				String alias = "tomcat";
				Key key = newKS.getKey(alias, Constants.STORES_PWD.toCharArray());
				if (key instanceof PrivateKey) {
					Certificate c = newKS.getCertificate(alias);
					File fpr = new File("C:\\Users\\jpasquon\\Desktop\\tomcat.priv.crt");
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					try (B64OutputStream bos = new B64OutputStream(baos, true)) {
						bos.write(((PrivateKey)key).getEncoded());
					}
					try (FileOutputStream fos = new FileOutputStream(fpr)) {
						fos.write("-----BEGIN PRIVATE KEY-----\r\n".getBytes());
						fos.write(baos.toByteArray());
						fos.write("\r\n-----END PRIVATE KEY-----\r\n".getBytes());
					}
					File fpu = new File("C:\\Users\\jpasquon\\Desktop\\tomcat.pub.crt");
					baos = new ByteArrayOutputStream();
					try (B64OutputStream bos = new B64OutputStream(baos, true)) {
						bos.write(c.getEncoded());
					}
					try (FileOutputStream fos = new FileOutputStream(fpu)) {
						fos.write("-----BEGIN CERTIFICATE-----\r\n".getBytes());
						fos.write(baos.toByteArray());
						fos.write("\r\n-----END CERTIFICATE-----\r\n".getBytes());
					}
				}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
