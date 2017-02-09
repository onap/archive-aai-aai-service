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

package org.openecomp.aai.rest.search;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.ext.RuntimeDelegate;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.openecomp.aai.dbmap.SearchGraph;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.rest.search.SearchProvider;
import org.openecomp.aai.util.AAIApiVersion;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.springframework.mock.web.MockHttpServletRequest;



//@RunWith(PowerMockRunner.class)
//@PowerMockIgnore( {"javax.management.*"}) 
@PrepareForTest({AAIApiVersion.class, SearchGraph.class, SearchProvider.class, ResponseBuilder.class})
public class SearchProviderTest {
	//In VM argument, need to pass the following arguments: 
	//-noverify
	//
	@Rule
	public PowerMockRule rule = new PowerMockRule();
	
	
	private String fromAppId = "searchProviderId";
	private String transId = "transId";
	private String aaiApiVersion = "v8";
	private String genericQueryUri = "https://localhost:8443/aai/v8/search" + SearchProvider.GENERIC_QUERY
			+ "?key=service-instance.service-instance-id:test-service-instance-id"
			+ "&start-node-type=service-instance&include=generic-vnf&depth=0"; 
	private String nodeQueryUri = "https://localhost:8443/aai/v8/search" + SearchProvider.NODES_QUERY
			+ "?search-node-type=vserver&filter=vserver-name2:EXISTS:"
			+ "&edge-filter=pserver:EXISTS:hostname:test-hostname";

	
	/**
	 * Sets the up.
	 */
	@Before
	public void setUp() {
		RuntimeDelegate.setInstance(runtimeDelegate);
	}
	
	@Mock
	private RuntimeDelegate runtimeDelegate;
	
	class MockSearchProvider extends SearchProvider {
		@Override
		protected String getFromAppId(HttpHeaders headers, LogLine logline) throws AAIException { 
			return fromAppId;
		}
		@Override
		protected String getTransId(HttpHeaders headers, LogLine logline) throws AAIException { 
			return transId;
		}
		
		@Override
		protected String genDate() {
			return null;
		}
		@Override
		protected String genDate(LogLine logline) {
			return null;
		}
		@Override
		public void logTransaction(	String appId, String tId, String action, 
				String input, String rqstTm, String respTm, String request, Response response, LogLine logline) {
		}
	}
	
	class MockResponse extends Response {

		@Override
		public Object getEntity() {
			return null;
		}

		@Override
		public int getStatus() {
			return 200;
		}

