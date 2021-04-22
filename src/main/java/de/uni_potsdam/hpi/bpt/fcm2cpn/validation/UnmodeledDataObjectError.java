package de.uni_potsdam.hpi.bpt.fcm2cpn.validation;

public class UnmodeledDataObjectError extends Error {

	private static final long serialVersionUID = 1L;
	
	public final String dataObject;
	
	public UnmodeledDataObjectError(String dataObject) {
		super("Data object \""+dataObject+"\" was found in process model, but not in data model.");
		this.dataObject = dataObject;
	}

}
