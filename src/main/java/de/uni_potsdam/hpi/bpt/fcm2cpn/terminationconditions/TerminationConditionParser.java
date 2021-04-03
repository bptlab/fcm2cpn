package de.uni_potsdam.hpi.bpt.fcm2cpn.terminationconditions;

import java.io.File;
import java.io.IOException;

public class TerminationConditionParser {
	
	public static TerminationCondition parse(File file) {
		try {
			return new TerminationConditionParser(file).parse();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private File file;
	
	public TerminationConditionParser(File file) {
		this.file = file;
	}

	private TerminationCondition parse() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
