package com.sword.tests;

import java.io.File;
import java.nio.file.Path;

import sword.common.utils.runtime.RuntimeUtils;

import com.sword.scs.Constants;


public class TestRun {

	public static void main(String[] args) {
		
		try {
			Path installDest = new File("D:\\_Tests\\TestGSPInstall").toPath();
			
			String envPath = "C:\\Program Files\\Documentum\\Shared;C:\\PROGRA~1\\CollabNet\\SUBVER~1;C:\\Windows\\system32;C:\\Windows;C:\\Windows\\System32\\Wbem;C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\;C:\\PROGRA~2\\MI3EDC~1\\100\\Tools\\Binn\\;C:\\PROGRA~1\\MI3EDC~1\\100\\Tools\\Binn\\;C:\\PROGRA~1\\MI3EDC~1\\100\\DTS\\Binn\\;D:\\_IDE\\ant-v1.8.2\\bin;D:\\_Utils\\_shortcuts;C:\\PROGRA~1\\Dell\\DW WLAN Card\\Driver;C:\\PROGRA~1\\WIDCOMM\\BLUETO~1\\;C:\\PROGRA~1\\WIDCOMM\\BLUETO~1\\syswow64;D:\\_Java\\jdk7x64-latest\\bin;C:\\Program Files\\Microsoft\\Web Platform Installer\\;C:\\Program Files (x86)\\Microsoft ASP.NET\\ASP.NET Web Pages\\v1.0\\;C:\\Program Files (x86)\\Windows Kits\\8.0\\Windows Performance Toolkit\\;C:\\Program Files\\Microsoft SQL Server\\110\\Tools\\Binn\\";
			Process p = RuntimeUtils.getProcessWithPath(
							new String[] {installDest.resolve(Constants.REL_PATH_JAVAW).toString(), "-jar", installDest.resolve(Constants.REL_PATH_SCS_BIN).resolve("GSP Manager.jar").toString(), "/EnvPath", envPath}, 
							installDest.toFile(), envPath);
			final int rc = p.waitFor();
			if (rc != 0) {
				System.out.println(rc + " => " + RuntimeUtils.readTerminatedProcessOutput(p));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
