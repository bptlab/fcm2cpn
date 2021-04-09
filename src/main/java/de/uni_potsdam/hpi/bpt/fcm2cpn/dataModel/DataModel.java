package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

public class DataModel {
	
	private final Set<String> dataObjects;
	private final Set<Association> associations;
	
	public DataModel(Collection<String> dataObjects, Collection<Association> associations) {
		dataObjects.forEach(Utils::assumeNameIsNormalized);
		this.dataObjects = new HashSet<>(dataObjects);
		this.associations = new HashSet<>(associations);
	}
	
	public static DataModel none() {
		return new DataModel(Collections.emptySet(), Collections.emptySet()) {
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
	
	public Stream<Association> getAssociationsForDataObject(String dataObject) {
		return getAssociations().stream()
				.filter(assoc -> assoc.containsDataObject(dataObject));
	}
	
}
