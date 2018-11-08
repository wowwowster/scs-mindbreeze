package com.sword.gsa.spis.gsp;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;

import org.apache.commons.codec.binary.Base64;
import org.junit.Assert;
import org.junit.Test;

import sword.common.utils.zip.DeflateUtil;


public class TestSAMLReqDecode {
	
	@SuppressWarnings("static-method")
	@Test
	public void test() {
		try {

			String sr = URLDecoder.decode("fZLdTuMwEEbv9yks3%2Be3aZtaTVC3XZYiYCsS2BU3yOsOqaXE7nqcAG%2B%2FbhMqEBKXI4%2FPfJ7j%2BdlLU5MODEqtMhr5ISWghN5KVWX0rjz3UnqWf5sjb%2Bp4zxat3alb%2BNcCWrJABGPdvaVW2DZgCjCdFHB3e5XRnbV7ZEGAwI3Y%2Bc%2FAO%2FChdbVojbSvXsMVr8AEBzJ%2FI4mBRMnKTZCK22OsA8yx3KkCYU8wloZpFBTLIuAuFygrBbdAyXqV0cfZNuKTURol23A8TcYRHz8l0zBKk1GSJLOZe%2BcaN26w7CCjT7zGw0XEFtYKLVc2o3EYjb0w8aJJGaYsGrF44s%2Fi5IGSjdFWC11%2Fl6pfVGsU0xwlMsUbQGYFKxbXVyz2Q%2Fa3b0J2UZYbb%2FOrKI%2BATm7B3LjujP7UuqqB%2FFAWzN5IBFIMWyLX%2FZYouX9TFB8UOWkKWS%2Fl6%2Bn7ISrNe4fs%2BEbznvA14OSG5oOG6hjXF7oJ4BQ5qJAH5ci7WV7eX16cL1eT33%2Bmn2zPg%2Fch8qH8%2BK%2Fy%2Fw%3D%3D", "utf-8");
			final String decodedRequest = new String(DeflateUtil.inflate(Base64.decodeBase64(sr), true), StandardCharsets.UTF_8);
			System.out.println("Decoded AuthNRequest is: " + decodedRequest);
//			String sr = "fZJJb9swEEbv/RUE71pNb4SlwLWbxkEWI1LSopeClSYKAYlUOZTb/vtSS4IEAXIkyHnzzTxuzv42NTmBQalVQiM/pARUoUupqoTe5+feip6lnzYomjpu+bazT+oOfneAlmwRwVhXt9MKuwZMBuYkC7i/u0rok7Ut8iCoUMQ+/tGm9Cqju9YvdBMgFJ2R9p/XCCUqMEGPF8+4YsJRsndtpBJ2yNYTHdDdKihs9A6a7bJAuHygrCyEBUoO+4T+XJeRWMxWESvD+ZLNIzF/ZMswWrEZY2y9dvMe8Oh6yxMk9FHU2BcidnBQaIWyCY3DaO6FzIsWebji0YzHC38dsx+UHI22utD1Z6nGhXVGcS1QIleiAeS24Nn2+orHfsh/jY+QX+T50TveZvkAOMkSzI17ndCvWlc1kC/KgmmNRCDZtChyPS6KkodnVXGvyslTyEc5H3dvp6g0HV3yYUbzmvAx4EUPTScT1RB32D28RO6FB/nMu9ldPlxenO/2i2/fl++Eb4LXIdLp+PZ/pf8B";
//			byte[] srDec = Base64.decodeBase64(sr);
//			byte[] srDecInfl = DeflateUtil.inflate(srDec, true);
//			String decodedRequest = new String(srDecInfl, StandardCharsets.UTF_8);
//			System.out.println(decodedRequest);

			byte[] newSr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<saml2p:AuthnRequest AssertionConsumerServiceURL=\"https://search.weave.eu/security-manager/samlassertionconsumer\" Destination=\"http://connect.weave.eu:8081/SCS/authenticate\" ID=\"_9d1a63814d057451a5f4701843444990\" IsPassive=\"false\" IssueInstant=\"2015-04-16T08:13:26.924Z\" ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" ProviderName=\"Google Enterprise Security Manager\" Version=\"2.0\" xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\"><saml2:Issuer xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\">http://google.com/enterprise/gsa/T3-NCJVJHFCD6WX7/security-manager</saml2:Issuer></saml2p:AuthnRequest>".getBytes(StandardCharsets.UTF_8);
			byte[] newSrDefl = DeflateUtil.deflate(newSr, new Deflater(Deflater.DEFLATED, true));
			byte[] newSrDeflEnc = Base64.encodeBase64(newSrDefl);
			String newSrDeflEncStr = new String(newSrDeflEnc, StandardCharsets.UTF_8);
			System.out.println(URLEncoder.encode(newSrDeflEncStr, "utf-8"));
			byte[] newSrDeflEncDec = Base64.decodeBase64(newSrDeflEnc);
			Assert.assertTrue(Arrays.equals(newSrDefl, newSrDeflEncDec));
			byte[] newSrDeflEncDecInfl = DeflateUtil.inflate(newSrDeflEncDec, true);
			Assert.assertTrue(Arrays.equals(newSr, newSrDeflEncDecInfl));
			System.out.println(new String(newSrDeflEncDecInfl, StandardCharsets.UTF_8));

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
