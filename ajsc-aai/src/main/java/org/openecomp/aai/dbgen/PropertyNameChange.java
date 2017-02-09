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

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.openecomp.aai.dbmap.AAIGraph;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;

import com.att.eelf.configuration.Configuration;
import com.thinkaurelius.titan.core.PropertyKey;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanProperty;
import com.thinkaurelius.titan.core.TitanVertex;
import com.thinkaurelius.titan.core.schema.TitanManagement;


public class PropertyNameChange {

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
		
		// NOTE -- We're just working with properties that are used for NODES for now. 
	   
    	TitanGraph graph = null;
    	TitanManagement graphMgt = null;;
    	Boolean preserveData = true;
    	String propName = "";
		String targetPropName = "";
		String targetNodeType = "";
		String skipCommit = "";
		boolean noCommit = false;
		
		
		String usageString = "Usage: PropertyNameChange propertyName targetPropertyName nodeType(or NA) skipCommit(true|false) \n";
		if( args.length != 4 ){
			String emsg = "Four Parameters are required.  \n" + usageString;
			System.out.println( emsg );
         	System.exit(1);
		}
		else {
			propName = args[0];
			targetPropName = args[1];
			targetNodeType = args[2];
			skipCommit = args[3];
			if ( skipCommit.toLowerCase().equals("true"))
				noCommit = true;
		}
				
		if( propName.equals("") ){
			 String emsg = "Bad parameter - propertyName cannot be empty.  \n" + usageString;
			 System.out.println( emsg );
          	 System.exit(1);
		}


		
		try {
			AAIConfig.init();
			ErrorLogHelper.loadProperties();
		}
		catch( Exception ae ){
			String emsg = "Problem with either AAIConfig.init() or ErrorLogHelper.LoadProperties(). ";
			System.out.println( emsg + "[" + ae.getMessage() + "]");
         	System.exit(1);
		}

       
		System.out.println(">>> Processing will begin in 5 seconds (unless interrupted). <<<");
		try {
			// Give them a chance to back out of this
			Thread.sleep(5000);
		} catch ( java.lang.InterruptedException ie) {
			System.out.println( " DB Schema Update has been aborted. ");
			System.exit(1);
		}
		
