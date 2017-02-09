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

package org.openecomp.aai.parsers.query;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.net.URI;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openecomp.aai.db.AAIProperties;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.parsers.query.QueryParser;
import org.openecomp.aai.serialization.engines.QueryStyle;
import org.openecomp.aai.serialization.engines.TitanDBEngine;
import org.openecomp.aai.serialization.engines.TransactionalGraphEngine;

import com.att.aft.dme2.internal.javaxwsrs.core.UriBuilder;

public class GraphTraversalTest {

	private TransactionalGraphEngine dbEngine = 
			new TitanDBEngine(QueryStyle.TRAVERSAL, 
				LoaderFactory.createLoaderForVersion(ModelType.MOXY, Version.v8, new LogLineBuilder("TEST", "TEST")),
				false);
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	/**
	 * Configure.
	 */
	@BeforeClass
	public static void configure() {
		System.setProperty("AJSC_HOME", ".");
		System.setProperty("BUNDLECONFIG_DIR", "bundleconfig-local");
	}
	
	/**
	 * Parent query.
	 *
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	@Ignore
	@Test
    public void parentQuery() throws UnsupportedEncodingException, AAIException {
		URI uri = UriBuilder.fromPath("cloud-infrastructure/complexes/complex/key1").build();
		
		QueryParser query = dbEngine.getQueryBuilder().createQueryFromURI(uri);
		
		GraphTraversal<Vertex, Vertex> expected = __.<Vertex>start().has("physical-location-id", "key1").has("aai-node-type", "complex");
		assertEquals(
				"gremlin query should be " + expected.toString(),
				expected.toString(),
				query.getQueryBuilder().getQuery().toString());
		assertEquals(
				"parent gremlin query should be equal to normal query",
				expected.toString(),
				query.getQueryBuilder().getParentQuery().toString());
		assertEquals(
				"result type should be complex",
				"complex",
				query.getResultType());
		assertEquals(
				"result type should be empty",
				"",
				query.getParentResultType());
		assertEquals("dependent",false, query.isDependent());

		
    }

	
	/**
	 * Gets the item affected by default cloud region.
	 *
	 * @return the item affected by default cloud region
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	@Ignore
	@Test
	public void getItemAffectedByDefaultCloudRegion() throws UnsupportedEncodingException, AAIException {
		URI uri = UriBuilder.fromPath("cloud-infrastructure/tenants/tenant/key1/vservers/vserver/key2/l-interfaces/l-interface/key3").build();
		QueryParser query = dbEngine.getQueryBuilder().createQueryFromURI(uri);
		GraphTraversal<Vertex, Vertex> expected = __.<Vertex>start()
				.has("cloud-owner", "CR").has("cloud-region-id", "AAI25")
				.has("aai-node-type", "cloud-region")
				.out("has")
				.has("tenant-id", "key1")
				.out("owns")
				.has("vserver-id", "key2")
				.out("hasLInterface")
				.has("interface-name", "key3");
		GraphTraversal<Vertex, Vertex> expectedParent = __.<Vertex>start()
				.has("cloud-owner", "CR").has("cloud-region-id", "AAI25")
				.has("aai-node-type", "cloud-region")
				.out("has")
				.has("tenant-id", "key1")
				.out("owns")
				.has("vserver-id", "key2");
		assertEquals(
				"gremlin query should be " + expected.toString(),
				expected.toString(),
				query.getQueryBuilder().getQuery().toString());
		assertEquals(
				"parent gremlin query should be equal the query for vserver",
				expectedParent.toString(),
				query.getQueryBuilder().getParentQuery().toString());
		assertEquals(
				"result type should be vserver",
				"vserver",
				query.getParentResultType());
		assertEquals(
				"result type should be l-interface",
				"l-interface",
				query.getResultType());
		assertEquals(
				"container type should be empty",
				"",
				query.getContainerType());
		assertEquals("dependent",true, query.isDependent());

	}
	
	/**
	 * Gets the via query param.
	 *
	 * @return the via query param
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	@Ignore
	@Test
	public void getViaQueryParam() throws UnsupportedEncodingException, AAIException {
		URI uri = UriBuilder.fromPath("cloud-infrastructure/tenants/tenant").build();
		MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
		map.putSingle("tenant-name", "Tenant1");
		QueryParser query = dbEngine.getQueryBuilder().createQueryFromURI(uri, map);
		GraphTraversal<Vertex, Vertex> expected = __.<Vertex>start()
				.has("cloud-owner", "CR").has("cloud-region-id", "AAI25")
				.has("aai-node-type", "cloud-region")
				.out("has")
				.has("tenant-name", "Tenant1");

		GraphTraversal<Vertex, Vertex> expectedParent = __.<Vertex>start()
				.has("cloud-owner", "CR").has("cloud-region-id", "AAI25")
				.has("aai-node-type", "cloud-region");
						
		assertEquals(
				"gremlin query should be " + expected.toString(),
				expected.toString(),
				query.getQueryBuilder().getQuery().toString());
		assertEquals(
				"parent gremlin query should be equal the query for cloud-region",
				expectedParent.toString(),
				query.getQueryBuilder().getParentQuery().toString());
		assertEquals(
				"result type should be cloud-region",
				"cloud-region",
				query.getParentResultType());
		assertEquals(
				"result type should be tenant",
				"tenant",
				query.getResultType());
		assertEquals(
				"container type should be empty",
				"",
				query.getContainerType());
		assertEquals("dependent",true, query.isDependent());

	}
	
	/**
	 * Gets the plural via query param.
	 *
	 * @return the plural via query param
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	@Test
	public void getPluralViaQueryParam() throws UnsupportedEncodingException, AAIException {
		URI uri = UriBuilder.fromPath("network/vnfcs").build();
		MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
		map.putSingle("prov-status", "up");
		QueryParser query = dbEngine.getQueryBuilder().createQueryFromURI(uri, map);
		GraphTraversal<Vertex, Vertex> expected = __.<Vertex>start()
				.has("aai-node-type", "vnfc")
				.has("prov-status", "up");

		GraphTraversal<Vertex, Vertex> expectedParent = __.<Vertex>start()
				.has("aai-node-type", "vnfc");
					
		assertEquals(
				"gremlin query should be " + expected.toString(),
				expected.toString(),
				query.getQueryBuilder().getQuery().toString());
		assertEquals(
				"parent",
				expectedParent.toString(),
				query.getQueryBuilder().getParentQuery().toString());
		assertEquals(
				"parent result type should be empty",
				"",
				query.getParentResultType());
		assertEquals(
				"result type should be vnfc",
				"vnfc",
				query.getResultType());
		assertEquals(
				"container type should be empty",
				"vnfcs",
				query.getContainerType());
		assertEquals("dependent",true, query.isDependent());

	}
	
	/**
	 * Gets the all query param naming exception.
	 *
	 * @return the all query param naming exception
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	@Ignore
	@Test
    public void getAllQueryParamNamingException() throws UnsupportedEncodingException, AAIException {
		URI uri = UriBuilder.fromPath("network/vces/vce/key1/port-groups/port-group/key2/cvlan-tags").build();
		MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
		map.putSingle("cvlan-tag", "333");
		QueryParser query = dbEngine.getQueryBuilder().createQueryFromURI(uri, map);
		
		GraphTraversal<Vertex, Vertex> expected = __.<Vertex>start()
				.has("vnf-id", "key1").has("aai-node-type", "vce")
				.out("hasPortGroup")
				.has("interface-id", "key2").out("hasCTag")
				.has("aai-node-type", "cvlan-tag")
				.has("cvlan-tag", 333);
		GraphTraversal<Vertex, Vertex> expectedParent = __.<Vertex>start()
				.has("vnf-id", "key1").has("aai-node-type", "vce")
				.out("hasPortGroup")
				.has("interface-id", "key2");
		assertEquals(
				"gremlin query should be " + expected.toString(),
				expected.toString(),
				query.getQueryBuilder().getQuery().toString());
		assertEquals(
				"parent gremlin query should be equal the query for port group",
				expectedParent.toString(),
				query.getQueryBuilder().getParentQuery().toString());
		assertEquals(
				"result type should be port-group",
				"port-group",
				query.getParentResultType());
		assertEquals(
				"result type should be cvlan-tag",
				"cvlan-tag",
				query.getResultType());
		assertEquals(
				"container type should be cvlan-tags",
				"cvlan-tags",
				query.getContainerType());
		assertEquals("dependent",true, query.isDependent());

		
    }
	
	/**
	 * Abstract type.
	 *
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	@Ignore
	@Test
    public void abstractType() throws UnsupportedEncodingException, AAIException {
		URI uri = UriBuilder.fromPath("vnf/key1").build();

		QueryParser query = dbEngine.getQueryBuilder().createQueryFromURI(uri);
		
		GraphTraversal<Vertex, Vertex> expected = __.<Vertex>start()
				.has("vnf-id", "key1").or(
						__.has(AAIProperties.NODE_TYPE, "vce"),
						__.has(AAIProperties.NODE_TYPE, "vpe"),
						__.has(AAIProperties.NODE_TYPE, "generic-vnf"));
			
		GraphTraversal<Vertex, Vertex> expectedParent = expected;
		assertEquals(
				"gremlin query should be " + expected.toString(),
				expected.toString(),
				query.getQueryBuilder().getQuery().toString());
		assertEquals(
				"parent gremlin query should be equal the query for port group",
				expectedParent.toString(),
				query.getQueryBuilder().getParentQuery().toString());
		assertEquals(
				"result type should be empty",
				"",
				query.getParentResultType());
		assertEquals(
				"result type should be vnf",
				"vnf",
				query.getResultType());
		
		assertEquals("dependent",false, query.isDependent());

		
    }
	
	/**
	 * Non parent abstract type.
	 *
	 * @throws UnsupportedEncodingException the unsupported encoding exception
	 * @throws AAIException the AAI exception
	 */
	@Test
    public void nonParentAbstractType() throws UnsupportedEncodingException, AAIException {
		URI uri = UriBuilder.fromPath("cloud-infrastructure/pservers/pserver/key2/vnf/key1").build();
		thrown.expect(AAIException.class);
		thrown.expectMessage(startsWith("AAI_3001"));
		QueryParser query = dbEngine.getQueryBuilder().createQueryFromURI(uri);
		

		
    }
}
