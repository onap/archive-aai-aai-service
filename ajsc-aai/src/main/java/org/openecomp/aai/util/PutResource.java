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

package org.openecomp.aai.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;

import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.ingestModel.DbMaps;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.ErrorObject;
import org.openecomp.aai.logging.LogLine;

import com.att.eelf.configuration.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;

/*
 * SWGK - 09/03/2015 - Added Generics to Put as well as to Get the object that was created/updated
 * by Put to get the Object back.
 */
public class PutResource {
	
	private static 	final  String    COMPONENT = "aairestctrl";
	private static 	final  String    FROMAPPID = "AAI-TOOLS";
	private static 	final  String    TRANSID   = UUID.randomUUID().toString();
	
	private static final String USAGE_STRING = "Usage: putTool.sh <resource-path> <filename> <UpdatingRelationshiplist> <UpdatingChild> <ChildNameList> <SkipIfExists>\n" +
			"resource-path for a particular resource starting after the aai/<version>\n" +
			"filename is the path to a file which contains the json input for the payload\n" +
			"optional UpdatingRelationshiplist setting 1 for updating the relationship list and if setting 0 relationship list will not be updated.\n" +
			"optional UpdatingChild setting 1 for updating the child(r)en and if setting 0 child(ren) will not be updated.\n" +
			"optional ChildNameList is a comma-separated child(ren) name list only applicable if UpdatingChild is set to 1.\n" +
			"optional SkipIfExisting setting 1 to skip the update if resource exists and if setting 0 put will be done.\n" +
			"for example 1: putTool.sh cloud-infrastructure/oam-networks/oam-network/test-100-oam /tmp/putoam.json\n" +
			"for example 2: putTool.sh cloud-infrastructure/pservers/pserver/dpa2r04c009-swgk-009 /tmp/pserver.json 0 1 PInterfaces,LagInterfaces\n" +
			"for example 2: putTool.sh cloud-infrastructure/pservers/pserver/dpa2r04c009-swgk-009 /tmp/pserver.json 0 1 PInterfaces,LagInterfaces 1\n";
	
