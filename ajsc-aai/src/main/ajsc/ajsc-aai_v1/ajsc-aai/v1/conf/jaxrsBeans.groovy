beans{
	xmlns cxf: "http://camel.apache.org/schema/cxf"
	xmlns jaxrs: "http://cxf.apache.org/jaxrs"
	xmlns util: "http://www.springframework.org/schema/util"

	RestProviders(org.openecomp.aai.rest.RestProviders)
	LegacyMoxyConsumer(org.openecomp.aai.rest.LegacyMoxyConsumer)
	URLFromVertexIdConsumer(org.openecomp.aai.rest.URLFromVertexIdConsumer)
	VertexIdConsumer(org.openecomp.aai.rest.VertexIdConsumer)
	BulkAddConsumer(org.openecomp.aai.rest.BulkAddConsumer)
	ExampleConsumer(org.openecomp.aai.rest.ExampleConsumer)
	SearchProvider(org.openecomp.aai.rest.search.SearchProvider)
	ModelAndNamedQueryRestProvider(org.openecomp.aai.rest.search.ModelAndNamedQueryRestProvider)
	ActionsProvider(org.openecomp.aai.rest.actions.ActionsProvider)
	
	TransLogRestProvider(org.openecomp.aai.rest.translog.TransLogRestProvider)
	EchoResponse(org.openecomp.aai.rest.util.EchoResponse)


	util.list(id: 'jaxrsServices') {
		
		//ref(bean:'RestProviders')
		ref(bean:'ExampleConsumer')
		ref(bean:'LegacyMoxyConsumer')
		ref(bean:'VertexIdConsumer')
		ref(bean:'URLFromVertexIdConsumer')
		ref(bean:'BulkAddConsumer')
		ref(bean:'SearchProvider')
		ref(bean:'ModelAndNamedQueryRestProvider')
		ref(bean:'ActionsProvider')

		
		ref(bean:'TransLogRestProvider')
		ref(bean:'EchoResponse')
	}
}