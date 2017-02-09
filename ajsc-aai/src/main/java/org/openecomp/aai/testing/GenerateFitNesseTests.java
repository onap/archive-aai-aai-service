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

package org.openecomp.aai.testing;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.openecomp.aai.db.AAIProperties;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.testsuitegeneration.TestSuite;

public class GenerateFitNesseTests {

	private static final String currentEndpointsLocation = "../../automated-testing/FitNesseRoot/AAI/1702/Endpoints/";
	private static final String currentRelationshipsLocation = "../../automated-testing/FitNesseRoot/AAI/1702/Relationships/";
	private static final String regressionEndpointsLocation = "../../automated-testing/FitNesseRoot/AAI/Regression/Endpoints/";
	private static final String regressionRelationshipsLocation = "../../automated-testing/FitNesseRoot/AAI/Regression/Relationships/";
	
	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws JSONException the JSON exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ClassNotFoundException the class not found exception
	 * @throws NoSuchFieldException the no such field exception
	 * @throws JAXBException the JAXB exception
	 */
	public static void main(String[] args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, JSONException, NoSuchMethodException, SecurityException, IOException, ClassNotFoundException, NoSuchFieldException, JAXBException {
		
		/* set up properties */
		System.setProperty("AJSC_HOME", ".");
		System.setProperty("BUNDLECONFIG_DIR", "bundleconfig-local");

		System.out.println("Starting FitNesse generation...");
		
		TestSuite.TestSuiteBuilder builder = new TestSuite.TestSuiteBuilder();
		builder
			.setDbModelPackage("org.openecomp.aai.dbmodel")
			.setApiVersion(AAIProperties.LATEST);
		
		TestSuite suite = builder.build();
		/*
		String root = System.getenv(testCaseRootVar);
		
		if (root == null) {
			throw new IOException("No environment variable " + testCaseRootVar + " found. Please set it before continuing.");
		}
		root = System.getenv(jUnitRootVar);
		if (root == null) {
			throw new IOException("No environment variable " + jUnitRootVar + " found. Please set it before continuing.");
		}
		*/

		File endpoints = new File(currentEndpointsLocation + AAIProperties.LATEST);
		File relationships = new File(currentRelationshipsLocation + AAIProperties.LATEST);
		FileUtils.deleteDirectory(endpoints);
		FileUtils.deleteDirectory(relationships);
		suite.create(endpoints, relationships);
		System.out.println("Generating current version suite.");
		
		for (int i = 8; i > 1; i--) {
			System.out.println("Generating v" + i + " suite.");

			createVersionedTestSuite(Version.valueOf("v" + i));
		}
		
		System.out.println("Finished.");
		
		
	}
	
	/**
	 * Creates the versioned test suite.
	 *
	 * @param version the version
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 * @throws JSONException the JSON exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ClassNotFoundException the class not found exception
	 * @throws NoSuchFieldException the no such field exception
	 * @throws InstantiationException the instantiation exception
	 * @throws JAXBException the JAXB exception
	 */
	private static void createVersionedTestSuite(Version version) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, JSONException, IOException, ClassNotFoundException, NoSuchFieldException, InstantiationException, JAXBException {
		
		TestSuite.TestSuiteBuilder builder = new TestSuite.TestSuiteBuilder();
		File endpoints = new File(regressionEndpointsLocation + version);
		File relationships = new File(regressionRelationshipsLocation + version);
		

		builder
			.setDbModelPackage("org.openecomp.aai.dbmodel." + version + ".gen")
			.setApiVersion(version);
		
		TestSuite suite = builder.build();
		suite.create(endpoints, relationships);
	}

	
}
