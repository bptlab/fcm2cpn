package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.Optional;

import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.cpntools.accesscpn.model.Place;

public class DataStoreWrapper extends DataElementWrapper<DataStore, DataStoreReference> {

	public DataStoreWrapper(CompilerApp compilerApp, String trimmedName) {
		super(compilerApp, trimmedName);

        compilerApp.createVariable(dataObjectId(), "STRING");
		
	}

	@Override
	protected Place createPlace() {
    	return compilerApp.createPlace(trimmedName, "DATA_STORE", "1`\"store_"+namePrefix()+"\"");
	}

	@Override
	public String annotationForDataFlow(Optional<String> stateName) {
        return dataObjectId();
	}

}
