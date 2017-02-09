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


import java.util.Iterator;
import java.util.Properties;

import com.att.eelf.configuration.Configuration;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanVertex;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.dbmap.AAIGraph;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;


public class AddResourceVersionProp {

	
	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
		
		// Set the logging file properties to be used by EELFManager
		Properties props = System.getProperties();
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_NAME, AAIConstants.AAI_SCHEMA_MOD_LOGBACK_PROPS);
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_PATH, AAIConstants.AAI_HOME_ETC_APP_PROPERTIES);

		// NOTE -- This is a one-time migration for adding the new property "resource-version" to ALL of our nodes.
	    // Also -- since it's a one-time thing, we just re-use AAI_SCHEMA_MOD_LOG4J_PROPS
	   
    	TitanGraph graph = null;

    	System.out.println(">>> WARNING: this script affects all nodes in the database. <<<< " );
    	System.out.println(">>> Processing will begin in 5 seconds (unless interrupted). <<<");
		try {
			// Give them a chance to back out of this
			Thread.sleep(5000);
		} catch ( java.lang.InterruptedException ie) {
			System.out.println( " AddResourceVersionProp script has been aborted. ");
			System.exit(1);
		}
		
		try {   
	 	    AAIConfig.init();
			ErrorLogHelper.loadProperties();
			
			System.out.println("    ---- NOTE --- about to open graph (takes a little while)\n");

			graph = AAIGraph.getInstance().getGraph();
			if( graph == null ){
				 String emsg = "Not able to get a graph object in AddResourceVersionProp.java\n";
	          	 System.out.println( emsg );
	          	 System.exit(1);
	 		}

			
			// For each node in the db -- update the "resource-version" and the last mod timestamp.
			Iterable<TitanVertex> verts = null;
			verts= graph.query().vertices();
			Iterator<TitanVertex> it = verts.iterator();
			int vtxCount = 0;
			long unixTimeNow = System.currentTimeMillis() / 1000L;
			String timeNowInSec = "" + unixTimeNow;
			
			while( it.hasNext() ){
				vtxCount++;
				TitanVertex tmpVtx = (TitanVertex)it.next();
				tmpVtx.property( "aai-last-mod-ts", timeNowInSec );
				tmpVtx.property( "resource-version", timeNowInSec );
			}
			
			System.out.println("Updated data for " + vtxCount + " vertexes.  Now call graph.tx().commit(). ");
			graph.tx().commit();
			
    	} 
	    catch (AAIException e) {
	    	System.out.print("Threw a AAIException: \n");
	    	System.out.println(e.getErrorObject().toString());
	    } 
	    catch (Exception ex) {
	    	System.out.print("Threw a regular Exception:\n");
	    	System.out.println(ex.getMessage());
	    }
	    finally {
	    	if( graph != null ){
	  	    	// Any changes that worked correctly should have already done their commits.
	  	    	graph.tx().rollback();
	  		    graph.close();
	  	    }
	    	
	  	}
	   
	    
	    System.exit(0);
	    
	}// End of main()
	

}


