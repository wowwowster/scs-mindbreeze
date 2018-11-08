package com.sword.gsa.spis.gsp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class __ChangePackage {
	
	private static final Pattern WRONG_PACKAGE = Pattern.compile("(package|import) com\\.sword\\.gsa\\.spis\\.gsp\\.");

	public static void main(String[] args) {
		
		try {
			Path p = new File("D:\\_Workspaces\\Luna\\SCS").toPath();
			Files.walkFileTree(p, new FileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (file.toString().endsWith(".java")) {
						try {
							ByteArrayOutputStream os = new ByteArrayOutputStream();
							Files.copy(file, os);
							String javaCode = new String(os.toByteArray());
							Matcher m = WRONG_PACKAGE.matcher(javaCode);
							StringBuffer sb = new StringBuffer();
							while (m.find()) m.appendReplacement(sb, m.group(1) + " com.sword.gsa.spis.scs.");
							m.appendTail(sb);
							Files.copy(new ByteArrayInputStream(sb.toString().getBytes()), file, StandardCopyOption.REPLACE_EXISTING);
						} catch (IOException e) {
							e.printStackTrace();
							return FileVisitResult.TERMINATE;
						}
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
					return FileVisitResult.CONTINUE;
				}
				
			});
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
