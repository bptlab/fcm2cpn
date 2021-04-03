package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import java.util.List;

public class TerminationCondition {
	
	/** A disjunction list of conjunction lists of literals */
	private final List<List<TerminationLiteral>> disjunctiveNormalForm;
	
	public TerminationCondition(List<List<TerminationLiteral>> disjunctiveNormalForm) {
		this.disjunctiveNormalForm = disjunctiveNormalForm;
	}

}
