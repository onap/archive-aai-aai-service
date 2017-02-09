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

package org.openecomp.aai.dbgen;

import java.util.Properties;
import java.util.UUID;

import org.openecomp.aai.dbmap.AAIGraph;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.logging.ErrorObject;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;

import com.att.eelf.configuration.Configuration;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.schema.TitanManagement;
//import com.thinkaurelius.titan.core.util.TitanCleanup;




public class GenTester {

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
	   
		TitanGraph graph = null;
		
		// Set the logging file properties to be used by EELFManager
		Properties props = System.getProperties();
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_NAME, AAIConstants.AAI_CREATE_DB_SCHEMA_LOGBACK_PROPS);
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_PATH, AAIConstants.AAI_HOME_ETC_APP_PROPERTIES);

		AAILogger aaiLogger = new AAILogger(GenTester.class.getName());
		LogLine logline = new LogLine();
		String component = "AAI-DB"; 
		String fromAppId = "GenTester";
		String transId = UUID.randomUUID().toString();
		logline.init(component, transId, fromAppId, "main");
		
		boolean genDbWithNoSchema = false;
		boolean addDefaultCR = true;
		boolean createHBaseLogTableIfNeeded = false;
		
		try {   
			if (args != null && args.length > 0 ){
	    		for( int i = 0; i < args.length; i++ ){
	    			// First - look to see if they passed a NO SCHEMA parameter - if they did, then we will
	    			//     ignore other parameters
		    		if ( "GEN_DB_WITH_NO_SCHEMA".equals(args[i]) ){
		    			genDbWithNoSchema = true;
		    		} 
		    		else if ("GEN_DB_WITH_NO_DEFAULT_CR".equals(args[i])) {
		    			addDefaultCR = false;
		    		}
		    		else if ("CREATE_HBASE_LOG_TBL".equals(args[i])) {
		    			createHBaseLogTableIfNeeded = true;
		    		}
		    		else {
		    			AAIException e = new AAIException("AAI_3000", "Unrecognized argument passed to GenTester.java: [" + args[i] + "]. ");
		    			ErrorObject eo = ErrorLogHelper.getErrorObject("AAI_3000", "Unrecognized argument passed to GenTester.java: [" + args[0] + "]. ");
		    			logline.add("error:", "Unrecognized argument passed to GenTester.java: [" + args[i] + "]. ");
		    			logline.add("error help:", "Either pass no argument for normal processing, or use 'GEN_DB_WITH_NO_SCHEMA'.");
		    			aaiLogger.error(eo, logline, e);
		    			aaiLogger.audit(logline);
		    			String msg = "Unrecognized argument passed to GenTester.java: [" + args[i] + "]. ";
				        System.out.println(msg);
				        System.exit(1);
		    		}
	    		}
	    	}
	    	
	    	if( createHBaseLogTableIfNeeded ){
	    		// Note - this is separate from the Titan stuff
	    		SchemaGenerator.createHBaseLogTableIfNeeded();
	    	}
	    	
	    	if( genDbWithNoSchema ){
	    		// Note this is done to create an empty DB with no Schema so that
				// an HBase copyTable can be used to set up a copy of the db.
				logline.add("", "    ---- NOTE --- about to load a graph without doing any schema processing (takes a little while) --------   ");
				graph = AAIGraph.getInstance().getGraph();
		    	
		       if( graph == null ){
		           AAIException e = new AAIException("AAI_5102");
		           ErrorObject eo = ErrorLogHelper.getErrorObject("AAI_5102", "Error creating Titan graph.");
		           aaiLogger.error(eo, logline, e);
		           aaiLogger.audit(logline);
		           String msg = "Error creating Titan graph.";
		           System.out.println(msg);
		           System.exit(1);
		       }
		       else {
		    	   logline.add("", "Successfully loaded a Titan graph without doing any schema work.  ");
		    	   aaiLogger.audit(logline);
		    	   System.exit(0);
		       }
    		} 
	    	else {
				AAIConfig.init();
				ErrorLogHelper.loadProperties();
				logline.add("", "    ---- NOTE --- about to open graph (takes a little while)--------;");
				graph = AAIGraph.getInstance().getGraph();
		    	
				if( graph == null ){
					AAIException e = new AAIException("AAI_5102");
			        ErrorObject eo = ErrorLogHelper.getErrorObject("AAI_5102", "Error creating Titan graph. ");
			        aaiLogger.error(eo, logline, e);
			        aaiLogger.audit(logline);
			        String msg = "Error creating Titan graph.";
			        System.out.println(msg);
			        System.exit(1);
				}
	    
				// Load the propertyKeys, indexes and edge-Labels into the DB
				TitanManagement graphMgt = graph.openManagement();
	
				logline.add("", "-- Loading new schema elements into Titan --");
				SchemaGenerator.loadSchemaIntoTitan( graph, graphMgt );
	    	}

	    } 
	    catch( Exception ex ){
	        logline.add("ERROR:", "caught this exception: " + ex);
	        ErrorObject eo = ErrorLogHelper.getErrorObject("AAI_4000", ex.getMessage());
	        aaiLogger.error(eo, logline, ex);
	        aaiLogger.audit(logline);
	        String msg = "caught this exception: " + ex;
	        System.out.println(msg);
	        System.exit(1);
	    }
	    

	    if( graph != null ){
		    logline.add("", "-- graph commit");
	        graph.tx().commit();
	    }

	    if( graph != null ){
		    logline.add("", "-- graph shutdown ");
	        graph.close();
	    }
	    logline.add("", "-- all done, if program does not exit, please kill.");
	    aaiLogger.audit(logline);
	    System.exit(0);
    }

}


