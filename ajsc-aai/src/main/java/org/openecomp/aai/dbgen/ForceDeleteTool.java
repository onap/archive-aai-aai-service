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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.UUID;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;

import com.att.eelf.configuration.Configuration;
import com.thinkaurelius.titan.core.TitanEdge;
import com.thinkaurelius.titan.core.TitanFactory;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanGraphQuery;
import com.thinkaurelius.titan.core.TitanVertex;



public class ForceDeleteTool {


	private static 	final  String    FROMAPPID = "AAI-DB";
	private static 	final  String    TRANSID   = UUID.randomUUID().toString();
	
	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
		
		//SWGK 01/21/2016 - To suppress the warning message when the tool is run from the Terminal.

		// Set the logging file properties to be used by EELFManager
		Properties props = System.getProperties();
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_NAME, AAIConstants.AAI_DATA_GROOMING_LOGBACK_PROPS);
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_PATH, AAIConstants.AAI_HOME_ETC_APP_PROPERTIES);
		
		if( args == null || args.length != 2 ){
				String msg = "usage:  ForceDeleteTool action data (action = COLLECT_DATA or DELETE_NODE or DELETE_EDGE) \n";
				System.out.println(msg);
				System.exit(1);
		}
	  	String actionVal = args[0];
	  	String dataString = args[1];
	  	
	 //DEBUG -- public static void forceDeleteTool( String actionVal, String dataString ){
		
	  	TitanGraph graph = null;
		
		if( !actionVal.equals("COLLECT_DATA") && !actionVal.equals("DELETE_NODE") && !actionVal.equals("DELETE_EDGE")){
	 		String emsg = "Bad action parameter [" + actionVal + "] passed to ForceDeleteTool().  Valid values = COLLECT_DATA or DELETE_NODE or DELETE_EDGE\n";
			System.out.println(emsg);
	 		System.exit(0);
	  	}
		try {   
    		AAIConfig.init(TRANSID, FROMAPPID);
    		System.out.println("    ---- NOTE --- about to open graph (takes a little while)--------\n");
    		graph = TitanFactory.open(AAIConstants.AAI_CONFIG_FILENAME);

    		//graph = AAIGraph.getInstance().getGraph();
    	
    		if( graph == null ){
    			String emsg = "could not get graph object in ForceDeleteTool() \n";
    			System.out.println(emsg);
    	 		System.exit(0);
    		}
    	}
	    catch (AAIException e1) {
			String msg =  e1.getErrorObject().toString();
			System.out.println(msg);
			System.exit(0);
        }
        catch (Exception e2) {
	 		String msg =  e2.toString();
	 		System.out.println(msg);
	 		System.exit(0);
        }

    	if( actionVal.equals("COLLECT_DATA") ){
	  		// When doing COLLECT_DATA, we expect the dataString string to look like this:
	  		//    "propName1|propVal1,propName2|propVal2" etc.  We will look for a node or nodes
	  		//    that have properties that ALL match what was passed in.
	  		
	  	   	int resCount = 0;
        	int firstPipeLoc = dataString.indexOf("|");
	  		if( firstPipeLoc <= 0 ){
	  			String msg =  "Bad dataString passed for collecting data: [" + dataString + "].  \n Expecting a format like, 'propName1|propVal1,propName2|propVal2'";
		 		System.out.println(msg);
		 		System.exit(0);
	  		}
	  		TitanGraphQuery tgQ  = graph.query();
	  		String qStringForMsg = " graph.query()";
	  	   	// Note - if they're only passing on parameter, there won't be any commas
	  		String [] paramArr = dataString.split(",");
	  		for( int i = 0; i < paramArr.length; i++ ){
	  			int pipeLoc = paramArr[i].indexOf("|");
	  			if( pipeLoc <= 0 ){
		  			String msg =  "Bad dataString passed for collecting data: [" + dataString + "].  \n Expecting a format like, 'propName1|propVal1,propName2|propVal2'";
			 		System.out.println(msg);
			 		System.exit(0);
	  			}
	  			else {
	  				String propName = paramArr[i].substring(0,pipeLoc);
		  			String propVal = paramArr[i].substring(pipeLoc + 1);
		  			tgQ = tgQ.has(propName,propVal);
		  			qStringForMsg = qStringForMsg + ".has(" + propName + "," + propVal + ")";
		  		}
	  		}
	  	   	if( (tgQ != null) && (tgQ instanceof TitanGraphQuery) ){
	        	Iterable <TitanVertex> verts = (Iterable<TitanVertex>) tgQ.vertices();
	        	Iterator <TitanVertex> vertItor = verts.iterator();
	           	while( vertItor.hasNext() ){
	        		resCount++;
	        		TitanVertex v = (TitanVertex)vertItor.next();
	    			Iterator<VertexProperty<Object>> pI = v.properties();
	    			System.out.println("\n\n>>> Found Vertex with VertexId = " + v.id() + ", properties:    ");
	    			while( pI.hasNext() ){
	    				VertexProperty<Object> tp = pI.next();
	    				System.out.println(" [" + tp.key() + "|" + tp.value() + "] ");
	    			}
	    			
	    			try {
		    			ArrayList <String> retArr = DbMeth.showAllEdgesForNode(TRANSID, FROMAPPID, v);
		  				for( String info : retArr ){ System.out.println( info ); }
	    			}
	    			catch (Exception e){
	    				System.out.println("Error trying to display edge info. " + e.getMessage() );
	    			}
	        	}
	        }
	        else {
	           	String msg =  "Bad TitanGraphQuery object.  ";
		 		System.out.println(msg);
		 		System.exit(0);
	  		}
	  		
	  		System.out.println("\n\n Found: " + resCount + " nodes for this query: [" + qStringForMsg + "]" );
	  		System.out.println("    ");
	  	} 
	  	else if( actionVal.equals("DELETE_NODE") ){
	  		long longVertId = Long.parseLong(dataString);
	  		Vertex vtx = graph.vertices( longVertId ).next();
	  		if (vtx != null)
	  		{
		  		vtx.remove();
		  		graph.tx().commit();
		  		System.out.println(">>>>>>>>>> Removed vertex with vertexId = " + dataString );
	  		}
	  		else
	  		{
	  			System.out.println(">>>>>>>>>> Vertex with vertexId = " + dataString + " not found.");
	  		
	  		}
	  	}
	  	else if( actionVal.equals("DELETE_EDGE") ){
	  		String edgeId = dataString;
	  		TitanEdge edge = (TitanEdge) graph.edges( edgeId ).next();
	  		if (edge != null)
	  		{
	  			edge.remove();
		  		graph.tx().commit();
		  		System.out.println(">>>>>>>>>> Removed edge with edgeId = " + edgeId );
	  		}
	  		else
	  		{
	  			System.out.println(">>>>>>>>>> Edge with edgeId = " + edgeId + " not found.");
	  		
	  		}
	 		System.exit(0);
	  	}
	  	else {
			String emsg = "Unknown action parameter [" + actionVal + "] passed to ForceDeleteTool().  Valid values = COLLECT_DATA, DELETE_NODE or DELETE_EDGE \n";
			System.out.println(emsg);
	 		System.exit(0);
	  	}

	  	System.exit(0);
    
	}// end of main()
	
	
	
}


