package com.sword.gsa.connectors.ezpublishdb;

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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import sword.common.databases.sql.DBType;
import sword.common.databases.sql.DataBaseConnection;
import sword.common.utils.StringUtils;
import sword.common.utils.throwables.ExceptionDigester;
import sword.connectors.commons.config.CPUtils;

import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;

public class Explorer extends AExplorer {

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

	private static final String ROOTNODE = "2";

	protected Map<String,String> refTypes=new HashMap<String,String>();
	protected Map<String,DocType> dtInConf;
	protected List<String> binaryTypes;
	protected DataBaseConnection dbc;
	protected List<String> dtToIndex;
	protected Map<String,String> mapUrl = new HashMap<String, String>();
	protected Map<String,String> mapIdNodeId= new HashMap<String, String>();
	protected Map<String,List<String>> mapRefDocs;
	protected boolean onlyRefFiles=false;
	protected List<String> langids;
	protected List<String> inSiteIds = new ArrayList<String>();
	protected Map<String, String> mapNodeIdParentId = new HashMap<String, String>();
	protected Map<String, String> mapNodeIdName = new HashMap<String, String>();
	protected String rootnode=null;


	public Explorer(final PushProcessSharedObjectsStore sharedObjects) throws Exception {
		super(sharedObjects);


		onlyRefFiles=Boolean.parseBoolean(configurationParams.get(Connector.ONLY_REFERENCED_FILES));
		DBType dbtype = DBType.lookupName(configurationParams.get(Connector.DB_TYPE));
		binaryTypes = CPUtils.stringToList(configurationParams.get(Connector.BINARY_TYPES));
		dtToIndex = CPUtils.stringToList(configurationParams.get(Connector.TO_INDEX_TYPES));
		dbc = new DataBaseConnection(dbtype, configurationParams.get(Connector.CONNECTION_STRING), configurationParams.get(Connector.USERNAME), CPUtils.decrypt(configurationParams.get(Connector.PWD)));
		dbc.connectWithRemoteDriver(dbtype.driverClass, this.getClass().getClassLoader(), true);
		mapRefDocs=new HashMap<String, List<String>>();
		langids=getlangquery(LANG.lookupName(configurationParams.get(Connector.LANGUAGE)));
		//dbc.connect(true);
		

	}


	private List<String> getlangquery(LANG lookupName) throws SQLException {
		List<String> list=new ArrayList<String>();
		String query = null;
		StringBuilder sb = new StringBuilder("SELECT ");
		sb.append("id");
		sb.append(" FROM ");
		sb.append("ezcontent_language");
		sb.append(" WHERE ");
		sb.append("locale LIKE " );
		sb.append("'"+lookupName.langStr+"'");
		query=sb.toString();
		LOG.debug("getlangquery: "+query);
		try (Statement st = dbc.getStatement()) {
			try(ResultSet rs = (st.executeQuery(query))){
				while(rs.next())list.add(rs.getString(1));
			}
		}
		//so there is this weird thing with language being power of two and language masks and id being possibly powerOftwo+1, which means we have to add those+1 as member of the list
		List<String> listPlusOne=new ArrayList<String>();
		for(String i:list){
			listPlusOne.add(String.valueOf(Integer.parseInt(i)+1));
		}
		list.addAll(listPlusOne);
		return list;
	}



	@Override
	public List<ContainerNode> getRootNodes() throws Exception {

		readCMSConfig("_conf/CMSObjects.xml","../conf/CMSObjects.xml");
		buildUrlMap();
		List<ContainerNode> rootNodes= new ArrayList<ContainerNode>();
		for (String dt : dtToIndex){
			DocType docType= dtInConf.get(dt);
			if(docType==null){
				docType=new DocType(dt, "", lookId(dt), true, "id", "modified");
				docType.attributes.addAll(defaultAttrList());
				docType.constants.addAll(defaultConstList());
				docType.constants.add(new Constant("Type", dt));
				dtInConf.put(dt, docType);
			}

			rootNodes.add(new ContainerNode(dt, null));
			LOG.info("Node type: "+dt);
		}
		for (String dt : binaryTypes){
			DocType docType= dtInConf.get(dt);
			if(docType==null){
				docType=new DocType(dt, "", lookId(dt), true, "id", "modified");
				docType.attributes.addAll(defaultAttrList());
				docType.constants.addAll(defaultConstList());
				docType.constants.add(new Constant("Type", dt));
				dtInConf.put(dt, docType);
			}
			if(onlyRefFiles&&!dtToIndex.contains(dt))//to be directly indexed only if referenced. 
				refTypes.put(lookId(dt),dt);
		}
		LOG.info("additionally, there are "+refTypes.size()+" binary types");
		return rootNodes;

	}


	protected List<Constant> defaultConstList() {
		List<Constant> constantList= new ArrayList<Constant>();
		constantList.add(new Constant("Source", "EZPublish"));
		return constantList;
	}


