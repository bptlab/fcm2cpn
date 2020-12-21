package de.uni_potsdam.hpi.bpt.fcm2cpn.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.camunda.bpm.model.bpmn.impl.instance.SourceRef;
import org.camunda.bpm.model.bpmn.impl.instance.TargetRef;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.DataAssociation;
import org.camunda.bpm.model.bpmn.instance.DataInput;
import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutput;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataState;
import org.camunda.bpm.model.bpmn.instance.DataStoreReference;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.xml.ModelInstance;
import org.cpntools.accesscpn.model.Transition;

import de.uni_potsdam.hpi.bpt.fcm2cpn.StatefulDataAssociation;

public class Utils {
	
	/**
	 * Determines a name for a bpmn element, uses id as substitute if no name is available
	 */
	public static String elementName(FlowElement element) {
    	String name = element.getName();
    	if(name == null || name.equals("")) name = element.getId();
    	return name;
	}
	
	/**
	 * Extends {@link #elementName(FlowElement)} to work for objects like datastores
	 */
	public static String elementName(ItemAwareElement element) {
		if(element instanceof FlowElement) {
			return elementName((FlowElement)element);
		} else {
	    	String name = element.getAttributeValue("name");
	    	if(name == null || name.equals("")) name = element.getId();
	    	return name;
		}
	}
	
	/**
	 * Makes element names usable as page ids, but also allows to equalize names with excess whitespaces
	 */
    public static String normalizeElementName(String name) {
    	return name.replace('\n', ' ').trim().toLowerCase();
    }
    
    /**
     * Splits a state name to single states and normalizes them. Example: [A | b |  c] becomes A, B, and C
     */
    public static Stream<String> dataObjectStateToNetColors(String state) {
    	return Arrays.stream(state.replaceAll("\\[", "").replaceAll("\\]", "").split("\\|"))
    			.map(Utils::singleDataObjectStateToNetColor);
    }
    
    /**
     * Normalizes a single state name, can then be used as value for the STATE color set
     */
    public static String singleDataObjectStateToNetColor(String state) {
    	return state
			.trim()
    		.replaceAll("\\s","_")
    		.replaceAll("-","_")
    		.toUpperCase();
    }
    
    
    /**
     * Resolves the target element of a data association
     */
    public static BaseElement getTarget(DataAssociation assoc) {
    	ModelInstance model = assoc.getModelInstance();
    	return (BaseElement) assoc.getChildElementsByType(TargetRef.class).stream()
    		.map(ref -> ref.getTextContent())
    		.map(model::getModelElementById)
    		.findAny().orElse(null);  		
    }
    

    /**
     * Resolves the source element of a data association
     */
    public static BaseElement getSource(DataAssociation assoc) {
    	ModelInstance model = assoc.getModelInstance();
    	return (BaseElement) assoc.getChildElementsByType(SourceRef.class).stream()
    		.map(ref -> ref.getTextContent())
    		.map(model::getModelElementById)
    		.findAny().orElse(null);  		
    }
    
    /**
     * Finds the data association that references an input object from an ioSpecification
     */
    public static DataInputAssociation getAssociation(DataInput input) {
    	ModelInstance model = input.getModelInstance();
    	return model.getModelElementsByType(DataInputAssociation.class).stream()
    		.filter(assoc -> input.equals(getTarget(assoc)))
    		.findAny()
    		.get();
    }
    
    /**
     * Finds the data association that references an output object from an ioSpecification
     */    
    public static DataOutputAssociation getAssociation(DataOutput output) {
    	ModelInstance model = output.getModelInstance();
    	return model.getModelElementsByType(DataOutputAssociation.class).stream()
    		.filter(assoc -> output.equals(getSource(assoc)))
    		.findAny()
    		.get();
    }
    
    public static ItemAwareElement getReferencedElement(ItemAwareElement dataElementReference) {
    	assert isDataElementReference(dataElementReference);
    	if(dataElementReference instanceof DataObjectReference) {
    		return ((DataObjectReference) dataElementReference).getDataObject();
    	} else {
    		return ((DataStoreReference) dataElementReference).getDataStore();
    	}
    }
    
    /***
     * Creates the n-fold cartesian product of a collection of lists.
     */
	public static <T> List<List<T>> allCombinationsOf(Collection<List<T>> sets) {
        int numberOfCombinations = sets.stream()
                .mapToInt(Collection::size)
                .reduce(1, (a,b) -> a*b);
        
        List<List<T>> combinations = new ArrayList<>(numberOfCombinations);
        for(int i = 0; i < numberOfCombinations; i++) {
            int j = 1;
            List<T> combination = new ArrayList<>();
            for(List<T> objectVariants : sets) {
            	combination.add(objectVariants.get((i/j)%objectVariants.size()));
                j *= objectVariants.size();
            }
            combinations.add(combination);
        }
        return combinations;
	}
	
	public static ItemAwareElement dataElementReferenceOf(DataAssociation assoc) {
    	BaseElement source = getSource(assoc);
    	BaseElement target = getTarget(assoc);
        ItemAwareElement dataElementReference = (ItemAwareElement) (isDataElementReference(source) ? source : target);
        assert dataElementReference != null;
        return dataElementReference;
	}
	
	public static Stream<String> dataElementStates(ItemAwareElement dataElementReference) {
		assert isDataElementReference(dataElementReference);
		return Optional.ofNullable(dataElementReference.getDataState())
        		.map(DataState::getName)
        		.map(stateName -> dataObjectStateToNetColors(stateName))
        		.orElse(Stream.of((String)null));
	}
	
	public static boolean isDataElementReference(BaseElement dataElementReference) {
		return dataElementReference instanceof DataObjectReference || dataElementReference instanceof DataStoreReference;
	}
	
	/**
	 * Creates distinct result object for each state of one bpmn data object reference, enables the [stateA | stateB] notation
	 */
    public static <T extends DataAssociation> Stream<StatefulDataAssociation<T, ?>> splitDataAssociationByState(T assoc) {
    	ItemAwareElement dataElementReference = dataElementReferenceOf(assoc);
    	Stream<String> possibleStates = dataElementStates(dataElementReference);
    	boolean isCollection = Optional.ofNullable(getReferencedElement(dataElementReference).getAttributeValue("isCollection")).map(Boolean::parseBoolean).orElse(false);
    	return possibleStates.map(state -> new StatefulDataAssociation<>(assoc, state, dataElementReference, isCollection));
    }
	
	/**
	 * Shortcut to append new conditions to cpn transition guards
	 */
    public static void addGuardCondition(Transition transition, String newGuard) {
        String existingGuard = transition.getCondition().asString();
        if (existingGuard.contains(newGuard)) return;
        existingGuard = existingGuard.replaceFirst("^\\[", "").replaceFirst("]$", "");
        if(!existingGuard.isEmpty()) existingGuard += ",\n";
        transition.getCondition().setText("[" + existingGuard + newGuard + "]");
    }

}
