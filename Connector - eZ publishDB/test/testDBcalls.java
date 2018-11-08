import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.ConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.sword.gsa.connectors.ezpublishdb.Attribute;
import com.sword.gsa.connectors.ezpublishdb.AttributeType;
import com.sword.gsa.connectors.ezpublishdb.Connector;
import com.sword.gsa.connectors.ezpublishdb.Constant;
import com.sword.gsa.connectors.ezpublishdb.DocType;
import com.sword.gsa.connectors.ezpublishdb.ForeignKey;
import com.sword.gsa.connectors.ezpublishdb.ResultSetCollection;

import sword.common.databases.sql.DBType;
import sword.common.databases.sql.DataBaseConnection;
import sword.common.utils.StringUtils;
import sword.common.utils.throwables.ExceptionDigester;
import sword.connectors.commons.config.CPUtils;



public class testDBcalls {

	private static final Logger LOG = Logger.getLogger(com.sword.gsa.connectors.ezpublishdb.Connector.class);

	public static final String DOCBASE_TAGNAME = "Docbase";
	public static final String DOCBASE_NAME_ATTR = "name";
	public static final String DOCBASE_USER_ATTR = "user";
	public static final String DOCBASE_PASSWORD_ATTR = "password";

	public static final String DOCTYPE_TAGNAME = "DocType";
	public static final String NAME_ATTR = "Name";
	public static final String UID_ATTR = "UniqueID";
	public static final String ID_ATTR = "IdAttribute";
	public static final String MODIF_DATE_ATTR = "ModifyDateAttribute";
	public static final String FILTER_ATTR = "Filter";
	public static final String INDEX_SYSTEM_ATTRIBUTES = "IndexSystemAttributes";

	public static final String CONSTANT_TAGNAME = "Constant";
	public static final String ATTRIBUTE_TAGNAME = "Attribute";
	public static final String FOREIGN_KEY_TAGNAME = "ForeignKey";
	public static final String VALUE_ATTR = "Value";
	public static final String TYPE_ATTR = "Type";
	public static final String DISPLAY_NAME_ATTR = "DisplayName";
	public static final String TARGET_TYPE_ATTR = "TargetType";

	private static ResultSet rs;
	private static List<DocType> dtlist;

	public static void main(String[] args) throws ClassNotFoundException, SQLException, InstantiationException, IllegalAccessException, ConfigurationException{
		
		String name="Tous les/contraceptifs/d’urgence=_peuvent-ils être délivrés à titre gratuit aux mineures ?";
		String separator="-";
		String normalized= Normalizer.normalize(name, Normalizer.Form.NFD)
			.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
			.replaceAll("[^a-zA-Z0-9./-]+", separator)//[ ’'()\"|:?=*!/\\+;@`~#{}\\[\\]<>,-]+
			//.replaceAll("[^a-zA-Z0-9]{2,}", separator)//then doublons
			.toLowerCase();
System.out.println(normalized);
		//quickrequest();
		

	}
	private static void quickrequest() throws ConfigurationException, ClassNotFoundException, SQLException {
		readCMSConfig("resources/conf/CMSObjects.xml");

		DBType dbtype = DBType.lookupName("my_sql");
		System.out.println(dbtype.name());
		String connectionString = "gspsconnect2.parisgsa.lan:3306/ezpub52";
		try(DataBaseConnection dbc = new DataBaseConnection(dbtype, connectionString, "connector", "sword75")){
			//dbc.connectWithRemoteDriver(DBType.MY_SQL.driverClass, testDBcalls.class.getClassLoader(), true);
			dbc.connect(true);

			//	for(DocType docType : dtlist){

			String query = null;
			StringBuilder sb = new StringBuilder("SELECT ");
			//	sb.append(docType.idAttrName);
			sb.append("path_identification_string");
			sb.append(" FROM ");
			sb.append("ezcontentobject_tree");
			sb.append(" where contentobject_id= 61 ");
			query=sb.toString();
			try (Statement st = dbc.getStatement()) {
				rs=(st.executeQuery(query));
			//	while(rs.next())System.out.println(rs.getString(1));
			}
System.out.println(urlBuild(dbc));
			//		}
			dbc.close();
		}
		
	}
	private static String urlBuild(DataBaseConnection dbc) throws SQLException{

		Map<String,String> mapIdNodeId= new HashMap<String, String>();
		Map<String,String> mapNodeIdParentId = new HashMap<String, String>();
		Map<String,String> mapNodeIdName = new HashMap<String, String>();
		StringBuilder sb = new StringBuilder("SELECT ");
		sb.append("id, node_id, parent_node_id, name");
		sb.append(" FROM "); 
		sb.append("ezcontentobject o LEFT JOIN ezcontentobject_tree t");
		sb.append(" ON "); 
		sb.append("o.id = t.contentobject_id");
		String query=sb.toString();
		try (Statement st = dbc.getStatement()) {
			try(ResultSet rs = (st.executeQuery(query))){
				while(rs.next()){
					String id=rs.getString("Id");
					String nodeId=rs.getString("node_id");
					String parentNodeId=rs.getString("parent_node_id");
					String name=rs.getString("name");
					name=normalizeName(name);
					//System.out.println("id:"+id+"nodeId:"+nodeId+"parentNodeId:"+parentNodeId+"name:"+name);

					if(!"null".equals(id)&&!"null".equals(nodeId)&&!"null".equals(parentNodeId)&&!"null".equals(name)){
						mapIdNodeId.put(id, nodeId);
						mapNodeIdParentId.put(nodeId, parentNodeId);
						mapNodeIdName.put(nodeId, name);
					}

				}
				for (String itid : mapIdNodeId.keySet()){
					String nodeId=mapIdNodeId.get(itid);
					String url=gatherUrlRec(mapNodeIdParentId,mapNodeIdName,nodeId,"");
					System.out.println("url for "+itid+"/"+nodeId+":"+url);
					return url;
				}
			}
		}
		return query;
	}