		try {
			System.out.println("    ---- NOTE --- about to open graph (takes a little while)\n");

			graph = AAIGraph.getInstance().getGraph();
			if( graph == null ){
				 String emsg = "Not able to get a graph object in SchemaMod.java\n";
	          	 System.out.println( emsg );
	          	 System.exit(1);
	 		}
	    	
			// Make sure this property is in the DB.
			graphMgt = graph.openManagement();
			if( graphMgt == null ){
				 String emsg = "Not able to get a graph Management object in SchemaMod.java\n";
	          	 System.out.println( emsg );
	          	 System.exit(1);
	 		}
			PropertyKey origPropKey = graphMgt.getPropertyKey(propName);
			if( origPropKey == null ){
				String emsg = "The propName = [" + propName + "] is not defined in our graph. ";
	          	System.out.println( emsg );
	          	System.exit(1);
			}
			origPropKey = graphMgt.getPropertyKey(targetPropName);
			if( origPropKey == null ){
				String emsg = "The targetPropName = [" + targetPropName + "] is not defined in our graph. ";
	          	System.out.println( emsg );
	          	System.exit(1);
			}			
			
			if ( !targetNodeType.equals("NA")) {
				if ( graphMgt.getVertexLabel(targetNodeType) == null ) {
					String emsg = "The targetNodeType = [" + targetNodeType + "] is not defined in our graph. ";
					System.out.println( emsg );
					System.exit(1);
				}
			}
			
			// For each node that has this property, update the new from the old and then remove the 
			// old property from that node
			Iterable <Vertex> verts = null;
			int vtxCount = 0;
			String label;
			long longId;
			
			int propertyCount;
			
			Iterator<VertexProperty<Object>> titanProperties = null;
			
			VertexProperty<Object> tmpProperty = null;
			Object origVal;
			Object targetVal;

			if ( targetNodeType.equals("NA")) {
				verts= graph.query().has(propName).vertices();
				Iterator <Vertex> it = verts.iterator();

				while( it.hasNext() ){
					
					TitanVertex tmpVtx = (TitanVertex)it.next();
					String tmpVid = tmpVtx.id().toString();
					
					//System.out.println("Show what we have in the vertex before trying to do an update...");
					//ArrayList <String> retArr = DbMeth.showPropertiesForNode("junkTransId", "junkFromAppId", tmpVtx);
			   		//for( String info : retArr ){ System.out.println(info); }
					
					origVal = tmpVtx.<Object>property(propName).orElse(null);
					targetVal = tmpVtx.<Object>property(targetPropName).orElse(null);
						
					label = tmpVtx.label();
					longId = tmpVtx.longId();
					
					if ( targetVal != null ) {
						System.out.println( "vertex [" + label + "] id " + tmpVid + " has " + targetPropName + 
							" with value " + targetVal + ", skip adding with value " + origVal);
						continue;
					}
					vtxCount++;
					titanProperties = tmpVtx.properties(); // current properties
					
					propertyCount = 0;
					
					while( titanProperties.hasNext() ){
						
						tmpProperty = titanProperties.next(); 

						if ( propertyCount == 0 )
							System.out.print( "adding to [" + label + "] vertex id " + tmpVid + " with existing properties " +
								tmpProperty.toString() );
						else
							System.out.print(", " + tmpProperty.toString());
						++propertyCount;
					}
					
					if ( propertyCount > 0 ) {
						System.out.println("");

						tmpVtx.property(targetPropName,origVal);					
						System.out.println("INFO -- just did the add using " + longId +
								" with label " + label + "] and updated it with the orig value (" +
								origVal.toString() + ")");
						titanProperties = tmpVtx.properties(); // current properties
						propertyCount = 0;
	
						
						while( titanProperties.hasNext() ){
							tmpProperty = titanProperties.next(); 
							if ( propertyCount == 0 )
								System.out.print( "new property list for [" + label + "] vertex id " + tmpVid + " with existing properties " +
									tmpProperty.toString() );
							else
								System.out.print(", " + tmpProperty.toString());
							++propertyCount;
						}
						
						if ( propertyCount > 0 )
							System.out.println("");
					}
				}
			} else {
				// targetNodeType is NA

                verts= graph.query().has("aai-node-type", targetNodeType).vertices();
                if( verts != null ){
                      // We did find some matching vertices
                	Iterator <?> it = verts.iterator();
                	Object propVal;
                	while( it.hasNext() ){
                		TitanVertex v = (TitanVertex)it.next();
     	  				label = v.label();
    	  				longId = v.longId();
    					targetVal = v.<Object>property(targetPropName).orElse(null);
    					origVal = v.<Object>property(propName).orElse(null);
    					
    					if ( origVal == null)
    						continue;
    					
    					if ( targetVal != null ) {
    						System.out.println( "vertex  [" + label + "] id " + longId + " has " + targetPropName + 
    							" with value " + targetVal + ", skip adding with value " + origVal);
    						continue;
    					}
    					titanProperties = v.properties(); // current properties
    					propertyCount = 0;
                         if ( v.<Object>property(propName).orElse(null) != null ) {
                               propVal = v.<Object>property(propName).orElse(null);
                                v.property(targetPropName, propVal);
                               ++vtxCount;
           					while( titanProperties.hasNext() ){
        						
        						tmpProperty = titanProperties.next(); 
        						if ( propertyCount == 0 )
        							System.out.print( "adding to vertex id " + longId + " with existing properties " +
        								tmpProperty.toString() );
        						else
        							System.out.print(", " + tmpProperty.toString());
        						++propertyCount;
        					}
        					
        					if ( propertyCount > 0 )
        						System.out.println("");
           					System.out.println("INFO -- just did the add target [" + targetNodeType + "] using " + longId +
        							" with label " + label + "] and updated it with the orig value (" +
        							propVal.toString() + ")");
           					propertyCount = 0;
           					while( titanProperties.hasNext() ){
        						
        						tmpProperty = titanProperties.next(); 
        						if ( propertyCount == 0 )
        							System.out.print( "new property list for vertex [" + label + "] id " + longId + " with existing properties " +
        								tmpProperty.toString() );
        						else
        							System.out.print(", " + tmpProperty.toString());
        						++propertyCount;
        					}
        					
        					if ( propertyCount > 0 )
        						System.out.println("");
                         }
                	}
                }

			}
			
			System.out.println("added properties data for " + vtxCount + " vertexes.  noCommit " + noCommit);
			if ( !noCommit )
				graph.tx().commit();
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


