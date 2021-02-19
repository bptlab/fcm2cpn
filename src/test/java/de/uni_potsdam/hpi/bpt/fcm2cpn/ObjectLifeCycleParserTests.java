package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.elementName;
import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.normalizeElementName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiConsumer;

import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ArgumentsSource;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.Association;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.AssociationEnd;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle.State;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.AssociationsProvider;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ModelsToTest;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.TestWithAllModels;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.DataObjectIOSet;
import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Pair;

public class ObjectLifeCycleParserTests extends ModelStructureTests {
	
	@Override
	public void compileModel() {
		BpmnPreprocessor.process(bpmn);
		parseDataModel();
		parseOLCs();
	}    
	
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	public void testOLCsAreComplete(DataObject dataObject) {
		String className = elementName(dataObject);
		assertNotNull(olcFor(className), "No OLC for "+className+" exists.");
	}
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	@ForEachIOSet
	public void testAllTransitionsByActivityArePossible(Activity activity, DataObjectIOSet ioSet) {
		Pair<Map<Pair<String, Boolean>, String>, Map<Pair<String, Boolean>, String>> ioStateMap = ioAssociationsToStateMaps(ioSet);
		ioStateMap.first.forEach((object, inputState) -> {
			String outputState = ioStateMap.second.get(object);// All read objects are written back, so this is never null
			if(!inputState.equals(outputState)) {
				ObjectLifeCycle olc = olcFor(object.first);
				assertTrue(olc.getState(inputState).get().getSuccessors().contains(olc.getState(outputState).get()), 
						"Olc does not support lifecycle transition ("+inputState+" -> "+outputState+") for data object "+object+" which is definied by activity "+elementName(activity));
			}
		});
	}
	
	@TestWithAllModels
	public void testAllOLCStateTransitionsAreValid() {
		forEachOLCTransition((olc, states) -> {
			String dataObject = olc.getClassName();
			boolean anyActivityCanTakeThisTransition = bpmn.getModelElementsByType(Activity.class).stream().anyMatch(activity -> {
				return ioAssociationCombinations(activity).stream().anyMatch(ioSet -> 
				ioSet.first.stream().anyMatch(inputAssoc -> inputAssoc.dataElementName().equals(dataObject) && states.first.getStateName().equals(inputAssoc.getStateName()))
				&& ioSet.second.stream().anyMatch(outputAssoc -> outputAssoc.dataElementName().equals(dataObject) && states.second.getStateName().equals(outputAssoc.getStateName()))
				);
			});
			assertTrue(anyActivityCanTakeThisTransition, "There is no activity which changes state of '"+dataObject+"' from "+states.first.getStateName()+" to "+states.second.getStateName());
		});
	}
	

	@Test
	@ModelsToTest("NoIOSpecification")
	public void testOLCIsCreatedWithoutIOSpecification() {
		ObjectLifeCycle olc = olcFor(normalizeElementName("A"));
		assertEquals(new HashSet<>(Arrays.asList(olc.getState("Y").get())), olc.getState("X").get().getSuccessors(),
				"OLC Transition for activity without IO Specification was not created.");
	}
	
	@TestWithAllModels
	@ArgumentsSource(AssociationsProvider.class)
	@ForEachBpmn(Activity.class)
	public void testEachCreationLeadsToUpdateableAssociations(String first, String second, Activity activity) {
		Association assoc = dataModel.getAssociation(first, second).get();
		AssociationEnd relevantEnd = assoc.getEnd(second);
		
		ioAssociationCombinations(activity).stream()
			.filter(ioSet -> ioSet.createsAssociationBetween(first, second) && ioSet.creates(second))
			.forEach(ioSet -> {
				var stateMap = ioAssociationsToStateMaps(ioSet);
				String readStateName = stateMap.first.getOrDefault(new Pair<>(first, false), stateMap.first.get(new Pair<>(first, true)));
				State readState = olcFor(first).getState(readStateName).get();
				assertTrue(readState.getUpdateableAssociations().contains(relevantEnd), 
						"Association between "+first+" and "+second+" is created in one IO-Set of activity "+elementName(activity)
								+ ", but not marked in state "+readState.getStateName()+" of "+first);
			});
	}
	
	@TestWithAllModels
	@ForEachBpmn(DataObject.class)
	public void testEachUpdateableAssociationsComesFromCreation(DataObject dataObject) {
		String dataObjectName = elementName(dataObject);
		ObjectLifeCycle olc = olcFor(dataObjectName);
		Collection<Activity> activities = bpmn.getModelElementsByType(Activity.class);
		
		olc.getStates().forEach(state -> {
			state.getUpdateableAssociations().forEach(relevantEnd -> {
				String otherDataObject = relevantEnd.getDataObject();
				assertTrue(activities.stream()
					.flatMap(activity -> ioAssociationCombinations(activity).stream())
					.filter(ioSet -> ioSet.createsAssociationBetween( dataObjectName, otherDataObject))
					
					.anyMatch(ioSet -> ioSet.readsInState( dataObjectName, state)),
					
					dataObjectName+" state "+state+" has an updateable association to "+otherDataObject
					+", but there is no IO combination of any activity that creates an association while "+dataObjectName+" is in that state");
			});
		});		
	}
	
	
//=========== Utility ================
	
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
