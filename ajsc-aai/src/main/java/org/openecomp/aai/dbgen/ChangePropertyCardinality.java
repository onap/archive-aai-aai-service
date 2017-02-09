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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.TimeZone;

import com.att.eelf.configuration.Configuration;
import com.thinkaurelius.titan.core.Cardinality;
import com.thinkaurelius.titan.core.PropertyKey;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanVertex;
import com.thinkaurelius.titan.core.schema.TitanManagement;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.dbmap.AAIGraph;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.ingestModel.DbMaps;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;


public class ChangePropertyCardinality {

	
	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {

	//public static void ChangePropertyCardinality( String[] args ) {
	
		// Set the logging file properties to be used by EELFManager
		Properties props = System.getProperties();
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_NAME, AAIConstants.AAI_SCHEMA_MOD_LOGBACK_PROPS);
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_PATH, AAIConstants.AAI_HOME_ETC_APP_PROPERTIES);

		// NOTE -- We're just working with properties that are used for NODES
	    String propName = "";
	    String targetDataType = "String";
	    String targetCardinality = "SET";
	    String preserveDataFlag = "false";
	    boolean preserveData = false;
	    
	    
		String usageString = "Usage: ChangePropertyCardinality propertyName targetDataType targetCardinality preserveDataFlag \n";
		if( args.length != 4 ){
			String emsg = "Four Parameters are required.  \n" + usageString;
			System.out.println( emsg );
         	System.exit(1);
		}
		else {
			propName = args[0];
			targetDataType = args[1];
			targetCardinality = args[2];
			preserveDataFlag = args[3];
		}
				
		if( propName.equals("") ){
			 String emsg = "Bad parameter - propertyName cannot be empty.  \n" + usageString;
			 System.out.println( emsg );
          	 System.exit(1);
		}
		else if( !targetDataType.equals("String") ){
			//	&& !targetDataType.equals("Integer")
			//	&& !targetDataType.equals("Long")
			//	&& !targetDataType.equals("Boolean") ){
			// String emsg = "Unsupported targetDataType.  We only support String, Integer, Long or Boolean for now.\n" + usageString;
			 String emsg = "Unsupported targetDataType.  We only support String for now.\n" + usageString;
			 System.out.println( emsg );
         	 System.exit(1);
		}
		else if( !targetCardinality.equals("SET") ){
					//	&& !targetCardinality.equals("LIST")
					//	&& !targetCardinality.equals("SINGLE") ){
			String emsg = "Unsupported targetCardinality.  We only support SET for now.\n" + usageString;
			 System.out.println( emsg );
         	 System.exit(1);
		}
		else {
			if( preserveDataFlag.equals("true") ){
				preserveData = true;
				String emsg = "Unsupported preserveDataFlag.  For now, we only support: 'false'.\n" + usageString;
				System.out.println( emsg );
	         	System.exit(1);
			}
			else if (preserveDataFlag.equals("false") ){
				preserveData = false;
			}
			else {
				String emsg = "Unsupported preserveDataFlag.  We only support: 'true' or 'false'.\n" + usageString;
				System.out.println( emsg );
	         	System.exit(1);
			}
		}
	    
		String targetDataTypeStr = "";
		if( targetCardinality.equals("SINGLE") ){
			targetDataTypeStr = targetDataType;
		}
		else if( targetCardinality.equals("SET") ){
			targetDataTypeStr = "Set<" + targetDataType + ">";
		}
		else if( targetCardinality.equals("LIST") ){
			targetDataTypeStr = "List<" + targetDataType + ">";
		}
		
		Class dType = null;
		if(targetDataType.equals("String")){ dType = String.class; }
	    else if(targetDataType.equals("Integer")){ dType = Integer.class; }
	    else if(targetDataType.equals("Boolean")){ dType = Boolean.class; }
	    else if(targetDataType.equals("Character")){ dType = Character.class; }
	    else if(targetDataType.equals("Long")){ dType = Long.class; }
	    else if(targetDataType.equals("Float")){ dType = Float.class; }
	    else if(targetDataType.equals("Double")){ dType = Double.class; }
		
		System.out.println("\n>> WARNING/NOTE - If the passed cardinality is not in synch with what is in the oxm file, ");
		System.out.println("\n>>         then this will cause problems when new environments are created.");
		
		// Give a big warning if the DbMaps.PropertyDataTypeMap value does not agree with what we're doing
		DbMaps dbMaps = null;
		try {
			dbMaps = IngestModelMoxyOxm.dbMapsContainer.get(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP));
		}
		catch( AAIException ae ){
			String emsg = "Could not instantiate a copy of dbMaps. ";
			System.out.println( emsg );
         	System.exit(1);
		}
		
		String warningMsg = "";
		if( !dbMaps.PropertyDataTypeMap.containsKey(propName) ){
			String emsg = "Property Name = [" + propName + "] not found in PropertyDataTypeMap. ";
			System.out.println( emsg );
         	System.exit(1);
	    }
		else {
			String currentDataType = dbMaps.PropertyDataTypeMap.get(propName);
			if( !currentDataType.equals(targetDataTypeStr) ){
				warningMsg = "TargetDataType [" + targetDataTypeStr + "] does not match what is in DbMaps (" + currentDataType + ").";
		    }
		}
		
		if( !warningMsg.equals("") ){
			System.out.println("\n>>> WARNING <<<< " );
			System.out.println(">>> " + warningMsg + " <<<");
			System.out.println(">>>      !!!    WARNING -- this change may be overwritten in some environments if  <<<");
			System.out.println(">>>      !!!       entries are out of synch with what is done with this script. <<<");
			System.out.println(">>> WARNING <<<< " );
		}
        
		System.out.println(">>> Processing will begin in 5 seconds (unless interrupted). <<<");
		try {
			// Give them a chance to back out of this
			Thread.sleep(5000);
		} catch ( java.lang.InterruptedException ie) {
			System.out.println( " DB Schema Update has been aborted. ");
			System.exit(1);
		}
		
		TitanManagement graphMgt = null;
		TitanGraph graph = null;
		try {   
	 	    AAIConfig.init();
			ErrorLogHelper.loadProperties();

			System.out.println("    ---- NOTE --- about to open graph (takes a little while)\n");

			graph = AAIGraph.getInstance().getGraph();
			if( graph == null ){
				 String emsg = "Not able to get a graph object in ChangePropertyCardinality.java\n";
	          	 System.out.println( emsg );
	          	 System.exit(1);
	 		}
	    	
			// Make sure this property is in the DB.
			graphMgt = graph.openManagement();
			if( graphMgt == null ){
				 String emsg = "Not able to get a graph Management object in ChangePropertyCardinality.java\n";
	          	 System.out.println( emsg );
	          	 System.exit(1);
	 		}
					
			PropertyKey origPropKey = graphMgt.getPropertyKey(propName);
			if( origPropKey == null ){
				// The old one wasn't there, so there's nothing to do
				String emsg = "The propName = [" + propName + "] is not defined in our graph. ";
		      	System.out.println( emsg );
		      	System.exit(1);
			}
			
			if( origPropKey.cardinality().equals(Cardinality.valueOf(targetCardinality)) ){
				// The existing one already has cardinality of what they're asking for.
				String emsg = "The propName = [" + propName + "] already has Cardinality of [" + targetCardinality + "]. ";
		      	System.out.println( emsg );
		      	System.exit(0);
			}
			
			// Rename this property to a backup name (old name with "retired_" appended plus a dateStr)
			SimpleDateFormat d = new SimpleDateFormat("MMddHHmm");
			d.setTimeZone(TimeZone.getTimeZone("GMT"));
			String dteStr = d.format(new Date()).toString();
			String retiredName = propName + "-" + dteStr + "-RETIRED";
			graphMgt.changeName( origPropKey, retiredName );
			
			// Create a new property using the original property name and the targetDataType
			PropertyKey freshPropKey = graphMgt.makePropertyKey(propName).dataType(dType).cardinality(Cardinality.valueOf(targetCardinality)).make();
			
			System.out.println("Committing schema changes with graphMgt.tx().commit()");
			graphMgt.commit();
			graph.tx().commit();
			graph.close();
		
			
			// Get A new graph object
			System.out.println("    ---- NOTE --- about to open a second graph object (takes a little while)\n");

			graph = AAIGraph.getInstance().getGraph();
			if( graph == null ){
				 String emsg = "Not able to get a graph object in SchemaMod.java\n";
	          	 System.out.println( emsg );
	          	 System.exit(1);
	 		}
			
			// For each node that has this property, update the new from the old and then remove the 
			// old property from that node 
			// ---- NOTE ---- We're not preserving data at this point
			Iterable <Vertex> verts = null;
			verts= graph.query().has(retiredName).vertices();
			Iterator <Vertex> it = verts.iterator();
			int vtxCount = 0;
			while( it.hasNext() ){
				vtxCount++;
				TitanVertex tmpVtx = (TitanVertex)it.next();
				String tmpVid = tmpVtx.id().toString();
				
				Object origVal = tmpVtx.property(retiredName).orElse(null);
				if( preserveData ){
					tmpVtx.property(propName,origVal);
					System.out.println("INFO -- just did the add of the freshPropertyKey and updated it with the orig value (" +
							origVal.toString() + ")");
				}
				else {
					// existing nodes just won't have this property anymore
					// 
				}
				tmpVtx.property(retiredName).remove();
				System.out.println("INFO -- just did the remove of the " + retiredName + " from this vertex. (vid=" + tmpVid + ")");
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
	    	if( graphMgt != null && graphMgt.isOpen() ){
	  	    	// Any changes that worked correctly should have already done their commits.
	  	    	graphMgt.rollback();
	  	    }
	    	if( graph != null ){
	  	    	// Any changes that worked correctly should have already done their commits.
	  	    	graph.tx().rollback();
	  		    graph.close();
	  	    }
	    	
	  	}
	 
	    System.exit(0);
	    
	}// End of main()
	

}


