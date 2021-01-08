package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

import java.util.stream.Stream;

import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;

public class Association extends Pair<AssociationEnd, AssociationEnd> {

	public Association(AssociationEnd first, AssociationEnd second) {
		super(first, second);
		assert(first != second);
	}
	
	public boolean associates(String dataObjectA, String dataObjectB) {
		return first.isForDataObject(dataObjectA) && second.isForDataObject(dataObjectB) ||
				first.isForDataObject(dataObjectB) && second.isForDataObject(dataObjectA);
	}
	
	private Stream<AssociationEnd> endsFor(String dataObject) {
		return stream().filter(end -> end.isForDataObject(dataObject));
	}
	
	public boolean containsDataObject(String dataObject) {
		return endsFor(dataObject).count() > 0;
	}
	
	public AssociationEnd getEnd(String dataObject) {
		return endsFor(dataObject).findAny().get();
	}
	
	public AssociationEnd getOtherEnd(String dataObject) {
		AssociationEnd end = getEnd(dataObject);
		return stream().filter(otherEnd -> !(otherEnd == end)).findAny().get();
	}
	
	public Stream<AssociationEnd> stream() {
		return Stream.of(first, second);
	}

}
