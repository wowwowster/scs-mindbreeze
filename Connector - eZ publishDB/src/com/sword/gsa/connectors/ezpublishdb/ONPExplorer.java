package com.sword.gsa.connectors.ezpublishdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.sword.gsa.spis.scs.exceptions.DoNotExplore;
import com.sword.gsa.spis.scs.push.PushProcessSharedObjectsStore;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;


public class ONPExplorer extends Explorer {

	protected String rootnode = configurationParams.get(Connector.ROOTNODE);

	public ONPExplorer(PushProcessSharedObjectsStore sharedObjects) throws Exception {
		super(sharedObjects);
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
			if(onlyRefFiles&&!dtToIndex.contains(dt))//to be directly indexed only if referred to. 
				refTypes.put(lookId(dt),dt);
		}
		LOG.info("additionally, there are "+refTypes.size()+" binary types");
		return rootNodes;

	}
	
	@Override
	public void loadChildren(final ContainerNode node, final boolean isUpdateMode, final boolean isPublicMode) throws Exception {
		DocType docType= dtInConf.get(node.id);
		if (!StringUtils.isNumeric(docType.uid)) {
			throw new DoNotExplore("Uid is not numeric");			
		}
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
					if(onpOk(rs.getString(1))){
						node.children.add(new DocumentNode(node.id+"|"+rs.getString(1), node));
						if(!refTypes.isEmpty()&&onlyRefFiles)node.children.addAll(addRefTypesToIndex(rs.getString(1),node));
					}
				}
			}catch(SQLException e){
	            System.err.println(e); 
	            return;
	        } 
		} 
	}
	@Override
	protected void buildUrlMap() throws SQLException {
		//if(rootnode==null)rootnode="";
		StringBuilder sb = new StringBuilder("SELECT ");
		sb.append("id, node_id, parent_node_id, name, path_identification_string");
		sb.append(" FROM "); 
		sb.append("ezcontentobject o INNER JOIN ezcontentobject_tree t");
		sb.append(" ON "); 
		sb.append("o.id = t.contentobject_id");
		if(Boolean.parseBoolean(configurationParams.get(Connector.PUBLISHEDONLY))){
			sb.append(" AND ");
			sb.append("is_hidden=0");
			sb.append(" AND ");
			sb.append("is_invisible=0");
			sb.append(" AND ");
			sb.append("section_id!=7");
			sb.append(" AND ");
			sb.append("section_id!=8");
			sb.append(" AND ");
			sb.append("section_id!=9");//TODO this is for cespharm. Mercure and meddispar may encounter trouble.
		}
		String query=sb.toString();
		try (Statement st = dbc.getStatement()) {
			try(ResultSet rs = (st.executeQuery(query))){
				while(rs.next()){
					String id=rs.getString("id");
					String nodeId=rs.getString("node_id");
					String parentNodeId=rs.getString("parent_node_id");
					String name=rs.getString("name");
					name=normalizeName(name);
					String url=rs.getString("path_identification_string");
					url=normalizeName(url);

					if(!"null".equals(id)&&!"null".equals(nodeId)&&!"null".equals(parentNodeId)&&!"null".equals(url)){
						if(!mapIdNodeId.containsKey(id))mapIdNodeId.put(id, nodeId);
						mapNodeIdParentId.put(nodeId, parentNodeId);
						mapNodeIdName.put(nodeId, name);
						if(!mapUrl.containsKey(id))mapUrl.put(id, url);
						//TODO ELSE DOUBLONS ?
					}
				}
				for (String itid : mapIdNodeId.keySet()){
					String nodeId=mapIdNodeId.get(itid);
					String url=mapUrl.get(itid);//gatherUrlRec(mapNodeIdParentId,mapNodeIdName,nodeId,"");
					String name=mapNodeIdName.get(nodeId);
					if(name.contains("!")){
						url=gatherUrlRec(mapNodeIdParentId,mapNodeIdName,nodeId,"");//if name is special, have to use it for url, in most case, we have to use path_identification_string
						mapUrl.put(itid, url);
					}
					LOG.trace("url for "+itid+"/"+nodeId+":"+url);
					if(!StringUtils.isEmpty(rootnode)&&url.startsWith(rootnode)){
						LOG.trace("match:"+rootnode+"|"+url);
						inSiteIds.add(itid);
						mapUrl.put(itid, url.substring(rootnode.length()));//if belongs to rootnode hierarchy, index and cut it from name (it is host already)
					}else if(StringUtils.isEmpty(rootnode)) {
						inSiteIds.add(itid);
					}
				}
			}
		}
	}
	
//TODO onpOk on getRefmap

	private boolean onpOk(String childId) {
		return inSiteIds.contains(childId);
	}
}
