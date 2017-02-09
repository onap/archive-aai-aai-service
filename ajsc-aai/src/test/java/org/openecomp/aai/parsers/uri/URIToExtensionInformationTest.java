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

package org.openecomp.aai.parsers.uri;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.net.URI;

import javax.xml.bind.JAXBException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.parsers.uri.URIToExtensionInformation;
import org.openecomp.aai.rest.HttpMethod;

import com.att.aft.dme2.internal.javaxwsrs.core.UriBuilder;

public class URIToExtensionInformationTest {

	private Loader v8Loader = LoaderFactory.createLoaderForVersion(ModelType.MOXY, Version.v8, new LogLineBuilder("TEST", "TEST"));
	
	/**
	 * Configure.
	 */
	@BeforeClass
	public static void configure() {
		System.setProperty("AJSC_HOME", ".");
		System.setProperty("BUNDLECONFIG_DIR", "bundleconfig-local");
	}
	
	
	/**
	 * Test spec.
	 *
	 * @param info the info
	 * @param httpMethod the http method
	 * @param namespace the namespace
	 * @param preMethodName the pre method name
	 * @param postMethodName the post method name
	 * @param topLevel the top level
	 */
	private void testSpec(URIToExtensionInformation info, HttpMethod httpMethod, String namespace, String preMethodName, String postMethodName, String topLevel) {
		

		String namespaceResult = info.getNamespace();
		String methodNameResult = info.getMethodName(httpMethod, true);
		
		assertEquals("namespace", namespace, namespaceResult);
		assertEquals("preprocess method name", preMethodName, methodNameResult);
		methodNameResult = info.getMethodName(httpMethod, false);

		assertEquals("postprocess method name", postMethodName, methodNameResult);

		String topLevelResult = info.getTopObject();
		
		assertEquals("topLevel", topLevel, topLevelResult);
	}
	
	
}
