package de.uni_potsdam.hpi.bpt.fcm2cpn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.RefPlace;
import org.cpntools.accesscpn.model.Transition;
import org.cpntools.accesscpn.model.util.BuildCPNUtil;

import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.AbstractPageScope;

/**
 * This class provides a wrapper for easy access to cpn subpages.
 * 
 * @author Leon Bein
 *
 */
public class SubpageElement implements AbstractPageScope {

	private final CompilerApp compilerApp;
	private final Page page;
	private final Instance mainPageTransition;
	private final Map<Place, RefPlace> placeReferences;
	
	public SubpageElement(CompilerApp compilerApp, Page page, Instance mainTransition) {
		this.compilerApp = compilerApp;
		this.page = page;
		this.mainPageTransition = mainTransition;
		this.placeReferences = new HashMap<>();
	}
	
	public RefPlace refPlaceFor(Place place) {
		return placeReferences.computeIfAbsent(place, sourcePlace -> {
			return createReferencePlace( 
				sourcePlace, 
				mainPageTransition);
		});
	}
	
	public List<Arc> createArcsFrom(Place place, String inscription) {
		return getSubpageTransitions().stream()
				.map(transition -> createArc(refPlaceFor(place), transition, inscription))
				.collect(Collectors.toList());
	}
	
	public Arc createArcFrom(Place place, Transition transition, String inscription) {
		assert getSubpageTransitions().contains(transition);
		return createArc(refPlaceFor(place), transition, inscription);
	}
	
	public List<Arc> createArcsTo(Place place, String inscription) {
		return getSubpageTransitions().stream()
				.map(transition -> createArc(transition, refPlaceFor(place), inscription))
				.collect(Collectors.toList());
	}
	
	public Arc createArcTo(Place place, Transition transition, String inscription) {
		assert getSubpageTransitions().contains(transition);
		return createArc(transition, refPlaceFor(place), inscription);
	}
	
	public Instance getMainTransition() {
		return mainPageTransition;
	}

	public Page getPage() {
		return page;
	}
	
	private List<Transition> getSubpageTransitions() {
		return StreamSupport.stream(page.transition().spliterator(), false).collect(Collectors.toList());
	}

	@Override
	public BuildCPNUtil builder() {
		return compilerApp.builder();
	}

	@Override
	public PetriNet petriNet() {
		return compilerApp.petriNet();
	}
}