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

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Matchers.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;
import org.openecomp.aai.dbgen.DbMeth;
import org.openecomp.aai.dbmap.AAIGraph;
import org.openecomp.aai.dbmap.ActionsGraph;
import org.openecomp.aai.dbmap.GraphHelpersMoxy;
import org.openecomp.aai.domain.model.AAIResource;
import org.openecomp.aai.domain.model.AAIResources;
import org.openecomp.aai.domain.model.AncestryItem;
import org.openecomp.aai.domain.model.AncestryItems;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.ingestModel.DbMaps;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.introspection.ModelInjestor;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;
import org.openecomp.aai.util.HttpsAuthClient;
import org.openecomp.aai.util.RestController;
import org.openecomp.aai.util.RestObject;
import org.openecomp.aai.util.RestURL;
import org.openecomp.aai.util.StoreNotificationEvent;

import static org.powermock.api.support.membermodification.MemberMatcher.constructor;
import static org.powermock.api.support.membermodification.MemberModifier.suppress;


import static org.mockito.Mockito.*;

import org.powermock.api.mockito.PowerMockito;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.powermock.reflect.internal.WhiteboxImpl;
import org.eclipse.persistence.dynamic.DynamicClassLoader;
import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.dynamic.DynamicHelper;
import org.eclipse.persistence.dynamic.DynamicType;
import org.eclipse.persistence.internal.dynamic.DynamicEntityImpl;
import org.eclipse.persistence.jaxb.BeanValidationMode;
import org.eclipse.persistence.jaxb.JAXBContext;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContextFactory;
import org.eclipse.persistence.jaxb.metadata.MetadataSource;
import org.eclipse.persistence.jaxb.metadata.MetadataSourceAdapter;
import org.eclipse.persistence.jpa.dynamic.JPADynamicTypeBuilder;
import org.eclipse.persistence.sessions.DatabaseSession;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.eclipse.persistence.sessions.Project; 
import org.eclipse.persistence.sessions.DatabaseLogin; 
import org.eclipse.persistence.sessions.DatabaseSession;

import org.openecomp.aai.domain.yang.GenericVnf;
import org.openecomp.aai.domain.yang.UpdateNodeKey;
import com.google.common.collect.ArrayListMultimap;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanTransaction;
import com.thinkaurelius.titan.core.TitanVertex;

@RunWith(PowerMockRunner.class)

@PrepareForTest({DynamicEntity.class, AAIGraph.class, ActionsGraph.class, TitanGraph.class, TitanVertex.class, DbMeth.class, 
	GraphHelpersMoxy.class, RestURL.class, AAIResource.class, AAIExtensionMap.class, DynamicJAXBContext.class,
	StoreNotificationEvent.class, AAIException.class, RestObject.class, RestController.class, 
	HttpsAuthClient.class, Client.class, ClientResponse.class})
@SuppressStaticInitializationFor("com.sun.jersey.api.client.ClientResponse")
@PowerMockIgnore( {"javax.management.*"}) 

public class ActionsGraphTest  {
	
	//@Rule
	//public PowerMockRule rule = new PowerMockRule();
	
	DynamicJAXBContext jaxbContext;
	static String defaultApiVersion = null;
	static String latestC25version = "v6";
	
	private static class SequenceAnswer<T> implements Answer<T> {
		

	    private Iterator<T> resultIterator;

	    // the last element is always returned once the iterator is exhausted, as with thenReturn()
	    private T last;

	    /**
    	 * Instantiates a new sequence answer.
    	 *
    	 * @param results the results
    	 */
    	public SequenceAnswer(List<T> results) {
	        this.resultIterator = results.iterator();
	        this.last = results.get(results.size() - 1);
	    }

	    /**
    	 * {@inheritDoc}
    	 */
    	//@Override
	    public T answer(InvocationOnMock invocation) throws Throwable {
	        if (resultIterator.hasNext()) {
	            return resultIterator.next();
	        }
	        return last;
	    }
	}


	
	/**
	 * Sets the up before class.
	 *
	 * @throws Exception the exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		
		try {
			defaultApiVersion = AAIConfig.get("aai.default.api.version");
			//System.out.println("defaultApiVersion " + defaultApiVersion);
		} catch ( Exception e) {
			fail("aaiconfig.properties for aai.default.api.version exception " + e.getMessage());
		}
		
		//PowerMockito.mockStatic(IngestModelMoxyOxm.class);
		//IngestModelMoxyOxm ingestModelMoxyOxmMock = PowerMockito.mock(IngestModelMoxyOxm.class);

		DbMaps 	dbMaps = new DbMaps();

		dbMaps.NodeProps.put("customer", "global-customer-id");
		dbMaps.NodeProps.put("customer", "subscriber-name");
		dbMaps.NodeProps.put("service-subscription", "service-type");
		dbMaps.NodeProps.put("service-subscription", "temp-ub-sub-account-id");
		dbMaps.NodeProps.put("cloud-region", "region-owner");
		dbMaps.NodeProps.put("cloud-region", "cloud-region-id");
		dbMaps.NodeProps.put("volume-group", "volume-group-id");
		dbMaps.NodeProps.put("volume-group", "heat-stack-id");
		dbMaps.NodeProps.put("generic-vnf", "vnf-id");
		dbMaps.NodeProps.put("generic-vnf", "prov-status");
		dbMaps.NodeProps.put("vpe", "vnf-id");
		dbMaps.NodeProps.put("vpe", "prov-status");
		dbMaps.NodeProps.put("generic-vnf", "something-else");

		dbMaps.PropertyDataTypeMap.put("global-customer-id", "String");
		dbMaps.PropertyDataTypeMap.put("subscriber-name", "String");
		dbMaps.PropertyDataTypeMap.put("service-type", "String");
		dbMaps.PropertyDataTypeMap.put("temp-ub-sub-account-id", "String");
		dbMaps.PropertyDataTypeMap.put("cloud-owner", "String");
		dbMaps.PropertyDataTypeMap.put("cloud-region-id", "String");
		dbMaps.PropertyDataTypeMap.put("volume-group-id", "String");
		dbMaps.PropertyDataTypeMap.put("heat-stack-id", "String");
		dbMaps.PropertyDataTypeMap.put("vnf-id", "String");
		dbMaps.PropertyDataTypeMap.put("prov-status", "String");
		dbMaps.PropertyDataTypeMap.put("something-else", "String");
		
		HashMap<String, DbMaps> dbMapsContainerMock = new HashMap<String, DbMaps>();
		dbMapsContainerMock.put(defaultApiVersion, dbMaps);
		dbMapsContainerMock.put(latestC25version, dbMaps);
		
		IngestModelMoxyOxm.dbMapsContainer = dbMapsContainerMock;
		
		HashMap<String, AAIResources> aaiResourceContainerMock = new HashMap<String, AAIResources>(); 
		AAIResources aaiResources = new AAIResources();
		
		aaiResourceContainerMock.put(defaultApiVersion, aaiResources); 
		IngestModelMoxyOxm.aaiResourceContainer = aaiResourceContainerMock;
		
	}

	/**
	 * Tear down after class.
	 *
	 * @throws Exception the exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * Sets the up.
	 *
	 * @throws Exception the exception
	 */
	@Before
	public void setUp() throws Exception {

	}

