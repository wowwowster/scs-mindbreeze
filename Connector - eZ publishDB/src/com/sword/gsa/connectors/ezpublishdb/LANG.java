package com.sword.gsa.connectors.ezpublishdb;

import sword.common.utils.StringUtils;


public enum LANG {
	FR("fre-FR"),
	EN("eng-GB");
	
	public String langStr;

	private LANG(String lang){
		this.langStr=lang;
	}
	
	public static LANG lookupName(String name) {
		if (StringUtils.isNullOrEmpty(name)) return null;
		else name = name.toLowerCase();
		LANG[] langs = values();
		for (LANG lang : langs) if (name.equalsIgnoreCase(lang.name())) return lang;
		return null;
	}
}
