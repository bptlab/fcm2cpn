package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.uni_potsdam.hpi.bpt.fcm2cpn.Pair;

public class Association extends Pair<AssociationEnd, AssociationEnd> {

	public Association(AssociationEnd first, AssociationEnd second) {
		super(first, second);
	}
	
	public boolean associates(String dataObjectA, String dataObjectB) {
		return Stream.of(dataObjectA, dataObjectB).collect(Collectors.toSet()).equals(
				Stream.of(first.getDataObject(), second.getDataObject()).collect(Collectors.toSet()));
	}
	
	public AssociationEnd getEnd(String dataObject) {
		return Stream.of(first, second).filter(end -> end.getDataObject().equals(dataObject)).findAny().get();
	}

}