		@Override
		public MultivaluedMap<String, Object> getMetadata() {
			return null;
		}
	}
	

	
	/**
	 * Test get generic query response.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testGetGenericQueryResponse() throws Exception {
		PowerMockito.mockStatic(AAIApiVersion.class);
		when(AAIApiVersion.get()).thenReturn(aaiApiVersion);

		SearchGraph mockSearchGraph = Mockito.mock(SearchGraph.class);
		PowerMockito.whenNew(SearchGraph.class).withNoArguments().thenReturn(mockSearchGraph);
		Response mockResp = new MockResponse();
		
		when(mockSearchGraph.runGenericQuery(Mockito.anyString(), Mockito.anyString(), 
				Mockito.anyString(), Mockito.anyListOf(String.class), 
				Mockito.anyListOf(String.class), Mockito.anyInt(), Mockito.any(AAIExtensionMap.class)))
				.thenReturn(mockResp);
		
		HttpHeaders headers = Mockito.mock(HttpHeaders.class);
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON_TYPE);
		when(headers.getAcceptableMediaTypes()).thenReturn(mediaTypeList);
		List<String> startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add("service-instance.service-instance-id:test-service-instance-id");
		List<String> includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("generic-vnf");
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRequestURI(genericQueryUri);
		MockSearchProvider mockSearchProvider = new MockSearchProvider();
		Response resp = mockSearchProvider.getGenericQueryResponse(headers, req, 
				"service-instance", startNodeKeyParams, includeNodeTypes,
				0);
		assertEquals(200, resp.getStatus());
	}


	/**
	 * Test get generic query response AAI exception.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testGetGenericQueryResponse_AAIException() throws Exception {
		PowerMockito.mockStatic(AAIApiVersion.class);
		when(AAIApiVersion.get()).thenReturn(aaiApiVersion);

		SearchGraph mockSearchGraph = Mockito.mock(SearchGraph.class);
		PowerMockito.whenNew(SearchGraph.class).withNoArguments().thenReturn(mockSearchGraph);
		
		Response response = Mockito.mock(Response.class);
		when(response.getStatus()).thenReturn(500);
		ResponseBuilder responseBuilder = Mockito.mock(ResponseBuilder.class);
		when(runtimeDelegate.createResponseBuilder()).thenReturn(responseBuilder);
		when((responseBuilder).status(Response.Status.INTERNAL_SERVER_ERROR)).thenReturn(responseBuilder);
		when((responseBuilder).entity(Mockito.anyObject())).thenReturn(responseBuilder);
		when((responseBuilder).build()).thenReturn(response);
		when(mockSearchGraph.runGenericQuery(Mockito.anyString(), Mockito.anyString(), 
				Mockito.anyString(), Mockito.anyListOf(String.class), 
				Mockito.anyListOf(String.class), Mockito.anyInt(), Mockito.any(AAIExtensionMap.class)))
				.thenThrow(new AAIException());
		
		HttpHeaders headers = Mockito.mock(HttpHeaders.class);
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON_TYPE);
		when(headers.getAcceptableMediaTypes()).thenReturn(mediaTypeList);
		List<String> startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add("service-instance.service-instance-id:test-service-instance-id");
		List<String> includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("generic-vnf");
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRequestURI(genericQueryUri);
		MockSearchProvider mockSearchProvider = new MockSearchProvider();
		Response resp = mockSearchProvider.getGenericQueryResponse(headers, req, 
				"service-instance", startNodeKeyParams, includeNodeTypes,
				0);
		assertEquals(500, resp.getStatus());
	}
	
	/**
	 * Test get generic query response exception.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testGetGenericQueryResponse_Exception() throws Exception {
		PowerMockito.mockStatic(AAIApiVersion.class);
		when(AAIApiVersion.get()).thenReturn(aaiApiVersion);

		SearchGraph mockSearchGraph = Mockito.mock(SearchGraph.class);
		PowerMockito.whenNew(SearchGraph.class).withNoArguments().thenReturn(mockSearchGraph);
		
		Response response = Mockito.mock(Response.class);
		when(response.getStatus()).thenReturn(500);
		ResponseBuilder responseBuilder = Mockito.mock(ResponseBuilder.class);
		when(runtimeDelegate.createResponseBuilder()).thenReturn(responseBuilder);
		when((responseBuilder).status(Response.Status.INTERNAL_SERVER_ERROR)).thenReturn(responseBuilder);
		when((responseBuilder).entity(Mockito.anyObject())).thenReturn(responseBuilder);
		when((responseBuilder).build()).thenReturn(response);
		PowerMockito.whenNew(SearchGraph.class).withNoArguments().thenThrow(new Exception());
		
		HttpHeaders headers = Mockito.mock(HttpHeaders.class);
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON_TYPE);
		when(headers.getAcceptableMediaTypes()).thenReturn(mediaTypeList);
		List<String> startNodeKeyParams = new ArrayList<String>();
		startNodeKeyParams.add("service-instance.service-instance-id:test-service-instance-id");
		List<String> includeNodeTypes = new ArrayList<String>();
		includeNodeTypes.add("generic-vnf");
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRequestURI(genericQueryUri);
		MockSearchProvider mockSearchProvider = new MockSearchProvider();
		Response resp = mockSearchProvider.getGenericQueryResponse(headers, req, 
				"service-instance", startNodeKeyParams, includeNodeTypes,
				0);
		assertEquals(500, resp.getStatus());
	}
	
	/**
	 * Test get nodes query response.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testGetNodesQueryResponse() throws Exception {
		PowerMockito.mockStatic(AAIApiVersion.class);
		when(AAIApiVersion.get()).thenReturn(aaiApiVersion);
		
		SearchGraph mockSearchGraph = Mockito.mock(SearchGraph.class);
		PowerMockito.whenNew(SearchGraph.class).withNoArguments().thenReturn(mockSearchGraph);
		Response mockResp = new MockResponse();
		when(mockSearchGraph.runNodesQuery(Mockito.anyString(), Mockito.anyString(), 
				Mockito.anyString(), Mockito.anyListOf(String.class), 
				Mockito.anyListOf(String.class), Mockito.any(AAIExtensionMap.class)))
				.thenReturn(mockResp);
		
		HttpHeaders headers = Mockito.mock(HttpHeaders.class);
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON_TYPE);
		when(headers.getAcceptableMediaTypes()).thenReturn(mediaTypeList);
		List<String> searchNodeTypes = new ArrayList<String>();
		searchNodeTypes.add("vserver");
		List<String> edgeFilterList = new ArrayList<String>();
		edgeFilterList.add("pserver:EXISTS");
		edgeFilterList.add("hostname:test-hostname");
		List<String> filterList = new ArrayList<String>();
		filterList.add("vserver-name2:EXISTS:");
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRequestURI(nodeQueryUri);
		MockSearchProvider mockSearchProvider = new MockSearchProvider();
		Response resp = mockSearchProvider.getNodesQueryResponse(headers, req , 
				"vserver", edgeFilterList,
				filterList);
		assertEquals(200, resp.getStatus());
	}
	
	/**
	 * Test get nodes query response AAI exception.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testGetNodesQueryResponse_AAIException() throws Exception {
		PowerMockito.mockStatic(AAIApiVersion.class);
		when(AAIApiVersion.get()).thenReturn(aaiApiVersion);

		SearchGraph mockSearchGraph = Mockito.mock(SearchGraph.class);
		PowerMockito.whenNew(SearchGraph.class).withNoArguments().thenReturn(mockSearchGraph);
		Response response = Mockito.mock(Response.class);
		when(response.getStatus()).thenReturn(500);
		ResponseBuilder responseBuilder = Mockito.mock(ResponseBuilder.class);
		when(runtimeDelegate.createResponseBuilder()).thenReturn(responseBuilder);
		when((responseBuilder).status(Response.Status.INTERNAL_SERVER_ERROR)).thenReturn(responseBuilder);
		when((responseBuilder).entity(Mockito.anyObject())).thenReturn(responseBuilder);
		when((responseBuilder).build()).thenReturn(response);
		when(mockSearchGraph.runNodesQuery(Mockito.anyString(), Mockito.anyString(), 
				Mockito.anyString(), Mockito.anyListOf(String.class), 
				Mockito.anyListOf(String.class), Mockito.any(AAIExtensionMap.class)))
				.thenThrow(new AAIException());
		
		HttpHeaders headers = Mockito.mock(HttpHeaders.class);
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON_TYPE);
		when(headers.getAcceptableMediaTypes()).thenReturn(mediaTypeList);
		List<String> searchNodeTypes = new ArrayList<String>();
		searchNodeTypes.add("vserver");
		List<String> edgeFilterList = new ArrayList<String>();
		edgeFilterList.add("pserver:EXISTS");
		edgeFilterList.add("hostname:test-hostname");
		List<String> filterList = new ArrayList<String>();
		filterList.add("vserver-name2:EXISTS:");
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRequestURI(nodeQueryUri);
		MockSearchProvider mockSearchProvider = new MockSearchProvider();
		Response resp = mockSearchProvider.getNodesQueryResponse(headers, req , 
				"vserver", edgeFilterList,
				filterList);
		assertEquals(500, resp.getStatus());
	}
	
	/**
	 * Test get nodes query response exception.
	 *
	 * @throws Exception the exception
	 */
	@Test
	public void testGetNodesQueryResponse_Exception() throws Exception {
		PowerMockito.mockStatic(AAIApiVersion.class);
		when(AAIApiVersion.get()).thenReturn(aaiApiVersion);
		
		SearchGraph mockSearchGraph = Mockito.mock(SearchGraph.class);
		PowerMockito.whenNew(SearchGraph.class).withNoArguments().thenReturn(mockSearchGraph);
		Response response = Mockito.mock(Response.class);
		when(response.getStatus()).thenReturn(500);
		ResponseBuilder responseBuilder = Mockito.mock(ResponseBuilder.class);
		when(runtimeDelegate.createResponseBuilder()).thenReturn(responseBuilder);
		when((responseBuilder).status(Response.Status.INTERNAL_SERVER_ERROR)).thenReturn(responseBuilder);
		when((responseBuilder).entity(Mockito.anyObject())).thenReturn(responseBuilder);
		when((responseBuilder).build()).thenReturn(response);
		PowerMockito.whenNew(SearchGraph.class).withNoArguments().thenThrow(new Exception());
		
		HttpHeaders headers = Mockito.mock(HttpHeaders.class);
		List<MediaType> mediaTypeList = new ArrayList<MediaType>();
		mediaTypeList.add(MediaType.APPLICATION_JSON_TYPE);
		when(headers.getAcceptableMediaTypes()).thenReturn(mediaTypeList);
		List<String> searchNodeTypes = new ArrayList<String>();
		searchNodeTypes.add("vserver");
		List<String> edgeFilterList = new ArrayList<String>();
		edgeFilterList.add("pserver:EXISTS");
		edgeFilterList.add("hostname:test-hostname");
		List<String> filterList = new ArrayList<String>();
		filterList.add("vserver-name2:EXISTS:");
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRequestURI(nodeQueryUri);
		MockSearchProvider mockSearchProvider = new MockSearchProvider();
		Response resp = mockSearchProvider.getNodesQueryResponse(headers, req , "vserver", 
				edgeFilterList, filterList);
		assertEquals(500, resp.getStatus());
	}
	
}
