package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static org.junit.jupiter.api.Assertions.*;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.junit.jupiter.api.Test;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycleParser;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;

public class ObjectLifeCycleParserTests extends ModelStructureTests {
	
	private ObjectLifeCycle[] olcs;
	@Override
	public void compileModel() {
		super.compileModel();
		this.olcs = ObjectLifeCycleParser.getOLCs(dataModel, bpmn);
	}    
	
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	public void testOLCsAreComplete(DataObject dataObject) {
		String className = normalizeElementName(dataObject.getName());
		assertNotNull(olcFor(className), "No OLC for "+className+" exists.");
	}
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testAllTransitionsByActivityArePossible(Activity activity, DataObjectIOSet ioSet) {
		Pair<Map<String, Optional<String>>, Map<String, Optional<String>>> ioCombination = ioAssociationsToStateMaps(ioSet);
		ioCombination.first.forEach((object, inputState) -> {
			if(ioCombination.second.containsKey(object)) {
				Optional<String> outputState = ioCombination.second.get(object);
				if(!inputState.equals(outputState)) {
					ObjectLifeCycle olc = olcFor(object);
					assertTrue(olc.getState(inputState.get()).get().getSuccessors().contains(olc.getState(outputState.get()).get()), 
							"Olc does not support lifecycle transition ("+inputState.get()+" -> "+outputState.get()+") for data object "+object+" which is definied by activity "+activity.getName());
				}
			}
		});
	}
	
	@TestWithAllModels
	public void testAllOLCStateTransitionsAreValid() {
		forEachOLCTransition((olc, states) -> {
			String dataObject = olc.getClassName();
			boolean anyActivityCanTakeThisTransition = bpmn.getModelElementsByType(Activity.class).stream().anyMatch(activity -> {
				return ioAssociationCombinations(activity).stream().anyMatch(ioSet -> 
				ioSet.first.stream().anyMatch(inputAssoc -> inputAssoc.dataElementName().equals(dataObject) && states.first.getStateName().equals(inputAssoc.getStateName().orElse(null)))
				&& ioSet.second.stream().anyMatch(outputAssoc -> outputAssoc.dataElementName().equals(dataObject) && states.second.getStateName().equals(outputAssoc.getStateName().orElse(null)))
				);
			});
			assertTrue(anyActivityCanTakeThisTransition, "There is no activity which changes state of '"+dataObject+"' from "+states.first.getStateName()+" to "+states.second.getStateName());
		});
	}
	

//	@Test
//	@ModelsToTest("NoIOSpecification")
//	public void testOLCIsCreatedWithoutIOSpecification() {
//		ObjectLifeCycle olc = olcFor(normalizeElementName("A"));
//		assertEquals(new HashSet<>(Arrays.asList(olc.getState("Y").get())), olc.getState("X").get().getSuccessors(),
//				"OLC Transition for activity without IO Specification was not created.");
//	}
	
	
//=========== Utility ================
	
	public ObjectLifeCycle olcFor(String dataObject) {
    	return Arrays.stream(olcs)
    			.filter(olc -> olc.getClassName().equals(dataObject))
    			.findAny()
    			.get();
    }
	
	public void forEachOLCTransition(BiConsumer<ObjectLifeCycle, Pair<ObjectLifeCycle.State, ObjectLifeCycle.State>> consumer) {
		for(ObjectLifeCycle olc : olcs) {
			for(ObjectLifeCycle.State startState : olc.getStates()) {
				for(ObjectLifeCycle.State targetState : startState.getSuccessors()) {
					consumer.accept(olc, new Pair<>(startState, targetState));
				}
			}
		}
	}
	

}
