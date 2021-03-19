package de.uni_potsdam.hpi.bpt.fcm2cpn.dataelements;


import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.DataStore;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.cpntools.accesscpn.model.Place;

import de.uni_potsdam.hpi.bpt.fcm2cpn.CompilerApp;
import de.uni_potsdam.hpi.bpt.fcm2cpn.StatefulDataAssociation;

public class DataStoreWrapper extends DataElementWrapper<DataStore, DataStoreReference> {
	
	protected Place place;

	public DataStoreWrapper(CompilerApp compilerApp, String trimmedName) {
		super(compilerApp, trimmedName);

        compilerApp.createVariable(dataElementId(), "STRING");
		
	}

	@Override
	protected void createPlaces() {
    	this.place = compilerApp.createPlace(normalizedName, "DATA_STORE", "1`\"store_"+namePrefix()+"\"");
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

	@Override
	public String collectionCreationGuard(BaseElement otherEnd, StatefulDataAssociation<?, ?> assoc) {
		throw new UnsupportedOperationException("Data stores cannot be created as lists");
	}

	@Override
	public Place getPlace(String state) {
		return place;
	}

}
