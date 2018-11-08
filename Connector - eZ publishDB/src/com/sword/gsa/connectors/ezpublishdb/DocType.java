package com.sword.gsa.connectors.ezpublishdb;

import java.util.ArrayList;
import java.util.List;

public final class DocType {

	public final List<Constant> constants = new ArrayList<>();
	public final List<Attribute> attributes = new ArrayList<>();
	public final String name;
	public String uid;
	public final String whereClause;
	public final String idAttrName;
	public final String mdAttrName;
	public final boolean indexSystemAttributes;
	
	public DocType(String name, String whereClause, String uid, boolean indexSystemAttributes, String idAttrName, String modifyDateAttrName) {
		this.name = name;
		this.uid = uid;
		this.idAttrName = idAttrName;
		this.mdAttrName = modifyDateAttrName;
		this.whereClause = whereClause==null ? "" : whereClause;
		this.indexSystemAttributes = indexSystemAttributes;
	}

}
