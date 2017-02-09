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
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.openecomp.aai.dbmap.AAIGraph;
import org.openecomp.aai.exceptions.AAIException;
import org.openecomp.aai.ingestModel.DbMaps;
import org.openecomp.aai.ingestModel.IngestModelMoxyOxm;
import org.openecomp.aai.logging.ErrorLogHelper;
import org.openecomp.aai.util.AAIConfig;
import org.openecomp.aai.util.AAIConstants;
import org.openecomp.aai.util.UniquePropertyCheck;
import org.slf4j.MDC;

import com.att.eelf.configuration.Configuration;
import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;
import com.thinkaurelius.titan.core.Cardinality;
import com.thinkaurelius.titan.core.PropertyKey;
import com.thinkaurelius.titan.core.TitanEdge;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanVertex;
import com.thinkaurelius.titan.core.schema.TitanManagement;

public class SchemaMod {

	private static final String FROMAPPID = "AAI-UTILS";
	private static final String TRANSID = UUID.randomUUID().toString();
	private static final String COMPONENT = "SchemaMod";

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {

		SchemaMod.execute(args);
		System.exit(0);

	}// End of main()

	/**
	 * Execute.
	 *
	 * @param args the args
	 */
	public static void execute(String[] args) {

		// Set the logging file properties to be used by EELFManager
		Properties props = System.getProperties();
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_NAME, AAIConstants.AAI_SCHEMA_MOD_LOGBACK_PROPS);
		props.setProperty(Configuration.PROPERTY_LOGGING_FILE_PATH, AAIConstants.AAI_HOME_ETC_APP_PROPERTIES);

		EELFLogger logger = EELFManager.getInstance().getLogger(UniquePropertyCheck.class.getSimpleName());
		MDC.put("logFilenameAppender", SchemaMod.class.getSimpleName());

		// NOTE -- We're just working with properties that are used for NODES
		// for now.

		TitanGraph graph = null;
		TitanManagement graphMgt = null;

		Boolean preserveData = true;
		String propName = "";
		String targetDataType = "";
		String targetIndexInfo = "";
		String preserveDataFlag = "";

		String usageString = "Usage: SchemaMod propertyName targetDataType targetIndexInfo preserveDataFlag \n";
		if (args.length != 4) {
			String emsg = "Four Parameters are required.  \n" + usageString;
			logAndPrint(logger, emsg);
			System.exit(1);
		} else {
			propName = args[0];
			targetDataType = args[1];
			targetIndexInfo = args[2];
			preserveDataFlag = args[3];
		}

		if (propName.equals("")) {
			String emsg = "Bad parameter - propertyName cannot be empty.  \n" + usageString;
			logAndPrint(logger, emsg);
			System.exit(1);
		} else if (!targetDataType.equals("String") && !targetDataType.equals("Set<String>")
				&& !targetDataType.equals("Integer") && !targetDataType.equals("Long")
				&& !targetDataType.equals("Boolean")) {
			String emsg = "Unsupported targetDataType.  We only support String, Set<String>, Integer, Long or Boolean for now.\n"
					+ usageString;
			logAndPrint(logger, emsg);
			System.exit(1);
		} else if (!targetIndexInfo.equals("uniqueIndex") && !targetIndexInfo.equals("index")
				&& !targetIndexInfo.equals("noIndex")) {
			String emsg = "Unsupported IndexInfo.  We only support: 'uniqueIndex', 'index' or 'noIndex'.\n"
					+ usageString;
			logAndPrint(logger, emsg);
			System.exit(1);
		} else {
			if (preserveDataFlag.equals("true")) {
				preserveData = true;
			} else if (preserveDataFlag.equals("false")) {
				preserveData = false;
			} else {
				String emsg = "Unsupported preserveDataFlag.  We only support: 'true' or 'false'.\n" + usageString;
				logAndPrint(logger, emsg);
				System.exit(1);
			}
		}