	protected List<Attribute> defaultAttrList() {
		List<Attribute> attributeList= new ArrayList<Attribute>();
		attributeList.add(new Attribute("id", AttributeType.lookUp("String"), "Id"));
		attributeList.add(new Attribute("owner_id", AttributeType.lookUp("String"), "owner_id"));
		attributeList.add(new Attribute("modified", AttributeType.lookUp("String"), "modified"));
		attributeList.add(new Attribute("current_version", AttributeType.lookUp("String"), "current_version"));
		attributeList.add(new Attribute("name", AttributeType.lookUp("String"), "name"));
		attributeList.add(new Attribute("contentclass_id", AttributeType.lookUp("String"), "contentclass_id"));

		return attributeList;
	}


	@Override
	public void loadChildren(final ContainerNode node, final boolean isUpdateMode, final boolean isPublicMode) throws Exception {
		DocType docType= dtInConf.get(node.id);
		String query = null;
		StringBuilder sb = new StringBuilder("SELECT ");
		sb.append(docType.idAttrName);
		sb.append(" FROM ");
		sb.append("ezcontentobject");
		sb.append(" WHERE ");
		sb.append(" contentclass_id=" + docType.uid);
		if(Boolean.parseBoolean(configurationParams.get(Connector.PUBLISHEDONLY))){
			sb.append(" AND ");
			sb.append("status=1");
		}
		query=sb.toString();
		try (Statement st = dbc.getStatement()) {
			try(ResultSet rs = (st.executeQuery(query))){
				while(rs.next()){
					node.children.add(new DocumentNode(node.id+"|"+rs.getString(1), node));
					if(!refTypes.isEmpty()&&onlyRefFiles)node.children.addAll(addRefTypesToIndex(rs.getString(1),node));
				}
			}

		}
	}
	
	/* addRefTypesToIndex  	=>  on indexe les pdfs à partir des pages HTML référencées dans la base  */
	//some types only should be indexed when referenced elsewhere
	protected List<DocumentNode> addRefTypesToIndex(String id, ContainerNode node) throws SQLException {
		ArrayList<DocumentNode> filesChildren=new ArrayList<DocumentNode>(); 
		//"SELECT to_contentobject_id FROM `ezcontentobject_link`where from_contentobject_id={0}"
		String query = null;
		//SELECT `to`.current_version, `to`.id, COUNT(`to`.current_version) AS NumberOfdoubles FROM 
		//(`ezcontentobject_link` inner join `ezcontentobject` `from` on `from_contentobject_id` = `from`.id) INNER JOIN `ezcontentobject` `to` ON to_contentobject_id=`to`.id where `from_contentobject_version`=`from`.current_version and `from`.id=101199 GROUP BY `to`.id 
		StringBuilder sb = new StringBuilder("SELECT ");
		sb.append("`to`.id, ");
		sb.append("`to`.contentclass_id, ");
		sb.append("COUNT(`to`.current_version)");
		sb.append(" FROM ");
		sb.append("(`ezcontentobject_link` inner join `ezcontentobject` `from` on `from_contentobject_id` = `from`.id) ");
		sb.append("INNER JOIN `ezcontentobject` `to` ON to_contentobject_id=`to`.id");
		sb.append(" WHERE ");
		sb.append(" from_contentobject_id=" + id);
		sb.append(" AND ");
		sb.append(" `from_contentobject_version`=`from`.current_version ");
		sb.append(" AND (");
		for(String refType : refTypes.keySet()){
			sb.append("`to`.contentclass_id=");
			sb.append(refType);
			sb.append(" OR ");
		}
		sb.append("0 )");
		sb.append(" GROUP BY `to`.id");
		query=sb.toString();
		LOG.debug("doc query: "+query);
		try (Statement st = dbc.getStatement()) {
			try(ResultSet rs = (st.executeQuery(query))){
				while(rs.next()){
					filesChildren.add(new DocumentNode(refTypes.get(rs.getString(2))+"|"+rs.getString(1),node));//to.contentclassid|id
					if(!mapRefDocs.containsKey(rs.getString(1)))mapRefDocs.put(rs.getString(1), new ArrayList<String>());
					if(!mapRefDocs.get(rs.getString(1)).contains(id))mapRefDocs.get(rs.getString(1)).add(id);
				}
			}
		}
		return filesChildren;
	}


	protected String lookId(String identifier) throws SQLException {
		String query = null;
		StringBuilder sb = new StringBuilder("SELECT ");
		sb.append("id");
		sb.append(" FROM ");
		sb.append("ezcontentclass");
		sb.append(" WHERE ");
		sb.append(" identifier='"+ identifier+"'");

		query=sb.toString();
		try (Statement st = dbc.getStatement()) {
			try(ResultSet rs = (st.executeQuery(query))){
				while(rs.next()){
					String uid=rs.getString(1);
					LOG.info("Type "+identifier+" not recognized, id looked up: " +uid);
					return uid;
				}
			}

		}
		return null;
	}