	private static String normalizeName(String name) {
		String separator="-";
		if(StringUtils.isNullOrEmpty(separator))separator="-";
		return Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("[\\p{InCombiningDiacriticalMarks}]", "").replaceAll(" ", separator);
	}

	private static String gatherUrlRec(Map<String, String> mapNodeIdParentId, Map<String, String> mapNodeIdName, String nodeId, String url) {
		url="/"+mapNodeIdName.get(nodeId)+url;
		String parentId=mapNodeIdParentId.get(nodeId);
		System.out.println("nodeId:"+nodeId+"/url:"+url);
		if("1".equals(parentId)||StringUtils.isNullOrEmpty(parentId)||!mapNodeIdName.containsKey(parentId))return url;

		return gatherUrlRec(mapNodeIdParentId, mapNodeIdName, mapNodeIdParentId.get(nodeId), url);

	}
	public static void readCMSConfig(String objFilePath) throws ConfigurationException {

		try {
			Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(objFilePath);
			Element xml = document.getDocumentElement();
			Element e = (Element) xml.getElementsByTagName(DOCBASE_TAGNAME).item(0);

			NodeList nlDoctypes = xml.getElementsByTagName(DOCTYPE_TAGNAME);
			if (nlDoctypes==null || nlDoctypes.getLength()==0) {
				throw new Exception("No tag "+DOCTYPE_TAGNAME+" found");
			} else {
				LOG.debug("Parsing "+nlDoctypes.getLength()+" doctypes");
				System.out.println("Parsing "+nlDoctypes.getLength()+" doctypes");
			}
			dtlist=processDocTypes(nlDoctypes);
		} catch (Exception e) {
			throw new ConfigurationException(ExceptionDigester.toString(e));
		}
	}

	private static List<DocType> processDocTypes(NodeList nlDoctypes) throws Exception {
		DocType curDocType = null;
		ArrayList<DocType> docList= new ArrayList<DocType>();
		String name=null, uniqueID=null, wc=null, idAttrName=null, mdAttrName=null;
		boolean indexSysAttr = true;
		Element currentEl = null;
		Set<String> uniqueIDs = new HashSet<String>();
		for (int j = 0; j < nlDoctypes.getLength(); j++) {
			currentEl = (Element) nlDoctypes.item(j);
			name = currentEl.getAttribute(NAME_ATTR);
			idAttrName = currentEl.getAttribute(ID_ATTR);
			mdAttrName = currentEl.getAttribute(MODIF_DATE_ATTR);
			LOG.trace("DocType name: "+name);
			System.out.println("DocType name: "+name);
			wc = currentEl.getAttribute(FILTER_ATTR);
			LOG.trace("Filter: "+wc);
			uniqueID = currentEl.getAttribute(UID_ATTR);
			if (currentEl.hasAttribute(INDEX_SYSTEM_ATTRIBUTES)) indexSysAttr = Boolean.parseBoolean(currentEl.getAttribute(INDEX_SYSTEM_ATTRIBUTES));
			if (name==null || "".equals(name)) {
				LOG.warn("Unconsistent doctype (no name) ; skipping");
				continue;
			}
			if (idAttrName==null || "".equals(idAttrName)) {
				LOG.warn("Unconsistent doctype (no idAttrName) ; skipping");
				continue;
			}
			if (mdAttrName==null)mdAttrName = "";
			if (uniqueID==null || "".equals(uniqueID)) uniqueID = name;
			if (uniqueIDs.contains(uniqueID)) throw new Exception("Found a duplicate doctype ID: " + uniqueID);
			else uniqueIDs.add(uniqueID);
			LOG.trace("DocType UID: "+uniqueID);
			curDocType = new DocType(name, wc, uniqueID, indexSysAttr, idAttrName, mdAttrName);
			NodeList nlAtributes = currentEl.getElementsByTagName(ATTRIBUTE_TAGNAME);
			if (nlAtributes==null || nlAtributes.getLength()==0) {//Only uses default attribute set
				LOG.trace("No attributes");
			} else {
				LOG.trace("Reading "+nlAtributes.getLength()+" attributes");
				processAttributes(nlAtributes, curDocType);
			}

			nlAtributes = currentEl.getElementsByTagName(FOREIGN_KEY_TAGNAME);
			if (nlAtributes==null || nlAtributes.getLength()==0) {//Only uses default attribute set
				LOG.trace("No Foreign keys");
			} else {
				LOG.trace("Reading "+nlAtributes.getLength()+" foreign keys");
				processForeignKeys(nlAtributes, curDocType);
			}

			nlAtributes = currentEl.getElementsByTagName(CONSTANT_TAGNAME);
			if (nlAtributes==null || nlAtributes.getLength()==0) {//Only uses default attribute set
				LOG.trace("No constant");
			} else {
				LOG.trace("Reading "+nlAtributes.getLength()+" constants");
				Element e = null;
				for (int i=0; i<nlAtributes.getLength(); i++) {
					e = (Element) nlAtributes.item(i);
					curDocType.constants.add(new Constant(e.getAttribute(NAME_ATTR), e.getAttribute(VALUE_ATTR)));
				}
			}
			docList.add(curDocType);
		}
		return docList;
	}

