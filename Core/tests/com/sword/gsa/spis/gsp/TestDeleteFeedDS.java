package com.sword.gsa.spis.gsp;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.sword.gsa.adminapi.gdatawrap.GsaClientProxy;
import com.sword.gsa.adminapi.gdatawrap.GsaEntry;
import com.sword.gsa.adminapi.gdatawrap.GsaFeed;

public class TestDeleteFeedDS {

	@SuppressWarnings("static-method")
	@Test
	public void test() {
		
		try {
			GsaClientProxy gcp = new GsaClientProxy(false, "gspgsa2.parisgsa.lan", 8000, "admin", "sword75", 20000);
			
			Map<String, String> queries = new HashMap<String, String>();
			queries.put("query","testldap");
			GsaFeed myFeed = gcp.queryFeed("feed", queries);
			for(GsaEntry myEntry : myFeed.getEntries()) {
				//get information on each myEntry
				System.out.println("Feed Name: " + myEntry.getGsaContent("entryID"));
				System.out.println("Feed Data Source: " + myEntry.getGsaContent("feedDataSource"));
				System.out.println("Feed Type: " + myEntry.getGsaContent("feedType"));
				System.out.println("Feed State: " + myEntry.getGsaContent("feedState"));
				System.out.println("Feed Time: " + myEntry.getGsaContent("feedTime"));
				System.out.println("Error Records: " + myEntry.getGsaContent("errorRecords"));
				System.out.println("Success Records: " + myEntry.getGsaContent("successRecords"));
				System.out.println("Log Content: " + myEntry.getGsaContent("logContent"));
			}

//			GsaEntry myEntry = gcp.getEntry("feed", "sc1-jive");
//			System.out.println("Feed Name: " + myEntry.getGsaContent("entryID"));
//			System.out.println("Feed Data Source: " + myEntry.getGsaContent("feedDataSource"));
//			System.out.println("Feed Type: " + myEntry.getGsaContent("feedType"));
//			System.out.println("Feed State: " + myEntry.getGsaContent("feedState"));
//			System.out.println("Feed Time: " + myEntry.getGsaContent("feedTime"));
//			System.out.println("Error Records: " + myEntry.getGsaContent("errorRecords"));
//			System.out.println("Success Records: " + myEntry.getGsaContent("successRecords"));
//			System.out.println("Log Content: " + myEntry.getGsaContent("logContent"));
			
			
			
//			GsaEntry updateEntry = gcp.createEntry();
//			updateEntry.addGsaContent("updateMethod", "delete");
//			gcp.updateEntry("feed", "sc1-jira_20141117_143723_000000_INCREMENTAL_FEED_0", updateEntry);
//			gcp.deleteEntry("feed", "sc1_sql_20150116_162952_000000_INCREMENTAL_FEED_0");
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
