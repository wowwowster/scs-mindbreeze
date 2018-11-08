package com.sword.gsa.connectors.ezpublishdb;

public class Attribute {
	
	public final AttributeType type;
	public final String name;
	public final String displayName;
	
	public Attribute(String name, AttributeType type, String displayName) {
		this.name = name;
		this.type = type;
		this.displayName = displayName;
	}
	
	public boolean isForeignReference() {
		return false;
	}
	
	public static Attribute cloneWithNewDisplayName(Attribute a, String displayName) {
		return new Attribute(a.name, a.type, displayName);
	}
	
	public static ForeignKey deriveToForeignKey(Attribute a, String displayName, AttributeType targType, String wc) {
		return new ForeignKey(a.name, a.type, targType, displayName, wc);
	}

}
