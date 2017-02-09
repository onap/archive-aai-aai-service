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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.eclipse.persistence.exceptions.DynamicException;
import org.json.JSONException;
import org.openecomp.aai.introspection.Introspector;
import org.openecomp.aai.introspection.IntrospectorFactory;
import org.openecomp.aai.introspection.Loader;
import org.openecomp.aai.introspection.Version;
import org.openecomp.aai.logging.LogLineBuilder;
import org.openecomp.aai.testing.LocateEndPoints;
import org.openecomp.aai.testsuitegeneration.TestSuite.TestSuiteBuilder;

import com.att.aft.dme2.internal.google.common.base.CaseFormat;

public class RelationshipWriter extends AbstractWriter {

	protected final String RELATIONSHIP_CONTENT = "relationship_content_txt.ftl";
	protected final String RELATIONSHIP_SINGLE_CONTENT = "relationship_single_content_txt.ftl";
	protected final String RELATIONSHIP_SINGLE_CONTENT_REVERSED = "relationship_single_reversed_content_txt.ftl";
	protected final LogLineBuilder llBuilder = new LogLineBuilder();

	// Hash map used for storing parent objects for re-use
	//HashMap<String, Introspector> parentMap = new HashMap<>();
	Loader loader = null;
	
	/**
	 * Instantiates a new relationship writer.
	 *
	 * @param file the file
	 * @param builder the builder
	 * @param loader the loader
	 */
	// Constructor
	public RelationshipWriter(File file, TestSuiteBuilder builder, Loader loader) {
		
		// Just call the super constructor
		super(file, builder);
		
		this.loader = loader;

	}

	
	/**
	 * Generate relationship test.
	 *
	 * @param objA the obj A
	 * @param objB the obj B
	 * @param lep the lep
	 * @param edgeMapProperties the edge map properties
	 * @param minListSize the min list size
	 * @param maxListSize the max list size
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws NoSuchMethodException the no such method exception
	 * @throws SecurityException the security exception
	 * @throws Exception the exception
	 */
	public void generateRelationshipTest(Introspector objA, Introspector objB, LocateEndPoints lep, String edgeMapProperties, int minListSize, int maxListSize) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, Exception {
		if(objA.getDbName().equals("ipaddress") || objB.getDbName().equals("ipaddress")) return;
				
		// Get the unpopulated feature URls
		String featureUrlA = this.getUnpopulatedFeatureUrl(objA, lep);
		String featureUrlB = this.getUnpopulatedFeatureUrl(objB, lep);
		String inputA = objA.getDbName();
		String inputB = objB.getDbName();
		String classA = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, inputA);
		String classB = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, inputB);
		// Prepare variable map
		Map<String, Object> variableMap = new HashMap<>();
		variableMap.put("type", "test");
		
		
		
		////////////////////////////////
		//      Process object A      //
		////////////////////////////////	
		cleanUpPage = ".AAI.Tests.GenericTests.CleanUp";
		if(hasDependency(featureUrlA)) {
			objA = createParentObject(featureUrlA, lep);
			
			// Get the parent name lower-hyphen format
			String hyphenatedParentName = this.getHyphenatedParentName(featureUrlA);
			Introspector parentObj =loader.introspectorFromName(hyphenatedParentName);
			String topFeatureUrl = this.getUnpopulatedFeatureUrl(parentObj, lep);
			String populatedUrl = replaceUsernamePlaceholders((String)lep.populatePath(objA, topFeatureUrl).get(0));
			variableMap.put("topFeatureUrlA", populatedUrl);
			
			boolean hasChildren = this.hasChildren(parentObj);

			if(hasChildren && DeleteScope.get(hyphenatedParentName).contains("THIS_NODE_ONLY")) {
				try {
					cleanUpPage = generateRecursiveCleanUp(populatedUrl, outputPath, objA, lep, classA + "To" + classB + "A", "${topFeatureURL_A}");

				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | NoSuchMethodException
						| SecurityException e) {
					e.printStackTrace();
				} 
			}
			
		} else {
			objA = lep.createChildFromClassName(objA.getName(), 1, 1, true);
			String populatedUrl = replaceUsernamePlaceholders((String)lep.populatePath(objA, featureUrlA).get(0));
			variableMap.put("topFeatureUrlA", populatedUrl);
			boolean hasChildren = this.hasChildren(objA);

			if(hasChildren && DeleteScope.get(objA.getDbName()).contains("THIS_NODE_ONLY")) {
				try {
					cleanUpPage = generateRecursiveCleanUp(populatedUrl, outputPath, objA, lep, classA + "To" + classB + "A", "${topFeatureURL_A}");

				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | NoSuchMethodException
						| SecurityException e) {
					e.printStackTrace();
				} 		
			}
		}
		
		// Create the relationship object
		Introspector relationshipA = lep.populateRelationship(objA, inputA, featureUrlA);
		relationshipA.setValue("related-link", "-!${address}${relationshipURLA}!-/");
		// Put the values to the map
		List<Object> rubbishTuple = lep.populatePath(objA, featureUrlA);
		String populatedPathA = (String)rubbishTuple.get(0);
		Introspector childA = (Introspector)rubbishTuple.get(1);
		variableMap.put("deletePageA", cleanUpPage);
		variableMap.put("objectBodyA", replaceUsernamePlaceholders(objA.marshal(true)));
		variableMap.put("featureUrlA", replaceUsernamePlaceholders(populatedPathA));
		variableMap.put("relationshipURLA", replaceUsernamePlaceholders(populatedPathA));
		variableMap.put("objectRelationshipA", replaceUsernamePlaceholders(relationshipA.marshal(true)));
		
		
		
        ////////////////////////////////
		//      Process object B      //
		////////////////////////////////
		cleanUpPage = ".AAI.Tests.GenericTests.CleanUp";
		if(hasDependency(featureUrlB)) {
			objB = createParentObject(featureUrlB, lep);
			
			// Get the parent name lower-hyphen format
			String hyphenatedParentName = this.getHyphenatedParentName(featureUrlB);
			Introspector parentObj = IntrospectorFactory.newInstance(objB.getModelType(), loader.objectFromName(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, hyphenatedParentName)), llBuilder);
			String topFeatureUrl = this.getUnpopulatedFeatureUrl(parentObj, lep);
			String populatedUrl = replaceUsernamePlaceholders((String)lep.populatePath(objB, topFeatureUrl).get(0));

			variableMap.put("topFeatureUrlB", populatedUrl);
			boolean hasChildren = this.hasChildren(parentObj);

			if(hasChildren && DeleteScope.get(hyphenatedParentName).contains("THIS_NODE_ONLY")) {
				try {
					cleanUpPage = generateRecursiveCleanUp(populatedUrl, outputPath, objB, lep, classA + "To" + classB + "B", "${topFeatureURL_B}");

				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | NoSuchMethodException
						| SecurityException e) {
					e.printStackTrace();
				} 
			}
			
		} else {
			objB = lep.createChildFromClassName(objB.getName(), 1, 1, true);
			String populatedUrl = replaceUsernamePlaceholders((String)lep.populatePath(objB, featureUrlB).get(0));
			variableMap.put("topFeatureUrlB", populatedUrl);
			boolean hasChildren = this.hasChildren(objB);

			if(hasChildren && DeleteScope.get(objB.getDbName()).contains("THIS_NODE_ONLY")) {
				try {
					cleanUpPage = generateRecursiveCleanUp(populatedUrl, outputPath, objB, lep, classA + "To" + classB + "B", "${topFeatureURL_B}");

				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | NoSuchMethodException
						| SecurityException e) {
					e.printStackTrace();
				} 			}
		}
		
		// Create the relationship object
		Introspector relationshipB = lep.populateRelationship(objB, inputB, featureUrlB);
		relationshipB.setValue("related-link", "-!${address}${relationshipURLB}!-/");

		if(hasDelTarget(edgeMapProperties)) {
			cleanUpPage = ".AAI.Tests.GenericTests.VerifyDeletion";
		}
		rubbishTuple = lep.populatePath(objB, featureUrlB);
		String populatedPathB = (String)rubbishTuple.get(0);
		Introspector childB = (Introspector)rubbishTuple.get(1);
		variableMap.put("deletePageB", cleanUpPage);
		variableMap.put("objectBodyB", replaceUsernamePlaceholders(objB.marshal(true)));
		variableMap.put("featureUrlB", replaceUsernamePlaceholders(populatedPathB));
		variableMap.put("relationshipURLB", replaceUsernamePlaceholders(populatedPathB));
		variableMap.put("objectRelationshipB", replaceUsernamePlaceholders(relationshipB.marshal(true)));
		
		
		// Now, we have all the objects we need and need to generate the files
		File dir = new File(outputPath + "/" + classA + "To" + classB + "RelationshipTest");
		dir.mkdir();
		

		//////////////////////////////////
		//      Special processing      //
		//////////////////////////////////
		Object relationshipList = null;
		relationshipList = this.loader.objectFromName("relationship-list");
		childA.setValue("relationship-list", relationshipList);
		relationshipList = this.loader.objectFromName("relationship-list");
		childB.setValue("relationship-list", relationshipList);
		
		((List)IntrospectorFactory.newInstance(childA.getModelType(), childA.getValue("relationship-list"), llBuilder).getValue("relationship")).add(relationshipB.getUnderlyingObject());
		((List)IntrospectorFactory.newInstance(childB.getModelType(), childB.getValue("relationship-list"), llBuilder).getValue("relationship")).add(relationshipA.getUnderlyingObject());

		variableMap.put("expectedResultA", replaceUsernamePlaceholders(childA.marshal(true)));
		variableMap.put("expectedResultB", replaceUsernamePlaceholders(childB.marshal(true)));
		
		processDataModel(RELATIONSHIP_CONTENT, "content.txt", dir, variableMap);	
		processDataModel(PROPERTIES_XML, "properties.xml", dir, variableMap);
		
	}

	/**
	 * Transform relationship UR is for vserver.
	 *
	 * @param variableMap the variable map
	 */
	private void transformRelationshipURIsForVserver(Map<String, Object> variableMap) {
		String aURL = (String)variableMap.get("relationshipURLA");
		String bURL = (String)variableMap.get("relationshipURLB");
	
		variableMap.put("relationshipURLA", this.replaceWithLegacyURI(aURL));
		variableMap.put("relationshipURLB", this.replaceWithLegacyURI(bURL));

	}
	
	/**
	 * Replace with legacy URI.
	 *
	 * @param s the s
	 * @return the string
	 */
	private String replaceWithLegacyURI(String s) {
		String substring = "/aai/(v\\d)/cloud-infrastructure/tenants/tenant/(.*?)/vservers/vserver/([^/]*?$)";
		String replacement = "/aai/servers/$1/$2/vservers/$3";
		if (s.matches(substring)) {
			s = s.replaceFirst(substring, replacement);
		}
		
		return s;
	}


	/**
	 * Gets the unpopulated feature url.
	 *
	 * @param hyphenatedClassName the hyphenated class name
	 * @param key the key
	 * @param lep the lep
	 * @return the unpopulated feature url
	 * @throws Exception the exception
	 */
	private String getUnpopulatedFeatureUrl(String hyphenatedClassName, String key, LocateEndPoints lep) throws Exception {
		
		// Each path ends with the endpoint suffix
		String endpointSuffix = "{" + hyphenatedClassName + "-" + key + "}";

		// Special case feature urls
		switch(hyphenatedClassName) {
		case "ctag-pool":
			// Note that these have dual keys and dependencies are not working properly (availabilityZoneName is null)
			endpointSuffix = "{ctag-pool-target-pe}/{ctag-pool-availability-zone-name}";
			break;
		
		case "service-capability":
			// Note that these have dual keys and dependencies are not working properly (vnfType is null)
			endpointSuffix = "{service-capability-service-type}/{service-capability-vnf-type}";
			break;
		case "cloud-region":
			endpointSuffix = "{cloud-region-cloud-owner}/{cloud-region-cloud-region-id}";
			break;
		}
		
		// Search the endpoints for the class endpoint
		for(String path : lep.getAllPaths()) {
			if(path.endsWith(endpointSuffix)) {
				return path;
			}
		}
		
		// The path was not found
		throw new IllegalArgumentException("The endpoint URL for " + hyphenatedClassName + " was not found!");		
	}
	
	/**
	 * Gets the unpopulated feature url.
	 *
	 * @param obj the obj
	 * @param lep the lep
	 * @return the unpopulated feature url
	 * @throws Exception the exception
	 */
	private String getUnpopulatedFeatureUrl(Introspector obj, LocateEndPoints lep) throws Exception {
		return this.getUnpopulatedFeatureUrl(obj.getDbName(), obj.getKeys().get(0), lep);
	}
	
	/**
	 * Determines if a class has a parent via the url.
	 *
	 * @param featureUrl The URL
	 * @return true, if successful
	 */
	private boolean hasDependency(String featureUrl) {
		return featureUrl.split("/").length > 7;
	}
	
	/**
	 * Creates the top-level parent object for a given feature URL.
	 *
	 * @param featureUrl the feature url
	 * @param lep the lep
	 * @return the introspector
	 * @throws IllegalAccessException the illegal access exception
	 * @throws IllegalArgumentException the illegal argument exception
	 * @throws InvocationTargetException the invocation target exception
	 * @throws InstantiationException the instantiation exception
	 * @throws JSONException the JSON exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws JAXBException the JAXB exception
	 */
	private Introspector createParentObject(String featureUrl, LocateEndPoints lep) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, JSONException, IOException, JAXBException {
		
		String topLevelClass = getHyphenatedParentName(featureUrl);

		return lep.createChildFromClassName(topLevelClass, 1, 1, true);
	}
	
	/**
	 * Checks for del target.
	 *
	 * @param properties the properties
	 * @return true, if successful
	 */
	private boolean hasDelTarget(String properties) {
		String[] arr = properties.split(",");
		
		if (arr.length > 5) {
			return arr[5].equals("true");
		}
		
		return false;
	}
	
	/**
	 * Determines if the specified object contains a relationship list.
	 *
	 * @param obj the obj
	 * @return true, if successful
	 * @throws ClassNotFoundException the class not found exception
	 * @throws SecurityException the security exception
	 */
	private boolean hasRelationshipList(Introspector obj) throws ClassNotFoundException, SecurityException {
		
		return obj.hasProperty("relationship-list");
	}
	
	/**
	 * Gets the hyphenated parent name from the given feature URL.
	 *
	 * @param featureUrl the feature url
	 * @return the hyphenated parent name
	 */
	private String getHyphenatedParentName(String featureUrl) {
		return featureUrl.split("/")[5];
	}

}
