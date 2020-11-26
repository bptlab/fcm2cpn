# fCM to CPN Compiler

Knowledge workers make interconnected decisions to drive processes.
Therefore, it is necessary to consider the available information, possible actions, and general rules.
The fragment-based Case Management (fCM) approach supports case models describing processes by a set of fragments, a domain model, object life cycles, and termination conditions. 

This repository contains a compiler translating fragment-based case management models to colore Petri nets, which are compatible with [CPNTools](https://cpntools.org).
The resulting Petri net describes the behavior including the involved data objects, their states, associations, and corresponding cardinality constraints.
This prototype complements the paper "Refining Case Models Using Cardinality Constraints" submitted to CAiSE 2021 (not yet reviewed/accepted/published).
The resulting model can be used in combination with an accompanying [execution engine](https://github.com/bptlab/fCM-Engine/tree/caise).

## Content of the Repository
* **Examples:**
  * `src/main/resources/conference_fragments_knowledge_intensive.bpmn` contains a BPMN file comprising the *paper submission and reviewing* example from the paper (modeled using [Signavio](https://academic.signavio.com), additional input sets have been added manually).
  * `src/main/resources/conference_domain_model.uml` contains a UML file comprising the domain model for the example (modeled using [Papyrus](https://www.eclipse.org/papyrus/)).
  * `src/main/resources/conference_petri_net.cpn` contains the colored Petri net created by the compiler and manually laid out.
* **Compiler:**
  * `src/main/java` contains the source files for the translator that translates a set of fragments to a CPN.
  * `src/main/resources` contains example models used for tests.
  * `lib/*.jar` the [Access/CPN](http://cpntools.org/access-cpn/) libraries required for the prototype.

## Usage

### Compiling and Building the Binaries

We use maven as build tool and for dependency management.
If you installed java and maven, you can build the compiler using the following commands.
First, install the CPNtools dependencies locally by executing
````bash
mvn initialize
````
Next, build the binaries. 
````bash
mvn clean package -DskipTests
````

Note, you can also run `mvn clean install -DskipTests` and provide libraries manually.

### Creating Inputs

We recommend using [Signavio](https://academic.signavio.com) to create fCM fragments (free for academic purposes) and [Papyrus](https://www.eclipse.org/papyrus/) to create domain models.
All fragments should be modeled within a single BPMN file.
Goal cardinalities can be specified by comments owned by the corresponding association and applyied to the end point that should be refined.
See the example for details.

### Running the Compiler

After compiling and building the binary, it will be available in the folder `target`.
We recommend using the jar file ending in `with-dependencies`.
````bash
java -jar fcm2cpn.jar <path-to-bpmn.bpmn> -d <path-to-uml.uml>
````
The CPN will be saved to the current working directory with the same name as the bpmn file.
*Note, any file with the same name will be overwritten.*

### Assumptions

* We assume that the input is provided as a single BPMN file (you can, for example use the [Signavio](https://academic.signavio.com)). Note, the implementation relies on a definition of data objects, data object references, and input/output sets. However, some common modelers do not generate these.
* We assume that the fragments are object life cycle conform and object life cycle complete. An object lfie cycle will be extracted by the compiler from the fragments.

### Limitations & Deviation From the Paper

To be usable from the engine, the outputs of the compiler differ from the description in the engine
* The CPN has no input place, so it can support multiple concurrent cases
* Data objects reference the case to prevent concurrent cases from mixing
* The state of data objects in stored in the token (not the labels of places) to access it easily from within the engine
* The generated colored Petri net uses CPNTools syntax for colorsets, operations, etc.
* Some functionality has been encapsulated into functions to improve readability
* Termination conditions are not yet supported

### Sources

All the sources are available in `src/main/*`, note that you have to add the Access/CPN libraries (`lib`) to your classpath in order to run/compile the tool.

### Dependencies & Requirements

The implementation requires java Version 9 or higher.
Please note, that the tool has dependencies, and that these dependencies may have different licenses. In the following we list the dependencies
* Camunda bpmn-model for parsing BPMN files. The dependency is linked via maven.
* Access/CPN to create CPNtools compatible CPNs. The dependency is linked as a set of external libraries (see `lib/`)
* Eclipse EMF dependency for using Access/CPN

### License

*fcm2cpn* is a compiler, translating process fragments to CPNtools compatible Petri nets.
Copyright (C) 2020  Hasso Plattner Institute gGmbH, University of Potsdam

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
