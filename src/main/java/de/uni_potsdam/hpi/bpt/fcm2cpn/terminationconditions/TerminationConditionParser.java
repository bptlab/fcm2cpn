package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

import de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils;

import static de.uni_potsdam.hpi.bpt.fcm2cpn.utils.Utils.normalizeElementName;

public class TerminationConditionParser {
	
	public static TerminationCondition parse(File file) {
		try {
			return new TerminationConditionParser(file).parse();
		} catch (IOException | JsonException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private File file;
	
	public TerminationConditionParser(File file) {
		this.file = file;
	}

	private TerminationCondition parse() throws IOException, JsonException {
		try (FileReader fileReader = new FileReader(file)) {
			JsonArray conditionJson = (JsonArray) Jsoner.deserialize(fileReader);
			
			return new TerminationCondition (
				conditionJson.stream()
					.map(JsonObject.class::cast)
					.map(this::parseClause)
					.collect(Collectors.toUnmodifiableList())
			);
		}
	}
	
	private List<TerminationLiteral> parseClause(JsonObject clauseJson) {
		List<TerminationLiteral> clause = new ArrayList<>();
		clauseJson.forEach((dataObject, stateString) -> clause.add(parseLiteral(dataObject, (String) stateString)));
		return Collections.unmodifiableList(clause);
	}
	
	private TerminationLiteral parseLiteral(String dataObject, String stateString) {
		List<String> stateList = Arrays.stream(stateString.split(" ")).map(Utils::singleDataObjectStateToNetColor).collect(Collectors.toList());
		Set<String> states = Collections.unmodifiableSet(new HashSet<>(stateList));
		if(stateList.size() != states.size()) System.err.println("[Warning] Termination condition state string \""+stateString+"\" contains duplicates.");
		return new TerminationLiteral(normalizeElementName(dataObject), states);
	}

}
