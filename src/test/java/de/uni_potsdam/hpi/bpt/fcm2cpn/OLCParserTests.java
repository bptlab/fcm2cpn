package de.uni_potsdam.hpi.bpt.fcm2cpn;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.camunda.bpm.model.bpmn.instance.Activity;

import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycle;
import de.uni_potsdam.hpi.bpt.fcm2cpn.dataModel.ObjectLifeCycleParser;
import de.uni_potsdam.hpi.bpt.fcm2cpn.testUtils.ForEachBpmn;

public class OLCParserTests extends ModelStructureTests {
	
	private ObjectLifeCycle[] olcs;
	@Override
	protected void compileModel(String modelName) {
		super.compileModel(modelName);
		this.olcs = ObjectLifeCycleParser.getOLCs(dataModel, bpmn);
	}    
	
	public ObjectLifeCycle olcFor(String dataObject) {
    	return Arrays.stream(olcs)
    			.filter(olc -> olc.getClassName().equals(dataObject))
    			.findAny()
    			.get();
    }
	
	@TestWithAllModels
	@ForEachBpmn(Activity.class)
	public void testAllTransitionsByActivityArePossible(Activity activity) {
		expectedIOCombinations(activity).forEach(ioCombination -> {
			ioCombination.first.forEach((object, inputState) -> {
				if(ioCombination.second.containsKey(object)) {
					String outputState = ioCombination.second.get(object);
					if(!inputState.equals(outputState)) {
						ObjectLifeCycle olc = olcFor(object);
						assertTrue(olc.getState(inputState).get().getSuccessors().contains(olc.getState(outputState).get()), 
								"Olc does not support lifecycle transition ("+inputState+" -> "+outputState+") for data object "+object+" which is definied by activity "+activity.getName());
						System.out.println(" -> proofed");
					}
				}
			});
		});
	}

}