	protected void buildUrlMap() throws SQLException {
		
		StringBuilder sb = new StringBuilder("SELECT ");
		sb.append("id, node_id, parent_node_id, name");
		sb.append(" FROM "); 
		sb.append("ezcontentobject o INNER JOIN ezcontentobject_tree t");
		sb.append(" ON "); 
		sb.append("o.id = t.contentobject_id");
		if(Boolean.parseBoolean(configurationParams.get(Connector.PUBLISHEDONLY))){
			sb.append(" AND ");
			sb.append("is_hidden=0");
			sb.append(" AND ");
			sb.append("is_invisible=0");
		}
		String query=sb.toString();
		try (Statement st = dbc.getStatement()) {
			try(ResultSet rs = (st.executeQuery(query))){
				while(rs.next()){
					String id=rs.getString("Id");
					String nodeId=rs.getString("node_id");
					String parentNodeId=rs.getString("parent_node_id");
					String name=rs.getString("name");
					name=normalizeName(name);

					if(!"null".equals(id)&&!"null".equals(nodeId)&&!"null".equals(parentNodeId)&&!"null".equals(name)){
						mapIdNodeId.put(id, nodeId);
						mapNodeIdParentId.put(nodeId, parentNodeId);
						mapNodeIdName.put(nodeId, name);
					}

				}
				for (String itid : mapIdNodeId.keySet()){
					String nodeId=mapIdNodeId.get(itid);
					String url=gatherUrlRec(mapNodeIdParentId,mapNodeIdName,nodeId,"");
					LOG.trace("url for "+itid+"/"+nodeId+":"+url);
					mapUrl.put(itid, url);
				}
			}
		}
	}

	protected String normalizeName(String name) {
		String separator=configurationParams.get(Connector.URLSEPARATOR);
		if(StringUtils.isNullOrEmpty(separator))separator="-";
		String normalized= Normalizer.normalize(name, Normalizer.Form.NFD)
			.replaceAll("Å“","oe")
			.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
			.replaceAll("[^a-zA-Z0-9./!-]+", separator)//[ â€™'()\"|:?=*!/\\+;@`~#{}\\[\\]<>,-]+
			.replaceAll("[^a-zA-Z0-9/!]{2,}", separator)//then doublons
			.toLowerCase();
		while(normalized.endsWith(separator)||normalized.endsWith(".")||normalized.endsWith("-")||normalized.endsWith("_"))normalized=normalized.substring(0, normalized.length()-1);//then trailing characters
		return normalized;
	}

	protected String gatherUrlRec(Map<String, String> mapNodeIdParentId, Map<String, String> mapNodeIdName, String nodeId, String url) {
		if(ROOTNODE.equals(nodeId))return url;
		url="/"+mapNodeIdName.get(nodeId).toLowerCase()+url;
		String parentId=mapNodeIdParentId.get(nodeId);
		if("1".equals(parentId)||StringUtils.isNullOrEmpty(parentId)||!mapNodeIdName.containsKey(parentId))return url;

		return gatherUrlRec(mapNodeIdParentId, mapNodeIdName, parentId, url);
	}

	@Override
	public void close() throws Exception {
		dbc.close();
	}

	public void readCMSConfig(String objFilePath, String DefaultFilePath) throws ConfigurationException {

		try {
			Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(objFilePath);
			Element xml = document.getDocumentElement();
			//	Element e = (Element) xml.getElementsByTagName(DOCBASE_TAGNAME).item(0);

			NodeList nlDoctypes = xml.getElementsByTagName(DOCTYPE_TAGNAME);
			if (nlDoctypes==null || nlDoctypes.getLength()==0) {
				throw new Exception("No tag "+DOCTYPE_TAGNAME+" found");
			} else {
				LOG.debug("Parsing "+nlDoctypes.getLength()+" doctypes");
			}
			dtInConf=processDocTypes(nlDoctypes);
		} catch (Exception e) {//if fails, try default
			LOG.warn("no configuration file found for this instance, using default. ");
			try {
				Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(DefaultFilePath);
				Element xml = document.getDocumentElement();
				//	Element e = (Element) xml.getElementsByTagName(DOCBASE_TAGNAME).item(0);

				NodeList nlDoctypes = xml.getElementsByTagName(DOCTYPE_TAGNAME);
				if (nlDoctypes==null || nlDoctypes.getLength()==0) {
					throw new Exception("No tag "+DOCTYPE_TAGNAME+" found");
				} else {
					LOG.debug("Parsing "+nlDoctypes.getLength()+" doctypes");
				}
				dtInConf=processDocTypes(nlDoctypes);
			}catch (Exception e1) {
				throw new ConfigurationException(ExceptionDigester.toString(e1));
			}
		}
	}

	private Map<String,DocType> processDocTypes(NodeList nlDoctypes) throws Exception {
		DocType curDocType = null;
		Map<String,DocType> docMap= new HashMap<String,DocType>();
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
			wc = currentEl.getAttribute(FILTER_ATTR);
			LOG.trace("Filter: "+wc);
			uniqueID = lookId(name);//CHanges from one version to the other, so we are asking EzPublish
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
			docMap.put(curDocType.name,curDocType);
		}
		return docMap;
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
				LOG.debug("No displayName for " + fkAttrName + " ; using its name as DN");
				fkAttrDisplayName = fkAttrName;
			}
			if (AttributeType.lookUp(fkAttrType)==null) {
				LOG.debug("No type for " + fkAttrName + " ; registering it as a String");
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
