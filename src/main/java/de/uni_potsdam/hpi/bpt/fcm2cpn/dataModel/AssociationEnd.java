package de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel;

public class AssociationEnd {
	
	public static int UNLIMITED = Integer.MAX_VALUE;
	
	private String dataObject;
	private int lowerBound = 1;
	private int goalLowerBound = 1;
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

	public int getGoalLowerBound() {
		return goalLowerBound;
	}
	
	public boolean hasTightGoalLowerBound() {
		return goalLowerBound > lowerBound;
	}

	public void setGoalLowerBound(int goalLowerBound) {
		this.goalLowerBound = goalLowerBound;
	}

	@Override
	public String toString() {
		return "("+lowerBound+(hasTightGoalLowerBound() ? "g"+goalLowerBound : "")+".."+(upperBound == UNLIMITED ? "*" : upperBound)+" "+dataObject+")";
	}
	
	public boolean isForDataObject(String dataObject) {
		return this.dataObject.equalsIgnoreCase(dataObject);
	}
}