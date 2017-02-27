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

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.openecomp.aai.dbmap.AAIGraph;
import org.openecomp.aai.dbmodel.DbEdgeRules;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.util.AAIConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.thinkaurelius.titan.core.TitanEdge;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanTransaction;



public class UpdateEdgeTags {

	private static 	final  String    FROMAPPID = "AAI-DB";
	private static 	final  String    TRANSID   = UUID.randomUUID().toString();
	
	//private static AAILogger aaiLogger = new AAILogger(UpdateEdgeTags.class.getName());

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
	
	  	if( args == null || args.length != 1 ){
				String msg = "usage:  UpdateEdgeTags edgeRuleKey  (edgeRuleKey can be either, all, or a rule key like 'nodeTypeA|nodeTypeB') \n";
				System.out.println(msg);
				System.exit(1);
		}
	  	String edgeRuleKeyVal = args[0];

		TitanGraph graph = null;

		HashMap <String,Object> edgeRuleHash = new HashMap <String,Object>();
		HashMap <String,Object> edgeRulesFullHash = new HashMap <String,Object>();
		HashMap <String,Object> edgeRuleLabelToKeyHash = new HashMap <String,Object>();
		ArrayList <String> labelMapsToMultipleKeys = new ArrayList<String>();
		
    	int tagCount = DbEdgeRules.EdgeInfoMap.size();
		// Loop through all the edge-rules make sure they look right and
    	//    collect info about which labels support duplicate ruleKeys.
    	Iterator<String> edgeRulesIterator = DbEdgeRules.EdgeRules.keySet().iterator();
    	while( edgeRulesIterator.hasNext() ){
        	String ruleKey = edgeRulesIterator.next();
        	Collection <String> edRuleColl = DbEdgeRules.EdgeRules.get(ruleKey);
        	Iterator <String> ruleItr = edRuleColl.iterator();
    		if( ruleItr.hasNext() ){
    			// For now, we only look for one type of edge between two nodes.
    			String fullRuleString = ruleItr.next();
    			edgeRulesFullHash.put(ruleKey,fullRuleString);
    			// An EdgeRule is comma-separated and the first item is the edgeLabel
  			  	String [] rules = fullRuleString.split(",");
  			  	//System.out.println( "rules.length = " + rules.length + ", tagCount = " + tagCount );
  			  	if( rules.length != tagCount ){
  			  		String detail = "Bad EdgeRule data (itemCount=" + rules.length + ") for key = [" + ruleKey + "].";
  			  		System.out.println(detail);
  			  		System.exit(0);
  			  	}
  			  	String edgeLabel = rules[0];
  			  	if( edgeRuleLabelToKeyHash.containsKey(edgeLabel) ){
  			  		// This label maps to more than one edge rule - we'll have to figure out
  			  		// which rule applies when we look at each edge that uses this label.
  			  		// So we take it out of mapping hash and add it to the list of ones that
  			  		// we'll need to look up later.
  			  		edgeRuleLabelToKeyHash.remove(edgeLabel);
  			  		labelMapsToMultipleKeys.add(edgeLabel);
  			  	}
  			  	else {
  			  		edgeRuleLabelToKeyHash.put(edgeLabel, ruleKey);
  			  	}
    		}
    	}
    	
		if( ! edgeRuleKeyVal.equals( "all" ) ){
			// If they passed in a (non-"all") argument, that is the single edgeRule that they want to update.
			// Note - the key looks like "nodeA|nodeB" as it appears in DbEdgeRules.EdgeRules
			Collection <String> edRuleColl = DbEdgeRules.EdgeRules.get(edgeRuleKeyVal);
        	Iterator <String> ruleItr = edRuleColl.iterator();
    		if( ruleItr.hasNext() ){
    			// For now, we only look for one type of edge between two nodes (Ie. for one key).
    			String edRule = ruleItr.next();
    			edgeRuleHash.put(edgeRuleKeyVal, edRule);
    			System.out.println("Adding this rule to list of rules to do: key = " + edgeRuleKeyVal + ", rule = [" + edRule + "]");
    		}
    		else {
    			String msg = " Error - Unrecognized edgeRuleKey: [" + edgeRuleKeyVal + "]. ";
    			System.out.println(msg);
    			System.exit(0);
    		}
		}
		else {
			// They didn't pass a target ruleKey in, so we'll work on all types of edges
			edgeRuleHash.putAll(edgeRulesFullHash);
		}
		
