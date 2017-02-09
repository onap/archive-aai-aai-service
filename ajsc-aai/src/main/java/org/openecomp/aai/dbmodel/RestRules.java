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

package org.openecomp.aai.dbmodel;

import java.util.Collection;
import java.util.Iterator;

import org.openecomp.aai.exceptions.AAIException;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;

public class RestRules {
	
	// resource, "package-name path/namespace,resource,plural,gp1,gp2,gp3,gp4,gp5"
	// when the nodetype is used for the first time in a relationship  use the nodetype as the resource
	// this entry will be used for generating the REST URL
	// if that nodetype can also be a child of another node - then add a new name for the resource to make it unique
	// and use that in the ChildMap to be used by the code generator
	// this works if the nodetypes are in the same namespace. If they are in different namespaces we will have 
	// to re-look at this.
	
    public static final Multimap<String, String> ResourceMap = 
            new ImmutableSetMultimap.Builder<String, String>() 
  	.putAll("dvs-switch", "CloudInfrastructure,dvs-switch,dvs-switches,,,,,")
  	.putAll("availability-zone", "CloudInfrastructure,availability-zone,availability-zones,,,,,")
  	.putAll("oam-network", "CloudInfrastructure,oam-network,oam-networks,,,,,")
  	.putAll("virtual-data-center", "CloudInfrastructure,virtual-data-center,virtual-data-centers,,,,,")
  	.putAll("network-profile", "CloudInfrastructure,network-profile,network-profiles,,,,,")
  	.putAll("volume-group", "CloudInfrastructure,volume-group,volume-groups,,,,,")
  	.putAll("pserver", "CloudInfrastructure,pserver,pservers,,,,,")
  	.putAll("p-interface", "CloudInfrastructure,p-interface,p-interfaces,pserver,,,,")
  	.putAll("l-interface", "CloudInfrastructure,l-interface,l-interfaces,p-interface,pserver,,,")
  	.putAll("vlan", "CloudInfrastructure,vlan,vlans,l-interface,p-interface,pserver,,")
  	.putAll("l3-interface-ipv4-address-list", "CloudInfrastructure,l3-interface-ipv4-address-list,,vlan,l-interface,p-interface,pserver,")
  	.putAll("l3-interface-ipv6-address-list", "CloudInfrastructure,l3-interface-ipv6-address-list,,vlan,l-interface,p-interface,pserver,")
  	.putAll("cloud-infrastructure-pservers-pserver-p-interfaces-p-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list", "CloudInfrastructure,l3-interface-ipv4-address-list,,l-interface,p-interface,pserver,,")
  	.putAll("cloud-infrastructure-pservers-pserver-p-interfaces-p-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list", "CloudInfrastructure,l3-interface-ipv6-address-list,,l-interface,p-interface,pserver,,")
  	.putAll("lag-interface", "CloudInfrastructure,lag-interface,lag-interfaces,pserver,,,,")
  	.putAll("cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface", "CloudInfrastructure,l-interface,l-interfaces,lag-interface,pserver,,,")
  	.putAll("cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan", "CloudInfrastructure,vlan,vlans,l-interface,lag-interface,pserver,,")
  	.putAll("cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list", "CloudInfrastructure,l3-interface-ipv4-address-list,,vlan,l-interface,lag-interface,pserver,")
  	.putAll("cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list", "CloudInfrastructure,l3-interface-ipv6-address-list,,vlan,l-interface,lag-interface,pserver,")
  	.putAll("cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list", "CloudInfrastructure,l3-interface-ipv4-address-list,,l-interface,lag-interface,pserver,,")
  	.putAll("cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list", "CloudInfrastructure,l3-interface-ipv6-address-list,,l-interface,lag-interface,pserver,,")
  	.putAll("complex", "CloudInfrastructure,complex,complexes,,,,,")
  	.putAll("ctag-pool", "CloudInfrastructure,ctag-pool,ctag-pools,complex,,,,")
  	.putAll("image", "CloudInfrastructure,image,images,,,,,")
  	.putAll("metadatum", "CloudInfrastructure,metadatum,metadata,image,,,,")
  	.putAll("flavor", "CloudInfrastructure,flavor,flavors,,,,,")
  	.putAll("tenant", "CloudInfrastructure,tenant,tenants,,,,,")
  	.putAll("vserver", "CloudInfrastructure,vserver,vservers,tenant,,,,")
  	.putAll("volume", "CloudInfrastructure,volume,volumes,vserver,tenant,,,")
  	.putAll("cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface", "CloudInfrastructure,l-interface,l-interfaces,vserver,tenant,,,")
  	.putAll("cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface-vlans-vlan", "CloudInfrastructure,vlan,vlans,l-interface,vserver,tenant,,")
  	.putAll("cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list", "CloudInfrastructure,l3-interface-ipv4-address-list,,vlan,l-interface,vserver,tenant,")
  	.putAll("cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list", "CloudInfrastructure,l3-interface-ipv6-address-list,,vlan,l-interface,vserver,tenant,")
  	.putAll("cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface-l3-interface-ipv4-address-list", "CloudInfrastructure,l3-interface-ipv4-address-list,,l-interface,vserver,tenant,,")
  	.putAll("cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface-l3-interface-ipv6-address-list", "CloudInfrastructure,l3-interface-ipv6-address-list,,l-interface,vserver,tenant,,")
  	.putAll("service-capability", "ServiceDesignAndCreation,service-capability,service-capabilities,,,,,")
  	.putAll("service", "ServiceDesignAndCreation,service,services,,,,,")
  	.putAll("model", "ServiceDesignAndCreation,model,models,,,,,")
  	.putAll("named-query", "ServiceDesignAndCreation,named-query,named-queries,,,,,")
  	.putAll("named-query-element", "ServiceDesignAndCreation,named-query-element,named-query-elements,named-query,,,,")
  	.putAll("license-key-resource", "LicenseManagement,license-key-resource,license-key-resources,,,,,")
  	.putAll("customer", "Business,customer,customers,,,,,")
  	.putAll("service-subscription", "Business,service-subscription,service-subscriptions,customer,,,,")
  	.putAll("service-instance", "Business,service-instance,service-instances,service-subscription,customer,,,")
  	.putAll("business-customers-customer-service-subscriptions-service-subscription-service-instances-service-instance-metadata-metadatum", "Business,metadatum,metadata,service-instance,service-subscription,customer,,")
  	.putAll("connector", "Business,connector,connectors,,,,,")
  	.putAll("business-connectors-connector-metadata-metadatum", "Business,metadatum,metadata,connector,,,,")
  	.putAll("generic-vnf", "Network,generic-vnf,generic-vnfs,,,,,")
  	.putAll("network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface", "Network,lag-interface,lag-interfaces,generic-vnf,,,,")
  	.putAll("network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface", "Network,l-interface,l-interfaces,lag-interface,generic-vnf,,,")
  	.putAll("network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan", "Network,vlan,vlans,l-interface,lag-interface,generic-vnf,,")
  	.putAll("network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,vlan,l-interface,lag-interface,generic-vnf,")
  	.putAll("network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,vlan,l-interface,lag-interface,generic-vnf,")
  	.putAll("network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,l-interface,lag-interface,generic-vnf,,")
  	.putAll("network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,l-interface,lag-interface,generic-vnf,,")
  	.putAll("network-generic-vnfs-generic-vnf-l-interfaces-l-interface", "Network,l-interface,l-interfaces,generic-vnf,,,,")
  	.putAll("network-generic-vnfs-generic-vnf-l-interfaces-l-interface-vlans-vlan", "Network,vlan,vlans,l-interface,generic-vnf,,,")
  	.putAll("network-generic-vnfs-generic-vnf-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,vlan,l-interface,generic-vnf,,")
  	.putAll("network-generic-vnfs-generic-vnf-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,vlan,l-interface,generic-vnf,,")
  	.putAll("network-generic-vnfs-generic-vnf-l-interfaces-l-interface-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,l-interface,generic-vnf,,,")
  	.putAll("network-generic-vnfs-generic-vnf-l-interfaces-l-interface-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,l-interface,generic-vnf,,,")
  	.putAll("logical-link", "Network,logical-link,logical-links,,,,,")
  	.putAll("physical-link", "Network,physical-link,physical-links,,,,,")
  	.putAll("vpn-binding", "Network,vpn-binding,vpn-bindings,,,,,")
  	.putAll("site-pair-set", "Network,site-pair-set,site-pair-sets,,,,,")
  	.putAll("routing-instance", "Network,routing-instance,routing-instances,site-pair-set,,,,")
  	.putAll("site-pair", "Network,site-pair,site-pairs,routing-instance,site-pair-set,,,")
  	.putAll("class-of-service", "Network,class-of-service,classes-of-service,site-pair,routing-instance,site-pair-set,,")
  	.putAll("multicast-configuration", "Network,multicast-configuration,multicast-configurations,,,,,")
  	.putAll("vce", "Network,vce,vces,,,,,")
  	.putAll("port-group", "Network,port-group,port-groups,vce,,,,")
  	.putAll("cvlan-tag", "Network,cvlan-tag-entry,cvlan-tags,port-group,vce,,,")
  	.putAll("newvce", "Network,newvce,newvces,,,,,")
  	.putAll("network-newvces-newvce-l-interfaces-l-interface", "Network,l-interface,l-interfaces,newvce,,,,")
  	.putAll("network-newvces-newvce-l-interfaces-l-interface-vlans-vlan", "Network,vlan,vlans,l-interface,newvce,,,")
  	.putAll("network-newvces-newvce-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,vlan,l-interface,newvce,,")
  	.putAll("network-newvces-newvce-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,vlan,l-interface,newvce,,")
  	.putAll("network-newvces-newvce-l-interfaces-l-interface-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,l-interface,newvce,,,")
  	.putAll("network-newvces-newvce-l-interfaces-l-interface-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,l-interface,newvce,,,")
  	.putAll("vpe", "Network,vpe,vpes,,,,,")
  	.putAll("network-vpes-vpe-lag-interfaces-lag-interface", "Network,lag-interface,lag-interfaces,vpe,,,,")
  	.putAll("network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface", "Network,l-interface,l-interfaces,lag-interface,vpe,,,")
  	.putAll("network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan", "Network,vlan,vlans,l-interface,lag-interface,vpe,,")
  	.putAll("network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,vlan,l-interface,lag-interface,vpe,")
  	.putAll("network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,vlan,l-interface,lag-interface,vpe,")
  	.putAll("network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,l-interface,lag-interface,vpe,,")
  	.putAll("network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,l-interface,lag-interface,vpe,,")
  	.putAll("network-vpes-vpe-l-interfaces-l-interface", "Network,l-interface,l-interfaces,vpe,,,,")
  	.putAll("network-vpes-vpe-l-interfaces-l-interface-vlans-vlan", "Network,vlan,vlans,l-interface,vpe,,,")
  	.putAll("network-vpes-vpe-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,vlan,l-interface,vpe,,")
  	.putAll("network-vpes-vpe-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,vlan,l-interface,vpe,,")
  	.putAll("network-vpes-vpe-l-interfaces-l-interface-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,l-interface,vpe,,,")
  	.putAll("network-vpes-vpe-l-interfaces-l-interface-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,l-interface,vpe,,,")
  	.putAll("vpls-pe", "Network,vpls-pe,vpls-pes,,,,,")
  	.putAll("network-vpls-pes-vpls-pe-p-interfaces-p-interface", "Network,p-interface,p-interfaces,vpls-pe,,,,")
  	.putAll("network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface", "Network,l-interface,l-interfaces,p-interface,vpls-pe,,,")
  	.putAll("network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface-vlans-vlan", "Network,vlan,vlans,l-interface,p-interface,vpls-pe,,")
  	.putAll("network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,vlan,l-interface,p-interface,vpls-pe,")
  	.putAll("network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,vlan,l-interface,p-interface,vpls-pe,")
  	.putAll("network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,l-interface,p-interface,vpls-pe,,")
  	.putAll("network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,l-interface,p-interface,vpls-pe,,")
  	.putAll("network-vpls-pes-vpls-pe-lag-interfaces-lag-interface", "Network,lag-interface,lag-interfaces,vpls-pe,,,,")
  	.putAll("network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface", "Network,l-interface,l-interfaces,lag-interface,vpls-pe,,,")
  	.putAll("network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan", "Network,vlan,vlans,l-interface,lag-interface,vpls-pe,,")
  	.putAll("network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,vlan,l-interface,lag-interface,vpls-pe,")
  	.putAll("network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,vlan,l-interface,lag-interface,vpls-pe,")
  	.putAll("network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list", "Network,l3-interface-ipv4-address-list,,l-interface,lag-interface,vpls-pe,,")
  	.putAll("network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list", "Network,l3-interface-ipv6-address-list,,l-interface,lag-interface,vpls-pe,,")
  	.putAll("lag-link", "Network,lag-link,lag-links,,,,,")
  	.putAll("l3-network", "Network,l3-network,l3-networks,,,,,")
  	.putAll("ctag-assignment", "Network,ctag-assignment,ctag-assignments,l3-network,,,,")
  	.putAll("subnet", "Network,subnet,subnets,l3-network,,,,")
  	.putAll("vnfc", "Network,vnfc,vnfcs,,,,,")
        .build();

