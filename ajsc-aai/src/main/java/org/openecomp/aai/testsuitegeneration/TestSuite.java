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

package org.openecomp.aai.testsuitegeneration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.json.JSONException;
import org.openecomp.aai.db.AAIProperties;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorFactory;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.LoaderFactory;
import org.openecomp.aai.introspection.ModelType;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.testing.LocateEndPoints;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Multimap;

public class TestSuite {


	private final int minListSize;
	private final int maxListSize;
	private final Version apiVersion;
	private TestSuiteBuilder builder = null;
	protected final LogLineBuilder llBuilder = new LogLineBuilder();

	/**
	 * Instantiates a new test suite.
	 *
	 * @param builder the builder
	 */
	private TestSuite(TestSuiteBuilder builder) {
		
		this.maxListSize = builder.maxListSize;
		this.minListSize = builder.minListSize;
		this.apiVersion = builder.apiVersion;
		
		this.builder = builder;
		
	}
	
	/**
	 * Creates the.
	 *
	 * @param endpoints the endpoints
	 * @param relationships the relationships
	 * @throws JSONException the JSON exception
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ClassNotFoundException the class not found exception
	 * @throws NoSuchFieldException the no such field exception
	 * @throws InstantiationException the instantiation exception
	 * @throws JAXBException the JAXB exception
	 */
	public void create(File endpoints, File relationships) throws JSONException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, IOException, ClassNotFoundException, NoSuchFieldException, InstantiationException, JAXBException {

		// Load all endpoints in the system
		LocateEndPoints lep = new LocateEndPoints(this.apiVersion);
		
		// Create the endpoints and relationship test folders in case they don't exist
		//createRequiredDirectories(testCaseDirPath);
		
		// Create the test suite and JUnit test writers
		TestSuiteWriter endpointWriter = new TestSuiteWriter(endpoints, builder);
		//FitNesseJUnitWriter jUnitWriter = new FitNesseJUnitWriter(jUnitDirPath);
		
		// Iterate through all the parent paths
		for (String parentPath : lep.getParentPaths()) {
			
			// Create a parent object
			Introspector parentObject = lep.createChildForPath(parentPath, minListSize, maxListSize);
			
			// Write the parent test suites
			String endpointSuitePath = endpointWriter.createParentSuite(parentPath, parentObject, lep, null);
			//String relationshipSuitePath = relationshipWriter.createTestSuite(parentPath, parentObject, lep, null);
			//jUnitWriter.createJUnitClass("aai.fitnesse.suites", apiVersion, endpointSuitePath);
			
			// Iterate through all the children paths
			for (String childPath : lep.getChildPaths()) {
				String genericPath = childPath;
				if (childPath.contains(parentPath)) {
					
					// Create a child object
					Introspector obj = lep.createChildForPath(childPath, minListSize, maxListSize);
					String path = lep.populateChildPath(parentObject, obj, childPath);
					endpointWriter.addTestCase(endpointSuitePath, path, obj, genericPath, lep);

				}
			}
		}
		
		
		// Generate all relationship tests
		RelationshipWriter relationshipWriter = new RelationshipWriter(relationships, builder, LoaderFactory.createLoaderForVersion(ModelType.MOXY, apiVersion, llBuilder));
		
		// Iterate through all the relationships
		for(String key : builder.getEdgeMap().keys()) {
			
			String edgeMapProperties = builder.getEdgeMap().get(key).iterator().next();
			
			// Checking to see if this is a parent-child relationship, if so then skip
			if(edgeMapProperties.split(",")[3].equals("true")) {
				continue;
			}

			// Extract the class names
			String classNameA = key.substring(0, key.indexOf('|'));
			String classNameB = key.substring(key.indexOf('|') + 1);
			
			// Check if we need to skip this test
			if(!builder.apiVersion.equals(builder.currentApiVersion)) {
				if(classNameA.equals("site-pair-set") || classNameB.equals("site-pair-set")) {
					continue;
				}
			}
			
			// Prepare full class path
			
			Loader loader = LoaderFactory.createLoaderForVersion(ModelType.MOXY, apiVersion, llBuilder);
			
			// Create the objects
				
				// Generate the relationship test
				try {
					relationshipWriter.generateRelationshipTest(loader.introspectorFromName(classNameA), loader.introspectorFromName(classNameB), lep, edgeMapProperties, minListSize, maxListSize);
				} catch (Exception e) {
					System.out.println("Failed to generate relationship test for " + classNameA + "-" + classNameB + ".");
					e.printStackTrace();
				}
			
		}
	}
	
	/**
	 * Creates the required directories.
	 *
	 * @param root the root
	 */
	// Creates all the required directories in the system in case they don't already exist.
	private void createRequiredDirectories(String root) {
		File dir = new File(root + "/Endpoints");
		dir.mkdir();
		dir = new File(root + "/Relationships");
		dir.mkdir();
		dir = new File(root + "/Endpoints/" + apiVersion);
		dir.mkdir();
		dir = new File(root + "/Relationships/" + apiVersion);
		dir.mkdir();
		// TODO Generate suite content files for the root folders
	}
	
