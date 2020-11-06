package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.RefPlace;
import org.cpntools.accesscpn.model.Transition;

public class SubpageElement {

	private final CompilerApp compilerApp;
	public final String id;
	private final Page page;
	private final Instance mainPageTransition;
	private final List<Transition> subpageTransitions;
	private final Map<Place, RefPlace> placeReferences;
	
	public SubpageElement(CompilerApp compilerApp, String id, Page page, Instance mainTransition) {
		this(compilerApp, id, page, mainTransition, new ArrayList<>());
	}
	
	public SubpageElement(CompilerApp compilerApp, String id, Page page, Instance mainTransition, List<Transition> subpageTransitions) {
		this.compilerApp = compilerApp;
		this.id = id;
		this.page = page;
		this.mainPageTransition = mainTransition;
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
				mainPageTransition);
		});
	}
	
	public List<Arc> createArcsFrom(Place place, String inscription) {
		return getSubpageTransitions().stream()
				.map(transition -> compilerApp.createArc(getPage(), refPlaceFor(place), transition, inscription))
				.collect(Collectors.toList());
	}
	
	public Arc createArcFrom(Place place, Transition transition, String inscription) {
		assert getSubpageTransitions().contains(transition);
		return compilerApp.createArc(getPage(), refPlaceFor(place), transition, inscription);
	}
	
	public List<Arc> createArcsTo(Place place, String inscription) {
		return getSubpageTransitions().stream()
				.map(transition -> compilerApp.createArc(getPage(), transition, refPlaceFor(place), inscription))
				.collect(Collectors.toList());
	}
	
	public Arc createArcTo(Place place, Transition transition, String inscription) {
		assert getSubpageTransitions().contains(transition);
		return compilerApp.createArc(getPage(), transition, refPlaceFor(place), inscription);
	}
	
	public void createGuards(String guard) {
		getSubpageTransitions().stream().forEach(transition -> {
			
			transition.getCondition().setText(guard);
		});
	}

	public Instance getMainTransition() {
		return mainPageTransition;
	}

	public Page getPage() {
		return page;
	}

	public List<Transition> getSubpageTransitions() {
		return subpageTransitions;
	}
}