       // resource, "parent,child1,child2,child3,child4,child5,child6"
       public static final Multimap<String, String> ChildMap = 
  		new ImmutableSetMultimap.Builder<String, String>() 
  	.putAll("Vce", "vce,port-group,cvlan-tag,,,,")
  	.putAll("Service", "service,,,,,,")
  	.putAll("LicenseKeyResource", "license-key-resource,,,,,,")
  	.putAll("Pserver", "pserver,p-interface,l-interface,vlan,l3-interface-ipv4-address-list,l3-interface-ipv6-address-list,cloud-infrastructure-pservers-pserver-p-interfaces-p-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list,cloud-infrastructure-pservers-pserver-p-interfaces-p-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list,lag-interface,cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface,cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan,cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list,cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list,cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list,cloud-infrastructure-pservers-pserver-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list,")
  	.putAll("VplsPe", "vpls-pe,network-vpls-pes-vpls-pe-p-interfaces-p-interface,network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface,network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface-vlans-vlan,network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list,network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list,network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list,network-vpls-pes-vpls-pe-p-interfaces-p-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list,network-vpls-pes-vpls-pe-lag-interfaces-lag-interface,network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface,network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan,network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list,network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list,network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list,network-vpls-pes-vpls-pe-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list,")
  	.putAll("LagLink", "lag-link,,,,,,")
  	.putAll("Complex", "complex,ctag-pool,,,,,")
  	.putAll("NetworkProfile", "network-profile,,,,,,")
  	.putAll("MulticastConfiguration", "multicast-configuration,,,,,,")
  	.putAll("OamNetwork", "oam-network,,,,,,")
  	.putAll("Newvce", "newvce,network-newvces-newvce-l-interfaces-l-interface,network-newvces-newvce-l-interfaces-l-interface-vlans-vlan,network-newvces-newvce-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list,network-newvces-newvce-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list,network-newvces-newvce-l-interfaces-l-interface-l3-interface-ipv4-address-list,network-newvces-newvce-l-interfaces-l-interface-l3-interface-ipv6-address-list,")
  	.putAll("VolumeGroup", "volume-group,,,,,,")
  	.putAll("PhysicalLink", "physical-link,,,,,,")
  	.putAll("Image", "image,metadatum,,,,,")
  	.putAll("SitePairSet", "site-pair-set,routing-instance,site-pair,class-of-service,,,")
  	.putAll("VpnBinding", "vpn-binding,,,,,,")
  	.putAll("DvsSwitch", "dvs-switch,,,,,,")
  	.putAll("ServiceCapability", "service-capability,,,,,,")
  	.putAll("Model", "model,,,,,,")
  	.putAll("NamedQuery", "named-query,named-query-element,,,,,")
  	.putAll("NamedQueryElement", "named-query-element,named-query-element,named-query-element,named-query-element,named-query-element,named-query-element,")
  	.putAll("Tenant", "tenant,vserver,volume,cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface,cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface-vlans-vlan,cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list,cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list,cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface-l3-interface-ipv4-address-list,cloud-infrastructure-tenants-tenant-vservers-vserver-l-interfaces-l-interface-l3-interface-ipv6-address-list,")
  	.putAll("GenericVnf", "generic-vnf,network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface,network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface,network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan,network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list,network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list,network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list,network-generic-vnfs-generic-vnf-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list,network-generic-vnfs-generic-vnf-l-interfaces-l-interface,network-generic-vnfs-generic-vnf-l-interfaces-l-interface-vlans-vlan,network-generic-vnfs-generic-vnf-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list,network-generic-vnfs-generic-vnf-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list,network-generic-vnfs-generic-vnf-l-interfaces-l-interface-l3-interface-ipv4-address-list,network-generic-vnfs-generic-vnf-l-interfaces-l-interface-l3-interface-ipv6-address-list,")
  	.putAll("Customer", "customer,service-subscription,service-instance,business-customers-customer-service-subscriptions-service-subscription-service-instances-service-instance-metadata-metadatum,,,")
  	.putAll("Flavor", "flavor,,,,,,")
  	.putAll("L3Network", "l3-network,ctag-assignment,subnet,,,,")
  	.putAll("VirtualDataCenter", "virtual-data-center,,,,,,")
  	.putAll("LogicalLink", "logical-link,,,,,,")
  	.putAll("Connector", "connector,business-connectors-connector-metadata-metadatum,,,,,")
  	.putAll("AvailabilityZone", "availability-zone,,,,,,")
  	.putAll("Vnfc", "vnfc,,,,,,")
  	.putAll("Vpe", "vpe,network-vpes-vpe-lag-interfaces-lag-interface,network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface,network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan,network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list,network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list,network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv4-address-list,network-vpes-vpe-lag-interfaces-lag-interface-l-interfaces-l-interface-l3-interface-ipv6-address-list,network-vpes-vpe-l-interfaces-l-interface,network-vpes-vpe-l-interfaces-l-interface-vlans-vlan,network-vpes-vpe-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv4-address-list,network-vpes-vpe-l-interfaces-l-interface-vlans-vlan-l3-interface-ipv6-address-list,network-vpes-vpe-l-interfaces-l-interface-l3-interface-ipv4-address-list,network-vpes-vpe-l-interfaces-l-interface-l3-interface-ipv6-address-list,")
        .build();





