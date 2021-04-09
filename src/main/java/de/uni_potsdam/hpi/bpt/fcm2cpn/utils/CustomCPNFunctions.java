package de.uni_potsdam.hpi.bpt.fcm2cpn.utils;

/**
 * Makes the custom functions in generated cpns available as java functions that return the corresponding cpn code
 */
public class CustomCPNFunctions {
	
	/** Comment to show that lower bound check is goal cardinality check.*/
	public static final String GOAL_CARDINALITY_COMMENT = "(*goal cardinality*)";
	
	public static String enforceLowerBound(String identifierId, String collectionClass, int bound) {
		return "(enforceLowerBound "+identifierId+" "+collectionClass+" assoc "+bound+")";
	}
	
	public static String enforceGoalLowerBound(String singleObjectId, String collectionClass, int bound) {
		return enforceLowerBound(singleObjectId, collectionClass, bound)+" "+GOAL_CARDINALITY_COMMENT;
	}
	
	public static String enforceGoalLowerBoundForAll(String singleObjectList, String collectionClass, int bound) {
		return "(List.all (fn oId => "+enforceLowerBound("oId", collectionClass, bound)+") (List.map (fn obj => #id obj) " + singleObjectList + ")) "+CustomCPNFunctions.GOAL_CARDINALITY_COMMENT;
	}
	
	public static String enforceUpperBound(String singleObjectId, String collectionClass, int bound) {
		return "(enforceUpperBound "+singleObjectId+" "+collectionClass+" assoc "+bound+")";
	}

}