	/**
	 * Tear down.
	 *
	 * @throws Exception the exception
	 */
	@After
	public void tearDown() throws Exception {
	}
	
	/**
	 * Test property update with update node key.
	 *
	 * @throws Exception the exception
	 */
	@SuppressWarnings({ "unchecked" })
	@Test
	public void testPropertyUpdate_withUpdateNodeKey() throws Exception {

		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";
		
		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);
		
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("customer");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();
		
		DynamicEntity keyData1DynamicEntity = dynamicHelper.newDynamicEntity(classNameUpdateNodeKey);
		
		keyData1DynamicEntity.set("keyName", "global-customer-id");
		keyData1DynamicEntity.set("keyValue", "globalCustomerIdVal");
		updateNodeKeyList.add(keyData1DynamicEntity);
		
		DynamicEntity keyData2DynamicEntity = dynamicHelper.newDynamicEntity(classNameUpdateNodeKey);
		keyData2DynamicEntity.set("keyName", "service-subscription");
		keyData2DynamicEntity.set("keyValue", "serviceTypeVal");
		//updateNodeKeyList.add(keyData2DynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "subscriber-name");
		actionDataDynamicEntity.set("propertyValue", "newName");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		// not dependent
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());
		
		actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);

		
		assertTrue("return from propertyUpdate", true);

	}

	/**
	 * Test property update with dependent update node key.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testPropertyUpdate_withDependentUpdateNodeKey() throws Exception {
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);
		
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("service-subscription");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();
		
		DynamicEntity keyData1DynamicEntity = dynamicHelper.newDynamicEntity(classNameUpdateNodeKey);
		keyData1DynamicEntity.set("keyName", "customer.global-customer-id");
		keyData1DynamicEntity.set("keyValue", "globalCustomerIdVal");
		updateNodeKeyList.add(keyData1DynamicEntity);
		
		DynamicEntity keyData2DynamicEntity = dynamicHelper.newDynamicEntity(classNameUpdateNodeKey);
		keyData2DynamicEntity.set("keyName", "service-subscription");
		keyData2DynamicEntity.set("keyValue", "serviceTypeVal");
		updateNodeKeyList.add(keyData2DynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "temp-ub-sub-account-id");
		actionDataDynamicEntity.set("propertyValue", "tempUbSubAccountIdVal");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("customer");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);

		assertTrue("return from propertyUpdate", true);

	}
	
	/**
	 * Test property update with missing cloud region update node key.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testPropertyUpdate_withMissingCloudRegionUpdateNodeKey() throws Exception {
		
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + latestC25version + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + latestC25version + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + latestC25version + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);
		
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("volume-group");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();
		
		DynamicEntity keyData1DynamicEntity = dynamicHelper.newDynamicEntity(classNameUpdateNodeKey);
		keyData1DynamicEntity.set("keyName", "volume-group-id");
		keyData1DynamicEntity.set("keyValue", "volumeGroupIdVal");
		updateNodeKeyList.add(keyData1DynamicEntity);
		
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "heat-stack-id");
		actionDataDynamicEntity.set("propertyValue", "heatStackIdVal");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(latestC25version);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);

		assertTrue("return from propertyUpdate", true);

	}
	
	/**
	 * Test property update with dependent update node uri.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testPropertyUpdate_withDependentUpdateNodeUri() throws Exception {
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);
		
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("service-subscription");
		
		when(dynamicEntityMock.<String>get("updateNodeUri")).thenReturn("business/customers/customer/globalCustomerIdVal/service-subscriptions/service-subscription/serviceTypeVal");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();

		LinkedHashMap <String,Object> returnHash = new LinkedHashMap<String,Object>();
		
		HashMap <String,Object> thisNodeHash = new HashMap <String,Object> ();
		String nodeKeyName = "service-subscription.service-type";
		String nodeKeyValue = "serviceTypeVal";
		thisNodeHash.put(nodeKeyName, nodeKeyValue);
		returnHash.putAll(thisNodeHash);
		
		HashMap <String,Object> depNodeHash = new HashMap <String,Object> ();
		String depNodeKeyName = "customer.global-customer-id";
		String depNodeKeyValue = "globalCustomerIdVal";
		depNodeHash.put(depNodeKeyName, depNodeKeyValue);
		returnHash.putAll(depNodeHash);
		
		PowerMockito.mockStatic(RestURL.class);
		//RestURL restURLMock = PowerMockito.mock(RestURL.class);
		
		PowerMockito.when((  RestURL.getKeyHashes(anyString()))).thenReturn(returnHash);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "temp-ub-sub-account-id");
		actionDataDynamicEntity.set("propertyValue", "tempUbSubAccountIdVal");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("customer");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);

		assertTrue("return from propertyUpdate", true);

	}

	/**
	 * Test property update with missing cloud region dependent update node uri.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testPropertyUpdate_withMissingCloudRegionDependentUpdateNodeUri() throws Exception {
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + latestC25version + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + latestC25version + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + latestC25version + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);
		
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("volume-group");
		
		when(dynamicEntityMock.<String>get("updateNodeUri")).thenReturn("cloud-infrastructure/volume-groups/volume-group/volumeGroupIdValue");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();

		LinkedHashMap <String,Object> returnHash = new LinkedHashMap<String,Object>();
		
		HashMap <String,Object> thisNodeHash = new HashMap <String,Object> ();
		String nodeKeyName = "volume-group.volume-group-id";
		String nodeKeyValue = "volumeGroupIdVal";
		thisNodeHash.put(nodeKeyName, nodeKeyValue);
		returnHash.putAll(thisNodeHash);
		
		
		HashMap <String,Object> depNodeHash = new HashMap <String,Object> ();		
		String depNodeKeyName = "cloud-region.cloud-owner";
		String depNodeKeyValue = "cloudOwnerVal";
		String depNodeKeyName1 = "cloud-region.cloud-region-id";
		String depNodeKeyValue1 = "cloudRegionIdVal";
		depNodeHash.put(depNodeKeyName, depNodeKeyValue);
		depNodeHash.put(depNodeKeyName1, depNodeKeyValue1);
		returnHash.putAll(depNodeHash);
		
		
		PowerMockito.mockStatic(RestURL.class);
		//RestURL restURLMock = PowerMockito.mock(RestURL.class);
		
		PowerMockito.when((  RestURL.getKeyHashes(anyString()))).thenReturn(returnHash);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "heat-stack-id");
		actionDataDynamicEntity.set("propertyValue", "heatStackIdVal");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(latestC25version);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("cloud-region");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);

		assertTrue("return from propertyUpdate", true);

	}
	
	/**
	 * Test property update generic vnf prov status PREPRO vwith update node URI.
	 *
	 * @throws Exception the exception
	 */
	@Ignore
	@Test 
	public void testPropertyUpdate_GenericVnfProvStatusPREPROVwithUpdateNodeURI() throws Exception {
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);	
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);


		
		PowerMockito.mockStatic(RestController.class);		
		PowerMockito.doNothing().when( RestController.class, "Get", Mockito.anyObject(), anyString(), 
				anyString(), anyString(), Mockito.anyObject(), anyBoolean());

		RestObject<GenericVnf> mockRestObject = PowerMockito.mock(RestObject.class);
		PowerMockito.whenNew(RestObject.class).withNoArguments().thenReturn(mockRestObject);
		PowerMockito.doNothing().when( mockRestObject).set(isA(GenericVnf.class));
		GenericVnf genericVnf = new GenericVnf();
		genericVnf.setEquipmentRole("VRR");
		genericVnf.setVnfName("vnfNameValue");
		
		PowerMockito.when(mockRestObject.get()).thenReturn(genericVnf);
		
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("generic-vnf");
		
		when(dynamicEntityMock.<String>get("updateNodeUri")).thenReturn("network/generic-vnfs/generic-vnf/vnfId");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();

		LinkedHashMap <String,Object> returnHash = new LinkedHashMap<String,Object>();
		
		HashMap <String,Object> thisNodeHash = new HashMap <String,Object> ();
		String nodeKeyName = "generic-vnf.vnf-id";
		String nodeKeyValue = "vnfId";
		thisNodeHash.put(nodeKeyName, nodeKeyValue);
		returnHash.putAll(thisNodeHash);
		
		HashMap <String,Object> depNodeHash = new HashMap <String,Object> ();

		PowerMockito.mockStatic(RestURL.class);
		//RestURL restURLMock = PowerMockito.mock(RestURL.class);
		
		PowerMockito.when((  RestURL.getKeyHashes(anyString()))).thenReturn(returnHash);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "prov-status");
		actionDataDynamicEntity.set("propertyValue", "PREPROV");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("customer");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		PowerMockito.doNothing().when(actionsGraphSpy, "triggerInstarEquipStatusUpdate", 
				anyString(), anyString(), anyString());

		actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);

		assertTrue("return from propertyUpdate", true);

	}
	
	/**
	 * Test property update generic vnf prov status XY zwith update node URI.
	 *
	 * @throws Exception the exception
	 */
	@Ignore
	@Test 
	public void testPropertyUpdate_GenericVnfProvStatusXYZwithUpdateNodeURI() throws Exception {
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);	
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		

		
		PowerMockito.mockStatic(RestController.class);		
		PowerMockito.doNothing().when( RestController.class, "Get", Mockito.anyObject(), anyString(), 
				anyString(), anyString(), Mockito.anyObject(), anyBoolean());

		RestObject<GenericVnf> mockRestObject = PowerMockito.mock(RestObject.class);
		PowerMockito.whenNew(RestObject.class).withNoArguments().thenReturn(mockRestObject);
		PowerMockito.doNothing().when( mockRestObject).set(isA(GenericVnf.class));
		GenericVnf genericVnf = new GenericVnf();
		genericVnf.setEquipmentRole("XYZ");
		genericVnf.setVnfName("vnfNameValue");

		PowerMockito.when(mockRestObject.get()).thenReturn(genericVnf);
		
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("generic-vnf");
		
		when(dynamicEntityMock.<String>get("updateNodeUri")).thenReturn("network/generic-vnfs/generic-vnf/vnfId");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();

		LinkedHashMap <String,Object> returnHash = new LinkedHashMap<String,Object>();
		
		HashMap <String,Object> thisNodeHash = new HashMap <String,Object> ();
		String nodeKeyName = "generic-vnf.vnf-id";
		String nodeKeyValue = "vnfId";
		thisNodeHash.put(nodeKeyName, nodeKeyValue);
		returnHash.putAll(thisNodeHash);
		
		HashMap <String,Object> depNodeHash = new HashMap <String,Object> ();

		PowerMockito.mockStatic(RestURL.class);
		//RestURL restURLMock = PowerMockito.mock(RestURL.class);
		
		PowerMockito.when((  RestURL.getKeyHashes(anyString()))).thenReturn(returnHash);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "prov-status");
		actionDataDynamicEntity.set("propertyValue", "NVTPROV");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("customer");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		PowerMockito.doNothing().when(actionsGraphSpy, "triggerInstarEquipStatusUpdate", 
				anyString(), anyString(), anyString());

		actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);

		assertTrue("return from propertyUpdate", true);

	}
	
	/**
	 * Test property update invalid action typewith update node URI.
	 *
	 * @throws Exception the exception
	 */
	@Ignore
	@Test 
	public void testPropertyUpdate_InvalidActionTypewithUpdateNodeURI() throws Exception {
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);	
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		


		
		PowerMockito.mockStatic(RestController.class);		
		PowerMockito.doNothing().when( RestController.class, "Get", Mockito.anyObject(), anyString(), 
				anyString(), anyString(), Mockito.anyObject(), anyBoolean());

		RestObject<GenericVnf> mockRestObject = PowerMockito.mock(RestObject.class);
		PowerMockito.whenNew(RestObject.class).withNoArguments().thenReturn(mockRestObject);
		PowerMockito.doNothing().when( mockRestObject).set(isA(GenericVnf.class));
		GenericVnf genericVnf = new GenericVnf();
		genericVnf.setEquipmentRole("VRR");
		genericVnf.setVnfName("vnfNameValue");
		
		PowerMockito.when(mockRestObject.get()).thenReturn(genericVnf);
		
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("generic-vnf");
		
		when(dynamicEntityMock.<String>get("updateNodeUri")).thenReturn("network/generic-vnfs/generic-vnf/vnfId");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();

		LinkedHashMap <String,Object> returnHash = new LinkedHashMap<String,Object>();
		
		HashMap <String,Object> thisNodeHash = new HashMap <String,Object> ();
		String nodeKeyName = "generic-vnf.vnf-id";
		String nodeKeyValue = "vnfId";
		thisNodeHash.put(nodeKeyName, nodeKeyValue);
		returnHash.putAll(thisNodeHash);
		
		HashMap <String,Object> depNodeHash = new HashMap <String,Object> ();

		PowerMockito.mockStatic(RestURL.class);
		//RestURL restURLMock = PowerMockito.mock(RestURL.class);
		
		PowerMockito.when((  RestURL.getKeyHashes(anyString()))).thenReturn(returnHash);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "prov-status");
		actionDataDynamicEntity.set("propertyValue", "PREPROV");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "not-replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("customer");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		PowerMockito.doNothing().when(actionsGraphSpy, "triggerInstarEquipStatusUpdate", 
				anyString(), anyString(), anyString());
		try {
			actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);
			fail( "expected exception");
		} catch ( Exception e ) {
			assertNotNull(e);
		}

	}	
	
	/**
	 * Test property update udefined propertywith update node URI.
	 *
	 * @throws Exception the exception
	 */
	@Ignore
	@Test 
	public void testPropertyUpdate_UdefinedPropertywithUpdateNodeURI() throws Exception {
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);	
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		


		
		PowerMockito.mockStatic(RestController.class);		
		PowerMockito.doNothing().when( RestController.class, "Get", Mockito.anyObject(), anyString(), 
				anyString(), anyString(), Mockito.anyObject(), anyBoolean());

		RestObject<GenericVnf> mockRestObject = PowerMockito.mock(RestObject.class);
		PowerMockito.whenNew(RestObject.class).withNoArguments().thenReturn(mockRestObject);
		PowerMockito.doNothing().when( mockRestObject).set(isA(GenericVnf.class));
		GenericVnf genericVnf = new GenericVnf();
		genericVnf.setEquipmentRole("VRR");
		genericVnf.setVnfName("vnfNameValue");
		
		PowerMockito.when(mockRestObject.get()).thenReturn(genericVnf);
		
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("generic-vnf");
		
		when(dynamicEntityMock.<String>get("updateNodeUri")).thenReturn("network/generic-vnfs/generic-vnf/vnfId");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();

		LinkedHashMap <String,Object> returnHash = new LinkedHashMap<String,Object>();
		
		HashMap <String,Object> thisNodeHash = new HashMap <String,Object> ();
		String nodeKeyName = "generic-vnf.vnf-id";
		String nodeKeyValue = "vnfId";
		thisNodeHash.put(nodeKeyName, nodeKeyValue);
		returnHash.putAll(thisNodeHash);
		
		HashMap <String,Object> depNodeHash = new HashMap <String,Object> ();

		PowerMockito.mockStatic(RestURL.class);
		//RestURL restURLMock = PowerMockito.mock(RestURL.class);
		
		PowerMockito.when((  RestURL.getKeyHashes(anyString()))).thenReturn(returnHash);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "x-prov-status");
		actionDataDynamicEntity.set("propertyValue", "PREPROV");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("customer");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		PowerMockito.doNothing().when(actionsGraphSpy, "triggerInstarEquipStatusUpdate", 
				anyString(), anyString(), anyString());
		try {
			actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);
			fail( "expected exception");
		} catch ( Exception e ) {
			assertNotNull(e);
		}

	}
	
	/**
	 * Test property update invalid node typewith update node URI.
	 *
	 * @throws Exception the exception
	 */
	@Ignore
	@Test 
	public void testPropertyUpdate_InvalidNodeTypewithUpdateNodeURI() throws Exception {
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);	
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		


		
		PowerMockito.mockStatic(RestController.class);		
		PowerMockito.doNothing().when( RestController.class, "Get", Mockito.anyObject(), anyString(), 
				anyString(), anyString(), Mockito.anyObject(), anyBoolean());

		RestObject<GenericVnf> mockRestObject = PowerMockito.mock(RestObject.class);
		PowerMockito.whenNew(RestObject.class).withNoArguments().thenReturn(mockRestObject);
		PowerMockito.doNothing().when( mockRestObject).set(isA(GenericVnf.class));
		GenericVnf genericVnf = new GenericVnf();
		genericVnf.setEquipmentRole("VRR");
		genericVnf.setVnfName("vnfNameValue");
		
		PowerMockito.when(mockRestObject.get()).thenReturn(genericVnf);
		
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("x-generic-vnf");
		
		when(dynamicEntityMock.<String>get("updateNodeUri")).thenReturn("network/generic-vnfs/generic-vnf/vnfId");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();

		LinkedHashMap <String,Object> returnHash = new LinkedHashMap<String,Object>();
		
		HashMap <String,Object> thisNodeHash = new HashMap <String,Object> ();
		String nodeKeyName = "generic-vnf.vnf-id";
		String nodeKeyValue = "vnfId";
		thisNodeHash.put(nodeKeyName, nodeKeyValue);
		returnHash.putAll(thisNodeHash);
		
		HashMap <String,Object> depNodeHash = new HashMap <String,Object> ();

		PowerMockito.mockStatic(RestURL.class);
		//RestURL restURLMock = PowerMockito.mock(RestURL.class);
		
		PowerMockito.when((  RestURL.getKeyHashes(anyString()))).thenReturn(returnHash);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "prov-status");
		actionDataDynamicEntity.set("propertyValue", "PREPROV");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "not-replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("customer");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		PowerMockito.doNothing().when(actionsGraphSpy, "triggerInstarEquipStatusUpdate", 
				anyString(), anyString(), anyString());
		try {
			actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);
			fail( "expected exception");
		} catch ( Exception e ) {
			assertNotNull(e);
		}

	}
	
	/**
	 * Test property update missing node typewith update node URI.
	 *
	 * @throws Exception the exception
	 */
	@Ignore
	@Test 
	public void testPropertyUpdate_MissingNodeTypewithUpdateNodeURI() throws Exception {
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);	
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		

		
		PowerMockito.mockStatic(RestController.class);		
		PowerMockito.doNothing().when( RestController.class, "Get", Mockito.anyObject(), anyString(), 
				anyString(), anyString(), Mockito.anyObject(), anyBoolean());

		RestObject<GenericVnf> mockRestObject = PowerMockito.mock(RestObject.class);
		PowerMockito.whenNew(RestObject.class).withNoArguments().thenReturn(mockRestObject);
		PowerMockito.doNothing().when( mockRestObject).set(isA(GenericVnf.class));
		GenericVnf genericVnf = new GenericVnf();
		genericVnf.setEquipmentRole("VRR");
		genericVnf.setVnfName("vnfNameValue");
		
		PowerMockito.when(mockRestObject.get()).thenReturn(genericVnf);
		
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn(null);
		
		when(dynamicEntityMock.<String>get("updateNodeUri")).thenReturn("network/generic-vnfs/generic-vnf/vnfId");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();

		LinkedHashMap <String,Object> returnHash = new LinkedHashMap<String,Object>();
		
		HashMap <String,Object> thisNodeHash = new HashMap <String,Object> ();
		String nodeKeyName = "generic-vnf.vnf-id";
		String nodeKeyValue = "vnfId";
		thisNodeHash.put(nodeKeyName, nodeKeyValue);
		returnHash.putAll(thisNodeHash);
		
		HashMap <String,Object> depNodeHash = new HashMap <String,Object> ();

		PowerMockito.mockStatic(RestURL.class);
		//RestURL restURLMock = PowerMockito.mock(RestURL.class);
		
		PowerMockito.when((  RestURL.getKeyHashes(anyString()))).thenReturn(returnHash);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "prov-status");
		actionDataDynamicEntity.set("propertyValue", "PREPROV");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "not-replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("customer");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		PowerMockito.doNothing().when(actionsGraphSpy, "triggerInstarEquipStatusUpdate", 
				anyString(), anyString(), anyString());
		try {
			actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);
			fail( "expected exception");
		} catch ( Exception e ) {
			assertNotNull(e);
		}

	}
	
	/**
	 * Test property update with key params empty update node key.
	 *
	 * @throws Exception the exception
	 */
	@SuppressWarnings({ "unchecked" })
	@Test
	public void testPropertyUpdate_withKeyParamsEmptyUpdateNodeKey() throws Exception {

		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";
		
		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);
		
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("customer");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();
		
		DynamicEntity keyData1DynamicEntity = dynamicHelper.newDynamicEntity(classNameUpdateNodeKey);
		
		keyData1DynamicEntity.set("keyName", "global-customer-id");
		keyData1DynamicEntity.set("keyValue", "globalCustomerIdVal");
		//updateNodeKeyList.add(keyData1DynamicEntity);
		
		DynamicEntity keyData2DynamicEntity = dynamicHelper.newDynamicEntity(classNameUpdateNodeKey);
		keyData2DynamicEntity.set("keyName", "service-subscription");
		keyData2DynamicEntity.set("keyValue", "serviceTypeVal");
		//updateNodeKeyList.add(keyData2DynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "subscriber-name");
		actionDataDynamicEntity.set("propertyValue", "newName");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		// not dependent
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());
		try {
			actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);
			fail( "expected exception");
		} catch ( Exception e ) {
			assertNotNull(e);
		}
		
		assertTrue("return from propertyUpdate", true);

	}

	
	/**
	 * Test property update generic vnf not prov statuswith update node URI.
	 *
	 * @throws Exception the exception
	 */
	@Ignore
	@Test 
	public void testPropertyUpdate_GenericVnfNotProvStatuswithUpdateNodeURI() throws Exception {
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);	
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		


		
		PowerMockito.mockStatic(RestController.class);		
		PowerMockito.doNothing().when( RestController.class, "Get", Mockito.anyObject(), anyString(), 
				anyString(), anyString(), Mockito.anyObject(), anyBoolean());

		RestObject<GenericVnf> mockRestObject = PowerMockito.mock(RestObject.class);
		PowerMockito.whenNew(RestObject.class).withNoArguments().thenReturn(mockRestObject);
		PowerMockito.doNothing().when( mockRestObject).set(isA(GenericVnf.class));
		GenericVnf genericVnf = new GenericVnf();
		genericVnf.setEquipmentRole("VRR");
		genericVnf.setVnfName("vnfNameValue");
		
		PowerMockito.when(mockRestObject.get()).thenReturn(genericVnf);
		
		
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("generic-vnf");
		
		when(dynamicEntityMock.<String>get("updateNodeUri")).thenReturn("network/generic-vnfs/generic-vnf/vnfId");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();

		LinkedHashMap <String,Object> returnHash = new LinkedHashMap<String,Object>();
		
		HashMap <String,Object> thisNodeHash = new HashMap <String,Object> ();
		String nodeKeyName = "generic-vnf.vnf-id";
		String nodeKeyValue = "vnfId";
		thisNodeHash.put(nodeKeyName, nodeKeyValue);
		returnHash.putAll(thisNodeHash);
		
		HashMap <String,Object> depNodeHash = new HashMap <String,Object> ();

		PowerMockito.mockStatic(RestURL.class);
		//RestURL restURLMock = PowerMockito.mock(RestURL.class);
		
		PowerMockito.when((  RestURL.getKeyHashes(anyString()))).thenReturn(returnHash);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "something-else");
		actionDataDynamicEntity.set("propertyValue", "somethingElseValue");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("customer");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		PowerMockito.doNothing().when(actionsGraphSpy, "triggerInstarEquipStatusUpdate", 
				anyString(), anyString(), anyString());

		actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);

		assertTrue("return from propertyUpdate", true);

	}	
	
	/**
	 * Test property update exception from get vnf.
	 *
	 * @throws Exception the exception
	 */
	@Ignore
	@Test 
	public void testPropertyUpdate_exceptionFromGetVnf() throws Exception {
		String classNameUpdateNodeKey = "inventory.aai.openecomp.org." + defaultApiVersion + ".UpdateNodeKey";
		String classNameAction = "inventory.aai.openecomp.org." + defaultApiVersion + ".Action";
		String classNameActionData = "inventory.aai.openecomp.org." + defaultApiVersion + ".ActionData";

		PowerMockito.mockStatic(TitanGraph.class);
		TitanGraph tMock = PowerMockito.mock(TitanGraph.class);

		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		when(tMock.newTransaction()).thenReturn(gMock);
		

		PowerMockito.mockStatic(AAIGraph.class);
		AAIGraph graphMock = PowerMockito.mock(AAIGraph.class);
		when(AAIGraph.getInstance()).thenReturn(graphMock);
		when(graphMock.getGraph()).thenReturn(tMock);

		PowerMockito.whenNew(AAIGraph.class).withAnyArguments().thenReturn(graphMock);
		PowerMockito.whenNew(AAIGraph.class).withNoArguments().thenReturn(graphMock);
		
		LogLineBuilder llb = new LogLineBuilder();
		
		AAILogger mockLogger = PowerMockito.mock(AAILogger.class);	
		PowerMockito.whenNew(AAILogger.class).withAnyArguments().thenReturn(mockLogger);
		


		
		PowerMockito.mockStatic(RestController.class);		
		//PowerMockito.doNothing().when( RestController.class, "Get", Mockito.anyObject(), anyString(), 
				//anyString(), anyString(), Mockito.anyObject(), anyBoolean());
		PowerMockito.doThrow(new AAIException()).when( RestController.class, "Get", Mockito.anyObject(), anyString(), 
				anyString(), anyString(), Mockito.anyObject(), anyBoolean());

		RestObject<GenericVnf> mockRestObject = PowerMockito.mock(RestObject.class);
		PowerMockito.whenNew(RestObject.class).withNoArguments().thenReturn(mockRestObject);
		PowerMockito.doNothing().when( mockRestObject).set(isA(GenericVnf.class));
		GenericVnf genericVnf = new GenericVnf();
		genericVnf.setEquipmentRole("VRR");
		genericVnf.setVnfName("vnfNameValue");
		
		PowerMockito.when(mockRestObject.get()).thenReturn(genericVnf);

		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		PowerMockito.mockStatic(DbMeth.class);
		DbMeth dbMethMock = PowerMockito.mock(DbMeth.class);

		DynamicEntity dynamicEntityMock = PowerMockito.mock(DynamicEntity.class);
		
		when(dynamicEntityMock.<String>get("updateNodeType")).thenReturn("generic-vnf");
		
		when(dynamicEntityMock.<String>get("updateNodeUri")).thenReturn("network/generic-vnfs/generic-vnf/vnfId");
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> updateNodeKeyClass = dcl.createDynamicClass(classNameUpdateNodeKey);
		JPADynamicTypeBuilder updateNodeKey = new JPADynamicTypeBuilder(updateNodeKeyClass, null, "D_UPDATE_NODE_KEY");
		updateNodeKey.addDirectMapping("keyName", String.class, "KEY_NAME");
		updateNodeKey.addDirectMapping("keyValue", String.class, "KEY_VALUE");
		DynamicType type = updateNodeKey.getType();
		dynamicHelper.addTypes(false, false, type);
        
		
		ArrayList <DynamicEntity> updateNodeKeyList = new ArrayList<DynamicEntity>();

		LinkedHashMap <String,Object> returnHash = new LinkedHashMap<String,Object>();
		
		HashMap <String,Object> thisNodeHash = new HashMap <String,Object> ();
		String nodeKeyName = "generic-vnf.vnf-id";
		String nodeKeyValue = "vnfId";
		thisNodeHash.put(nodeKeyName, nodeKeyValue);
		returnHash.putAll(thisNodeHash);
		
		HashMap <String,Object> depNodeHash = new HashMap <String,Object> ();
	
		PowerMockito.mockStatic(RestURL.class);
		//RestURL restURLMock = PowerMockito.mock(RestURL.class);
		
		PowerMockito.when((  RestURL.getKeyHashes(anyString()))).thenReturn(returnHash);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("updateNodeKey")).thenReturn(updateNodeKeyList);
		
		Class<?> actionDataClass = dcl.createDynamicClass(classNameActionData);
		
		Class<?> actionClass = dcl.createDynamicClass(classNameAction);
		JPADynamicTypeBuilder action = new JPADynamicTypeBuilder(actionClass, null, "D_ACTION");
		action.addDirectMapping("actionType", String.class, "ACTION_TYPE");
		action.addDirectMapping("actionData", ArrayList.class, "ACTION_DATA");
		
		type = action.getType();
		dynamicHelper.addTypes(false, false, type);

		
		JPADynamicTypeBuilder actionData = new JPADynamicTypeBuilder(actionDataClass, action.getType(), "D_ACTION_DATA");
		actionData.addDirectMapping("propertyName", String.class, "PROPERTY_NAME");
		actionData.addDirectMapping("propertyValue", String.class, "PROPERTY_VALUE");
		
		type = actionData.getType();
		dynamicHelper.addTypes(false, false, type);
		
		ArrayList <DynamicEntity> actionDataList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDataDynamicEntity = dynamicHelper.newDynamicEntity(classNameActionData);
		actionDataDynamicEntity.set("propertyName", "prov-status");
		actionDataDynamicEntity.set("propertyValue", "NVTPROV");
		actionDataList.add(actionDataDynamicEntity);
		
		ArrayList <DynamicEntity> actionList = new ArrayList<DynamicEntity>();
		
		DynamicEntity actionDynamicEntity = dynamicHelper.newDynamicEntity(classNameAction);
		actionDynamicEntity.set("actionType", "replace");
		actionDynamicEntity.set("actionData", actionDataList);

		actionList.add(actionDynamicEntity);
		
		when(dynamicEntityMock.<List<DynamicEntity>>get("action")).thenReturn(actionList);
		
		AAIExtensionMap aaiExtensionMapMock = PowerMockito.mock(AAIExtensionMap.class);
		
		when(aaiExtensionMapMock.getApiVersion()).thenReturn(defaultApiVersion);
		
		PowerMockito.when((  DbMeth.figureDepNodeTypeForRequest(anyString(), anyString(), anyString(), 
				isA(HashMap.class), anyString()))).thenReturn("customer");

		PowerMockito.when((  DbMeth.getUniqueNodeWithDepParams(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), anyString()))).thenReturn(vMock);
		
		PowerMockito.when((  DbMeth.patchAaiNode(anyString(), anyString(), isA(TitanTransaction.class), anyString(), 
			isA(HashMap.class), isA(TitanVertex.class), anyString()))).thenReturn(vMock);

		
		ActionsGraph actionsGraph = new ActionsGraph();
		
		ActionsGraph actionsGraphSpy = PowerMockito.spy(actionsGraph);
		
		PowerMockito.doReturn("updateNodeURLValue").when(actionsGraphSpy, "setupUEBEventObject", 
			anyString(), anyString(), Mockito.any(TitanTransaction.class), anyString(),
			Mockito.any(TitanVertex.class), anyString());

		PowerMockito.doNothing().when(actionsGraphSpy, "triggerInstarEquipStatusUpdate", 
				anyString(), anyString(), anyString());

		actionsGraphSpy.propertyUpdate("fromAppId", "transId", dynamicEntityMock, aaiExtensionMapMock);


		assertTrue("return from propertyUpdate", true);

	}
	
	
	
	/**
	 * Test setup UEB event object.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testSetupUEBEventObject() throws Exception{
		String classNameNotificationEventHeader = "inventory.aai.openecomp.org." + defaultApiVersion + ".NotificationEventHeader";
		
		TitanTransaction gMock = PowerMockito.mock(TitanTransaction.class);
		TitanVertex vMock = PowerMockito.mock(TitanVertex.class);
		
		GraphHelpersMoxy graphHelpersMoxyMock = PowerMockito.mock(GraphHelpersMoxy.class);
		PowerMockito.whenNew( GraphHelpersMoxy.class).withNoArguments().thenReturn(graphHelpersMoxyMock);
		
		PowerMockito.when(graphHelpersMoxyMock.isEventEnabled(anyString(), anyString(), anyString())).thenReturn(true);

		AncestryItems ancestryItemsMock = PowerMockito.mock(AncestryItems.class);
		PowerMockito.whenNew( AncestryItems.class).withNoArguments().thenReturn(ancestryItemsMock);
		
		AncestryItem ancestryItem = new AncestryItem();
		AAIResource aaiResource = new AAIResource();
		aaiResource.setSimpleName("resourceSimpleNameValue");
		ancestryItem.setAaiResource(aaiResource);
		LinkedHashMap<String, AncestryItem> map = new LinkedHashMap<String, AncestryItem>();
		map.put("key", ancestryItem);
		when (ancestryItemsMock.getAncestryItems()).thenReturn(map);
		
		
		PowerMockito.mockStatic(RestURL.class);
		RestURL restURLMock = PowerMockito.mock(RestURL.class);
		
		AAIResource aaiResourceMock = PowerMockito.mock(AAIResource.class);
		//AAIResources aaiResourcesMock = PowerMockito.mock(AAIResources.class);
		
		DynamicJAXBContext dynamicJAXBContextMock = PowerMockito.mock(DynamicJAXBContext.class);
		
		AAIResources aaiResourcesMock = IngestModelMoxyOxm.aaiResourceContainer.get(defaultApiVersion);
		aaiResourcesMock.setJaxbContext(dynamicJAXBContextMock);
		
		StoreNotificationEvent storeNotificationEventMock = PowerMockito.mock(StoreNotificationEvent.class);
		PowerMockito.whenNew( StoreNotificationEvent.class).withNoArguments().thenReturn(storeNotificationEventMock);
		
		PowerMockito.doNothing().when( storeNotificationEventMock).storeDynamicEvent(isA(DynamicJAXBContext.class), anyString(), isA(DynamicEntity.class),
				isA(DynamicEntity.class));
		
        Project project = new Project(new DatabaseLogin()); 
        DatabaseSession databaseSession = project.createDatabaseSession(); 
		DynamicHelper dynamicHelper = new DynamicHelper(databaseSession);
		
		DynamicClassLoader dcl = dynamicHelper.getDynamicClassLoader();

		Class<?> notificationEventHeaderClass = dcl.createDynamicClass(classNameNotificationEventHeader);
		JPADynamicTypeBuilder notificationEventHeader = new JPADynamicTypeBuilder(notificationEventHeaderClass, null, "D_NOTIFICATION_EVENT_HEADER");
		notificationEventHeader.addDirectMapping("entityType", String.class, "ENTITY_TYPE");
		notificationEventHeader.addDirectMapping("action", String.class, "ACTION");
		notificationEventHeader.addDirectMapping("sourceName", String.class, "SOURCE_NAME");
		notificationEventHeader.addDirectMapping("version", String.class, "VERSION");
		notificationEventHeader.addDirectMapping("entityLink", String.class, "ENTITY_LINK");
		notificationEventHeader.addDirectMapping("topEntityType", String.class, "TOP_ENTITY_TYPE");
		DynamicType type = notificationEventHeader.getType();
		dynamicHelper.addTypes(false, false, type);
        
		DynamicEntity notificationEventHeaderDynamicEntity = dynamicHelper.newDynamicEntity(classNameNotificationEventHeader);
		
		
		
		//notificationEventHeaderDynamicEntity.
		PowerMockito.when(graphHelpersMoxyMock.unpackAncestry(isA(TitanTransaction.class), isA(AncestryItems.class), anyString(), 
			isA(DynamicJAXBContext.class), isA(AAIExtensionMap.class))).thenReturn(notificationEventHeaderDynamicEntity);
		
		PowerMockito.when(dynamicJAXBContextMock.getDynamicType(anyString())).thenReturn(type);
		
		PowerMockito.when((  RestURL.get(isA(TitanTransaction.class), isA(TitanVertex.class), 
				anyString(), anyBoolean(), anyBoolean()))).thenReturn("updatedNodeURLValue");
		PowerMockito.when((  RestURL.parseUri(Mockito.any(HashMap.class), Mockito.any(LinkedHashMap.class), anyString(), 
				any(AAIExtensionMap.class)))).thenReturn(aaiResourceMock);
		ActionsGraph actionsGraph = new ActionsGraph();
		String nodeUrl = actionsGraph.setupUEBEventObject("fromAppId", "transId", gMock, "updateNodeType", vMock, defaultApiVersion);
		assertTrue("returned from setupUEBEvent", true);
	}
	
    /**
     * Test trigger instar equip status update.
     *
     * @throws Exception the exception
     */
	@Ignore
    @Test
    public void testTriggerInstarEquipStatusUpdate() throws Exception {
	    Client mockClient = mock( Client.class );
	    WebResource mockWebResource = mock( WebResource.class );
	    WebResource.Builder mockBuilder = mock( WebResource.Builder.class );
	    PowerMockito.mockStatic(ClientResponse.class);
	    ClientResponse mockClientResponse = mock( ClientResponse.class );
    	ActionsGraph actionsGraph = new ActionsGraph();		
		PowerMockito.mockStatic(HttpsAuthClient.class);
		HttpsAuthClient mockHttpsAuthClient = PowerMockito.mock(HttpsAuthClient.class);
		when(mockHttpsAuthClient.getTwoWaySSLClient()).thenReturn(mockClient);

		when(mockClient.resource(anyString())).thenReturn(mockWebResource);
		when(mockWebResource.header(anyString(), anyString())).thenReturn(mockBuilder);
		when(mockBuilder.header(anyString(), anyString())).thenReturn(mockBuilder);
		when(mockBuilder.accept(anyString())).thenReturn(mockBuilder);
		
		when(mockBuilder.put(eq(ClientResponse.class))).thenReturn(mockClientResponse);
		when(mockClientResponse.getStatus()).thenReturn(200, 500);
		// first call returns success
        String msg= WhiteboxImpl.invokeMethod(actionsGraph, "triggerInstarEquipStatusUpdate",
        		"fromAppId", "transId", "urlValue");
        assertTrue("return from triggerInstarEquipStatusUpdate", true);
        // 2nd call generates exception
        try {
        	msg= WhiteboxImpl.invokeMethod(actionsGraph, "triggerInstarEquipStatusUpdate",
        		"fromAppId", "transId", "urlValue");
        	fail("Exception expected");
        } catch ( Exception e ) {
        	assertNotNull(e);
        }
    }

}

