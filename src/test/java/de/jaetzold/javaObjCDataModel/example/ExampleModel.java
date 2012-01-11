package de.jaetzold.javaObjCDataModel.example;

import de.jaetzold.javaObjCDataModel.ObjCDataModel;

import java.util.List;
import java.util.Map;

/** @author Stephan Jaetzold <p><small>Created at 11.01.12, 13:56</small> */
@ObjCDataModel(targetDirectoryVariable = ExampleModel.LOCATION_FOR_GENERATED_FILES)
public class ExampleModel {
	static final String LOCATION_FOR_GENERATED_FILES = "Put the name of an environment variable here which contains the path where generated Objective-C code should be placed";

	private String name;
	public Double number;
	private List<String> texts;
	private Map<Integer, Object> id2instance;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Double getNumber() {
		return number;
	}

	public void setNumber(Double number) {
		this.number = number;
	}

	public List<String> getTexts() {
		return texts;
	}

	public void setTexts(List<String> texts) {
		this.texts = texts;
	}

	public Map<Integer, Object> getId2instance() {
		return id2instance;
	}

	public void setId2instance(Map<Integer, Object> id2instance) {
		this.id2instance = id2instance;
	}
}
