package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import java.util.List;
import java.util.stream.Collectors;

public class TerminationCondition {
	
	/** A disjunction list of conjunction lists of literals */
	private final List<List<TerminationLiteral>> disjunctiveNormalForm;

	public TerminationCondition(List<List<TerminationLiteral>> disjunctiveNormalForm) {
		this.disjunctiveNormalForm = disjunctiveNormalForm;
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName() + "(\n\t   "+
				String.join("\n\t|| ", 
						disjunctiveNormalForm.stream().map(clause -> 
							"("+String.join(" && ", clause.stream().map(Object::toString).collect(Collectors.toList()))+")"
						).collect(Collectors.toList()))+"\n)";
	}
	
	public List<List<TerminationLiteral>> getDisjunctiveNormalForm() {
		return disjunctiveNormalForm;
	}
	
	public List<List<TerminationLiteral>> getClauses() {
		return getDisjunctiveNormalForm();
	}

}