	@SuppressWarnings("deprecation")
	private static void processAttributes(NodeList nlAtributes, DocType curDocType) {
		Attribute attr = null;
		String attrName, attrType, attrDisplayName;
		Element attrElement = null;
		for (int i = 0; i < nlAtributes.getLength(); i++) {
			attrElement = (Element) nlAtributes.item(i);
			attrName = attrElement.getAttribute(NAME_ATTR);
			attrType = attrElement.getAttribute(TYPE_ATTR);
			attrDisplayName = attrElement.getAttribute(DISPLAY_NAME_ATTR);
			if (attrName==null || "".equals(attrName)) {
				LOG.warn("Unconsistent attribute (no name) ; skipping");
				continue;
			} else if (attrDisplayName==null || "".equals(attrDisplayName)) {
				LOG.warn("No displayName for " + attrName + " ; using its name as DN");
				attrDisplayName = attrName;
			}
			if (AttributeType.lookUp(attrType)==null) {
				LOG.warn("No type for " + attrName + " ; registering it as a String");
				attrType = "string";
			}
			attr = new Attribute(attrName, AttributeType.lookUp(attrType), attrDisplayName);
			LOG.trace("Adding attribute: "+attrName + " ("+attrDisplayName+") ; " + attrType);

			/**
			 * For retro-compatibility with old style Objects.xml file
			 */
			if (attr.type == AttributeType.FIXED) curDocType.constants.add(new Constant(attr.name, attr.displayName));
			else curDocType.attributes.add(attr);
		}
	}

	private static void processForeignKeys(NodeList nlFK, DocType curDocType) {
		ForeignKey fk = null;
		String fkAttrName, fkAttrType, fkTargAttrType, fkAttrDisplayName, subQuery;
		Element fkElement = null;
		for (int k = 0; k < nlFK.getLength(); k++) {
			fkElement = (Element) nlFK.item(k);
			fkAttrName = fkElement.getAttribute(NAME_ATTR);
			fkAttrType = fkElement.getAttribute(TYPE_ATTR);
			fkTargAttrType = fkElement.getAttribute(TARGET_TYPE_ATTR);
			fkAttrDisplayName = fkElement.getAttribute(DISPLAY_NAME_ATTR);
			try {
				subQuery = fkElement.getFirstChild().getNodeValue();
			} catch (Exception e) {
				LOG.warn("Found foreign key with no sub query");
				subQuery = null;
			}
			if (fkAttrName==null || "".equals(fkAttrName)) {
				LOG.warn("Unconsistent foreign key (no name) ; skipping");
				continue;
			} else if (fkAttrDisplayName==null || "".equals(fkAttrDisplayName)) {
				LOG.info("No displayName for " + fkAttrName + " ; using its name as DN");
				fkAttrDisplayName = fkAttrName;
			}
			if (AttributeType.lookUp(fkAttrType)==null) {
				LOG.info("No type for " + fkAttrName + " ; registering it as a String");
				fkAttrType = "string";
			}

			LOG.trace("Adding foreign key: "+fkAttrName + " ("+fkAttrDisplayName+") ; " + fkAttrType);
			fk = new ForeignKey(
				fkAttrName, 
				AttributeType.lookUp(fkAttrType), 
				AttributeType.lookUp(fkTargAttrType), 
				fkAttrDisplayName, 
				subQuery);
			curDocType.attributes.add(fk);
		}
	}
}
