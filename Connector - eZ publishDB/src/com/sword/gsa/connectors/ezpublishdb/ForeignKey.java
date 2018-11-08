package com.sword.gsa.connectors.ezpublishdb;

import java.text.MessageFormat;

public final class ForeignKey extends Attribute {

	private final String query;
	private final AttributeType targetAttributeType;

	public ForeignKey(String name, AttributeType sourceAttributeType, AttributeType targetAttributeType, String displayName, String queryPattern) {
		super(name, sourceAttributeType, displayName);
		query = queryPattern;
		this.targetAttributeType = targetAttributeType;
	}
	
	public AttributeType getTargetAttributeType() {
		return targetAttributeType;
	}
	
	public MessageFormat getQuery() {
		return new MessageFormat(query);
	}
	
	public boolean isForeignReference() {
		return true;
	}
}