	/**
	 * The main method.
	 *
	 * @param <T> the generic type
	 * @param args the arguments
	 */
	public static <T> void main(String[] args) {

		// Set the logging file properties to be used by EELFManager
		Properties props = System.getProperties();
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_NAME, AAIConstants.AAI_PUTTOOL_LOGBACK_PROPS);
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_PATH, AAIConstants.AAI_HOME_ETC_APP_PROPERTIES);
		
		AAILogger aaiLogger = new AAILogger(PutResource.class.getName());
		LogLine   logline = new LogLine();
		logline.init(COMPONENT, TRANSID, FROMAPPID, "main");
		
		Boolean bResVersionEnabled = false;
		
		try
		{
		    String strEnableResVersion = AAIConfig.get(AAIConstants.AAI_RESVERSION_ENABLEFLAG);
		    if (strEnableResVersion != null && !strEnableResVersion.isEmpty())
		       bResVersionEnabled = Boolean.valueOf(strEnableResVersion);
		}
		catch (Exception e) {
		
		}
		
		boolean doPutIfExists = true;
		if (args.length > 5)
			if (args[5].equals("1"))
				doPutIfExists = false;
		
		try {
			if (args.length < 2) {
				System.out.println("Insufficient arguments");
				System.out.println(USAGE_STRING);
				logline.add("msg", "Insufficient arguments");
				aaiLogger.info(logline, true, "0");
				System.exit(1);
			} 
			
			// Assume the config AAI_SERVER_URL has a last slash so remove if  
			//  resource-path has it as the first char
			String path = args[0].replaceFirst("^/", "");		
			Path p = Paths.get(path);
			
			// if the node type has one key
			String resource = p.getName(p.getNameCount() - 2).toString();
			// if the node type has two keys - this assumes max 2 keys
			IngestModelMoxyOxm moxyMod = new IngestModelMoxyOxm();
			DbMaps dbMaps = null;
			try {
				ArrayList <String> defaultVerLst = new ArrayList <String> ();
				defaultVerLst.add( AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP) );
				moxyMod.init( defaultVerLst, false);
				// Just make sure we can get DbMaps - don't actually need it until later.
				dbMaps = IngestModelMoxyOxm.dbMapsContainer.get(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP));
				}
			catch (Exception ex){
				String emsg = " ERROR - Could not get the DbMaps object. ";
				System.out.println( emsg );
				aaiLogger.info(logline, false, "AAI_7402");
				aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_7402"), logline, ex);
				System.exit(1);
			}
			if (!dbMaps.NodeKeyProps.containsKey(resource))
				resource = p.getName(p.getNameCount() - 3).toString();
			String resourceClass = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, resource);
			resourceClass = "org.openecomp.aai.domain.yang." + resourceClass;
			
			logline.add("class", resourceClass);
			logline.add("path", path);
			System.out.println("class=" + resourceClass);
			System.out.println("path=" + path);
			
			RestObject<T> restObj = new RestObject<T>();
			@SuppressWarnings("unchecked")
			T t2 = (T)getInstance(Class.forName(resourceClass));
			restObj.set(t2);
			
			boolean bExist = true;
			
			try
			{
				if ( !doPutIfExists ) {
					
					String url = AAIConfig.get(AAIConstants.AAI_SERVER_URL) + path;

					logline.add("url", url);
					System.out.println("url=" + url);
					if ( nodeExists( url) ) {
						System.out.println("PUT succeeded, the resource exists already in the DB. Skipping the put based on the skipIfExists command line parameter.\n");
						System.exit(0);	
					}
					
				}
			    RestController.<T>Get(t2, FROMAPPID, TRANSID, path, restObj, false);
			    t2 = restObj.get();
			    System.out.println(" GET succeeded\n");
			} catch (AAIException e) {
				if ( !doPutIfExists ) { 
					System.out.println("GET " + path + " returned " + e.getMessage() + "\n");
					ErrorObject eo = e.getErrorObject();
					System.out.println( "details " + eo.getDetails() + "\n");
					
				}
				bExist = false;
			}
			catch (Exception e1)
			{
				if ( !doPutIfExists ) {
					System.out.println(" GET exception ignored with skipExists parameter\n");
					e1.printStackTrace();
				}
				bExist = false;
			}
			
			
			@SuppressWarnings("unchecked")
			T resJson1 = (T)readJsonFile(Class.forName(resourceClass), args[1]);
			String resourceUpdateVersion = GetResourceVersion(resJson1);
			
			
			if (bResVersionEnabled && bExist)
			{

				String DBresourceVersion = GetResourceVersion(t2);
				if ( !doPutIfExists ) {
					System.out.println("PUT succeeded, the resource exists already in the DB. Skipping the put based on the skipIfExists command line parameter.\n");
					 logline.add("dbVersion", DBresourceVersion);
				     logline.add("UpdateVersion", resourceUpdateVersion);
				     aaiLogger.info(logline, true, "0");
				     System.exit(0);
				}
				
				if (resourceUpdateVersion == null || resourceUpdateVersion.isEmpty())
				{
					 if ( DBresourceVersion != null && !DBresourceVersion.isEmpty())
					    System.out.println("The resource with version = " +  DBresourceVersion + "  exists already in the DB. Please supply the right resourceVersion in input data file.\n");
					 else
						 System.out.println("The resource exists already in the DB. Please supply the right resourceVersion in input data file.\n");
				     
					 logline.add("dbVersion", DBresourceVersion);
				     logline.add("UpdateVersion", resourceUpdateVersion);
				     aaiLogger.info(logline, false, "AAI_7402");
				     System.exit(1);	
				 }
					
				if ( DBresourceVersion != null && !DBresourceVersion.isEmpty() )
				{
					if ( resourceUpdateVersion != null && !resourceUpdateVersion.isEmpty() )
						 if (!DBresourceVersion.equals(resourceUpdateVersion))
						 {
							 System.out.println("DB version doesn't match current version. Please get the latest version and modify.\n");
						
						     logline.add("dbVersion", DBresourceVersion);
						     logline.add("UpdateVersion", resourceUpdateVersion);
						     aaiLogger.info(logline, false, "AAI_7402");
						     System.exit(1);	
						 }
				}
			}
			else //sanity check
			{
				if ( bResVersionEnabled && resourceUpdateVersion != null && !resourceUpdateVersion.isEmpty())
				{
					 System.out.println("DB doesn't have this resource any more. Please create a new version by taking out the resourceVersion tag from your input resource data file.\n");
				
				     logline.add("UpdateVersion", resourceUpdateVersion);
				     aaiLogger.info(logline, false, "AAI_7403");
				     System.exit(1);
				}
				
			}
			
			if (bExist) //merge
			{
				boolean bUpdateChildren = false;
				boolean bUpdateRL = false;
			
				if (args.length == 3)
				{
					if (args[2].equals("1"))
						bUpdateRL = true;
					resJson1 = MergeResource.merge(t2, resJson1, false, bUpdateRL);
				}
				else if (args.length == 4)
				{
					if (args[2].equals("1"))
						bUpdateRL = true;
					if (args[3].equals("1"))
						bUpdateChildren = true;
					resJson1 = MergeResource.merge(t2, resJson1, bUpdateChildren, bUpdateRL);
				}
				else if (args.length == 5)
				{
					if (args[2].equals("1"))
						bUpdateRL = true;
					if (args[3].equals("1"))
						bUpdateChildren = true;
					String[] strChildArray = args[4].split("\\,");
					resJson1 = MergeResource.merge(t2, resJson1, bUpdateChildren, strChildArray, bUpdateRL);
					
				}
				else
					resJson1 = MergeResource.merge(t2, resJson1);
			
			}
			
			RestController.<T>Put(resJson1, FROMAPPID, TRANSID, path, false);
			
			System.out.println(" PUT succeeded\n");
			
			System.out.println("\nDone!!");
			
			aaiLogger.info(logline, true, "0");
			System.exit(0); 

		} catch (AAIException e) {
			if ( !doPutIfExists ) { // ignore 412 failure
				ErrorObject eo = e.getErrorObject();
				System.out.println( "ignore 412 AAIException " + e.getMessage() + " errorObject " + eo.getHTTPResponseCode() + 
						" details " + eo.getDetails());
				if ( e.getMessage().equals("AAI_7116")  ) {
					if ( eo.getDetails().indexOf("status=412") > 0)
						System.out.println("PUT succeeded, return 412 ignored\n");	
						System.out.println("\nDone!!");
						aaiLogger.info(logline, true, "0");
						System.exit(0); 
					}
			}
			System.out.println("PUT failed: " + e.getMessage());
			aaiLogger.error(e.getErrorObject(), logline, e);
			aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());

			System.exit(1);	
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("PUT failed: " + e.toString());
			aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_7402", e.getMessage()), logline, e);
			aaiLogger.info(logline, false, "AAI_7402");
			System.exit(1);
		}
	}
	
	/**
	 * Gets the single instance of PutResource.
	 *
	 * @param <T> the generic type
	 * @param clazz the clazz
	 * @return single instance of PutResource
	 * @throws IllegalAccessException the illegal access exception
	 * @throws InstantiationException the instantiation exception
	 */
	public static <T> T getInstance(Class<T> clazz) throws IllegalAccessException, InstantiationException
	{
		return clazz.newInstance();
	} 
	
	/**
	 * Read json file.
	 *
	 * @param <T> the generic type
	 * @param clazz the clazz
	 * @param fName the f name
	 * @return the t
	 * @throws AAIException the AAI exception
	 */
	public static <T> T  readJsonFile( Class<T> clazz, String fName ) throws AAIException 
	{    	
        String jsonData = "";
        BufferedReader br = null;
        T t;
        
        try {
            String line;
            br = new BufferedReader(new FileReader(fName));
            while ((line = br.readLine()) != null) {
                jsonData += line + "\n";
            }
        } catch (IOException e) {

            throw new AAIException("AAI_7403", e, "Error opening json file");
        } finally {
            try {
                if (br != null)
                    br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
                throw new AAIException("AAI_7403", ex, "Error closing json file");
            }
        }

        try {	        	
        	t = MapperUtil.readWithDashesAsObjectOf(clazz, jsonData);
        }
        catch (Exception je){
            throw new AAIException("AAI_7403", je, "Error parsing json file"); 
        }

        return t;

    }//End readJsonFile()	 
	
	/**
	 * Gets the resource version.
	 *
	 * @param <T> the generic type
	 * @param resource the resource
	 * @return the string
	 */
	public static <T> String GetResourceVersion(T resource)
	{
		Field[] fields = resource.getClass().getDeclaredFields();
		if (fields != null)
		{
		    for (Field field : fields)
		    {
		    	try
		    	{
			    	field.setAccessible(true);
			    	if ( field.getName().equalsIgnoreCase("resourceVersion") )
			    	{
			    		Object obj = field.get(resource);
			    		return (String)obj;
			    	}
			    		
			  
		    	}
		    	catch (Exception e)
	    		{
	    		
	    		}
		    	
		    	
		    }
		}
		return null;
	}
	
	/**
	 * Node exists.
	 *
	 * @param url the url
	 * @return true, if successful
	 * @throws AAIException the AAI exception
	 */
	public static boolean nodeExists(String url) throws AAIException {		
		try{
			String useBasicAuth = AAIConfig.get("aai.tools.enableBasicAuth");
			Client client = null;

			if (useBasicAuth != null && useBasicAuth.equals("true")) {
				client = HttpsAuthClient.getBasicAuthClient();
			} else {
				client = HttpsAuthClient.getTwoWaySSLClient();
			}
			
			System.out.println("Getting the resource...: " + url);
			
			ClientResponse cres;
			if(AAIConfig.get("aai.tools.enableBasicAuth").equals("true"))
			{
				cres = client.resource(url)
		         .accept("application/json")
		         .header("X-TransactionId", TRANSID)
		         .header("X-FromAppId",  FROMAPPID)
		         .header("Authorization", HttpsAuthClient.getBasicAuthHeaderValue())
		         .type("application/json")
		         .get(ClientResponse.class);
			}
			
			else{
				cres = client.resource(url)
		         .accept("application/json")
		         .header("X-TransactionId", TRANSID)
		         .header("X-FromAppId",  FROMAPPID)
		         .type("application/json")
		         .get(ClientResponse.class);
			}

			
			
			if (cres.getStatus() == 404) { // resource not found
				return false;
			} else if (cres.getStatus() == 200){
				return true;
			} else {
				System.out.println("Getting the Resource failed: " + cres.getStatus()
												+ ":\n" + cres.getEntity(String.class));
	           return false;
			}
		} catch (KeyManagementException e) {
            throw new AAIException("AAI_7401", e, "Error during GET");
		}  catch (Exception e) {
            throw new AAIException("AAI_7402", e, "Error during GET");
		}
	}	

}
