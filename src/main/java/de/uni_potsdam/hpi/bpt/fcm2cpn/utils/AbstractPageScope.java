package de.uni_potsdam.hpi.bpt.fcm2cpn.utils;

import org.cpntools.accesscpn.model.Arc;
import org.cpntools.accesscpn.model.Instance;
import org.cpntools.accesscpn.model.Node;
import org.cpntools.accesscpn.model.Page;
import org.cpntools.accesscpn.model.PetriNet;
import org.cpntools.accesscpn.model.Place;
import org.cpntools.accesscpn.model.RefPlace;
import org.cpntools.accesscpn.model.Transition;
import org.cpntools.accesscpn.model.util.BuildCPNUtil;

/**
 * Interface with default utility methods. For compiler components that work in the scope of one cpn page.
 *
 */
public interface AbstractPageScope {
	
	public BuildCPNUtil builder();
	public PetriNet petriNet();
	public Page getPage();
	
    //=======Generation Methods======
    public default Page createPage(String name) {
    	return builder().addPage(petriNet(), name);
    }
    
    public default Place createPlace(String name, String type) {
    	return builder().addPlace(getPage(), name, type);
    }
    
    public default Place createPlace(String name, String type, String initialMarking) {
    	return builder().addPlace(getPage(), name, type, initialMarking);
    }

    public default RefPlace createReferencePlace(Place sourcePlace, Instance mainPageTransition) {
    	return builder().addReferencePlace(
				getPage(), 
				sourcePlace.getName().asString(), 
				sourcePlace.getSort().getText(), 
				"", 
				sourcePlace, 
				mainPageTransition);
    }
    
    public default RefPlace createFusionPlace(String name, String type, String initialMarking) {
    	return builder().addFusionPlace(getPage(), name, type, initialMarking, name);
    }
    
    public default Transition createTransition(String name) {
    	return builder().addTransition(getPage(), name);
    }
    
    public default Instance createSubpageTransition(String name, Page page) {
    	return builder().createSubPageTransition(page, getPage(), name);
    }
    
    public default Arc createArc(Node source, Node target, String annotation) {
    	return builder().addArc(getPage(), source, target, annotation);
    }
    
    public default Arc createArc(Node source, Node target) {
    	return createArc(source, target, "");
    }
    
    public default void createVariable(String name, String type) {
        builder().declareVariable(petriNet(), name, type);
    }

}
