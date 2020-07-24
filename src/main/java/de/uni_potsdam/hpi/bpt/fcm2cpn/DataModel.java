package de.uni_potsdam.hpi.bpt.fcm2cpn;


/**
 * Just a mock
 * @author Leon Bein
 *
 */
public class DataModel {

	/**
	 * 
	 * @param dataObjectA: Normalized Data Object Name
	 * @param dataObjectB: Normalized Data Object Name
	 * @return
	 */
	public boolean isAssociated(String dataObjectA, String dataObjectB) {
		if(dataObjectA.equals("A") && (dataObjectB.equals("C") || dataObjectB.equals("D"))) return false;
		if(dataObjectB.equals("A") && (dataObjectA.equals("C") || dataObjectA.equals("D"))) return false;
		return true; //TODO
	}
	
}
