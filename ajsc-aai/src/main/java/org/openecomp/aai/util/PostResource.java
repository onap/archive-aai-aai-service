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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;

import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.LogLine;

import com.att.eelf.configuration.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;

/*
 * Allows to call POST REST API that AAI supports - currently for edge-tag-query
 */
public class PostResource {
	
	private static 	final  String    COMPONENT = "aairestctrl";
	private static 	final  String    FROMAPPID = "AAI-TOOLS";
	private static 	final  String    TRANSID   = UUID.randomUUID().toString();
	
	private static final String USAGE_STRING = "Usage: postTool.sh <resource-path> <filename>\n" +
			"resource-path for a particular resource or query starting after the aai/<version>\n" +
			"filename is the path to a file which contains the json input for the payload\n" +
			"for example: postTool.sh search/edge-tag-query /tmp/query-input.json\n";
	
	/**
	 * The main method.
	 *
	 * @param <T> the generic type
	 * @param args the arguments
	 */
	public static <T> void main(String[] args) {

		// Set the logging file properties to be used by EELFManager
		Properties props = System.getProperties();
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_NAME, AAIConstants.AAI_POSTTOOL_LOGBACK_PROPS);
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_PATH, AAIConstants.AAI_HOME_ETC_APP_PROPERTIES);
		
		AAILogger aaiLogger = new AAILogger(PostResource.class.getName());
		LogLine   logline = new LogLine();
		logline.init(COMPONENT, TRANSID, FROMAPPID, "main");
		
		try {
			if (args.length < 2) {
				System.out.println("Insufficient arguments");
				System.out.println(USAGE_STRING);
				logline.add("msg", "Insufficient arguments");
				aaiLogger.info(logline, false, "AAI_7403");
				aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_7403"), logline, null);
				System.exit(1);
			} 
			
			// Assume the config AAI_SERVER_URL has a last slash so remove if  
			//  resource-path has it as the first char
			String path = args[0].replaceFirst("^/", "");		
			Path p = Paths.get(path);
			
			// currently , it is for edge-taq-query only
			String query = p.getName(p.getNameCount() - 1).toString();
			String resourceClass = null;
			if (query.equals("edge-tag-query"))
				resourceClass = "org.openecomp.aai.domain.search." + 
						CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, query) + "Request";
			else {
				logline.add("msg", "Incorrect resource or query");
				aaiLogger.info(logline, false, "AAI_7403");
				aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_7403"), logline, null);
				System.exit(1);
			}
			
			logline.add("class", resourceClass);
			logline.add("path", path);
//			System.out.println("class=" + resourceClass);
//			System.out.println("path=" + path);
			
			@SuppressWarnings("unchecked")
			T resJson1 = (T)readJsonFile(Class.forName(resourceClass), args[1]);
						
			String response = RestController.<T>Post(resJson1, FROMAPPID, TRANSID, path);
			ObjectMapper mapper = new ObjectMapper();
			Object json = mapper.readValue(response, Object.class);
			
			System.out.println(" POST succeeded\n");
			System.out.println("Response = " + mapper.writer().withDefaultPrettyPrinter().writeValueAsString(json));
			System.out.println("\nDone!!");
			
			aaiLogger.info(logline, true, "0");
			System.exit(0); 

		} catch (AAIException e) {
			aaiLogger.error(e.getErrorObject(), logline, e);
			aaiLogger.info(logline, false, e.getErrorObject().getErrorCodeString());
			System.exit(1);	
		} catch (Exception e) {
			aaiLogger.error(ErrorLogHelper.getErrorObject("AAI_7402", e.getMessage()), logline, e);
			aaiLogger.info(logline, false, "AAI_7402");
			System.exit(1);
		}
	}
	
	/**
	 * Gets the single instance of PostResource.
	 *
	 * @param <T> the generic type
	 * @param clazz the clazz
	 * @return single instance of PostResource
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
}