	/**
	 * Gets the res rules.
	 *
	 * @param resource the resource
	 * @return the res rules
	 * @throws AAIException the AAI exception
	 */
	public static String [] getResRules(String resource) throws AAIException{
		try {
			
			if (resource.equals("cloud-region")) {
				
				String[] tmp = {null,null,null};
				return tmp;
			}
			String resRule = "";
			Collection <String> resRuleColl =  ResourceMap.get(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, resource));
			Iterator <String> ruleItr = resRuleColl.iterator();
			if (ruleItr.hasNext()){
				// For now, we only look for one type of edge between two nodes.
				resRule = ruleItr.next();
			}
			else {
				// No edge rule found for this
				String detail = "No rule found for resource: " + resource;
				throw new AAIException("AAI_4012", detail); 
			}
		
			String [] rules = resRule.split(",", 8);
			if( rules.length != 8 ){
				String detail = "(itemCount=" + rules.length + ") for resource: " + resource;
				throw new AAIException("AAI_4012", detail); 
			}
		
			for (int i=1; i < 8; i++) {
				if (rules[i].equals(""))
					rules[i] = null;
			}
			return rules;
		} catch (Exception e) {
			// No rule found for this
			String detail = "Exception when looking for rule: No rule found for resource: " + resource;
			throw new AAIException("AAI_4012", detail); 
		}
	}
	
	/**
	 * Gets the child rules.
	 *
	 * @param resource the resource
	 * @return the child rules
	 * @throws AAIException the AAI exception
	 */
	public static String [] getChildRules(String resource) throws AAIException{
		try {
			String childRule = "";
			Collection <String> childRuleColl =  ChildMap.get(resource);
			Iterator <String> ruleItr = childRuleColl.iterator();
			if( ruleItr.hasNext() ){
				childRule = ruleItr.next();
			}
			else {
				// No rule found for this
				String detail = "No child rule found for resource: " + resource;
				throw new AAIException("AAI_4012", detail); 
			}  	
			return childRule.split(",");
		} catch (Exception e) {
			// No rule found for this
			String detail = "Exception when looking for rule: No child rule found for resource: " + resource;
			throw new AAIException("AAI_4012", detail); 
		}
	}

}
