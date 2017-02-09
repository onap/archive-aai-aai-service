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

package org.openecomp.aai.schema.db;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.openecomp.aai.db.schema.AuditDoc;
import org.openecomp.aai.db.schema.AuditOXM;
import org.openecomp.aai.db.schema.Auditor;
import org.openecomp.aai.db.schema.AuditorFactory;
import org.openecomp.aai.introspection.Version;

import com.thinkaurelius.titan.core.TitanFactory;
import com.thinkaurelius.titan.core.TitanGraph;

@Ignore("not ready for testing")
public class AuditOXMTest {
	
	/**
	 * Before.
	 */
	@BeforeClass
	public static void before() {
		System.setProperty("AJSC_HOME", ".");
		System.setProperty("BUNDLECONFIG_DIR", "bundleconfig-local");
	
	}
	
	
	/**
	 * Gets the graph audit.
	 *
	 * @return the graph audit
	 * @throws JsonGenerationException the json generation exception
	 * @throws JsonMappingException the json mapping exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Test
	public void getGraphAudit() throws JsonGenerationException, JsonMappingException, IOException {
		TitanGraph graph = TitanFactory.open("bundleconfig-local/etc/appprops/aaiconfig.properties");
		Auditor a = AuditorFactory.getGraphAuditor(graph);
		AuditDoc doc = a.getAuditDoc();
		
		ObjectMapper mapper = new ObjectMapper();
		
		String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc);
		System.out.println(json);
		
	}
	
	/**
	 * Gets the audit.
	 *
	 * @return the audit
	 * @throws JsonGenerationException the json generation exception
	 * @throws JsonMappingException the json mapping exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Test
	public void getAudit() throws JsonGenerationException, JsonMappingException, IOException {
		AuditOXM oxm = new AuditOXM(Version.v8);
		
		AuditDoc doc = oxm.getAuditDoc();
		
		ObjectMapper mapper = new ObjectMapper();
		
		String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc);
		System.out.println(json);
	}
}
