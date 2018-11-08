package com.sword.tests;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.List;

public class PrincipalLookup {

	public static void main(String[] args) {
		try {
			File f = new File("D:\\logs\\");
			AclFileAttributeView view = Files.getFileAttributeView(f.toPath(), AclFileAttributeView.class);
			List<AclEntry> acl = view.getAcl();
			
			for (AclEntry ae : acl) {
				System.out.println(ae.principal() + " => " + ae.permissions());
			}
			
//			UserPrincipal up = f.toPath().getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("EVERYONE");
//			System.out.println(up);
//			AclEntry entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(up).setPermissions(AclEntryPermission.WRITE_ACL, AclEntryPermission.DELETE_CHILD, 
//							AclEntryPermission.WRITE_ATTRIBUTES, AclEntryPermission.EXECUTE, AclEntryPermission.READ_DATA, AclEntryPermission.READ_NAMED_ATTRS, 
//							AclEntryPermission.SYNCHRONIZE, AclEntryPermission.WRITE_OWNER, AclEntryPermission.READ_ATTRIBUTES, AclEntryPermission.WRITE_NAMED_ATTRS, 
//							AclEntryPermission.WRITE_DATA, AclEntryPermission.APPEND_DATA, AclEntryPermission.READ_ACL, AclEntryPermission.DELETE).build();
//			acl.clear();
//			acl.add(entry);
//			view.setAcl(acl);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
