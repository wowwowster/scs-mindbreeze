package com.sword.gsa.connectors.ezpublishdb;

import java.util.Map;

import sword.common.databases.sql.DBType;
import sword.connectors.commons.config.CP;
import sword.connectors.commons.config.CPType;
import sword.connectors.commons.config.ConnectorSpec;

import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.gsa.spis.scs.commons.connector.models.ADocLoader;
import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.commons.connector.models.Indexer;

@ConnectorSpec(name = "eZ publishDB", version = "1.3.5")
public class Connector extends AConnector implements Indexer {
	
	private static final String[] DB_TYPES;
	static {
		final DBType[] dbTypes = DBType.values();
		DB_TYPES = new String[dbTypes.length];
		for (int i = 0; i < dbTypes.length; i++)
			DB_TYPES[i] = dbTypes[i].name();
	}
	
	private static final String[] LANGS;
	static {
		final LANG[] langs = LANG.values();
		LANGS = new String[langs.length];
		for (int i = 0; i < langs.length; i++)
			LANGS[i] = langs[i].name();
	}

	static final String ACL_PREFIX_USER = "user";
	static final String ACL_PREFIX_GROUP = "group";
	static final String ACL_PREFIX_ROLE = "role";

	protected static final String DB_TYPE = "db-type";
	protected static final String CONNECTION_STRING = "connection-string";
	protected static final String USERNAME = "u";
	protected static final String PWD = "p";
	protected static final String HOST = "host";
	protected static final String BINARY_TYPES = "binarytypes";
	protected static final String SEARCHABLEONLY = "searchable";
	protected static final String PUBLISHEDONLY = "published";
	protected static final String URLSEPARATOR = "separator";
	protected static final String TO_INDEX_TYPES = "toIndex";
	protected static final String ROOTNODE = "rootnode";
	protected static final String ONLY_REFERENCED_FILES = "onlyreffiles";
	protected static final String LANGUAGE = "lang";
	protected static final String FOLLOWMETAS = "followmetas";
	protected static final String TYPE_RENAMING = "type_rename";
	protected static final String EXCLUDED_METAS = "excluded_meta";
	protected static final String ISMULTILANG = "multilang";



	public static final CP[] CONFIGURATION_PARAMETERS = new CP[] {
		new CP(CPType.ENUM, DB_TYPE, "database type", "The database type your EzPublish is built with", CP.MANDATORY, DB_TYPES),
		new CP(CPType.STRING, CONNECTION_STRING, "Connection String", "JDBC connection string: <server>:<port>/<db>?<options>", CP.MANDATORY),
		new CP(CPType.STRING, USERNAME, "User", "A SQL user with read permission on all required tables", CP.MANDATORY),
		new CP(CPType.STRING, PWD, "Password", "Above user's password", CP.MANDATORY_ENCRYPTED),
		new CP(CPType.STRING, HOST, "Hostname",
			"Fully qualified EzPublish hostname e.g.: http://<yourezpublish>.com",CP.MANDATORY),
			new CP(CPType.ENUM, LANGUAGE, "Site language", "Will not get attributes with another language defined.", LANGS),
		new CP(CPType.BOOLEAN, ISMULTILANG,"Is multilang" ,"If a site has more than one language. Note that you need one connector instance for each language you wish to index"),
		new CP(CPType.BOOLEAN, SEARCHABLEONLY, "Only index searchable attributes", "Only get attributes that can be found with navigation."),
		new CP(CPType.BOOLEAN, PUBLISHEDONLY, "Only index published content", "Only get published documents."),
		new CP(CPType.STRING, URLSEPARATOR, "Url separator", "Separator used in URL to replace illegal characters, default \'-\'"),
		new CP(CPType.STRING, ROOTNODE,"Root Node" ,"If EzPublish contains more than one site, you can define a root node."),
		new CP(CPType.BOOLEAN, ONLY_REFERENCED_FILES, "Only referenced files", "Only get files if they are linked from an index document. Default false"),
		new CP(CPType.BOOLEAN, FOLLOWMETAS, "get full metadata", "Will allow recursively looking for metadata about objects linked to an object."),
		new CP(CPType.STRING, TO_INDEX_TYPES, "Direct index types", "List of data types relevant for indexing (article, newspaper, forum,...)", CP.MANDATORY_MULTIVALUE),
		new CP(CPType.STRING, BINARY_TYPES, "Binary types", "Non-textual data types. If a type is on this list but not on the index types list, its elements will only be indexed when other types are referring to them.", CP.MULTIVALUE),
		new CP(CPType.STRING, EXCLUDED_METAS, "Excluded meta", "List of Metadata names you do not wish to include in the documents. Case sensitive.", CP.MULTIVALUE),
		new CP(CPType.STRING, TYPE_RENAMING, "Rename types", "List of data types to rename <default_name>=<new_name>", CP.MULTIVALUE),
};
	


	




	
	public Connector(final String uniqueId, final Map<String, String> configurationParameters, final String namespace, final String nameTransformer) {
		super(uniqueId, configurationParameters, namespace, nameTransformer);
	}


	@Override
	public boolean supportsEarlyBinding() {
		return true;
	}

	@Override
	public boolean canDetectAclModification() {
		return false;
	}

	@Override
	public Class<? extends AExplorer> getExplorerClass() {
		return ONPExplorer.class;
	}

	@Override
	public Class<? extends ADocLoader> getDocLoaderClass() {
		return DocLoader.class;
	}

}
