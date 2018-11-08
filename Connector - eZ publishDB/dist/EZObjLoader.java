package sword.push.generic.cmsimpl.ez;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import sword.common.databases.sql.DBType;
import sword.common.databases.sql.DataBaseConnection;
import sword.common.http.client.SwHttpClient;
import sword.common.utils.chars.Strings;
import sword.common.utils.dates.Dates;
import sword.push.generic.config.ConfigurationStore;
import sword.push.generic.config.cms.CMSDocbaseDef;
import sword.push.generic.config.objects.Attribute;
import sword.push.generic.config.objects.AttributeType;
import sword.push.generic.config.objects.DocType;
import sword.push.generic.config.objects.ForeignKey;
import sword.push.generic.connector.CMSObjectLoader;
import sword.push.generic.throwables.CMSFault;

public class EZObjLoader extends CMSObjectLoader {

	private final CMSDocbaseDef dbDef;
	private final String binurl;
	private final String binarytypes;
	private final DataBaseConnection dbc;
	private final SwHttpClient httpClient;
	private final boolean indexSearchableOnly;

	private String workerID;
	private int workerVersion;

	public EZObjLoader(DocType dt) throws CMSFault {
		super(dt);
		httpClient = new SwHttpClient(5, 5, 15000);
		dbDef = ConfigurationStore.get().getCMSDocbaseDef();
		binurl = dbDef.getParameter("binaryTypesFormat");
		binarytypes = dbDef.getParameter("binaryTypes");
		indexSearchableOnly = Boolean.parseBoolean(dbDef.getParameter("indexSearchableOnly"));
		if (Strings.isNullOrEmpty(binurl)) LOG.error("Error while reading BinaryTypesFormat parameter - Value is null");
		String connectionString = dbDef.getParameter("connectionString");
		DBType dbtype = DBType.lookupName(dbDef.getParameter("dbType"));

		dbc = new DataBaseConnection(dbtype, connectionString, dbDef.username, dbDef.password);
		try {
			dbc.connect(true);
		} catch (Exception e) {
			throw new CMSFault(e);
		}
	}

