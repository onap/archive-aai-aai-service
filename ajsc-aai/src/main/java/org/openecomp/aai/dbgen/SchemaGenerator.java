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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;

import com.thinkaurelius.titan.core.Cardinality;
import com.thinkaurelius.titan.core.Multiplicity;
import com.thinkaurelius.titan.core.PropertyKey;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.schema.TitanManagement;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.dbmodel.DbEdgeRules;
import org.openecomp.aai.ingestModel.DbMaps;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.logging.AAILogger;
import org.openecomp.aai.logging.LogLine;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.util.Bytes;



public class SchemaGenerator{

	private static AAILogger aaiLogger = new AAILogger(SchemaGenerator.class.getName());
	private static final String FROMAPPID = "AAI-DB";
	private static final String TRANSID = UUID.randomUUID().toString();
	
	 // public static void main(String[] args) {
	 //       System.out.println("just try the hbase piece ..");
	 //       createHBaseLogTableIfNeeded();
	 //}
	
	
    /**
     * Load schema into titan.
     *
     * @param graph the graph
     * @param graphMgmt the graph mgmt
     */
    public static void loadSchemaIntoTitan(final TitanGraph graph, final TitanManagement graphMgmt) {
       
    	LogLine logline = new LogLine();
    	logline.init("aaidbgen", TRANSID, FROMAPPID, "loadSchemaIntoTitan");
    	LogLine dbLogline = new LogLine();
    	dbLogline.init("aaidbgen", TRANSID, FROMAPPID, "loadSchemaIntoTitan");
    	
    	try {
    		AAIConfig.init(TRANSID, FROMAPPID);
    	}
    	catch (Exception ex){
			String emsg = " ERROR - Could not run AAIConfig.init(). ";
			dbLogline.add("msg", emsg); 
			System.out.print(emsg);
			System.out.println("Exception.getMessage() = [" + ex.getMessage() + "]");
			aaiLogger.debug(dbLogline, dbLogline.finish(true));
	        aaiLogger.info(logline, false, "AAI_4002");
			System.exit(1);
		}
    	
        // NOTE - Titan 0.5.3 doesn't keep a list of legal node Labels.  
    	//   They are only used when a vertex is actually being created.  Titan 1.1 will keep track (we think).
        	

		// Use EdgeRules to make sure edgeLabels are defined in the db.  NOTE: the multiplicty used here is 
    	// always "MULTI".  This is not the same as our internal "Many2Many", "One2One", "One2Many" or "Many2One"
    	// We use the same edge-label for edges between many different types of nodes and our internal
    	// multiplicty definitions depends on which two types of nodes are being connected.
    	HashMap<String, String> labelHash = new HashMap<String, String>();
		Iterator<String> edgeLabelKeyIterator = DbEdgeRules.EdgeRules.keySet().iterator();
		while( edgeLabelKeyIterator.hasNext() ){
			// We re-use a lot of labels, so put them in a hash so we only look at each one once.
			String lKey = edgeLabelKeyIterator.next();
			Collection <String> labelInfoColl = DbEdgeRules.EdgeRules.get(lKey);
			Iterator <String> labItr = labelInfoColl.iterator();
			while( labItr.hasNext() ){
				// Note - there's never more than one... But it is defined as a multimap, so technically, we need to loop
				String labInfo = labItr.next();
				String [] flds = labInfo.split(",");
				String label = flds[0];
				labelHash.put(label,  "");
			}
		}
			
		for( String key: labelHash.keySet() ){
			String labelTxt = key;
			if( graphMgmt.containsRelationType(labelTxt) ){
            	String msg = " EdgeLabel  [" + labelTxt + "] already existed. ";
            	System.out.println( msg );
            	dbLogline.add("msg", msg);
            	}
            else {
            	String msg = "Making EdgeLabel: [" + labelTxt + "]";
            	System.out.println( msg );
            	dbLogline.add("msg", msg);
            	graphMgmt.makeEdgeLabel(labelTxt).multiplicity(Multiplicity.valueOf("MULTI")).make();
            }
        }     

		IngestModelMoxyOxm moxyMod = new IngestModelMoxyOxm();
		DbMaps dbMaps = null;
		try {
			ArrayList <String> defaultVerLst = new ArrayList <String> ();
			defaultVerLst.add( AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP) );
			moxyMod.init( defaultVerLst, false);
			dbMaps = IngestModelMoxyOxm.dbMapsContainer.get(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP));
			}
		catch (Exception ex){
			String emsg = " ERROR - Could not get the DbMaps object. ";
			dbLogline.add("msg", emsg); 
			System.out.print(emsg);
			System.out.println("Exception.getMessage() = [" + ex.getMessage() + "]");
			aaiLogger.debug(dbLogline, dbLogline.finish(true));
	        aaiLogger.info(logline, false, "AAI_4000");
			System.exit(1);
		}
	
		// dbMaps.NodeMapIndexedProps		dbMaps.NodeMapUniqueProps  -- These maps capture which properties
		//      are indexed/unique-indexed per nodeType.   But for Titan 0.5.3 we are only using global-level
		//      indexes, so we will translate this to a single hash for use below.
		HashMap<String, Boolean> propertyIsIndexed = new HashMap<String, Boolean>();
		HashMap<String, Boolean> propertyIsUnique = new HashMap<String, Boolean>();
		
		Iterator<String> indexKeyIterator = dbMaps.NodeMapIndexedProps.keySet().iterator();
		while( indexKeyIterator.hasNext() ){
			// Our maps list which props are indexed by nodeType, but we just want the list of the indexed properties
			String iKey = indexKeyIterator.next();
			Collection <String> indexInfoColl = dbMaps.NodeMapIndexedProps.get(iKey);
			Iterator <String> indItr = indexInfoColl.iterator();
			while( indItr.hasNext() ){
				// Note - there's never more than one... But it is defined as a multimap, so technically, we need to loop
				String indInfo = indItr.next();
				String [] flds = indInfo.split(",");
				for( int i = 0; i < flds.length; i++ ){
					String propName = flds[i];
					propertyIsIndexed.put(propName, true);
				}
			}
		}
		
		Iterator<String> uniqueIndexKeyIterator = dbMaps.NodeMapUniqueProps.keySet().iterator();
		while( uniqueIndexKeyIterator.hasNext() ){
			// Our maps list which props are indexed by nodeType, but we just want the list of the unique indexed properties
			String uiKey = uniqueIndexKeyIterator.next();
			Collection <String> uniqueIndexInfoColl = dbMaps.NodeMapUniqueProps.get(uiKey);
			Iterator <String> unIndItr = uniqueIndexInfoColl.iterator();
			while( unIndItr.hasNext() ){
				// Note - there's never more than one... But it is defined as a multimap, so technically, we need to loop
				String unIndInfo = unIndItr.next();
				String [] flds = unIndInfo.split(",");
				for( int i = 0; i < flds.length; i++ ){
					String propName = flds[i];
					propertyIsUnique.put(propName, true);
				}
			}
		}
		
		// dbMaps.PropertyDataTypeMap  -- key is the property name, value is the data type
		// In our DB, Cardinality is only either SINGLE or SET.   This is also captured in the PropertyDataTypeMap 		
		//    By default, cardinality is SINGLE, but if the dataType looks like, "Set<String>" -- then the data type is
		//    String, but the cardinality is SET.
		for( Map.Entry<?,?> entry : dbMaps.PropertyDataTypeMap.entrySet() ){
            String vName = (String) entry.getKey();
            String dataTypeStr = (String) entry.getValue();
            Class<?> dType = String.class;  // Default to String
            String cardinality = "SINGLE";  // Default cardinality to SINGLE

            if(dataTypeStr.equals("Set<String>")){ 
            	dType = String.class; 
            	cardinality = "SET";
            }
            else if(dataTypeStr.equals("Set<Integer>")){ 
            	dType = Integer.class; 
            	cardinality = "SET";
            	
            }
            else if(dataTypeStr.equals("String")){ dType = String.class; }
            else if(dataTypeStr.equals("Integer")){ dType = Integer.class; }
            else if(dataTypeStr.equals("Boolean")){ dType = Boolean.class; }
            else if(dataTypeStr.equals("Character")){ dType = Character.class; }
            else if(dataTypeStr.equals("Long")){ dType = Long.class; }
            else if(dataTypeStr.equals("Float")){ dType = Float.class; }
            else if(dataTypeStr.equals("Double")){ dType = Double.class; }
            else {
            	// Default to String -- but flag it
            	dType = String.class;
            	String msg = ">>> WARNING >>> UNRECOGNIZED dataType: [" + dataTypeStr + "] found for [" + vName + "] ";
            	System.out.println( "\n" + msg + "\n" );
            	dbLogline.add("msg", msg);
            }
            
            if( graphMgmt.containsRelationType(vName) ){
            	String msg = " PropertyKey  [" + vName + "] already existed in the DB. ";
            	System.out.println( msg );
            	dbLogline.add("msg", msg);
            }
            else {
            	String msg = "Creating PropertyKey: [" + vName + "], ["+ dataTypeStr + "], [" + cardinality + "]";
                System.out.println( msg );
            	dbLogline.add("msg", msg);
            	PropertyKey propK = graphMgmt.makePropertyKey(vName).dataType(dType).cardinality(Cardinality.valueOf(cardinality)).make();
                if( propertyIsIndexed.containsKey(vName) ){
                	if( propertyIsUnique.containsKey(vName) ){
                    	 msg = " Add Unique index for PropertyKey: [" + vName + "]";
                         System.out.println( msg );
                         dbLogline.add("msg", msg);
                         graphMgmt.buildIndex(vName,Vertex.class).addKey(propK).unique().buildCompositeIndex();
                    }
                    else {
                    	msg = " Add index for PropertyKey: [" + vName + "]";
                         System.out.println( msg );
                         dbLogline.add("msg", msg);
                         graphMgmt.buildIndex(vName,Vertex.class).addKey(propK).buildCompositeIndex();
                    }
                }
                else {
                	msg = " No index added for PropertyKey: [" + vName + "]";
                    System.out.println( msg );
                    dbLogline.add("msg", msg);
                }
            }
        } 
		aaiLogger.debug(dbLogline, dbLogline.finish(true));
        aaiLogger.info(logline, true, "0");
        System.out.println("-- About to call graphMgmt commit");
        graphMgmt.commit();

    }// End of loadSchemaIntoTitan()
    
    
    public static void createHBaseLogTableIfNeeded( ) {
 		// Check to see if the HBase Logging table exists.  If it does not, then create it.
 		
 		int ttlDays = 15;   // should get this from a property file
 		try {
 			String ttlValDaysStr = AAIConfig.get("hbase.column.ttl.days");
 			if( ttlValDaysStr != null ){
 				int ttlValDaysInt = Integer.parseInt(ttlValDaysStr);
 				ttlDays = ttlValDaysInt;
 			}
 		} catch ( Exception e) { /* don't worry - we'll just use the default */ }
 		int ttlSec = ttlDays * 86400;
 		System.out.println( "Using ttl value of: " + ttlSec );
 		
 		String tblName = "aailogging";
 		try {
 			String logTblVal = AAIConfig.get(AAIConstants.HBASE_TABLE_NAME);
 			if( logTblVal != null && !logTblVal.equals("") ){
 				tblName = logTblVal;
 			}
 		} catch ( Exception e) { /* don't worry - we'll just use the default */ }
 		System.out.println( "Using logging table name of: " + tblName );
 			
 		List <String> colNames = Arrays.asList("payload", "resource", "transaction");
 		HBaseAdmin hbAdmin = null;
 		
 		LogLine logline = new LogLine();
    	logline.init("aaidbgen", TRANSID, FROMAPPID, "createHBaseLogTableIfNeeded");
    	LogLine dbLogline = new LogLine();
    	dbLogline.init("aaidbgen", TRANSID, FROMAPPID, "createHBaseLogTableIfNeeded");
    	try {
    		AAIConfig.init(TRANSID, FROMAPPID);
    	}
    	catch (Exception ex){
			String emsg = " ERROR - Could not run AAIConfig.init(). ";
			dbLogline.add("msg", emsg); 
			System.out.print(emsg);
			System.out.println("Exception.getMessage() = [" + ex.getMessage() + "]");
			aaiLogger.debug(dbLogline, dbLogline.finish(true));
	        aaiLogger.info(logline, false, "AAI_4002");
			System.exit(1);
		}
 		
 		try {
 			System.out.println(" First create an HBaseConfiguration object. ");
	 		Configuration hConf = HBaseConfiguration.create();
	 		
	 		String val2Use = null;
	 		Boolean setOk = false;
	 		try {
	 			val2Use = AAIConfig.get(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_QUORUM);
	 			if( val2Use != null ){
	 				hConf.set(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_QUORUM, val2Use );
	 				setOk = true;
	 			}
	 		} catch ( Exception e) { /* don't worry */ }
	 		if( !setOk ){
	 			System.out.println("Warning: we will not be setting " + AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_QUORUM 
 					+ " on the HBaseAdmin object." );
	 		}
	 		
	 		try {
	 			setOk = false;
	 			val2Use = AAIConfig.get(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_CLIENTPORT);
	 			if( val2Use != null ){
	 				hConf.set(AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_CLIENTPORT, val2Use );
	 				setOk = true;
	 			}
	 		} catch ( Exception e) { /* don't worry */ }
	 		if( !setOk ){
	 			System.out.println("Warning: we will not be setting " + AAIConstants.HBASE_CONFIGURATION_ZOOKEEPER_CLIENTPORT 
 					+ " on the HBaseAdmin object." );
	 		}
	 		
	 		try {
	 			setOk = false;
	 			val2Use = AAIConfig.get(AAIConstants.HBASE_TABLE_NAME);
	 			if( val2Use != null ){
	 				hConf.set(AAIConstants.HBASE_TABLE_NAME, val2Use );
	 				setOk = true;
	 			}
	 		} catch ( Exception e) { /* don't worry */ }
	 		if( !setOk ){
	 			System.out.println("Warning: we will not be setting " + AAIConstants.HBASE_TABLE_NAME 
 					+ " on the HBaseAdmin object." );
	 		}
	 			 		
	 		try {
	 			setOk = false;
	 			val2Use = AAIConfig.get(AAIConstants.HBASE_TABLE_TIMESTAMP_FORMAT);
	 			if( val2Use != null ){
	 				hConf.set(AAIConstants.HBASE_TABLE_TIMESTAMP_FORMAT, val2Use );
	 				setOk = true;
	 			}
	 		} catch ( Exception e) { /* don't worry */ }
	 		if( !setOk ){
	 			System.out.println("Warning: we will not be setting " + AAIConstants.HBASE_TABLE_TIMESTAMP_FORMAT 
 					+ " on the HBaseAdmin object." );
	 		}
	 			 		
	 		try {
	 			setOk = false;
	 			val2Use = AAIConfig.get(AAIConstants.HBASE_ZOOKEEPER_ZNODE_PARENT);
	 			if( val2Use != null ){
	 				hConf.set(AAIConstants.HBASE_ZOOKEEPER_ZNODE_PARENT, val2Use );
	 				setOk = true;
	 			}
	 		} catch ( Exception e) { /* don't worry */ }
	 		if( !setOk ){
	 			System.out.println("Warning: we will not be setting " + AAIConstants.HBASE_ZOOKEEPER_ZNODE_PARENT 
 					+ " on the HBaseAdmin object." );
	 		}
	
	 		System.out.println(" Use the hConf to get an HBaseAdmin object. ");
	 		hbAdmin = new HBaseAdmin(hConf);
	 	    
	 	    System.out.println(" Use the hbAdmin object to check if the table exists or not. ");
	 	    boolean tblExists = hbAdmin.tableExists(tblName);
	
	 		if( tblExists ){
	 			System.out.println("HBase Logging Table " + tblName + " already exists.");
	 		}
	 		else {
	 			System.out.println("HBase Logging Table " + tblName + " does not yet exist.  We will try to create it.");
	 			
	 			HTableDescriptor tabledescriptor = new HTableDescriptor(Bytes.toBytes(tblName));
	 			hbAdmin.createTable(tabledescriptor);
	 			
	 			for( Iterator<String> iter = colNames.iterator(); iter.hasNext(); ) {
	 			    String colNameStr = iter.next();
	 			    HColumnDescriptor hColDes = new HColumnDescriptor( colNameStr );
	 			    hColDes.setTimeToLive(ttlSec);  
	 			    hbAdmin.addColumn(tblName, hColDes);  
	 			}
	 			
	 			System.out.println("HBase Logging Table " + tblName + " has been added.");
	 			aaiLogger.debug(dbLogline, dbLogline.finish(true));
	 	        aaiLogger.info(logline, true, "0");
	 		}
 		}
 		catch (Exception ex){
			String emsg = " ERROR trying to add the Hbase Logging Table. ";
			dbLogline.add("msg", emsg); 
			System.out.print(emsg);
			System.out.println("Exception.getMessage() = [" + ex.getMessage() + "]");
			aaiLogger.debug(dbLogline, dbLogline.finish(true));
			aaiLogger.info(logline, false, "AAI_4000");
			System.exit(1);
 		}
 		finally {
 			if( hbAdmin != null ){
 				try {
 					hbAdmin.close();
 				}
 				catch( IOException e ){
 					String emsg = " ERROR trying to close the HBaseAdmin object. ";
 					dbLogline.add("msg", emsg); 
 					System.out.print(emsg);
 					System.out.println("Exception.getMessage() = [" + e.getMessage() + "]");
 					aaiLogger.debug(dbLogline, dbLogline.finish(true));
 					aaiLogger.info(logline, false, "AAI_4000");
 					System.exit(1);
 				}
 			}
 		}
 	} // end createHBaseLogTableIfNeeded()

}