	/**
	 * Class exists.
	 *
	 * @param className the class name
	 * @return true, if successful
	 */
	private boolean classExists(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
	
	public static class TestSuiteBuilder {
		
		private String pojoPackage = "";
		private String restPackage = "";
		private String dbmodelPackage = "";
		private int minListSize = 1;
		private int maxListSize = 2;
		private String topLevelPath = "";
		private List<String> children = null;
		private Version apiVersion = null;
		private Version currentApiVersion = AAIProperties.LATEST;
		
		private Multimap<String, String> DeleteScope;
		private Multimap<String, String> EdgeMap;
		
		
		/**
		 * Instantiates a new test suite builder.
		 */
		public TestSuiteBuilder()  {
			
		}
		
		/**
		 * Gets the top level path.
		 *
		 * @return the top level path
		 */
		public String getTopLevelPath() {
			return topLevelPath;
		}
		
		/**
		 * Sets the top level path.
		 *
		 * @param topLevelPath the top level path
		 * @return the test suite builder
		 */
		public TestSuiteBuilder setTopLevelPath(String topLevelPath) {
			this.topLevelPath = topLevelPath;
			return this;
		}
		
		/**
		 * Gets the children.
		 *
		 * @return the children
		 */
		public List<String> getChildren() {
			return children;
		}
		
		/**
		 * Sets the children.
		 *
		 * @param children the children
		 * @return the test suite builder
		 */
		public TestSuiteBuilder setChildren(List<String> children) {
			this.children = children;
			return this;
		}
		
		/**
		 * Gets the pojo package.
		 *
		 * @return the pojo package
		 */
		public String getPojoPackage() {
			return pojoPackage;
		}
		
		/**
		 * Gets the db model package.
		 *
		 * @return the db model package
		 */
		public String getDbModelPackage() {
			return dbmodelPackage;
		}
		
		/**
		 * Sets the pojo package.
		 *
		 * @param pojoPackage the pojo package
		 * @return the test suite builder
		 */
		public TestSuiteBuilder setPojoPackage(String pojoPackage) {
			this.pojoPackage = pojoPackage;
			return this;
		}
		
		/**
		 * Gets the min list size.
		 *
		 * @return the min list size
		 */
		public int getMinListSize() {
			return minListSize;
		}
		
		/**
		 * Sets the min list size.
		 *
		 * @param minListSize the min list size
		 * @return the test suite builder
		 */
		public TestSuiteBuilder setMinListSize(int minListSize) {
			this.minListSize = minListSize;
			return this;
		}
		
		/**
		 * Gets the max list size.
		 *
		 * @return the max list size
		 */
		public int getMaxListSize() {
			return maxListSize;
		}
		
		/**
		 * Sets the max list size.
		 *
		 * @param maxListSize the max list size
		 * @return the test suite builder
		 */
		public TestSuiteBuilder setMaxListSize(int maxListSize) {
			this.maxListSize = maxListSize;
			return this;
		}
		
		/**
		 * Gets the rest package.
		 *
		 * @return the rest package
		 */
		public String getRestPackage() {
			return restPackage;
		}
		
		/**
		 * Sets the rest package.
		 *
		 * @param restPackage the rest package
		 * @return the test suite builder
		 */
		public TestSuiteBuilder setRestPackage(String restPackage) {
			this.restPackage = restPackage;
			return this;
		}
		
		/**
		 * Gets the api version.
		 *
		 * @return the api version
		 */
		public Version getApiVersion() {
			return apiVersion;
		}
		
		/**
		 * Sets the api version.
		 *
		 * @param apiVersion the api version
		 * @return the test suite builder
		 */
		public TestSuiteBuilder setApiVersion(Version apiVersion) {
			this.apiVersion = apiVersion;
			return this;
		}
		
		/**
		 * Sets the db model package.
		 *
		 * @param dbmodelPackage the dbmodel package
		 * @return the test suite builder
		 */
		public TestSuiteBuilder setDbModelPackage(String dbmodelPackage) {
			this.dbmodelPackage = dbmodelPackage;
			return this;
		}
		
		/**
		 * Gets the current api version.
		 *
		 * @return the current api version
		 */
		public Version getCurrentApiVersion() {
			return currentApiVersion;
		}
		
		/**
		 * Sets the current api version.
		 *
		 * @param currentApiVersion the new current api version
		 */
		public void setCurrentApiVersion(Version currentApiVersion) {
			this.currentApiVersion = currentApiVersion;
		}
		

		/**
		 * Gets the delete scope.
		 *
		 * @return the delete scope
		 */
		public Multimap<String, String> getDeleteScope() {
			return DeleteScope;
		}
		
		/**
		 * Gets the edge map.
		 *
		 * @return the edge map
		 */
		public Multimap<String, String> getEdgeMap() {
			return EdgeMap;
		}

		
		/**
		 * Builds the.
		 *
		 * @return the test suite
		 * @throws IllegalArgumentException the illegal argument exception
		 * @throws IllegalAccessException the illegal access exception
		 * @throws NoSuchFieldException the no such field exception
		 * @throws SecurityException the security exception
		 * @throws ClassNotFoundException the class not found exception
		 */
		@SuppressWarnings("unchecked")
		public TestSuite build() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException, ClassNotFoundException {
			Class<?> dbEdgeRules = Class.forName(dbmodelPackage + ".DbEdgeRules");
		
			DeleteScope = (Multimap<String, String>)dbEdgeRules.getDeclaredField("DefaultDeleteScope").get(null);
			EdgeMap = (Multimap<String, String>)dbEdgeRules.getDeclaredField("EdgeRules").get(null);

			return new TestSuite(this);
			
		}
		
	}
	
}