	@Override
	protected void _setWorkingObject(String curID) throws Exception {
		workerID = curID;
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT current_version FROM ezcontentobject WHERE " + docType.idAttrName + "=" + workerID)) {
				if (rs.next()) workerVersion = rs.getInt("current_version");
				else workerVersion = 1;
			}
		}
	}

	protected boolean _hasFormerVersion() throws Exception {
		workerVersion -= 1;
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT count(*) FROM ezcontentobject WHERE " + docType.idAttrName + "=" + workerID + " AND version=" + workerVersion)) {
				if (rs.next() && rs.getInt(1)>0) return true;
				else return false;
			}
		}
	}

	protected int _loadFormerVersion() throws Exception {
		LOG.info("Load workerVersion: " + workerVersion);
		return workerVersion;
	}

	@Override
	protected Date _getModifyDate() throws Exception {
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT " + docType.mdAttrName + " FROM ezcontentobject_version WHERE contentobject_id=" + workerID + " AND version=" + workerVersion)) {
				if (rs.next()) return new Date(rs.getInt(1) * 1000L);
				else return null;
			}
		}
	}

	@Override
	protected void _getAttributeValue(Attribute attr, Set<String> valueContainer) throws Exception {

		if (workerID == null)
			return;

		if (attr.name.startsWith("ATTRID::")) {
			LOG.debug("This is an automatically detected attribute: " + attr.displayName + " --- Getting value from ezcontentobject_attribute");
			try {
				String attrToFetch = "";
				String query = "";
				String attrType = attr.name.split("::")[1].toLowerCase();
				if (attrType.equals("ezstring")) {
					LOG.debug("This is an ezstring");
					query = "SELECT data_text FROM ezcontentobject_attribute WHERE contentclassattribute_id=" + attr.name.split("::")[2] + " AND contentobject_id=" + workerID
							+ " AND version=" + workerVersion;
					attrToFetch = "data_text";
				} else if (attrType.equals("ezxmltext")) {
					LOG.debug("This is an ezxmltext");
					query = "SELECT data_text FROM ezcontentobject_attribute WHERE contentclassattribute_id=" + attr.name.split("::")[2] + " AND contentobject_id=" + workerID
							+ " AND version=" + workerVersion;
					attrToFetch = "data_text"; // XSL TRANSFORMATION
				} else if (attrType.equals("ezbinaryfile")) {
					LOG.debug("This is an ezbinaryfile");
					query = "SELECT original_filename FROM ezbinaryfile WHERE contentobject_attribute_id in (SELECT id from ezcontentobject_attribute WHERE contentclassattribute_id="
							+ attr.name.split("::")[2] + " AND contentobject_id=" + workerID + " AND version=" + workerVersion + ")";
					attrToFetch = "original_filename";
				} else if (attrType.equals("ezkeyword")) {
					LOG.debug("This is an ezkeyword");
					query = "select keyword from ezkeyword inner join ezkeyword_attribute_link on ezkeyword_attribute_link.keyword_id=ezkeyword.id AND objectattribute_id in (SELECT id from ezcontentobject_attribute WHERE contentclassattribute_id="
							+ attr.name.split("::")[2] + " AND contentobject_id=" + workerID + " AND version=" + workerVersion + ")";
					attrToFetch = "keyword";
				} else if (attrType.equals("ezbinaryfileid")) {
					LOG.debug("@@ This is an ezbinaryfileID @@");
					query = "SELECT id from ezcontentobject_attribute WHERE data_type_string='ezbinaryfile'  AND contentobject_id=" + workerID + " AND version=" + workerVersion + "";
					attrToFetch = "id";
				} else {
					// ELSE Getting data_text
					LOG.debug("This is something else");
					query = "SELECT data_text FROM ezcontentobject_attribute WHERE contentclassattribute_id=" + attr.name.split("::")[2] + " AND contentobject_id=" + workerID
							+ " AND version=" + workerVersion;
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
				try (ResultSet rs = st.executeQuery("SELECT " + attr.name + " FROM ezcontentobject WHERE " + docType.idAttrName + "=" + workerID)) {
					if (rs.next()) {
						String result = rs.getString(attr.name);
						if (attr.type == AttributeType.DATE) {
							result = Dates.GSA_META_DATES_FORMAT.format(new Date(new Long(result + "000")));
							LOG.debug("Attribute value for " + attr.displayName + " : " + result + " (is foreign key: " + attr.isForeignReference() + ")");
							valueContainer.add(result);
						}
					}
				}
			}
		}
	}

	@Override
	protected void _buildForeignValue(ForeignKey attr, String value, Set<String> valueContainer) throws Exception {
		if (workerID == null) return;
		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery(attr.getQuery().format(new String[] { value, Integer.toString(workerVersion) }))) {
				if (rs.next()) {
					String result = rs.getString(1);
					if (attr.type == AttributeType.DATE) {
						result = Dates.GSA_META_DATES_FORMAT.format(new Date(new Long(result + "000")));
						LOG.debug("Foreing Key value for " + attr.displayName + " : " + result);
						valueContainer.add(result);
					}
				}
			}
		}
	}

	@Override
	protected String _getMIME() throws Exception {
		if (workerID == null) return null;
		if (binarytypes.contains(docType.name)) {
			try (Statement st = dbc.getStatement()) {
				try (ResultSet rs = st.executeQuery("SELECT mime_type FROM ezbinaryfile WHERE contentobject_attribute_id in ( select id from ezcontentobject_attribute where contentobject_id=" + workerID + " and version=" + workerVersion + ")")) {
					if (rs.next()) return rs.getString(1);
					else return "text/html";
				}
			}
		} else {
			return "text/html";
		}
	}

	@Override
	protected boolean _hasContent() throws Exception {
		return workerID != null;
	}

	@Override
	protected InputStream _getContent() throws Exception {
		if (workerID == null)
			return null;
		if (binarytypes.contains(docType.name)) {
			MessageFormat binurlmf = new MessageFormat(binurl);
			if (binarytypes.contains(docType.name)) {
				try (Statement st = dbc.getStatement()) {
					try (ResultSet rs = st.executeQuery("SELECT bin.original_filename AS filename, bin.contentobject_attribute_id AS contentattrid, bin.version AS vers, attr.contentobject_id AS id FROM ezbinaryfile AS bin, ezcontentobject_attribute AS attr WHERE bin.contentobject_attribute_id=attr.id AND attr.contentobject_id=" + workerID + " AND bin.version=" + workerVersion)) {
						if (rs.next()) {
							String filename = URLEncoder.encode(rs.getString("filename"), "utf-8").replaceAll("\\+", "%20");
							String attributeid = rs.getString("contentattrid");
							String id = rs.getString("id");
							String version = rs.getString("vers");

							String fileurl = binurlmf.format(new String[] { id, attributeid, version, filename });
							LOG.debug("File URL: " + fileurl);
							if (fileurl != null) {
								HttpGet get = new HttpGet(fileurl);
								try {
									HttpResponse r = httpClient.executeRequest(get);
									return r.getEntity().getContent();
								} catch (Exception e) {
									get.abort();
									throw e;
								}
							}
						} else return null;
					}
				}
			}
		} else {
			// Getting all Attributes from table and formating into an XML
			LOG.debug("Getting content for a Text Document ");
			try {
				return buildContentXML();
			} catch (ParserConfigurationException e) {
				LOG.error("An error occured:", e);
				return null;
			}
		}
		return null;
	}

	private InputStream buildContentXML() throws ParserConfigurationException, CMSFault, SQLException, SAXException, IOException, TransformerException {

		LOG.info("Building Content XML File ");
		// Création d'un nouveau DOM
		DocumentBuilderFactory fabrique = DocumentBuilderFactory.newInstance();
		DocumentBuilder constructeur = fabrique.newDocumentBuilder();
		Document document = constructeur.newDocument();

		// Propriétés du DOM
		document.setXmlVersion("1.0");
		document.setXmlStandalone(true);

		// Création de l'arborescence du DOM
		Element racine = document.createElement("content");
		racine.appendChild(document.createComment("EZPublish Content"));

		String issearchable = "";
		if (indexSearchableOnly) issearchable = " and ezcontentclass_attribute.is_searchable=1";

		String query = "select distinct ezcontentobject_attribute.id as attrid ,ezcontentclass_attribute.identifier as attridentifier,ezcontentclass_attribute.data_type_string as attrdatatypestring,ezcontentobject_attribute.data_text as attrdatatext,ezcontentobject_attribute.data_int as attrdataint,ezcontentobject_attribute.data_float as attrdatafloat from ezcontentclass_attribute inner join ezcontentobject_attribute on ezcontentobject_attribute.contentclassattribute_id=ezcontentclass_attribute.id and ezcontentobject_attribute.contentobject_id=" + workerID + " and ezcontentobject_attribute.version=" + workerVersion + issearchable;
		LOG.debug("query: " + query);

		try (Statement st = dbc.getStatement()) {
			try (ResultSet rs = st.executeQuery(query)) {
				String name = "";
				String value = "";
				String datatype;
				String objattrid = "";
				while (rs.next()) {
					objattrid = rs.getString("attrid");
					name = rs.getString("attridentifier");
					datatype = rs.getString("attrdatatypestring");
					String lcType = datatype.toLowerCase();
					if (lcType.equals("ezstring")) {
						value = rs.getString("attrdatatext");
					} else if (lcType.equals("ezboolean")) {
						value = rs.getString("attrdataint");
					} else if (lcType.equals("ezdatetime") || lcType.equals("ezdate")) {
						value = Dates.GSA_META_DATES_FORMAT.format(new Date(new Long(rs.getInt("attrdataint")) * 1000));
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
						File stylesheet = new File("xsl/ezxmltext.xsl");
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
						transformer.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");

						// création du XML source
						InputSource inputSource = new InputSource(new StringReader(s));
						SAXSource source = new SAXSource(inputSource);

						// création de l'objet de sortie
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
								queryKey), workerID);
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
						String result = "";
						for (int i = 0; i < authList.getLength(); i++) {
							Element cur = (Element) authList.item(i);
							result += cur.getTextContent();
						}
						value = result;
					} else if (lcType.equals("ezprice")) {
						value = rs.getString("attrdatafloat");
					} else if (lcType.equals("ezurl")) {
						value = rs.getString("attrdatatext");
					} else if (lcType.equals("ezobjectrelation")) {
						value = rs.getString("attrdataint");
					} else {
						LOG.info("The attribute '" + name + "' has the datetype '" + datatype + "'. As it is not standard - we will try to look for value in data_text");
						value = rs.getString("attrdatatext");
						LOG.info("Adding entry: Name: " + name + " Value: " + value + " DataType: " + datatype);
						Element attribute = document.createElement("attribute");
						racine.appendChild(attribute);

						Element nameE = document.createElement("name");
						nameE.setTextContent(name);
						attribute.appendChild(nameE);

						Element valueE = document.createElement("value");
						valueE.setTextContent(value);
						attribute.appendChild(valueE);

						Element datatypeE = document.createElement("datatype");
						datatypeE.setTextContent(datatype);
						attribute.appendChild(datatypeE);
					}
				}
			}
		}

		document.appendChild(racine);

		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer t;
		try {
			t = tf.newTransformer();
			DOMSource in = new DOMSource(document);
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			StreamResult out = new StreamResult(outputStream);
			t.transform(in, out);
			return xslTransform(new ByteArrayInputStream(outputStream.toByteArray()));
		} catch (TransformerException e) {
			LOG.error("XSL transform error: ", e);
		}
		return null;
	}

	private Set<String> buildForeignValueFromNewEZProc(ForeignKey attr, String value) throws SQLException {
		Set<String> attrValueStr = new HashSet<String>();
		if (workerID == null) {
			return attrValueStr;
		}
		String query = attr.getQuery().format(new String[] { value, Integer.toString(workerVersion) });
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

	private InputStream xslTransform(InputStream bis) {

		try {
			TransformerFactory fabrique = TransformerFactory.newInstance();

			// chargement du xsl
			File stylesheet = new File("xsl/" + docType.name + ".xsl");
			if (!stylesheet.exists()) {
				LOG.warn("Stylesheet " + stylesheet.getAbsolutePath() + " could not be found. Using default stylesheet instead.");
				stylesheet = new File("xsl/default.xsl");
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
			transformer.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");

			// création du XML source
			InputSource inputSource = new InputSource(bis);
			SAXSource source = new SAXSource(inputSource);

			// création de l'objet de sortie
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

	@Override
	public void close() {
		httpClient.shutdown();
	}

}
