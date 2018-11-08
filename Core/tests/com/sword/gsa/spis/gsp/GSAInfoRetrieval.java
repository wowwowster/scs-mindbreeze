package com.sword.gsa.spis.gsp;

import java.io.StringWriter;
import java.util.regex.Pattern;

import org.junit.Test;

import sword.common.utils.files.FileUtils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.sword.gsa.spis.scs.gsaadmin.GSAInfo;
import com.sword.gsa.spis.scs.gsaadmin.GsaManager;

public class GSAInfoRetrieval {

	@SuppressWarnings("static-method")
	@Test
	public void test() {
		
		String s = "window.location.replace(\r\n\twindow.location.href.replace(/\\?[^#]*/, ''));\r\n} else {\r\n\tstartGsaAdmin(\r\n\t\t1,\r\n\t\t{'sysVersion': \"7.2.0.G.252\\n\",\r\n\t\t'applianceId': \"T3-NCJVJHFCD6WX7\"});\r\n\t}";
		
		System.out.println(Pattern.compile("startGsaAdmin\\(.+?\\{['\"]sysVersion['\"]:.*?['\"]([0-9A-Z]+(?:\\.[0-9A-Z]+)*)(?:\\\\n)?['\"],.*?['\"]applianceId['\"]:.*?", Pattern.DOTALL | Pattern.MULTILINE).matcher(s).find());

		try {
			final GsaManager gm = new GsaManager(FileUtils.getJarFile(String.class).getParentFile().toPath().resolve("security/cacerts"));
			for (String gsaHost: new String[]{"gspgsa1.parisgsa.lan", "gspgsa2.parisgsa.lan", "gspgsa3.parisgsa.lan", "gspgsa4.parisgsa.lan"}) {
				GSAInfo gi = gm.getGsaInfo(true, gsaHost, gsaHost);
				try (StringWriter sw = new StringWriter()) {
					try (JsonGenerator jg = new JsonFactory().createGenerator(sw)) {
						GSAInfo.toJson(gi, jg);
					}
					sw.flush();
					System.out.println(sw.toString());
					System.out.println();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
