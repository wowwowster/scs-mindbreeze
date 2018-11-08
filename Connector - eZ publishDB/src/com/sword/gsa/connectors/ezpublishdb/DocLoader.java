package com.sword.gsa.connectors.ezpublishdb;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import sword.common.databases.sql.DataBaseConnection;
import sword.common.http.client.FullyClosableInputStream;
import sword.common.http.client.HttpClientHelper;
import sword.common.utils.dates.ThreadSafeDateFormat;
import sword.connectors.commons.config.CPUtils;
import sword.gsa.xmlfeeds.builder.Metadata;

import com.mysql.jdbc.StringUtils;
import com.sword.gsa.spis.scs.commons.connector.models.ADocLoader;
import com.sword.gsa.spis.scs.commons.connector.models.AExplorer;
import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.throwables.DoNotIndex;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;

public class DocLoader extends ADocLoader {

	public static final ThreadSafeDateFormat EZ_DATES = new ThreadSafeDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"));
	private static final String ROOTNODE = "2";
	protected String objectId;
	final Explorer ezExpl;
	protected DocType docType;
	protected DataBaseConnection dbc;
	protected String version;
	protected Boolean isBinaryType;
	private CloseableHttpClient httpClient; 
	protected String fileurl=null;
	protected boolean indexSearchableOnly;
	private boolean onlyRefFiles=false;
	protected boolean followMetas=false;
	private String mime;
	private List<Metadata> references;
	private boolean isRefType=false;
	private Map<String, String> typeRenameMap;
	private List<String> excludedMeta;
	protected Map<String, String> mapNodeIdName = new HashMap<String, String>();//rebuild a new one for new url method
	private String url;


	public DocLoader(final AExplorer explorer, final PushProcessSharedObjectsStore sharedObjects, final ContainerNode parentNode) {
		super(explorer, sharedObjects, parentNode);
		List<String> toBeRenamedTypes = CPUtils.stringToList(configurationParams.get(Connector.TYPE_RENAMING));
		excludedMeta = CPUtils.stringToList(configurationParams.get(Connector.EXCLUDED_METAS));
		typeRenameMap=processTypeRenaming(toBeRenamedTypes);
		ezExpl = (Explorer) explorer;
		onlyRefFiles= ezExpl.onlyRefFiles;
		dbc=ezExpl.dbc;
		indexSearchableOnly=Boolean.parseBoolean(configurationParams.get(Connector.SEARCHABLEONLY));
		httpClient = HttpClientHelper.getHttpClient(HttpClientHelper.getMultithreadedConnMgr(80, 100), HttpClientHelper.createRequestConfig(true, true, 3, 100_000, 100_000, (int) sharedObjects.pushConf.httpClientTimeout), HttpClientHelper.createHttpRoutePlannerFromSysProps(), null, null);
		followMetas=Boolean.parseBoolean(configurationParams.get(Connector.FOLLOWMETAS));
	}

	private Map<String, String> processTypeRenaming(List<String> toBeRenamedTypes) {
		Map<String,String> map=new HashMap<String, String>();
		for(String type : toBeRenamedTypes){
			String[] typetab = type.split("=");
			map.put(typetab[0], typetab[1]);
		}
		return map;
	}

	@Override
	public void loadObject(final DocumentNode docNode) throws DoNotIndex, Exception {
		String [] idSplit = docNode.id.split("\\|");//<smth>|<ID>
		String stringType = idSplit[0];
		objectId=idSplit[1];
		docType=ezExpl.dtInConf.get(stringType);
		version=getlatestversion(objectId);
		fileurl=null;
		mime="";

		isBinaryType=ezExpl.binaryTypes.contains(stringType);
		isRefType=ezExpl.refTypes.containsValue(stringType);
		references=new ArrayList<Metadata>();//reset for each object
		if(isBinaryType){
			loadBinary();
			if(onlyRefFiles&&isRefType){
				constructRefMetas(references);
			}
		}else {
			String path=buildPath();
			url= configurationParams.get(Connector.HOST);
			if(Boolean.parseBoolean(configurationParams.get(Connector.ISMULTILANG))){
				if(!url.endsWith("/"))url+="/";
				url+=configurationParams.get(Connector.LANGUAGE).toLowerCase();
			}
			if(!url.endsWith("/")&&!path.startsWith("/"))url+="/";
			url+=path;
			LOG.debug(url);
			//try validity
			HttpGet get = new HttpGet(url);
			try(CloseableHttpResponse r = httpClient.execute(get)){
				if (r.getStatusLine().getStatusCode() == 404){//if 404, use default url for all nodes, ie host/content/view/full/
					LOG.warn("url failed for "+objectId+"/"+ezExpl.mapIdNodeId.get(objectId)+": "+url);
					url=configurationParams.get(Connector.HOST);
					if(!url.endsWith("/"))url+="/";
					if(Boolean.parseBoolean(configurationParams.get(Connector.ISMULTILANG)))url+=configurationParams.get(Connector.LANGUAGE).toLowerCase();
					if(!url.endsWith("/"))url+="/";
					url+="content/view/full/"+ezExpl.mapIdNodeId.get(objectId);
					HttpGet get2 = new HttpGet(url);
					try(CloseableHttpResponse r2 = httpClient.execute(get2)){
						if (r2.getStatusLine().getStatusCode() == 404){
							throw new DoNotIndex(url);
						}
					}
				}
			}
		}

	}

