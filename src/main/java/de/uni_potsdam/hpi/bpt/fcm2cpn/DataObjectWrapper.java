package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.PlaceNode;

public class DataObjectWrapper extends DataElementWrapper<DataObject, DataObjectReference> {

	private final Map<Page, PlaceNode> creationCounterPlaces = new HashMap<>();
	

	public DataObjectWrapper(CompilerApp compilerApp, String trimmedName) {
		super(compilerApp, trimmedName);
		
		assert compilerApp.getDataModel().hasDataObject(getNormalizedName());
		
        compilerApp.createVariable(dataElementId(), "STRING");
        compilerApp.createVariable(dataElementCount(), "INT");
	}


	@Override
	protected Place createPlace() {
		return compilerApp.createPlace(normalizedName, "DATA_OBJECT");
	}


	public PlaceNode creationCounterForPage(Page page) {
		return creationCounterPlaces.computeIfAbsent(page, _page -> compilerApp.createFusionPlace(_page, dataElementCount(), "INT", "1`0"));
	}


	@Override
	public String annotationForDataFlow(Optional<String> stateName) {
        String stateString = stateName.map(x -> ", state = "+x).orElse("");
        String caseId = compilerApp.caseId();
        return "{id = "+dataElementId()+" , "+caseId+" = "+caseId+stateString+"}";
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
