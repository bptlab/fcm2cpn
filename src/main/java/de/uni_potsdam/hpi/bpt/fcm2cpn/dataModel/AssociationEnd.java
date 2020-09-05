package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

public class AssociationEnd {
	private String dataObject;
	private int lowerBound = 1;
	private int upperBound = 1;
	
	public AssociationEnd(String dataObject) {
		this.dataObject = dataObject;
	}

	public String getDataObject() {
		return dataObject;
	}

	public int getLowerBound() {
		return lowerBound;
	}

	public void setLowerBound(int lowerBound) {
		this.lowerBound = lowerBound;
	}

	public int getUpperBound() {
		return upperBound;
	}

	public void setUpperBound(int upperBound) {
		this.upperBound = upperBound;
	}
	
	@Override
	public String toString() {
		return "("+lowerBound+".."+(upperBound == Integer.MAX_VALUE ? "*" : upperBound)+" "+dataObject+")";
	}
}