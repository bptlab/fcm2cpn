package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.camunda.bpm.model.bpmn.instance.DataAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.PlaceNode;

public class DataObjectWrapper extends DataElementWrapper<DataObject, DataObjectReference> {
	
	private List<String> states;
	private List<StatefulDataAssociation<DataAssociation>> references;

	private final Map<Page, PlaceNode> creationCounterPlaces = new HashMap<>();
	

	public DataObjectWrapper(CompilerApp compilerApp, String trimmedName) {
		super(compilerApp, trimmedName);
		
        compilerApp.createVariable(dataObjectId(), "STRING");
        compilerApp.createVariable(dataObjectCount(), "INT");
	}


	@Override
	protected Place createPlace() {
		return compilerApp.createPlace(trimmedName, "DATA_OBJECT");
	}


	public PlaceNode creationCounterForPage(Page page) {
		return creationCounterPlaces.computeIfAbsent(page, _page -> compilerApp.createFusionPlace(_page, namePrefix()+" Count", "INT", "1`0", dataObjectCount()));
	}


	@Override
	public String annotationForDataFlow(Optional<String> stateName) {
        String stateString = stateName.map(x -> ", state = "+x).orElse("");
        return "{id = "+dataObjectId()+" , caseId = caseId"+stateString+"}";
	}

}