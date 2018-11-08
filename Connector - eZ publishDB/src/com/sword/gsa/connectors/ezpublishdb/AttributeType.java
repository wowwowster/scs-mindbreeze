package com.sword.gsa.connectors.ezpublishdb;

import java.util.regex.Pattern;

public enum AttributeType {

	@Deprecated
	FIXED("fixed|const(?:ant)?"), 
	ID("id(?:entifier)?"), 
	STRING("str(?:ing)?"), 
	BOOLEAN("bool(?:ean)?"), 
	DATE("date"), 
	NUMERIC("num(?:eric)?|int(?:eger)?"), 
	CLOB("(?:c|b)lob");

	private final Pattern p;
	
	private AttributeType(String re) {
		p = Pattern.compile(re, Pattern.CASE_INSENSITIVE);
	}

	public String getLbl() {
		return name().toLowerCase();
	}
	
	public static AttributeType lookUp(String lbl) {
		for (AttributeType at : values()) if (at.p.matcher(lbl).matches()) return at;
		return null;
	}

}
