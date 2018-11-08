package com.sword.gsa.spis.gsp;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;

import sword.connectors.commons.config.ConnectorSpec;

public class TestExpBackoff {
	
	@SuppressWarnings("static-method")
	@Test
	public void test() {
		
		try (URLClassLoader c = new URLClassLoader(new URL[]{new File("D:\\utils\\java\\Decompile").toURI().toURL()})) {
			
			Class<?> jc = c.loadClass("com.sword.gsa.connectors.jive.Connector");
			ConnectorSpec cs = jc.getAnnotation(ConnectorSpec.class);
			System.out.println(cs.name() + " -> " + cs.version());
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		long i = 1L;
		System.out.println(1 << i);
		i++;
		System.out.println(1 << i);
		i++;
		System.out.println(1 << i);
		i++;
		System.out.println(1 << i);
		i++;
		System.out.println(1 << i);
		i++;
		System.out.println(1 << i);
		i++;
		System.out.println(1 << i);
		i++;
		System.out.println(1 << i);
		i++;
		System.out.println(1 << i);
		i++;
		System.out.println(1 << i);
	}

}
