package de.uni_potsdam.hpi.bpt.fcm2cpn.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.instance.DataInputAssociation;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.bpmn.instance.DataOutputAssociation;
import org.camunda.bpm.model.bpmn.instance.IoSpecification;

import de.uni_potsdam.hpi.bpt.fcm2cpn.StatefulDataAssociation;

/** {@link DataObjectIOSet} with data object names as identifier*/
public class DataObjectIdIOSet extends DataObjectIOSet<String> {

	public DataObjectIdIOSet(List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>> first,
			List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>> second) {
		super(first, second);
	}

	@Override
	protected boolean matches(StatefulDataAssociation<?, DataObjectReference> assoc, String dataObject) {
		return assoc.dataElementName().equals(dataObject);
	}
	
	

	@SuppressWarnings("unchecked")
	// TODO potential duplicates to ActivityCompiler#ioSetsFromIoSpecification
	public static Set<DataObjectIdIOSet> parseIOSpecification(IoSpecification io) {
		return statelessIoAssociationCombinations(io).stream().flatMap(ioAssociationCombination -> {
			Map<Pair<String, Boolean>, List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> inputStates = ioAssociationCombination.first.stream()
					.flatMap(Utils::splitDataAssociationByState)
					.filter(StatefulDataAssociation::isDataObjectReference)
					.map(assoc -> (StatefulDataAssociation<DataInputAssociation, DataObjectReference>) assoc)
					.collect(Collectors.groupingBy(StatefulDataAssociation::dataElementNameAndCollection));
			Map<Pair<String, Boolean>, List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> outputStates = ioAssociationCombination.second.stream()
					.flatMap(Utils::splitDataAssociationByState)
					.filter(StatefulDataAssociation::isDataObjectReference)
					.map(assoc -> (StatefulDataAssociation<DataOutputAssociation, DataObjectReference>) assoc)
					.collect(Collectors.groupingBy(StatefulDataAssociation::dataElementNameAndCollection));

			List<List<StatefulDataAssociation<DataInputAssociation, DataObjectReference>>> possibleInputForms = Utils.allCombinationsOf(inputStates.values());
			List<List<StatefulDataAssociation<DataOutputAssociation, DataObjectReference>>> possibleOutputForms = Utils.allCombinationsOf(outputStates.values());
			List<DataObjectIdIOSet> ioForms = new ArrayList<>();
			for(var inputForm: possibleInputForms) {
				for(var outputForm: possibleOutputForms) {
					ioForms.add(new DataObjectIdIOSet(inputForm, outputForm));
				}
			}
			return ioForms.stream();
		})
		.collect(Collectors.toSet());
	}
	
	private static Set<Pair<List<DataInputAssociation>, List<DataOutputAssociation>>> statelessIoAssociationCombinations(IoSpecification io) {
		Set<Pair<List<DataInputAssociation>, List<DataOutputAssociation>>> combinations = new HashSet<>();
		io.getInputSets().forEach(inputSet -> {
			inputSet.getOutputSets().forEach(outputSet -> {
				combinations.add(new Pair<>(
						inputSet.getDataInputs().stream().map(Utils::getAssociation).collect(Collectors.toList()), 
						outputSet.getDataOutputRefs().stream().map(Utils::getAssociation).collect(Collectors.toList()))
				);
			});	
		});
		return combinations;		
	}
	

}