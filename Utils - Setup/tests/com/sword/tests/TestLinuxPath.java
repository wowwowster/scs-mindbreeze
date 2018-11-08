package com.sword.tests;

import java.io.Console;
import java.io.File;
import java.nio.file.Path;


public class TestLinuxPath {

	public static void main(String[] args) {
		
		Console c = System.console();
		Path p = new File(args[0]).toPath();
		c.format("folder: %s\n", p.toString());
		c.format("sub-folder: %s", p.resolve("jdk").toString());

	}

}
