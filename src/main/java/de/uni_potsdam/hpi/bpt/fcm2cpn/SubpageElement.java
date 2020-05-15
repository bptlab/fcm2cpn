package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.RefPlace;
import org.cpntools.accesscpn.model.Transition;

public class SubpageElement {

	private final CompilerApp compilerApp;
	public final String id;
	private Page page;
	private Instance mainTransition;
	private List<Transition> subpageTransitions;
	private Map<Place, RefPlace> placeReferences;
	public SubpageElement(CompilerApp compilerApp, String id, Page page, Instance mainTransition, List<Transition> subpageTransitions) {
		this.compilerApp = compilerApp;
		this.id = id;
		this.page = page;
		this.mainTransition = mainTransition;
		this.subpageTransitions = subpageTransitions;
		this.placeReferences = new HashMap<>();
	}
	
	RefPlace refPlaceFor(Place place) {
		return placeReferences.computeIfAbsent(place, sourcePlace -> {
			return this.compilerApp.getBuilder().addReferencePlace(
				page, 
				sourcePlace.getName().asString(), 
				sourcePlace.getSort().getText(), 
				"", 
				sourcePlace, 
				mainTransition);
		});
	}

	public Instance getMainTransition() {
		return mainTransition;
	}

	public Page getPage() {
		return page;
	}

	public List<Transition> getSubpageTransitions() {
		return subpageTransitions;
	}

	public void setSubpageTransitions(List<Transition> subpageTransitions) {
		this.subpageTransitions = subpageTransitions;
	}
}