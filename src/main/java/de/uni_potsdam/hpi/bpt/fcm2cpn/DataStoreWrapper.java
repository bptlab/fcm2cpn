package de.uni_potsdam.hpi.bpt.fcm2cpn;

import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.cpntools.accesscpn.model.Place;

public class DataStoreWrapper extends DataElementWrapper<DataStore, DataStoreReference> {

	public DataStoreWrapper(CompilerApp compilerApp, String trimmedName) {
		super(compilerApp, trimmedName);

        compilerApp.createVariable(dataElementId(), "STRING");
		
	}

	@Override
	protected Place createPlace() {
    	return compilerApp.createPlace(normalizedName, "DATA_STORE", "1`\"store_"+namePrefix()+"\"");
	}

	@Override
	public String annotationForDataFlow(BaseElement otherEnd, StatefulDataAssociation<?, ?> assoc) {
        return dataElementId();
	}

	@Override
	public boolean isDataObjectWrapper() {
		return false;
	}

	@Override
	public boolean isDataStoreWrapper() {
		return true;
	}

}
