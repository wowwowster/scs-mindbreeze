package com.sword.gsa.connectors.ezpublishdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class ResultSetCollection extends ArrayList<String> {
	
	private static final long serialVersionUID = 1L;
	
	public ResultSetCollection(ResultSet rs) throws SQLException {
		super();
		while (rs.next()) this.add(rs.getString(1));
		rs.close();
	}

}
