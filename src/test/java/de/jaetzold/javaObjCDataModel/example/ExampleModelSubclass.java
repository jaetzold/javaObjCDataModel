package de.jaetzold.javaObjCDataModel.example;

import de.jaetzold.javaObjCDataModel.ObjCDataModel;

/** @author Stephan Jaetzold <p><small>Created at 11.01.12, 13:57</small> */
@ObjCDataModel(targetDirectoryVariable = ExampleModel.LOCATION_FOR_GENERATED_FILES, className = "ExampleModelSub")
public class ExampleModelSubclass extends ExampleModel {
	public String getReadOnlyProperty() {
		return "This property is readonly";
	}
}
