package com.sword.gsa.spis.gsp;

import static org.junit.Assert.*;

import org.junit.Test;

import sword.common.utils.files.FileUtils;


public class TestPaths {
	
	@Test
	public void test() throws Exception {
		System.out.println(FileUtils.getJarFile(String.class).toPath().toFile().toURI().toASCIIString());
	}

}