	private void loadBinary() throws SQLException, UnsupportedEncodingException {
		String binurl=configurationParams.get(Connector.HOST)+"/content/download/{0}/{1}/version/{2}/file/{3}";
		MessageFormat binurlmf = new MessageFormat(binurl);

		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT bin.original_filename AS filename, bin.contentobject_attribute_id AS contentattrid, bin.version AS vers, attr.contentobject_id AS id FROM ezbinaryfile AS bin inner join ezcontentobject_attribute AS attr ON bin.contentobject_attribute_id=attr.id WHERE attr.contentobject_id=" + objectId + " AND bin.version=" + version)) {
				if (rs.next()) {
					String filename =URLEncoder.encode(rs.getString("filename"), "utf-8");
					String attributeid = rs.getString("contentattrid");
					String id = rs.getString("id");
					String version = rs.getString("vers");

					fileurl = binurlmf.format(new String[] { id, attributeid, version, filename });
					LOG.info("File URL: " + fileurl);

				}
			}
		}
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT mime_type FROM ezbinaryfile "
				+ "WHERE contentobject_attribute_id in "
				+ "( select id from ezcontentobject_attribute where contentobject_id=" + objectId + " and version=" + version + ")")) {
				if (rs.next()) {
					mime= rs.getString(1);
				}else {
					mime = null;
				}


			}
		}
		if(fileurl == null || mime == null) {
			LOG.info("File "+objectId+" is not found as binary, handling as html");
			mime= "text/html";
			String path=buildPath();
			fileurl= configurationParams.get(Connector.HOST);
			if(Boolean.parseBoolean(configurationParams.get(Connector.ISMULTILANG))){
				if(!fileurl.endsWith("/"))fileurl+="/";
				fileurl+=configurationParams.get(Connector.LANGUAGE).toLowerCase();
			}
			if(!fileurl.endsWith("/")&&!path.startsWith("/"))fileurl+="/";
			fileurl+=path;
			LOG.info("url for non binary "+objectId+" in binary type: "+fileurl);
		}
	}



	@Override
	public Date getModifyDate() throws ParseException, SQLException {
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT " + docType.mdAttrName + " FROM ezcontentobject_version WHERE contentobject_id=" + objectId + " AND version=" + version)) {
				if (rs.next()) return new Date(rs.getInt(1) * 1000L);
				else return null;
			}
		}
	}

	@Override
	public void getMetadata(final List<Metadata> metadata) throws Exception {

		readMetadata(metadata);
		buildMetasRecursively("", objectId, metadata);
		if(!references.isEmpty())metadata.addAll(references);
		if(isBinaryType&&!StringUtils.isNullOrEmpty(ezExpl.mapUrl.get(objectId))){
			String url= configurationParams.get(Connector.HOST);
			if(!url.endsWith("/")&&!ezExpl.mapUrl.get(objectId).startsWith("/"))url+="/";
			url+=ezExpl.mapUrl.get(objectId);
			metadata.add(new Metadata("nonbinary_url",url));
		}
		if(typeRenameMap.containsKey(docType.name))typeExchange(typeRenameMap.get(docType.name), metadata);
	}

	private void typeExchange(String value, List<Metadata> metadata) {
		for (int i = 0;i<metadata.size();i++){
			if(metadata.get(i).name.equalsIgnoreCase("type")){
				metadata.remove(i);
				metadata.add(new Metadata("Type", value));
			}
		}
	}

	private List<Metadata> constructRefMetas(List<Metadata> metadata) throws DoNotIndex {
		List<String> referencers=ezExpl.mapRefDocs.get(objectId);
		int i=0;
		if(referencers!=null)
			for (String refId: referencers){
				i++;
				String url=configurationParams.get(Connector.HOST);
				if(!url.endsWith("/")&&!ezExpl.mapUrl.get(refId).startsWith("/"))url+="/";
				url+=ezExpl.mapUrl.get(refId);
				metadata.add(new Metadata("reference#"+i, url));
			}else throw new DoNotIndex("No ref for ref-only doc:"+objectId);
		return metadata;
	}

	private void readMetadata(List<Metadata> metas) throws Exception {
		for (Constant c: docType.constants) metas.add(new Metadata(c.name, c.value));
		Set<String> valueContainer = new HashSet<String>();
		for (Attribute a: docType.attributes) {
			valueContainer.clear();
			getAttributeValue(a, valueContainer);
			if (a.isForeignReference()) {
				Set<String> vc2 = new HashSet<String>();
				for (String value: valueContainer) buildForeignValue((ForeignKey) a, value, vc2);
				valueContainer.clear();
				valueContainer.addAll(vc2);
			}
			for (String value: valueContainer) {
				metas.add(new Metadata(a.displayName,value));
				LOG.trace("\t\t\t\t\t- "+a.displayName+"="+value);
			}
		}
	}

	protected void getAttributeValue(Attribute attr, Set<String> valueContainer) throws Exception {

		if (attr.name.startsWith("ATTRID::")) {
			LOG.debug("This is an automatically detected attribute: " + attr.displayName + " --- Getting value from ezcontentobject_attribute");
			try {
				String attrToFetch = "";
				String query = "";
				String attrType = attr.name.split("::")[1].toLowerCase();
				if (attrType.equals("ezstring")) {
					LOG.debug("This is an ezstring");
					query = "SELECT data_text FROM ezcontentobject_attribute WHERE contentclassattribute_id=" + attr.name.split("::")[2] + " AND contentobject_id=" + objectId
						+ " AND version=" + version;
					attrToFetch = "data_text";
				} else if (attrType.equals("ezxmltext")) {
					LOG.debug("This is an ezxmltext");
					query = "SELECT data_text FROM ezcontentobject_attribute WHERE contentclassattribute_id=" + attr.name.split("::")[2] + " AND contentobject_id=" + objectId
						+ " AND version=" + version;
					attrToFetch = "data_text"; // XSL TRANSFORMATION
				} else if (attrType.equals("ezbinaryfile")) {
					LOG.debug("This is an ezbinaryfile");
					query = "SELECT original_filename FROM ezbinaryfile WHERE contentobject_attribute_id in (SELECT id from ezcontentobject_attribute WHERE contentclassattribute_id="
						+ attr.name.split("::")[2] + " AND contentobject_id=" + objectId + " AND version=" + version + ")";
					attrToFetch = "original_filename";
				} else if (attrType.equals("ezkeyword")) {
					LOG.debug("This is an ezkeyword");
					query = "select keyword from ezkeyword inner join ezkeyword_attribute_link on ezkeyword_attribute_link.keyword_id=ezkeyword.id AND objectattribute_id in (SELECT id from ezcontentobject_attribute WHERE contentclassattribute_id="
						+ attr.name.split("::")[2] + " AND contentobject_id=" + objectId + " AND version=" + version + ")";
					attrToFetch = "keyword";
				} else if (attrType.equals("ezbinaryfileid")) {
					LOG.debug("@@ This is an ezbinaryfileID @@");
					query = "SELECT id from ezcontentobject_attribute WHERE data_type_string='ezbinaryfile'  AND contentobject_id=" + objectId + " AND version=" + version + "";
					attrToFetch = "id";
				} else {
					// ELSE Getting data_text
					LOG.debug("This is something else");
					query = "SELECT data_text FROM ezcontentobject_attribute WHERE contentclassattribute_id=" + attr.name.split("::")[2] + " AND contentobject_id=" + objectId
						+ " AND version=" + version;
					attrToFetch = "data_text";
				}
				LOG.debug("query get ATTR VALUE: " + query);

				try (Statement st = dbc.getStatement()) {
					try (ResultSet rs = st.executeQuery(query)) {
						if (rs.next()) {
							String result = rs.getString(attrToFetch);
							if (attrType.equals("ezxmltext")) {
								LOG.debug("Attribute value (" + attr.displayName + ") is ezxmltext. We won't push as metadata");
								return;
							} else {
								LOG.debug("Attribute value for " + attr.displayName + " : " + result + " (is foreign key: " + attr.isForeignReference() + ")");
								valueContainer.add(result);
							}
						}
					}
				}
			} catch (Exception e) {
				LOG.error("Error while getting the Attribute value (" + attr.displayName + "). Aborting.");
			}
		} else {
			try (Statement st = dbc.getStatement()) {
				try (ResultSet rs = st.executeQuery("SELECT " + attr.name + " FROM ezcontentobject WHERE " + docType.idAttrName + "=" + objectId)) {
					if (rs.next()) {
						String result = rs.getString(attr.name);
						if (attr.type == AttributeType.DATE) {
							result = new Date(new Long(result + "000")).toString();
							LOG.debug("Attribute value for " + attr.displayName + " : " + result + " (is foreign key: " + attr.isForeignReference() + ")");
							valueContainer.add(result);
						}
					}
				}
			}
		}
	}

	protected void buildForeignValue(ForeignKey attr, String value, Set<String> valueContainer) throws Exception {
		if (objectId == null) return;
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery(attr.getQuery().format(new String[] { value, version }))) {
				if (rs.next()) {
					String result = rs.getString(1);
					if (attr.type == AttributeType.DATE) {
						result = new Date(new Long(result + "000")).toString();
						LOG.debug("Foreing Key value for " + attr.displayName + " : " + result);
						valueContainer.add(result);
					}
				}
			}
		}
	}

	@Override
	public String getUrl() throws SQLException, ClientProtocolException, IOException {
		if (objectId == null) return null;
		if(!isBinaryType){			
			LOG.trace("non-binary url for "+objectId+": "+url);
			return url;
		}else {
			return fileurl;
		}
	}
	protected String buildPath() throws SQLException{
		String url=gatherUrlRec(ezExpl.mapNodeIdParentId, ezExpl.mapIdNodeId.get(objectId), "");
		if(!StringUtils.isNullOrEmpty(ezExpl.rootnode)&&url.startsWith(ezExpl.rootnode)){
			LOG.info("match:"+ezExpl.rootnode+"|"+url);
			url=url.substring(ezExpl.rootnode.length());
		}
		if(url.startsWith("/Accueil/"))url=url.substring("/Accueil".length());
		LOG.info("path for "+objectId+": "+url);

		return url;
	}
	protected String gatherUrlRec(Map<String, String> mapNodeIdParentId, String nodeId, String url) throws SQLException {
		if(ROOTNODE.equals(nodeId))return url;
		String parentId=mapNodeIdParentId.get(nodeId);
		if(!StringUtils.isNullOrEmpty(ezExpl.rootnode)&&ROOTNODE.equals(parentId))return url;
		if(mapNodeIdName.get(nodeId) != null)url="/"+mapNodeIdName.get(nodeId)+url;//to optimize db calls across documents
		else{
			String name=getName(nodeId);
			if(StringUtils.isNullOrEmpty(name)||"null".equals(name))name=ezExpl.mapNodeIdName.get(nodeId);
			url="/"+name+url;
			mapNodeIdName.put(nodeId,name);
		}

		//LOG.info("building url: "+nodeId+" "+url);

		if("1".equals(parentId)||StringUtils.isNullOrEmpty(parentId))return url;

		return gatherUrlRec(mapNodeIdParentId, parentId, url);
	}

	private String getName(String nodeId) throws SQLException {
		StringBuilder sb = new StringBuilder("SELECT ");
		sb.append("text");
		sb.append(" FROM "); 
		sb.append("ezurlalias_ml");
		sb.append(" WHERE action LIKE '%eznode:"); 
		sb.append(nodeId);
		sb.append("'");
		if(!ezExpl.langids.isEmpty()){
			sb.append(" AND (");
			for(String langid : ezExpl.langids){
				sb.append("lang_mask=");
				sb.append(langid);
				sb.append(" OR ");
			}
			sb.append("0)");//just not to leave an or hanging
		}
		String query=sb.toString();
		LOG.debug("get name query: "+query);
		try (Statement st = dbc.getStatement()) {
			try(ResultSet rs = (st.executeQuery(query))){
				while(rs.next()){
					String name=rs.getString("text");
					//name=normalizeName(name);
					return name;
				}
			}
		}
		return null;
	}

	@Override
	public String getMIME() throws SQLException, HttpException {
		if (objectId == null) return null;
		if(isBinaryType){
			return mime;
		}else return "text/html";

	}


	@Override
	public boolean hasContent() {
		return objectId!=null;
	}

	@Override
	public InputStream getContent() throws IllegalStateException, ClientProtocolException, IOException, InterruptedException, HttpException, SQLException, SAXException, TransformerException, DoNotIndex {
		if (objectId == null)
			return null;
		else if(isBinaryType){
			if (fileurl != null) {
				HttpGet get = new HttpGet(fileurl);
				LOG.debug("Load binary file: "+fileurl);
				CloseableHttpResponse r = httpClient.execute(get);
					final HttpEntity entity = r.getEntity();
				InputStream content = null;
				if (entity != null) if (r.getStatusLine().getStatusCode() == 200 || r.getStatusLine().getStatusCode() == 302) content = new FullyClosableInputStream(r);
				if (r.getStatusLine().getStatusCode() == 403) {
					r.close();
					throw new DoNotIndex("Not allowed to index: "+fileurl);
				}
				if (content == null) {
					r.close();
					throw new DoNotIndex("Binary doc empty or unavailable: "+fileurl);
				}
				ContentType contentType = ContentType.getOrDefault(entity);
				if(contentType.getMimeType().equalsIgnoreCase((ContentType.TEXT_HTML).getMimeType()) && mime !="text/html" ){
					r.close();
					throw new DoNotIndex("Got html in response for: "+fileurl);
				}else return content;

			}throw new DoNotIndex("No url for binary doc:"+objectId);

		}else {
			// Getting all Attributes from table and formating into an XML
			LOG.debug("Getting content for a Text Document ");

			return buildContentXML();
		}
	}

	@Override
	public void close() throws IOException {
		//if (content!=null)content.close();
	}



	private InputStream buildContentXML() throws SQLException, DoNotIndex {
		String content="";
		String issearchable = "";
		if (indexSearchableOnly) issearchable = " and ezcontentclass_attribute.is_searchable=1";
		String query = null;
		int versint=Integer.parseInt(version);
		boolean no_result=true;
		while(no_result&&versint!=0){
			StringBuilder sb = new StringBuilder("select ");
			sb.append("distinct ezcontentobject_attribute.id as attrid ,");
			sb.append("ezcontentclass_attribute.identifier as attridentifier,");
			sb.append("ezcontentclass_attribute.data_type_string as attrdatatypestring,");
			sb.append("ezcontentobject_attribute.data_text as attrdatatext,");
			sb.append("ezcontentobject_attribute.data_int as attrdataint,");
			sb.append("ezcontentobject_attribute.data_float as attrdatafloat ");
			sb.append("from ezcontentclass_attribute inner join ezcontentobject_attribute ");
			sb.append("on ezcontentobject_attribute.contentclassattribute_id=ezcontentclass_attribute.id ");
			sb.append("and ezcontentobject_attribute.contentobject_id="); 
			sb.append(objectId); 
			sb.append(" and ezcontentobject_attribute.version=");
			sb.append(versint);
			if (indexSearchableOnly)sb.append(issearchable);
			if(!ezExpl.langids.isEmpty()){
				sb.append(" where ");
				for(String langid : ezExpl.langids){
					sb.append("language_id=");
					sb.append(langid);
					sb.append(" OR ");
				}
				sb.append("0");//just not to leave an or hanging
			}
			query=sb.toString();
			LOG.debug("query: " + query);

			try (Statement st = dbc.getStatement()) {
				try (ResultSet rs = st.executeQuery(query)) {

					String name = "";
					String value = "";
					String datatype;
					if(rs.isBeforeFirst()){
						no_result=false; //is before first returns false when no result
					}else versint--;//if no attribute for current version, decrease version to find some
					while (rs.next()) {
						name = rs.getString("attridentifier");
						datatype = rs.getString("attrdatatypestring");
						String lcType = datatype.toLowerCase();
						if (lcType.equals("ezstring")) {
							value = rs.getString("attrdatatext");
						} else if (lcType.equals("ezhtml")) {
							value = rs.getString("attrdatatext");
						} else if (lcType.equals("eztext")) {
							value = rs.getString("attrdatatext");
						} else if (lcType.equals("ezxmltext")) {
							value = rs.getString("attrdatatext");
						} 
						else {						
							value = rs.getString("attrdatatext");
							//	LOG.info("The attribute '" + name + "' has the datatype '" + datatype + "'. As it is not standard - we will try to look for value in data_text: "+value);
						}
						//LOG.debug("Adding entry: Name: " + name + " Value: " + value + " DataType: " + datatype);
						if(value!=null){
							value.replaceAll("<[^>]+>", "").replace("&nbsp;"," ").replace("&amp;","&");//try to get rid of xml in the process
							content+=name+" "+value+" ";
						}
					}
				}
			}
		}
		if(StringUtils.isNullOrEmpty(content))throw new DoNotIndex("Empty content");
		return  new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));


	}


	protected String buildMetasRecursively(String name, String objectId, List<Metadata> metadata) throws SQLException, SAXException, IOException, ParserConfigurationException, TransformerException{

		String version = getlatestversion(objectId);
		String issearchable = "";
		if (indexSearchableOnly) issearchable = " and ezcontentclass_attribute.is_searchable=1";
		String query = null;
		StringBuilder sb = new StringBuilder("select ");
		sb.append("distinct ezcontentobject_attribute.id as attrid ,");
		sb.append("ezcontentclass_attribute.identifier as attridentifier,");
		sb.append("ezcontentclass_attribute.data_type_string as attrdatatypestring,");
		sb.append("ezcontentobject_attribute.data_text as attrdatatext,");
		sb.append("ezcontentobject_attribute.data_int as attrdataint,");
		sb.append("ezcontentobject_attribute.data_float as attrdatafloat ");
		sb.append("from ezcontentclass_attribute inner join ezcontentobject_attribute ");
		sb.append("on ezcontentobject_attribute.contentclassattribute_id=ezcontentclass_attribute.id ");
		sb.append("and ezcontentobject_attribute.contentobject_id="); 
		sb.append(objectId); 
		sb.append(" and ezcontentobject_attribute.version=");
		sb.append(version);
		if (indexSearchableOnly)sb.append(issearchable);
		if(!ezExpl.langids.isEmpty()){
			sb.append(" where ");
			for(String langid : ezExpl.langids){
				sb.append("language_id=");
				sb.append(langid);
				sb.append(" OR ");
			}
			sb.append("0");//just not to leave an or hanging
		}
		query=sb.toString();
		LOG.debug("recQuery: " + query);

		String returnvalue = "";
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery(query)) {
				String attrname = "";

				String datatype;
				String objattrid = "";
				if(!StringUtils.isNullOrEmpty(name))name+="_";
				while (rs.next()) {
					objattrid = rs.getString("attrid");
					attrname = name+rs.getString("attridentifier");
					if(excludedMeta.contains(attrname))continue;//skip and keep going
					datatype = rs.getString("attrdatatypestring");
					String lcType = datatype.toLowerCase();
					String value="";
					if (lcType.equals("ezstring")) {
						value = rs.getString("attrdatatext");
					} else if (lcType.equals("ezboolean")) {
						value = rs.getString("attrdataint");
					} else if (lcType.equals("ezdatetime") || lcType.equals("ezdate")) {
						//value = Dates.GSA_META_DATES_FORMAT.format(new Date(new Long(rs.getInt("attrdataint")) * 1000));

					} else if (lcType.equals("ezauthor")) {
						// APPLY XML Transformation
						DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
						String s = rs.getString("attrdatatext");
						LOG.debug(s);
						if (s == null || s.equals("")) {
							value = "";
							continue;
						}
						InputSource source = new InputSource(new StringReader(s));
						Document author = factory.newDocumentBuilder().parse(source);
						NodeList authList = author.getElementsByTagName("author");
						String result = "";
						for (int i = 0; i < authList.getLength(); i++) {
							Element cur = (Element) authList.item(i);
							result += cur.getAttribute("name") + " (" + cur.getAttribute("email") + ") ,";
						}
						value = result.substring(0, result.length() - 2);

					} else if (lcType.equals("ezhtml")) {
						value = rs.getString("attrdatatext");
					} else if (lcType.equals("eztext")) {
						value = rs.getString("attrdatatext");
					} else if (lcType.equals("ezxmltext")) {
						String s = rs.getString("attrdatatext");
						LOG.debug(s);
						if (s == null || s.equals("")) {
							value = "";
							continue;
						}

						TransformerFactory fabrique2 = TransformerFactory.newInstance();

						// chargement du xsl
						File stylesheet = new File(".."+File.separator+"xsl"+File.separator+"ezxmltext.xsl");
						if (!stylesheet.exists()) {
							LOG.debug("Stylesheet " + stylesheet.getAbsolutePath() + " could not be found. ");
							value = "";
							continue;
						}
						// String xslt = "";
						// inputStream = new
						// ByteArrayInputStream(xslt);
						StreamSource stylesource = new StreamSource(stylesheet);

						// instantiation du transformer
						Templates template = fabrique2.newTemplates(stylesource);
						Transformer transformer;
						transformer = template.newTransformer();

						// configuration du transformer
						transformer.setOutputProperty(OutputKeys.INDENT, "yes");
						transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

						// cr�ation du XML source
						InputSource inputSource = new InputSource(new StringReader(s));
						SAXSource source = new SAXSource(inputSource);

						// cr�ation de l'objet de sortie
						ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
						StreamResult resultat = new StreamResult(outputStream);

						// transformation
						transformer.transform(source, resultat);

						String result = outputStream.toString();

						value = result;
					} else if (lcType.equals("ezimage")) {
						DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
						String s = rs.getString("attrdatatext");
						LOG.debug(s);
						if (s == null || s.equals("")) {
							value = "";
							continue;
						}
						InputSource source = new InputSource(new StringReader(s));
						Document author = factory.newDocumentBuilder().parse(source);
						NodeList authList = author.getElementsByTagName("ezimage");
						String result = "";
						for (int i = 0; i < authList.getLength(); i++) {
							Element cur = (Element) authList.item(i);
							result += cur.getAttribute("url") + " (" + cur.getAttribute("alternative_text") + ") ,";
						}
						value = result.substring(0, result.length() - 2);
					} else if (lcType.equals("ezinisetting")) {
						value = rs.getString("attrdatatext");
					} else if (lcType.equals("ezkeyword")) {
						String queryKey = "select keyword from ezkeyword inner join ezkeyword_attribute_link on ezkeyword_attribute_link.keyword_id=ezkeyword.id AND ezkeyword_attribute_link.objectattribute_id="
							+ objattrid;
						Set<String> result = buildForeignValueFromNewEZProc(new ForeignKey("keyword", AttributeType.STRING, AttributeType.STRING, "keyword",
							queryKey), objectId);
						if (result != null && result.size() >= 1) value = result.iterator().next();
						else value = "";
					} else if (lcType.equals("ezmultioption")) {
						DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
						String s = rs.getString("attrdatatext");
						LOG.debug(s);
						if (s == null || s.equals("")) {
							value = "";
							continue;
						}
						InputSource source = new InputSource(new StringReader(s));
						Document author = factory.newDocumentBuilder().parse(source);
						NodeList authList = author.getElementsByTagName("name");
						String result = "";
						for (int i = 0; i < authList.getLength(); i++) {
							Element cur = (Element) authList.item(i);
							result += cur.getTextContent() + " : ";
						}
						authList = author.getElementsByTagName("multioption");
						for (int i = 0; i < authList.getLength(); i++) {
							Element cur = (Element) authList.item(i);
							result += "\r\n- " + cur.getAttribute("name");
							NodeList option = cur.getChildNodes();
							for (int j = 0; j < option.getLength(); j++) {
								Element opt = (Element) option.item(j);
								result += " (" + opt.getAttribute("value") + ": " + opt.getAttribute("additional_price") + ")";
							}
						}
						value = result;

					} else if (lcType.equals("ezpackage")) {
						value = rs.getString("attrdatatext");
					} else if (lcType.equals("ezobjectrelationlist")) {
						DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
						String s = rs.getString("attrdatatext");
						LOG.debug(s);
						if (s == null || s.equals("")) {
							value = "";
							continue;
						}
						InputSource source = new InputSource(new StringReader(s));
						Document author = factory.newDocumentBuilder().parse(source);
						NodeList authList = author.getElementsByTagName("related-objects");
						if(followMetas&&!excludedMeta.contains(attrname)){
							for (int i = 0; i < authList.getLength(); i++) {

								getObjectFromList(attrname,authList.item(i), metadata);
							}
						}
						continue;//even if not followMetas
					} else if (lcType.equals("ezprice")) {
						value = rs.getString("attrdatafloat");
					} else if (lcType.equals("ezurl")) {
						value = rs.getString("attrdatatext");
					} else if (lcType.equals("ezobjectrelation")) {
						String objId=rs.getString("attrdataint");
						if(objId!=null&&followMetas&&!excludedMeta.contains(attrname)){
							buildMetasRecursively(attrname, objId, metadata);
						}
					} else {
						LOG.debug("The attribute '" + name + "' has the datetype '" + datatype + "'. As it is not standard - we will try to look for value in data_text");
						value = rs.getString("attrdatatext");
					}
					if(!StringUtils.isNullOrEmpty(value)){
						value=value.replace("&nbsp;"," ").replace("&amp;","&").replaceAll("<[^>]+>", "");	
						if(!StringUtils.isNullOrEmpty(value)&&!StringUtils.isEmptyOrWhitespaceOnly(value)&&!excludedMeta.contains(attrname)){
							metadata.add(new Metadata(attrname,value));//try to get rid of xml in the process
							returnvalue=value;
						}
					}

				}
			}
		}
		return returnvalue;
	}

	protected String getObjectFromList(String lastname, Node item, List<Metadata> metadata) throws SQLException, SAXException, IOException, ParserConfigurationException, TransformerException {
		String objectid = ((Element)item).getAttribute("contentobject-id");
		String value = null;
		if(!StringUtils.isNullOrEmpty(objectid)){
			value=buildMetasRecursively(lastname, objectid, metadata);
		}
		NodeList list=item.getChildNodes();
		String aggregvalue="";
		for (int j = 0; j < list.getLength(); j++) {
			String subvalue=getObjectFromList(lastname+j,list.item(j),metadata);
			if (!StringUtils.isNullOrEmpty(subvalue))aggregvalue+=subvalue+",";
		}
		if(!StringUtils.isNullOrEmpty(aggregvalue)){
			aggregvalue.substring(0, aggregvalue.length());
			metadata.add(new Metadata(lastname+"_aggregat",aggregvalue));// useful for theme based dynamic navigation
		}
		return value;

	}

	protected String getlatestversion(String objectId) throws SQLException {
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT current_version FROM ezcontentobject WHERE id=" + objectId)) {
				if (rs.next()) return rs.getString("current_version");
				else return "1";
			}
		}
	}

	private InputStream xslTransform(InputStream bis) {

		try {
			TransformerFactory fabrique = TransformerFactory.newInstance();

			// chargement du xsl
			File stylesheet = new File(".."+File.separator+"xsl"+ File.separator + docType.name + ".xsl");
			if (!stylesheet.exists()) {
				LOG.warn("Stylesheet " + stylesheet.getAbsolutePath() + " could not be found. Using default stylesheet instead.");
				stylesheet = new File(".."+File.separator+"xsl"+File.separator+"default.xsl");
				if (!stylesheet.exists()) {
					LOG.info("Default stylesheet could not be found. Pushing default XML.");
					return bis;
				}
			}
			// String xslt = "";
			// inputStream = new ByteArrayInputStream(xslt);
			StreamSource stylesource = new StreamSource(stylesheet);

			// instantiation du transformer
			Templates template = fabrique.newTemplates(stylesource);
			Transformer transformer;
			transformer = template.newTransformer();

			// configuration du transformer
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");//"ISO-8859-1");

			// cr�ation du XML source
			InputSource inputSource = new InputSource(bis);
			SAXSource source = new SAXSource(inputSource);

			// cr�ation de l'objet de sortie
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			StreamResult resultat = new StreamResult(outputStream);

			// transformation
			transformer.transform(source, resultat);

			// conversion de l'objet de resultat sous forme de chaine
			return new ByteArrayInputStream(outputStream.toByteArray());

		} catch (TransformerConfigurationException ex) {
			LOG.error("XSL transform error: ", ex);
		} catch (TransformerException e) {
			LOG.error("XSL transform error: ", e);
		}

		return null;
	}

	protected Set<String> buildForeignValueFromNewEZProc(ForeignKey attr, String value) throws SQLException {
		Set<String> attrValueStr = new HashSet<String>();
		if (objectId == null) {
			return attrValueStr;
		}
		String query = attr.getQuery().format(new String[] { value, version });
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery(query)) {
				String column = query.toLowerCase().split("select")[1].split("from")[0].trim();
				LOG.debug("Getting foreing Key value from the following column: " + column);
				while (rs.next()) {
					String result = rs.getString(column);
					LOG.debug("Foreing Key value for " + attr.displayName + ": " + result);

					attrValueStr.add(result);
				}
				return attrValueStr;
			}
		}
	}

}