		try {
			AAIConfig.init();
			ErrorLogHelper.loadProperties();
		} catch (Exception ae) {
			String emsg = "Problem with either AAIConfig.init() or ErrorLogHelper.LoadProperties(). ";
			logAndPrint(logger, emsg + "[" + ae.getMessage() + "]");
			System.exit(1);
		}

		DbMaps dbMaps = null;
		try {
			ArrayList<String> apiVersions = new ArrayList<String>();
			apiVersions.add(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP));
			final IngestModelMoxyOxm m = new IngestModelMoxyOxm();
			m.init(apiVersions, false);
			dbMaps = m.dbMapsContainer.get(AAIConfig.get(AAIConstants.AAI_DEFAULT_API_VERSION_PROP));
		} catch (AAIException ae) {
			String emsg = "Could not instantiate a copy of DbMaps. ";
			logAndPrint(logger, emsg + "[" + ae.getMessage() + "]");
			System.exit(1);
		} catch (Exception e) {
			String emsg = "exception, Could not instantiate a copy of DbMaps. ";
			logAndPrint(logger, emsg + "[" + e.getMessage() + "]");
			System.exit(1);
		}
		// Give a big warning if the DbMaps.PropertyDataTypeMap value does not
		// agree with what we're doing
		String warningMsg = "";
		if (!dbMaps.PropertyDataTypeMap.containsKey(propName)) {
			String emsg = "Property Name = [" + propName + "] not found in PropertyDataTypeMap. ";
			logAndPrint(logger, emsg);
			System.exit(1);
		} else {
			String currentDataType = dbMaps.PropertyDataTypeMap.get(propName);
			if (!currentDataType.equals(targetDataType)) {
				warningMsg = "TargetDataType [" + targetDataType + "] does not match what is in DbRules.java ("
						+ currentDataType + ").";
			}
		}

		if (!warningMsg.equals("")) {
			logAndPrint(logger, "\n>>> WARNING <<<< ");
			logAndPrint(logger, ">>> " + warningMsg + " <<<");
		}

		logAndPrint(logger, ">>> Processing will begin in 5 seconds (unless interrupted). <<<");
		try {
			// Give them a chance to back out of this
			Thread.sleep(5000);
		} catch (java.lang.InterruptedException ie) {
			logAndPrint(logger, " DB Schema Update has been aborted. ");
			System.exit(1);
		}

		try {
			Class dType = null;
			if (targetDataType.equals("String")) {
				dType = String.class;
			} else if (targetDataType.equals("Set<String>")) {
				dType = String.class;
			} else if (targetDataType.equals("Integer")) {
				dType = Integer.class;
			} else if (targetDataType.equals("Boolean")) {
				dType = Boolean.class;
			} else if (targetDataType.equals("Character")) {
				dType = Character.class;
			} else if (targetDataType.equals("Long")) {
				dType = Long.class;
			} else if (targetDataType.equals("Float")) {
				dType = Float.class;
			} else if (targetDataType.equals("Double")) {
				dType = Double.class;
			} else {
				String emsg = "Not able translate the targetDataType [" + targetDataType + "] to a Class variable.\n";
				logAndPrint(logger, emsg);
				System.exit(1);
			}
			String cardinality = "SINGLE"; // Default cardinality to SINGLE

			if (targetDataType.equals("Set<String>")) {
				cardinality = "SET";
			}
			logAndPrint(logger, "    ---- NOTE --- about to open graph (takes a little while)\n");

			graph = AAIGraph.getInstance().getGraph();
			if (graph == null) {
				String emsg = "Not able to get a graph object in SchemaMod.java\n";
				logAndPrint(logger, emsg);
				System.exit(1);
			}

			// Make sure this property is in the DB.
			graphMgt = graph.openManagement();
			if (graphMgt == null) {
				String emsg = "Not able to get a graph Management object in SchemaMod.java\n";
				logAndPrint(logger, emsg);
				System.exit(1);
			}
			PropertyKey origPropKey = graphMgt.getPropertyKey(propName);
			if (origPropKey == null) {
				String emsg = "The propName = [" + propName + "] is not defined in our graph. ";
				logAndPrint(logger, emsg);
				System.exit(1);
			}

			if (targetIndexInfo.equals("uniqueIndex")) {
				// Make sure the data in the property being changed can have a
				// unique-index put on it.
				// Ie. if there are duplicate values, we will not be able to
				// migrate the data back into the property.
				Boolean foundDupesFlag = UniquePropertyCheck.runTheCheckForUniqueness(TRANSID, FROMAPPID,
						graph.newTransaction(), propName, logger);
				if (foundDupesFlag) {
					logAndPrint(logger,
							"\n\n!!!!!! >> Cannot add a uniqueIndex for the property: [" + propName
									+ "] because duplicate values were found.  See the log for details on which"
									+ " nodes have this value.  \nThey will need to be resolved (by updating those values to new"
									+ " values or deleting unneeded nodes) using the standard REST-API \n");
					System.exit(1);
				}
			}

			// -------------- If we made it to here - we must be OK with making
			// this change ------------

			// Rename this property to a backup name (old name with "retired_"
			// appended plus a dateStr)

			SimpleDateFormat d = new SimpleDateFormat("MMddHHmm");
			d.setTimeZone(TimeZone.getTimeZone("GMT"));
			String dteStr = d.format(new Date()).toString();
			String retiredName = propName + "-" + dteStr + "-RETIRED";
			graphMgt.changeName(origPropKey, retiredName);

			// Create a new property using the original property name and the
			// targetDataType
			PropertyKey freshPropKey = graphMgt.makePropertyKey(propName).dataType(dType)
					.cardinality(Cardinality.valueOf(cardinality)).make();

			// Create the appropriate index (if any)
			if (targetIndexInfo.equals("uniqueIndex")) {
				String freshIndexName = propName + dteStr;
				graphMgt.buildIndex(freshIndexName, Vertex.class).addKey(freshPropKey).unique().buildCompositeIndex();
			} else if (targetIndexInfo.equals("index")) {
				String freshIndexName = propName + dteStr;
				graphMgt.buildIndex(freshIndexName, Vertex.class).addKey(freshPropKey).buildCompositeIndex();
			}

			logAndPrint(logger, "Committing schema changes with graphMgt.commit()");
			graphMgt.commit();
			graph.tx().commit();
			graph.close();

			// Get A new graph object
			logAndPrint(logger, "    ---- NOTE --- about to open a second graph object (takes a little while)\n");

			graph = AAIGraph.getInstance().getGraph();
			if (graph == null) {
				String emsg = "Not able to get a graph object in SchemaMod.java\n";
				logAndPrint(logger, emsg);
				System.exit(1);
			}

			// For each node that has this property, update the new from the old
			// and then remove the
			// old property from that node
			Iterable<?> verts = null;
			verts = graph.query().has(retiredName).vertices();
			Iterator<?> it = verts.iterator();
			int vtxCount = 0;
			ArrayList<String> alreadySeenVals = new ArrayList<String>();
			while (it.hasNext()) {
				vtxCount++;
				TitanVertex tmpVtx = (TitanVertex) it.next();
				String tmpVid = tmpVtx.id().toString();

				// System.out.println("Show what we have in the vertex before
				// trying to do an update...");
				// ArrayList <String> retArr =
				// DbMeth.showPropertiesForNode("junkTransId", "junkFromAppId",
				// tmpVtx);
				// for( String info : retArr ){ System.out.println(info); }

				Object origVal = tmpVtx.<Object> property(retiredName).orElse(null);
				if (preserveData) {
					tmpVtx.property(propName, origVal);
					if (targetIndexInfo.equals("uniqueIndex")) {
						// We're working on a property that is being used as a
						// unique index
						String origValStr = "";
						if (origVal != null) {
							origValStr = origVal.toString();
						}
						if (alreadySeenVals.contains(origValStr)) {
							// This property is supposed to be unique, but we've
							// already seen this value in this loop
							// This should have been caught up in the first part
							// of SchemaMod, but since it wasn't, we
							// will just log the problem.
							logAndPrint(logger,
									"\n\n ---------- ERROR - could not migrate the old data [" + origValStr
											+ "] for propertyName [" + propName
											+ "] because this property is having a unique index put on it.");
							showPropertiesAndEdges(TRANSID, FROMAPPID, tmpVtx, logger);
							logAndPrint(logger, "-----------------------------------\n");
						} else {
							// Ok to add this prop in as a unique value
							tmpVtx.property(propName, origVal);
							logAndPrint(logger,
									"INFO -- just did the add of the freshPropertyKey and updated it with the orig value ("
											+ origValStr + ")");
						}
						alreadySeenVals.add(origValStr);
					} else {
						// We are not working with a unique index
						tmpVtx.property(propName, origVal);
						logAndPrint(logger,
								"INFO -- just did the add of the freshPropertyKey and updated it with the orig value ("
										+ origVal.toString() + ")");
					}
				} else {
					// existing nodes just won't have that property anymore
					// Not sure if we'd ever actually want to do this -- maybe
					// we'd do this if the new
					// data type was not compatible with the old?
				}
				tmpVtx.property(retiredName).remove();

				logAndPrint(logger, "INFO -- just did the remove of the " + retiredName + " from this vertex. (vid="
						+ tmpVid + ")");
			}

			logAndPrint(logger, "Updated data for " + vtxCount + " vertexes.  Now call graph2.commit(). ");
			graph.tx().commit();
		} catch (Exception ex) {
			logAndPrint(logger, "Threw a regular Exception: ");
			logAndPrint(logger, ex.getMessage());
		} finally {
			if (graphMgt != null && graphMgt.isOpen()) {
				// Any changes that worked correctly should have already done
				// their commits.
				graphMgt.rollback();
			}
			if (graph != null) {
				// Any changes that worked correctly should have already done
				// their commits.
				graph.tx().rollback();
				graph.close();
			}
		}

	}

	/**
	 * Show properties and edges.
	 *
	 * @param transId the trans id
	 * @param fromAppId the from app id
	 * @param tVert the t vert
	 * @param logger the logger
	 */
	private static void showPropertiesAndEdges(String transId, String fromAppId, TitanVertex tVert, EELFLogger logger) {

		if (tVert == null) {
			logAndPrint(logger, "Null node passed to showPropertiesAndEdges.");
		} else {
			String nodeType = "";
			Object ob = tVert.<String> property("aai-node-type");
			if (ob == null) {
				nodeType = "null";
			} else {
				nodeType = ob.toString();
			}

			logAndPrint(logger, " AAINodeType/VtxID for this Node = [" + nodeType + "/" + tVert.id() + "]");
			logAndPrint(logger, " Property Detail: ");
			Iterator<VertexProperty<Object>> pI = tVert.properties();
			while (pI.hasNext()) {
				VertexProperty<Object> tp = pI.next();
				Object val = tp.value();
				logAndPrint(logger, "Prop: [" + tp.key() + "], val = [" + val + "] ");
			}

			Iterator<Edge> eI = tVert.edges(Direction.BOTH);
			if (!eI.hasNext()) {
				logAndPrint(logger, "No edges were found for this vertex. ");
			}
			while (eI.hasNext()) {
				TitanEdge ed = (TitanEdge) eI.next();
				String lab = ed.label();
				TitanVertex vtx = (TitanVertex) ed.otherVertex(tVert);
				if (vtx == null) {
					logAndPrint(logger,
							" >>> COULD NOT FIND VERTEX on the other side of this edge edgeId = " + ed.id() + " <<< ");
				} else {
					String nType = vtx.<String> property("aai-node-type").orElse(null);
					String vid = vtx.id().toString();
					logAndPrint(logger, "Found an edge (" + lab + ") from this vertex to a [" + nType
							+ "] node with VtxId = " + vid);
				}
			}
		}
	} // End of showPropertiesAndEdges()

	/**
	 * Log and print.
	 *
	 * @param logger the logger
	 * @param msg the msg
	 */
	protected static void logAndPrint(EELFLogger logger, String msg) {
		System.out.println(msg);
		logger.info(msg);
	}

}
