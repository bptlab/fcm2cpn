package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class DataModel {
	
	private final Set<String> dataObjects;
	private final Set<Association> associations;
	
	public DataModel(Collection<String> dataObjects, Collection<Association> associations) {
		this.dataObjects = new HashSet<>(dataObjects);
		this.associations = new HashSet<>(associations);
	}
	
	public static DataModel none() {
		return new DataModel(null, null) {
			@Override
			public boolean hasDataObject(String dataObject) {
				return true;
			}
			@Override
			public boolean isAssociated(String dataObjectA, String dataObjectB) {
				return false;
			}
		};
	}
	
	
	public boolean hasDataObject(String dataObject) {
		return getDataObjects().contains(dataObject);
	}

	/**
	 * 
	 * @param dataObjectA: Normalized Data Object Name
	 * @param dataObjectB: Normalized Data Object Name
	 * @return
	 */
	public boolean isAssociated(String dataObjectA, String dataObjectB) {
//		if(dataObjectA.equals("A") && (dataObjectB.equals("C") || dataObjectB.equals("D"))) return false;
//		if(dataObjectB.equals("A") && (dataObjectA.equals("C") || dataObjectA.equals("D"))) return false;
//		return true; //TODO
		return getAssociation(dataObjectA, dataObjectB).isPresent();
	}
	
	public Optional<Association> getAssociation(String dataObjectA, String dataObjectB) {
		return associations.stream().filter(assoc -> assoc.associates(dataObjectA, dataObjectB)).findAny();
	}

	public Set<String> getDataObjects() {
		return dataObjects;
	}

	public Set<Association> getAssociations() {
		return associations;
	}
	
}
