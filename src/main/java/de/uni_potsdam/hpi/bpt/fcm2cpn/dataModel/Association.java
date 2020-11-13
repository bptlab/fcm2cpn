package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;

public class Association extends Pair<AssociationEnd, AssociationEnd> {

	public Association(AssociationEnd first, AssociationEnd second) {
		super(first, second);
	}
	
	public boolean associates(String dataObjectA, String dataObjectB) {
		return (dataObjectA.equalsIgnoreCase(first.getDataObject()) && dataObjectB.equalsIgnoreCase(second.getDataObject())) ||
		(dataObjectA.equalsIgnoreCase(second.getDataObject()) && dataObjectB.equalsIgnoreCase(first.getDataObject()));
	}
	
	public AssociationEnd getEnd(String dataObject) {
		return stream().filter(end -> end.getDataObject().equalsIgnoreCase(dataObject)).findAny().get();
	}
	
	public Stream<AssociationEnd> stream() {
		return Stream.of(first, second);
	}

}
