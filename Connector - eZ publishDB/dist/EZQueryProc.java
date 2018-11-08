package com.sword.gsa.connectors.ezpublishdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

import sword.common.databases.sql.DBType;
import sword.common.databases.sql.DataBaseConnection;
import sword.common.utils.chars.Strings;
import sword.push.commons.configuration.PushType;
import sword.push.generic.config.ConfigurationStore;
import sword.push.generic.config.cms.CMSDocbaseDef;
import sword.push.generic.config.objects.Attribute;
import sword.push.generic.config.objects.AttributeType;
import sword.push.generic.config.objects.DocType;
import sword.push.generic.connector.QueryProcessor;
import sword.push.generic.databases.ResultSetCollection;
import sword.push.generic.throwables.CMSFault;

public class EZQueryProc extends QueryProcessor {

	private final ConfigurationStore cs = ConfigurationStore.get();
	private final CMSDocbaseDef dbDef;
	private final DataBaseConnection dbc;
	private final boolean indexSearchableOnly;

	public EZQueryProc(DocType dt) throws CMSFault {
		super(dt);
		try {
			dbDef = cs.getCMSDocbaseDef();
			String connectionString = dbDef.getParameter("connectionString");
			indexSearchableOnly = Boolean.parseBoolean(dbDef.getParameter("indexSearchableOnly"));
			if (Strings.isNullOrEmpty(connectionString) || Strings.isNullOrEmpty(dbDef.getParameter("dbType"))) {
				throw new CMSFault("One of the required parameters was not specified. Please check config");
			}

			DBType dbtype = DBType.lookupName(dbDef.getParameter("dbType"));

			dbc = new DataBaseConnection(dbtype, connectionString, dbDef.username, dbDef.password);
			dbc.connect(true);

		} catch (Exception e) {
			throw new CMSFault(e);
		}

	}

	@Override
	protected Collection<String> _executeQuery() throws Exception {

		String binarytypes = dbDef.getParameter("binaryTypes");

		if (binarytypes.contains(docType.name)) {
			LOG.info("DocType (" + docType.name + ") is a binary type. Attribute will be added automatically as feed metadata ");
			retrieveDocTypeAttributes();
		}

		StringBuilder sb = new StringBuilder("SELECT ");
		sb.append(docType.idAttrName);
		sb.append(" FROM ");
		sb.append("ezcontentobject");
		sb.append(" WHERE ");
		sb.append(" contentclass_id=" + docType.uid + " AND ");

		if (ConfigurationStore.getPushType() == PushType.UPDATE) {
			sb.append(docType.mdAttrName + ">");
			sb.append(cs.getDateOfLastPush().getTime() / 1000 + " AND ");
		}

		String additionalClause = docType.whereClause.replaceFirst("^[ ]*[aA][nN][dD][ ]*", "").replaceFirst("[ ]*[aA][nN][dD][ ]*$", "");
		if (additionalClause.length() > 1) {
			sb.append(additionalClause);
			sb.append(" AND ");
		}

		String query = sb.substring(0, sb.length() - 4) + "ORDER BY " + docType.idAttrName;

		if (dbc == null)
			return null;

		try (Statement st = dbc.getStatement()) {
			return new ResultSetCollection(st.executeQuery(query));
		}
	}

	private void retrieveDocTypeAttributes() throws SQLException {

		Attribute at;
		int id;
		String identifier;
		String datatype;
		String issearchable = "";
		if (indexSearchableOnly) issearchable = " and is_searchable=1";

		String query = "select distinct id,identifier,data_type_string from ezcontentclass_attribute where contentclass_id=" + docType.uid + issearchable;
		LOG.debug("query: " + query);
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery(query)) {
				while (rs.next()) {
					id = rs.getInt("id");
					identifier = rs.getString("identifier");
					datatype = rs.getString("data_type_string");
					LOG.info("Adding the following attribute: " + identifier);
					at = new Attribute("ATTRID::" + datatype + "::" + Integer.toString(id), AttributeType.STRING, identifier);
					docType.attributes.add(at);
				}
				// Adding attribute ID for URL generation
				at = new Attribute("ATTRID::ezbinaryfileID::@@", AttributeType.STRING, "ATTRID");
				docType.attributes.add(at);
			}
		}

	}

	@Override
	public void close() throws SQLException {
		dbc.close();
	}
}
