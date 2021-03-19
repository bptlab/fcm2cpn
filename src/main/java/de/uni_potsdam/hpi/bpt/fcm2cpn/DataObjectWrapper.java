package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.PlaceNode;

public class DataObjectWrapper extends DataElementWrapper<DataObject, DataObjectReference> {

	private final Map<SubpageElement, PlaceNode> creationCounterPlaces = new HashMap<>();
	

	public DataObjectWrapper(CompilerApp compilerApp, String normalizedName, Set<String> states) {
		super(compilerApp, normalizedName, states);
		
		assert compilerApp.getDataModel().hasDataObject(getNormalizedName());
		
        compilerApp.createVariable(dataElementId(), "ID");
        compilerApp.createVariable(dataElementCount(), "INT");
        compilerApp.createVariable(dataElementList(), "LIST_OF_DATA_OBJECT");
	}


	@Override
	protected Place createPlace() {
		return compilerApp.createPlace(normalizedName, "DATA_OBJECT");
	}


	public PlaceNode creationCounterForPage(SubpageElement page) {
		return creationCounterPlaces.computeIfAbsent(page, _page -> _page.createFusionPlace(dataElementCount(), "INT", "1`0"));
	}


	@Override
	public String annotationForDataFlow(BaseElement otherEnd, StatefulDataAssociation<?, ?> assoc) {
        String stateString = ", state = "+assoc.getStateName();
        String caseId = compilerApp.caseId();
        if(!assoc.isCollection()) {
            return "{id = "+dataElementId()+" , "+caseId+" = "+caseId+stateString+"}";
        } else {
        	if(assoc.isInput() || assoc.getStateName().isEmpty()) {
        		return dataElementList();
        	} else {
        		return "mapState "+dataElementList()+" "+assoc.getStateName();
        	}
        }
        
	}

	@Override
	public String collectionCreationGuard(BaseElement otherEnd, StatefulDataAssociation<?, ?> assoc) {
        String stateString = ", state = "+assoc.getStateName();
		String caseId = compilerApp.caseId();
		String className = normalizedName;
		String identifyingObjectId = compilerApp.getDataObjectCollectionIdentifier((Activity) otherEnd, this).dataElementId();
		return dataElementList() + " = (map (fn(el) => {id = unpack el "+className+" , "+caseId+" = "+caseId+stateString+"}) (listAssocs "+identifyingObjectId+" "+className+" assoc))";
	}

	@Override
	public boolean isDataObjectWrapper() {
		return true;
	}


	@Override
	public boolean isDataStoreWrapper() {
		return false;
	}

}
