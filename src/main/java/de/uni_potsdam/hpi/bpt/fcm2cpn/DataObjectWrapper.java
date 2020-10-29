package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.PlaceNode;

public class DataObjectWrapper extends DataElementWrapper<DataObject, DataObjectReference> {

	private final Map<Page, PlaceNode> creationCounterPlaces = new HashMap<>();
	

	public DataObjectWrapper(CompilerApp compilerApp, String normalizedName) {
		super(compilerApp, normalizedName);
		
		assert compilerApp.getDataModel().hasDataObject(getNormalizedName());
		
        compilerApp.createVariable(dataElementId(), "ID");
        compilerApp.createVariable(dataElementCount(), "INT");
        compilerApp.createVariable(dataElementList(), "LIST_OF_DATA_OBJECT");
	}


	@Override
	protected Place createPlace() {
		return compilerApp.createPlace(normalizedName, "DATA_OBJECT");
	}


	public PlaceNode creationCounterForPage(Page page) {
		return creationCounterPlaces.computeIfAbsent(page, _page -> compilerApp.createFusionPlace(_page, dataElementCount(), "INT", "1`0"));
	}


	@Override
	public String annotationForDataFlow(BaseElement otherEnd, StatefulDataAssociation<?, ?> assoc) {
        String stateString = assoc.getStateName().map(x -> ", state = "+x).orElse("");
        String caseId = compilerApp.caseId();
        if(!assoc.isCollection()) {
            return "{id = "+dataElementId()+" , "+caseId+" = "+caseId+stateString+"}";
        } else {
        	String className = normalizedName;
        	String identifyingObjectId = compilerApp.getDataObjectCollectionIdentifier((Activity) otherEnd, this).dataElementId();
        	return "map (fn(el) => {id = unpack el "+className+" , "+caseId+" = "+caseId+stateString+"}) (listAssocs "+identifyingObjectId+" "+className+" assoc)";
        }
        
	}

	@Override
	public String guardForList(BaseElement otherEnd, StatefulDataAssociation<?, ?> assoc) {
		String stateString = assoc.getStateName().map(x -> ", state = "+x).orElse("");
		String caseId = compilerApp.caseId();
		if(!assoc.isCollection()) {
			return null;
		} else {
			String className = normalizedName;
			String identifyingObjectId = compilerApp.getDataObjectCollectionIdentifier((Activity) otherEnd, this).dataElementId();
			return dataElementList() + " = (map (fn(el) => {id = unpack el "+className+" , "+caseId+" = "+caseId+stateString+"}) (listAssocs "+identifyingObjectId+" "+className+" assoc))";
		}
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