    	try {   
    		AAIConfig.init(TRANSID, FROMAPPID);
    		System.out.println("    ---- NOTE --- about to open graph (takes a little while)--------\n");
    		ErrorLogHelper.loadProperties();

    		graph = AAIGraph.getInstance().getGraph();
    	
    		if( graph == null ){
    			String emsg = "null graph object in updateEdgeTags() \n";
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
	 		e2.printStackTrace();
	 		System.exit(0);
        }

    	TitanTransaction g = graph.newTransaction();
		try {  
        	 Iterable <?> edges = graph.query().edges();
        	 Iterator <?> edgeItr = edges.iterator();
        	 
     		 // Loop through all edges and update their tags if they are a type we are interested in.
        	 //    Sorry about looping over everything, but for now, I can't find a way to just select one type of edge at a time...!?
			 StringBuffer sb;
			 boolean missingEdge = false;
        	 while( edgeItr != null && edgeItr.hasNext() ){
				 TitanEdge tmpEd = (TitanEdge) edgeItr.next();
	        	 String edLab = tmpEd.label().toString(); 
	        	 
	     		 // Since we have edgeLabels that can be used for different pairs of node-types, we have to
	        	 //    look to see what nodeTypes this edge is connecting (if it is a label that could do this).
	        	 String derivedEdgeKey = "";
	        	 if( labelMapsToMultipleKeys.contains(edLab) ){
	        		 // need to figure out which key is right for this edge
	        		 derivedEdgeKey = DbMeth.deriveEdgeRuleKeyForThisEdge( TRANSID, FROMAPPID, g, tmpEd );
	        	 }
	        	 else {
	        		 // This kind of label only maps to one key -- so we can just look it up.
	        		 if ( edgeRuleLabelToKeyHash.get(edLab) == null ) {
	        			 if ( !missingEdge ) {
	        				 System.out.print("DEBUG - missing edge(s) in edgeRuleLabelToKeyHash " + edgeRuleLabelToKeyHash.toString());
	        				 missingEdge = true;
	        			 }
		        		 sb = new StringBuffer();
	        			 Vertex vIn = null;
	        			 Vertex vOut = null;
	        			 Object obj = null;
	        			 vIn = tmpEd.vertex(Direction.IN);
	        			 if ( vIn != null ){
	        				 obj = vIn.<String>property("aai-node-type").orElse(null);
	        				 if ( obj != null ) {
	        					 sb.append("from node-type " + obj.toString());
	        				 
	        					 obj = vIn.id();
	        					 sb.append(" id " + obj.toString());
	        				 } else {
	        					 sb.append(" missing from node-type ");
	        				 }
	        			 } else {
	        				 sb.append(" missing inbound vertex ");
	        			 }
	        			 vOut = tmpEd.vertex(Direction.OUT);
	        			 if ( vOut != null ) {
	        				 obj = vOut.<String>property("aai-node-type").orElse(null);
	        				 if ( obj != null ) {
		        				 sb.append(" to node-type " + obj.toString());
		        				 obj = vOut.id();
		        				 sb.append(" id " + obj.toString());
	        				 } else {
	        					 sb.append(" missing to node-type ");
	        				 }
	        			 } else {
	        				 sb.append(" missing to vertex ");
	        			 }
	        			 System.out.println("DEBUG - null entry for [" + edLab + "] between " + sb.toString());
	        			 continue;
	        		 }
	        		 derivedEdgeKey = edgeRuleLabelToKeyHash.get(edLab).toString();
	        	 }
	        	 
	        	 if( edgeRuleHash.containsKey(derivedEdgeKey) ){
	        		 // this is an edge that we want to update
	        		 System.out.print("DEBUG - key = " + derivedEdgeKey + ", label = " + edLab 
	        				 + ", for id = " + tmpEd.id().toString() + ", set: ");
			         HashMap <String,Object> edgeTagPropHash = DbMeth.getEdgeTagPropPutHash(TRANSID, FROMAPPID, derivedEdgeKey);
			         for( Map.Entry<String, Object> entry : edgeTagPropHash.entrySet() ){
		  				 System.out.print( "[" + entry.getKey() + " = " + entry.getValue() + "] " );
		  				 tmpEd.property( entry.getKey(), entry.getValue() );
					 }
			         System.out.print("\n");
	        	 }
			 } // End of looping over all edges
			 graph.tx().commit();
			 System.out.println("DEBUG - committed updates for listed edges " );
		}
		catch (Exception e2) {
			String msg = e2.toString();
			System.out.println(msg);
			e2.printStackTrace();
			if( graph != null ){
				graph.tx().rollback();
			}
			System.exit(0);
		}

	    System.exit(0);
    
	}// end of main()
	
	
	
	
	
}



