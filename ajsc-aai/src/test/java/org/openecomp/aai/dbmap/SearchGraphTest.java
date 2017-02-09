/*-
 * ============LICENSE_START=======================================================
 * org.openecomp.aai
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.openecomp.aai.dbmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.openecomp.aai.dbgen.DbMeth;
import org.openecomp.aai.dbmap.AAIGraph;
import org.openecomp.aai.dbmap.SearchGraph;
import org.openecomp.aai.domain.model.AAIResources;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.ingestModel.DbMaps;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.util.RestURL;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.rule.PowerMockRule;

import com.google.common.collect.ArrayListMultimap;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanGraphQuery;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.core.TitanVertex;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AAIGraph.class, TitanGraph.class, DbMeth.class, IngestModelMoxyOxm.class, 
	AAIResources.class, DynamicJAXBContext.class, DynamicEntity.class, VertexProperty.class, 
	RestURL.class, TitanTransaction.class, TitanVertex.class,  SearchGraph.class})
@PowerMockIgnore("javax.management.*")
public class SearchGraphTest {
//    @Rule
//    public PowerMockRule rule = new PowerMockRule();
	
	private String fromAppId = "searchGraphId";
	private String transId = "transId";
	private String aaiApiVersion = "v8";
	
	/**
	 * Test run nodes query.
	 *
	 * @throws Exception the exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testRunNodesQuery() throws Exception {
		String targetNodeType = "vserver";
		String nodePropsValue = "testNodePropsValue";
		List<String> edgeFilterParams = new ArrayList<String>();
		edgeFilterParams.add("pserver:EXISTS:hostname:test-pserver-hostname");
		List<String> filterParams = new ArrayList<String>();
		filterParams.add("vserver-name2:EXISTS:");
		String serverNodeURL = "testServerNodeURL";
		
		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph aaiGraphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(aaiGraphMock);
		TitanGraph titanGraphMock = PowerMockito.mock(TitanGraph.class);
		when(aaiGraphMock.getGraph()).thenReturn(titanGraphMock);
		TitanTransaction titanTransactionMock = PowerMockito.mock(TitanTransaction.class);
		when(titanGraphMock.newTransaction()).thenReturn(titanTransactionMock);
		
		HashMap<String, DbMaps> dbMapsContainer = new HashMap<String, DbMaps>();
		DbMaps dbMaps = new DbMaps();
		dbMaps.NodeProps = ArrayListMultimap.create();
		dbMaps.NodeProps.put(targetNodeType, nodePropsValue);
		dbMapsContainer.put(aaiApiVersion, dbMaps);
		IngestModelMoxyOxm.dbMapsContainer = dbMapsContainer;
		
		TitanGraphQuery titanGraphQueryMock = PowerMockito.mock(TitanGraphQuery.class);
		when(titanTransactionMock.query()).thenReturn(titanGraphQueryMock);
		when(titanGraphQueryMock.has("aai-node-type", targetNodeType)).thenReturn(titanGraphQueryMock);
		when(titanGraphQueryMock.has("vserver-name2")).thenReturn(titanGraphQueryMock);
		Iterable<TitanVertex> iterableMock =(Iterable<TitanVertex>) PowerMockito.mock(Iterable.class);
		when(titanGraphQueryMock.vertices()).thenReturn(iterableMock);
		Iterator<TitanVertex> iteratorTitanVertexMock = (Iterator<TitanVertex>) PowerMockito.mock(Iterator.class);
		when(iterableMock.iterator()).thenReturn(iteratorTitanVertexMock);
		when(iteratorTitanVertexMock.hasNext()).thenReturn(true, false);
		TitanVertex titanVertexMock = PowerMockito.mock(TitanVertex.class);
		when(iteratorTitanVertexMock.next()).thenReturn(titanVertexMock);
		
		Iterator<Vertex> iteratorVertexMock = (Iterator<Vertex>) PowerMockito.mock(Iterator.class);
		when(titanVertexMock.vertices(Direction.BOTH, "runsOnPserver")).thenReturn(iteratorVertexMock);
		when(iteratorVertexMock.hasNext()).thenReturn(true, false);
		when(iteratorVertexMock.next()).thenReturn(titanVertexMock);		

		VertexProperty<String> vertexPropertyMock = (VertexProperty<String>)PowerMockito.mock(VertexProperty.class);
		when(titanVertexMock.<String>property("hostname")).thenReturn(vertexPropertyMock);
		when(vertexPropertyMock.orElse(null)).thenReturn("test-pserver-hostname");
		when(vertexPropertyMock.equals("test-pserver-hostname")).thenReturn(true);
		
		PowerMockito.mockStatic(RestURL.class);
		when(RestURL.getSearchUrl(Mockito.any(TitanTransaction.class), Mockito.any(TitanVertex.class), 
				Mockito.anyString())).thenReturn(serverNodeURL);
		when(titanVertexMock.<String>property("aai-node-type")).thenReturn(vertexPropertyMock);
		when(vertexPropertyMock.orElse(null)).thenReturn("pserver");

		PowerMockito.mockStatic(IngestModelMoxyOxm.class);
		HashMap<String, AAIResources> aaiResourceContainerMock = new HashMap<String, AAIResources>();
		AAIResources aaiResourcesMock = PowerMockito.mock(AAIResources.class);
		aaiResourceContainerMock.put("v8", aaiResourcesMock);
		IngestModelMoxyOxm.aaiResourceContainer = aaiResourceContainerMock;
		DynamicJAXBContext dynamicJAXBContextMock = PowerMockito.mock(DynamicJAXBContext.class);
		when(aaiResourcesMock.getJaxbContext()).thenReturn(dynamicJAXBContextMock);
		
		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		when(dynamicJAXBContextMock.newDynamicEntity(Mockito.anyString())).thenReturn(dynamicEntityMock);
		
		Response respMock = PowerMockito.mock(Response.class);
		when(respMock.getStatus()).thenReturn(200);

		SearchGraph searchGraph = new SearchGraph();
		SearchGraph searchGraphSpy = PowerMockito.spy(searchGraph);
		PowerMockito.doReturn(respMock).when(searchGraphSpy, "getResponseFromDynamicEntity", 
				Mockito.any(DynamicEntity.class), Mockito.any(DynamicJAXBContext.class), 
				Mockito.any(AAIExtensionMap.class));
		AAIExtensionMap aaiExtMap = new AAIExtensionMap();
		aaiExtMap.setApiVersion(aaiApiVersion);
		Response resp = searchGraphSpy.runNodesQuery(fromAppId, transId, 
				targetNodeType, edgeFilterParams, filterParams, aaiExtMap);
		assertEquals(200, resp.getStatus());
	}
	
	/**
	 * Test run nodes query exception.
	 *
	 * @throws Exception the exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testRunNodesQuery_exception() throws Exception {
		String targetNodeType = "";
		String nodePropsValue = "testNodePropsValue";
		List<String> edgeFilterParams = new ArrayList<String>();
		edgeFilterParams.add("pserver:EXISTS:hostname:test-pserver-hostname");
		List<String> filterParams = new ArrayList<String>();
		filterParams.add("vserver-name2:EXISTS:");
		
		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph aaiGraphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(aaiGraphMock);
		TitanGraph titanGraphMock = PowerMockito.mock(TitanGraph.class);
		when(aaiGraphMock.getGraph()).thenReturn(titanGraphMock);
		TitanTransaction titanTransactionMock = PowerMockito.mock(TitanTransaction.class);
		when(titanGraphMock.newTransaction()).thenReturn(titanTransactionMock);
		
		HashMap<String, DbMaps> dbMapsContainer = new HashMap<String, DbMaps>();
		DbMaps dbMaps = new DbMaps();
		dbMaps.NodeProps = ArrayListMultimap.create();
		dbMaps.NodeProps.put(targetNodeType, nodePropsValue);
		dbMapsContainer.put(aaiApiVersion, dbMaps);
		IngestModelMoxyOxm.dbMapsContainer = dbMapsContainer;
		
		SearchGraph searchGraph = new SearchGraph();
		AAIExtensionMap aaiExtMap = new AAIExtensionMap();
		aaiExtMap.setApiVersion(aaiApiVersion);
		
		//targetNodeType is empty
		try {
			searchGraph.runNodesQuery(fromAppId, transId, 
					targetNodeType, edgeFilterParams, filterParams, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		
		//dbMaps.NodeProps doesn't contain targetNodeType
		targetNodeType = "vserver";
		dbMaps.NodeProps.clear();
		dbMaps.NodeProps.put("pserver", nodePropsValue);
		try {
			searchGraph.runNodesQuery(fromAppId, transId, 
					targetNodeType, edgeFilterParams, filterParams, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		
		//filterParams.isEmpty()  && edgeFilterParams.isEmpty()
		dbMaps.NodeProps.clear();
		dbMaps.NodeProps.put(targetNodeType, nodePropsValue);
		edgeFilterParams.clear();
		filterParams.clear();
		try {
			searchGraph.runNodesQuery(fromAppId, transId, 
					targetNodeType, edgeFilterParams, filterParams, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		
		//Exception : bad filter passed to node query
		filterParams.add("vserver-name2");
		TitanGraphQuery titanGraphQueryMock = PowerMockito.mock(TitanGraphQuery.class);
		when(titanTransactionMock.query()).thenReturn(titanGraphQueryMock);
		when(titanGraphQueryMock.has("aai-node-type", targetNodeType)).thenReturn(titanGraphQueryMock);
		try {
			searchGraph.runNodesQuery(fromAppId, transId, 
					targetNodeType, edgeFilterParams, filterParams, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		
		//Exception - FilterType EQUALS - No value passed for filter
		filterParams.clear();
		filterParams.add("vserver-name2:EQUALS");
		try {
			searchGraph.runNodesQuery(fromAppId, transId, 
					targetNodeType, edgeFilterParams, filterParams, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		
		//Exception - FilterType DOES-NOT-EQUAL - No value passed for filter
		filterParams.clear();
		filterParams.add("vserver-name2:DOES-NOT-EQUAL");
		try {
			searchGraph.runNodesQuery(fromAppId, transId, 
					targetNodeType, edgeFilterParams, filterParams, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		
		//Exception - bad filterType passed
		filterParams.clear();
		filterParams.add("vserver-name2:NOT-EQUAL");
		try {
			searchGraph.runNodesQuery(fromAppId, transId, 
					targetNodeType, edgeFilterParams, filterParams, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		
		when(titanGraphQueryMock.has("vserver-name2")).thenReturn(titanGraphQueryMock);
		Iterable<TitanVertex> iterableMock =(Iterable<TitanVertex>) PowerMockito.mock(Iterable.class);
		when(titanGraphQueryMock.vertices()).thenReturn(iterableMock);
		Iterator<TitanVertex> iteratorTitanVertexMock = (Iterator<TitanVertex>) PowerMockito.mock(Iterator.class);
		when(iterableMock.iterator()).thenReturn(iteratorTitanVertexMock);
		
		//Exception - bad filter passed. pieces.length = 3
		filterParams.clear();
		filterParams.add("vserver-name2:EXISTS:");
		edgeFilterParams.clear();
		edgeFilterParams.add("pserver:EXISTS:hostname");
		try {
			searchGraph.runNodesQuery(fromAppId, transId, 
					targetNodeType, edgeFilterParams, filterParams, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		
		//Exception - filterType - bad filterType passed
		edgeFilterParams.clear();
		edgeFilterParams.add("pserver:NOT-EXIST:hostname:test-pserver-hostname");
		try {
			searchGraph.runNodesQuery(fromAppId, transId, 
					targetNodeType, edgeFilterParams, filterParams, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
	}
	
	/**
	 * Test run generic query service instance.
	 *
	 * @throws Exception the exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testRunGenericQuery_serviceInstance() throws Exception {
		String startNodeType = "service-instance";
		String serviceSubscriptionURL = "testServiceSubscriptionURL";
		String startNodeKey = "service-instance-id";
		String startNodeValue = "test-service-instance-id";
		String startNodeKeyParam = "service-instance." + startNodeKey + ":" + startNodeValue;
		
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);
		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		TitanGraphQuery titanGraphQueryMock = PowerMockito.mock(TitanGraphQuery.class);
		when(gMock.query()).thenReturn(titanGraphQueryMock);
		when(titanGraphQueryMock.has(startNodeKey, startNodeValue)).thenReturn(titanGraphQueryMock);
		Iterable<TitanVertex> iterableMock =(Iterable<TitanVertex>) PowerMockito.mock(Iterable.class);
		when(titanGraphQueryMock.vertices()).thenReturn(iterableMock);
		Iterator<TitanVertex> iteratorMock = (Iterator<TitanVertex>) PowerMockito.mock(Iterator.class);
		when(iterableMock.iterator()).thenReturn(iteratorMock);
		when(iteratorMock.hasNext()).thenReturn(true, false);
		
		TitanVertex titanVertexMock = PowerMockito.mock(TitanVertex.class);
		when(iteratorMock.next()).thenReturn(titanVertexMock);
		when(tMock.newTransaction()).thenReturn(gMock);

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);
		
		PowerMockito.mockStatic(DbMeth.class);
		VertexProperty<String> vertexPropertyMock = (VertexProperty<String>)PowerMockito.mock(VertexProperty.class);
		when(titanVertexMock.<String>property("aai-node-type")).thenReturn(vertexPropertyMock);
		when(vertexPropertyMock.orElse(null)).thenReturn(startNodeType);

		when(DbMeth.getUniqueNodeWithDepParams(Mockito.anyString(), Mockito.anyString(), 
				Mockito.any(TitanTransaction.class), Mockito.anyString(), (HashMap<String, Object>) 
				Mockito.anyMapOf(String.class, Object.class), Mockito.anyString())).thenReturn(titanVertexMock);
		
		PowerMockito.mockStatic(IngestModelMoxyOxm.class);
		HashMap<String, AAIResources> aaiResourceContainerMock = new HashMap<String, AAIResources>();
		AAIResources aaiResourcesMock = PowerMockito.mock(AAIResources.class);
		aaiResourceContainerMock.put("v8", aaiResourcesMock);
		IngestModelMoxyOxm.aaiResourceContainer = aaiResourceContainerMock;
		DynamicJAXBContext dynamicJAXBContextMock = PowerMockito.mock(DynamicJAXBContext.class);
		when(aaiResourcesMock.getJaxbContext()).thenReturn(dynamicJAXBContextMock);
		
		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		when(dynamicJAXBContextMock.newDynamicEntity(Mockito.anyString())).thenReturn(dynamicEntityMock);
		
		PowerMockito.mockStatic(RestURL.class);
		when(RestURL.getSearchUrl(Mockito.any(TitanTransaction.class), Mockito.any(TitanVertex.class), 
				Mockito.anyString())).thenReturn(serviceSubscriptionURL);
		
		Response respMock = PowerMockito.mock(Response.class);
		when(respMock.getStatus()).thenReturn(200);

		SearchGraph searchGraph = new SearchGraph();
		SearchGraph searchGraphSpy = PowerMockito.spy(searchGraph);
		PowerMockito.doReturn(respMock).when(searchGraphSpy, "getResponseFromDynamicEntity", Mockito.any(DynamicEntity.class), 
				Mockito.any(DynamicJAXBContext.class), Mockito.any(AAIExtensionMap.class));
		List<String> startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add(startNodeKeyParam);
		List<String> includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("generic-vnf");
		AAIExtensionMap aaiExtMap = new AAIExtensionMap();
		aaiExtMap.setApiVersion(aaiApiVersion);
		Response resp = searchGraphSpy.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 0, aaiExtMap);
		assertEquals(200, resp.getStatus());
	}
	
	/**
	 * Test run generic query service instance depth 1 vertex is empty.
	 *
	 * @throws Exception the exception
	 */
	@Ignore
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testRunGenericQuery_serviceInstance_depth1_vertexIsEmpty() throws Exception {
		String startNodeType = "service-instance";
		String startNodeKey = "service-instance-id";
		String startNodeValue = "test-service-instance-id";
		String startNodeKeyParam = "service-instance." + startNodeKey + ":" + startNodeValue;
		
		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		TitanGraphQuery titanGraphQueryMock = PowerMockito.mock(TitanGraphQuery.class);
		when(gMock.query()).thenReturn(titanGraphQueryMock);
		when(titanGraphQueryMock.has(startNodeKey, startNodeValue)).thenReturn(titanGraphQueryMock);
		Iterable<TitanVertex> iterableMock =(Iterable<TitanVertex>) PowerMockito.mock(Iterable.class);
		when(titanGraphQueryMock.vertices()).thenReturn(iterableMock);
		Iterator<TitanVertex> iteratorMock = (Iterator<TitanVertex>) PowerMockito.mock(Iterator.class);
		when(iterableMock.iterator()).thenReturn(iteratorMock);
		when(iteratorMock.hasNext()).thenReturn(true, false);
		
		TitanVertex titanVertexMock = PowerMockito.mock(TitanVertex.class);
		when(iteratorMock.next()).thenReturn(titanVertexMock);
		GraphTraversalSource graphTraversalSourceMock = PowerMockito.mock(GraphTraversalSource.class);
		when(titanVertexMock.graph()).thenReturn(gMock);
		when(gMock.traversal()).thenReturn(graphTraversalSourceMock);
		
		GraphTraversal graphTraversalMock = PowerMockito.mock(GraphTraversal.class);
		when(graphTraversalSourceMock.V(titanVertexMock)).thenReturn(graphTraversalMock);
		when(graphTraversalMock.repeat(Mockito.any(Traversal.class))).thenReturn(graphTraversalMock);
		when(graphTraversalMock.times(1)).thenReturn(graphTraversalMock);
		
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		SearchGraph searchGraph = new SearchGraph();
		List<String> startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add(startNodeKeyParam);
		List<String> includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("l-interface");
		AAIExtensionMap aaiExtMap = new AAIExtensionMap();
		aaiExtMap.setApiVersion(aaiApiVersion);
		Response resp = searchGraph.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 1, aaiExtMap);
		assertNull(resp);
	}
	
	/**
	 * Test run generic query service instance 6112 exception.
	 *
	 * @throws Exception the exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testRunGenericQuery_serviceInstance_6112Exception() throws Exception {
		String startNodeType = "service-instance";
		String startNodeKey = "service-instance-id";
		String startNodeValue = "test-service-instance-id";
		String startNodeKeyParam = "service-instance." + startNodeKey + ":" + startNodeValue;
		
		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		TitanGraphQuery titanGraphQueryMock = PowerMockito.mock(TitanGraphQuery.class);
		when(gMock.query()).thenReturn(titanGraphQueryMock);
		when(titanGraphQueryMock.has(startNodeKey, startNodeValue)).thenReturn(titanGraphQueryMock);
		Iterable<TitanVertex> iterableMock =(Iterable<TitanVertex>) PowerMockito.mock(Iterable.class);
		when(titanGraphQueryMock.vertices()).thenReturn(iterableMock);
		Iterator<TitanVertex> iteratorMock = (Iterator<TitanVertex>) PowerMockito.mock(Iterator.class);
		when(iterableMock.iterator()).thenReturn(iteratorMock);
		when(iteratorMock.hasNext()).thenReturn(true);
		
		TitanVertex titanVertexMock = PowerMockito.mock(TitanVertex.class);
		when(iteratorMock.next()).thenReturn(titanVertexMock);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		SearchGraph searchGraph = new SearchGraph();
		List<String> startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add(startNodeKeyParam);
		List<String> includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("generic-vnf");
		AAIExtensionMap aaiExtMap = new AAIExtensionMap();
		aaiExtMap.setApiVersion(aaiApiVersion);
		try {
			searchGraph.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 0, aaiExtMap);
			fail("Exception expectd.");
		} catch (Exception e) {
			assertNotNull(e);
		}
	}
	
	/**
	 * Test run generic query service instance 6114 exception.
	 *
	 * @throws Exception the exception
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testRunGenericQuery_serviceInstance_6114Exception() throws Exception {
		String startNodeType = "service-instance";
		String startNodeKey = "service-instance-id";
		String startNodeValue = "test-service-instance-id";
		String startNodeKeyParam = "service-instance." + startNodeKey + ":" + startNodeValue;
		
		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);
		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		TitanGraphQuery titanGraphQueryMock = PowerMockito.mock(TitanGraphQuery.class);
		when(gMock.query()).thenReturn(titanGraphQueryMock);
		when(titanGraphQueryMock.has(startNodeKey, startNodeValue)).thenReturn(titanGraphQueryMock);
		Iterable<TitanVertex> iterableMock =(Iterable<TitanVertex>) PowerMockito.mock(Iterable.class);
		when(titanGraphQueryMock.vertices()).thenReturn(iterableMock);
		Iterator<TitanVertex> iteratorMock = (Iterator<TitanVertex>) PowerMockito.mock(Iterator.class);
		when(iterableMock.iterator()).thenReturn(iteratorMock);
		when(tMock.newTransaction()).thenReturn(gMock);
		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		SearchGraph searchGraph = new SearchGraph();
		List<String> startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add(startNodeKeyParam);
		List<String> includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("generic-vnf");
		AAIExtensionMap aaiExtMap = new AAIExtensionMap();
		aaiExtMap.setApiVersion(aaiApiVersion);
		
		try {
			searchGraph.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 0, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
	}
	
	/**
	 * Test run generic query service subscription.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testRunGenericQuery_serviceSubscription() throws Exception {
		String startNodeType = "service-subscription";
		String serviceSubscriptionURL = "testServiceSubscriptionURL";
		
		List<String> startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add("service-subscription.service-type:test-service-type");
		List<String> includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("service-instance");
		AAIExtensionMap aaiExtMap = new AAIExtensionMap();
		aaiExtMap.setApiVersion(aaiApiVersion);
		
		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);
		
		PowerMockito.mockStatic(DbMeth.class);
		TitanVertex titanVertexMock = PowerMockito.mock(TitanVertex.class);
		@SuppressWarnings("unchecked")
		VertexProperty<String> vertexPropertyMock = (VertexProperty<String>)PowerMockito.mock(VertexProperty.class);
		when(titanVertexMock.<String>property("aai-node-type")).thenReturn(vertexPropertyMock);
		when(vertexPropertyMock.orElse(null)).thenReturn(startNodeType);

		when(DbMeth.getUniqueNodeWithDepParams(Mockito.anyString(), Mockito.anyString(), 
				Mockito.any(TitanTransaction.class), Mockito.anyString(), (HashMap<String, Object>) 
				Mockito.anyMapOf(String.class, Object.class), Mockito.anyString())).thenReturn(titanVertexMock);
		
		PowerMockito.mockStatic(IngestModelMoxyOxm.class);
		HashMap<String, AAIResources> aaiResourceContainerMock = new HashMap<String, AAIResources>();
		AAIResources aaiResourcesMock = PowerMockito.mock(AAIResources.class);
		aaiResourceContainerMock.put("v8", aaiResourcesMock);
		IngestModelMoxyOxm.aaiResourceContainer = aaiResourceContainerMock;
		DynamicJAXBContext dynamicJAXBContextMock = PowerMockito.mock(DynamicJAXBContext.class);
		when(aaiResourcesMock.getJaxbContext()).thenReturn(dynamicJAXBContextMock);
		
		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		when(dynamicJAXBContextMock.newDynamicEntity(Mockito.anyString())).thenReturn(dynamicEntityMock);
		
		PowerMockito.mockStatic(RestURL.class);
		when(RestURL.getSearchUrl(Mockito.any(TitanTransaction.class), Mockito.any(TitanVertex.class), 
				Mockito.anyString())).thenReturn(serviceSubscriptionURL);
		
//		SearchGraph searchGraphMock = PowerMockito.mock(SearchGraph.class);
		Response respMock = PowerMockito.mock(Response.class);
		when(respMock.getStatus()).thenReturn(200);
//		PowerMockito.when(SearchGraph.class, "getResponseFromDynamicEntity", Mockito.any(DynamicEntity.class), 
//				Mockito.any(DynamicJAXBContext.class), Mockito.any(AAIExtensionMap.class)).thenReturn(respMock);
		SearchGraph searchGraph = new SearchGraph();
		SearchGraph searchGraphSpy = PowerMockito.spy(searchGraph);
		PowerMockito.doReturn(respMock).when(searchGraphSpy, "getResponseFromDynamicEntity", Mockito.any(DynamicEntity.class), 
				Mockito.any(DynamicJAXBContext.class), Mockito.any(AAIExtensionMap.class));
		
		Response resp = searchGraphSpy.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 0, aaiExtMap);
		assertEquals(200, resp.getStatus());
	}
	
	/**
	 * Test run generic query start node not found exception.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testRunGenericQuery_startNodeNotFoundException() throws Exception {
		String startNodeType = "testStartNodeType";
		
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);
		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);
		
		PowerMockito.mockStatic(DbMeth.class);
		TitanVertex titanVertexMock = PowerMockito.mock(TitanVertex.class);
		@SuppressWarnings("unchecked")
		VertexProperty<String> vertexPropertyMock = (VertexProperty<String>)PowerMockito.mock(VertexProperty.class);
		when(titanVertexMock.<String>property("aai-node-type")).thenReturn(vertexPropertyMock);
		when(vertexPropertyMock.orElse(null)).thenReturn(startNodeType);
		when(DbMeth.getUniqueNodeWithDepParams(Mockito.anyString(), Mockito.anyString(), 
				Mockito.any(TitanTransaction.class), Mockito.anyString(), (HashMap<String, Object>) 
				Mockito.anyMapOf(String.class, Object.class), Mockito.anyString())).thenReturn(null);
		
		Response respMock = PowerMockito.mock(Response.class);
		when(respMock.getStatus()).thenReturn(200);
		SearchGraph searchGraph = new SearchGraph();
		List<String> startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add("service-subscription.service-type:test-service-type");
		List<String> includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("service-instance");
		AAIExtensionMap aaiExtMap = new AAIExtensionMap();
		aaiExtMap.setApiVersion(aaiApiVersion);
		try {
			searchGraph.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 0, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
	}
	
	/**
	 * Test run generic query exception.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testRunGenericQuery_exception() throws Exception {
		String startNodeType = "testStartNodeType";
		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);
		when(graphMock.getGraph()).thenReturn(tMock);
		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);

		SearchGraph searchGraph = new SearchGraph();
		List<String> startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add("service-subscription.service-type:test-service-type");
		List<String> includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("service-instance");
		AAIExtensionMap aaiExtMap = new AAIExtensionMap();
		try {
			searchGraph.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 0, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
	}
	
	/**
	 * Test run generic query request error.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testRunGenericQuery_requestError() throws Exception {
		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);
		when(graphMock.getGraph()).thenReturn(tMock);
		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);

		SearchGraph searchGraph = new SearchGraph();
		String startNodeType = null;
		List<String> startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add("service-subscription.service-type:test-service-type");
		List<String> includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("service-instance");
		AAIExtensionMap aaiExtMap = new AAIExtensionMap();
		try {
			searchGraph.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 0, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		
		startNodeType = "testStartNodeType";
		startNodeKeyParams = null;
		try {
			searchGraph.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 0, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		
		startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add("service-subscription.service-type:test-service-type");
		includeNodeTypes = null;
		try {
			searchGraph.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 0, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		
		includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("service-instance");
		try {
			searchGraph.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 7, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
		startNodeKeyParams.add("service-subscription.service-type");
		try {
			searchGraph.runGenericQuery(fromAppId, transId, startNodeType, startNodeKeyParams, includeNodeTypes, 0, aaiExtMap);
			fail("Exception expected");
		} catch (Exception e) {
			assertNotNull(e);
		}
	}
}
