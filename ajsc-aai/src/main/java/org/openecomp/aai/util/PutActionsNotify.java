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
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.persistence.dynamic.DynamicEntity;
import org.eclipse.persistence.jaxb.JAXBUnmarshaller;
import org.eclipse.persistence.jaxb.dynamic.DynamicJAXBContext;
import org.openecomp.aai.domain.model.AAIResource;
import org.openecomp.aai.domain.model.AAIResources;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.extensions.AAIExtensionMap;
import org.openecomp.aai.extensions.ExtensionController;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.domain.yang.Notify;
import com.att.eelf.configuration.Configuration;
import com.google.common.base.CaseFormat;

public class PutActionsNotify {
	
	private static 	final  String    COMPONENT = "aairestctrl";
	private static 	final  String    FROMAPPID = "AAI-TOOLS";
	private static 	final  String    TRANSID   = UUID.randomUUID().toString();
	
	private static final String USAGE_STRING = "Usage: notifyTool.sh <resource-path> <filename> \n" +
						"for example 1: putNotify.sh actions/notify /tmp/request.json \n";
	
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
		
		AAILogger aaiLogger = new AAILogger(PutActionsNotify.class.getName());
		LogLine   logline = new LogLine();
		logline.init(COMPONENT, TRANSID, FROMAPPID, "main");
		
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
			
			String resource = p.getName(p.getNameCount() - 1).toString();
			String resourceClass = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, resource);
			resourceClass = "org.openecomp.aai.domain.yang." + resourceClass;
			
			logline.add("class", resourceClass);
			logline.add("path", path);
			
			Notify resJson1 = (Notify)readJsonFile(Class.forName(resourceClass), args[1]);
			RestController.<Notify>Put(resJson1, FROMAPPID, TRANSID, path, false);
			
			System.out.println(" PUT succeeded\n");
			System.out.println("\nDone!!");
			
			aaiLogger.info(logline, true, "0");
			System.exit(0); 

		} catch (AAIException e) {
			System.out.println("PUT failed: " + e.getMessage());
			aaiLogger.error(e.getErrorObject(), logline, e);
			aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());
			System.exit(1);	
		} catch (Exception e) {
			System.out.println("PUT failed: " + e.toString());
			aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_7402", e.getMessage()), logline, e);
			aaiLogger.info(logline, false, "AAI_7402");
			System.exit(1);
		}
	}
	
	/**
	 * Gets the single instance of PutActionsNotify.
	 *
	 * @param <T> the generic type
	 * @param clazz the clazz
	 * @return single instance of PutActionsNotify
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
	 * Gets the dynamic entity for request.
	 *
	 * @param jaxbContext the jaxb context
	 * @param aaiRes the aai res
	 * @param objectFromRequest the object from request
	 * @param aaiExtMap the aai ext map
	 * @return the dynamic entity for request
	 * @throws JAXBException the JAXB exception
	 */
	protected static DynamicEntity getDynamicEntityForRequest(DynamicJAXBContext jaxbContext,
			AAIResource aaiRes,
			String objectFromRequest, 
			AAIExtensionMap aaiExtMap) throws JAXBException {
DynamicEntity request = null;
if (objectFromRequest != null) {
JAXBUnmarshaller unmarshaller = jaxbContext.createUnmarshaller();
String dynamicClass = aaiRes.getResourceClassName();

//if (aaiExtMap.getHttpServletRequest().getContentType() == null || 
//aaiExtMap.getHttpServletRequest().getContentType().equalsIgnoreCase("application/json")) {
unmarshaller.setProperty("eclipselink.media-type", "application/json");
unmarshaller.setProperty("eclipselink.json.include-root", false);
//}

Class<? extends DynamicEntity> resultClass = jaxbContext.newDynamicEntity(dynamicClass).getClass();
StringReader reader = new StringReader(objectFromRequest);
request = (DynamicEntity) unmarshaller.unmarshal(new StreamSource(reader), resultClass).getValue();
}
return request;
}
}
