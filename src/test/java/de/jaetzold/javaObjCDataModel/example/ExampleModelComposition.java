package de.jaetzold.javaObjCDataModel.example;

import de.jaetzold.javaObjCDataModel.ObjCDataModel;

import java.util.Map;
import java.util.Set;

/** @author Stephan Jaetzold <p><small>Created at 11.01.12, 13:57</small> */
@ObjCDataModel(targetDirectoryVariable = ExampleModel.LOCATION_FOR_GENERATED_FILES, implementNSCopying = true)
public class ExampleModelComposition {
	private ExampleModel contained;
	private Map<String, Set<ExampleModelSubclass>> name2models;

	public ExampleModel getContained() {
		return contained;
	}

	public void setContained(ExampleModel contained) {
		this.contained = contained;
	}

	public Map<String, Set<ExampleModelSubclass>> getName2model() {
		return name2models;
	}

	public void setName2model(Map<String, Set<ExampleModelSubclass>> name2model) {
		this.name2models = name2model;
	}
}
