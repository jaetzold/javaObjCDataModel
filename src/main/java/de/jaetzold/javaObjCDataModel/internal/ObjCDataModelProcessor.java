package de.jaetzold.javaObjCDataModel.internal;

import de.jaetzold.javaObjCDataModel.ObjCDataModel;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** @author Stephan Jaetzold <p><small>Created at 05.01.12, 11:22</small> */
@SupportedAnnotationTypes("de.jaetzold.tryouts.annotation.ObjCDataModel")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class ObjCDataModelProcessor extends AbstractProcessor {
	private static class PropertyInformation {
		String name;
		VariableElement field;
		ExecutableElement getter;
		ExecutableElement setter;
		TypeMirror type;
		boolean matching = true;
		boolean fieldPublic = false;
		boolean propertyReadable = false;
		boolean propertyWritable = false;

		public PropertyInformation(String name) {
			this.name = name;
		}
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		populateTypeMap();
		for(TypeElement annotationElement: annotations) {
			this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "processing " +annotationElement.getQualifiedName());
			Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotationElement);
			for(Element element : annotatedElements) {
				ObjCDataModel objCDataModel = element.getAnnotation(ObjCDataModel.class);
				if(objCDataModel!=null) {
					this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, element +" is annotated with " +objCDataModel);
					String className = deriveClassName(element);
					this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Using className=" +className);
					Writer headerWriter = null;
					Writer implementationWriter = null;
					String targetDirectoryVariable = objCDataModel.targetDirectoryVariable();
					if(targetDirectoryVariable.length()>0) {
						String targetDirectory = System.getenv(targetDirectoryVariable);
						if(targetDirectory==null) {
							this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
																		  "The value for targetDirectoryVariable does not refer to an existing environment variable: " +targetDirectoryVariable,
																		  element);
							continue;
						}
						this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Using targetDirectory=" + targetDirectory);
						try {
//							URI baseUri = this.processingEnv.getFiler().createSourceFile(
//									this.getClass().getSimpleName().concat("_Dummy"), element).toUri();
//							this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
//																		  "baseUri " + baseUri);
//							String dummyPath = baseUri.getPath();
//							File baseDirectory = new File(dummyPath.substring(0, dummyPath.lastIndexOf("/")));
							File directory = new File(targetDirectory);
							this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
																		  "Creating files in " + directory.getCanonicalPath());
							if(directory.exists() || directory.mkdirs()) {
								headerWriter = new FileWriter(new File(directory, className+".h"), false);
								implementationWriter = new FileWriter(new File(directory, className+".m"), false);
							} else {
								this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
																			  "Can not create targetDirectory path: " + directory, element);
							}
						} catch(IOException e) {
							this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
																		  "Can not create file: " +e.getLocalizedMessage(), element);
						}
					} else {
						Name packageName =
								processingEnv.getElementUtils().getPackageOf(element).getQualifiedName();
						this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Using package=" + packageName);
						try {
							FileObject fileObject = processingEnv.getFiler()
									.createResource(StandardLocation.SOURCE_OUTPUT, packageName,
													className + ".h", element);
							headerWriter = new FileWriter(new File(fileObject.toUri()));
							fileObject = processingEnv.getFiler()
									.createResource(StandardLocation.SOURCE_OUTPUT, packageName, className + ".m", element);
							implementationWriter = new FileWriter(new File(fileObject.toUri()));
						} catch(IOException e) {
							this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
																		  "Can not create file: " +e.getLocalizedMessage(), element);
						}
					}
					if(headerWriter!=null && implementationWriter!=null) {
						Map<String, PropertyInformation> properties = collectPropertyInformationFrom((TypeElement)element);
						writeObjCFiles((TypeElement)element, className, headerWriter, implementationWriter, properties, objCDataModel.implementNSCopying());
					}
				}
			}
		}
		return false;
	}

	private Map<String, PropertyInformation> collectPropertyInformationFrom(TypeElement element) {
		List<? extends Element> members =
				processingEnv.getElementUtils().getAllMembers((TypeElement)element);
		List<VariableElement> fields = ElementFilter.fieldsIn(members);
		List<ExecutableElement> methods = ElementFilter.methodsIn(members);
		Map<String, PropertyInformation> properties = new LinkedHashMap<String, PropertyInformation>();
		for(VariableElement field : fields) {
			if(element.equals(field.getEnclosingElement())) {
				String name = field.getSimpleName().toString();
				PropertyInformation property = properties.get(name);
				if(property==null) {
					property = new PropertyInformation(name);
					properties.put(name, property);
				} else {
					if(!field.asType().equals(property.type)) {
						property.matching = false;
					}
				}
				property.type = field.asType();
				property.field = field;
				if(field.getModifiers().contains(Modifier.PUBLIC)) {
					property.fieldPublic = true;
				}
			}
		}
		for(ExecutableElement method : methods) {
			if(element.equals(method.getEnclosingElement())) {
				String methodName = method.getSimpleName().toString();
				// skip this, it is not a regular property
				if(methodName.equals("getClass")) {
					continue;
				}
				String name;
				boolean isSetter = false;
				if(methodName.startsWith("set")) {
					isSetter = true;
					name = methodName.substring(3);
				} else if(methodName.startsWith("get")) {
					name = methodName.substring(3);
				} else if(methodName.startsWith("is")) {
					name = methodName.substring(2);
				} else {
					continue;
				}
				TypeMirror type;
				// check type signature
				if(isSetter) {
					if(method.getReturnType().getKind()!=TypeKind.VOID) {
						continue;
					}
					List<? extends VariableElement> parameters = method.getParameters();
					if(parameters.size()!=1) {
						continue;
					}
					type = parameters.get(0).asType();
				} else {
					List<? extends VariableElement> parameters = method.getParameters();
					if(!parameters.isEmpty()) {
						continue;
					}
					if(method.getReturnType().getKind()==TypeKind.VOID) {
						continue;
					}
					type = method.getReturnType();
				}
				// decapitalize
				name = name.substring(0,1).toLowerCase() + name.substring(1);

				PropertyInformation property = properties.get(name);
				if(property==null) {
					property = new PropertyInformation(name);
					properties.put(name, property);
					property.type = type;
				} else {
					if(!type.equals(property.type)) {
						property.matching = false;
					}
				}
				if(isSetter) {
					property.setter = method;
					if(property.setter.getModifiers().contains(Modifier.PUBLIC)) {
						property.propertyWritable = true;
					}
				} else {
					property.getter = method;
					if(property.getter.getModifiers().contains(Modifier.PUBLIC)) {
						property.propertyReadable = true;
					}
				}
			}
		}
		return properties;
	}

	private void writeObjCFiles(TypeElement element, String className, Writer headerWriter,
								Writer implementationWriter,
								Map<String, PropertyInformation> properties,
								boolean implementNSCopying) {
		try {
			// header comment
			headerWriter.append("//").append("\n");
			headerWriter.append("// Created by " + this.getClass().getCanonicalName() + " on "
								+ new Date()).append("\n");
			headerWriter.append("// DO NOT MODIFY! As this file WILL GET OVERWRITTEN each time the original Java source is compiled.").append("\n");
			headerWriter.append("// Use subclasses or extend the annotation processor to include maybe a delegation mechanism.").append("\n");
			headerWriter.append("//").append("\n");
			headerWriter.append("\n");
			implementationWriter.append("//").append("\n");
			implementationWriter.append("// Created by " + this.getClass().getCanonicalName() + " on "
										+ new Date()).append("\n");
			implementationWriter.append("// DO NOT MODIFY! As this file WILL GET OVERWRITTEN each time the original Java source is compiled.").append("\n");
			implementationWriter.append("// Use subclasses or extend the annotation processor to include maybe a delegation mechanism.").append("\n");
			implementationWriter.append("//").append("\n");

			implementationWriter.append("\n\n");

			// imports
			headerWriter.append("#import <Foundation/Foundation.h>").append("\n");
			// check for usage of other objCDataModel classes either as reference or as superclass
			// so that their header can be imported
			Set<DeclaredType> referencedDataModelTypes = new HashSet<DeclaredType>();
			for(PropertyInformation property : properties.values()) {
				if(isObjCDataModel(property.type)) {
					referencedDataModelTypes.add(asDeclaredType(property.type));
				}
			}
			TypeMirror superclassMirror = element.getSuperclass();
			if(isObjCDataModel(superclassMirror)) {
				headerWriter.append("#import \"" + objCTypeOf(superclassMirror).toTypeString() + ".h\"").append(
						"\n");
				referencedDataModelTypes.remove(asDeclaredType(superclassMirror));
			}
			headerWriter.append("\n");
			for(DeclaredType type : referencedDataModelTypes) {
				headerWriter.append("@class ").append(objCTypeOf(type).toTypeString()).append(";\n");
			}
			if(!referencedDataModelTypes.isEmpty()) {
				headerWriter.append("\n");
			}
			headerWriter.append("\n");

			implementationWriter.append("#import \"" +className +".h\"").append("\n");
			implementationWriter.append("\n\n");

			// class declaration
			String superclassName = objCTypeOf(element.getSuperclass()).toTypeString();

			headerWriter.append("@interface " +className + " : " + superclassName + " "
								+(implementNSCopying ?"<NSCopying> ":"") +"{").append("\n");
			implementationWriter.append("@implementation " +className +" {").append("\n");
			implementationWriter.append("@private").append("\n");

			// generate variable definitions
			for(Map.Entry<String, PropertyInformation> entry : properties.entrySet()) {
				PropertyInformation property = entry.getValue();
				if(property.fieldPublic) {
					headerWriter.append("\t" +objCTypeOf(property.type).toVariableDefinitionString() +property.name).append(";\n");
				} else if(property.propertyReadable || property.propertyWritable) {
					implementationWriter.append("\t" +objCTypeOf(property.type).toVariableDefinitionString() +"_"+property.name).append(";\n");
				}
			}
			headerWriter.append("}").append("\n");
			headerWriter.append("\n\n");
			implementationWriter.append("}").append("\n");
			implementationWriter.append("\n\n");

			// generate property definitions
			List<PropertyInformation> retainedProperties = new ArrayList<PropertyInformation>();
			for(Map.Entry<String, PropertyInformation> entry : properties.entrySet()) {
				PropertyInformation property = entry.getValue();
				if(property.propertyReadable || property.propertyWritable) {
					DeclaredType declaredType = (DeclaredType)property.type;
					List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
					if(!typeArguments.isEmpty()) {
						headerWriter.append("// kind of ").append(objCTypeOf(property.type).toTypeString());
						headerWriter.append("<");
						boolean first = true;
						for(TypeMirror typeArgument : typeArguments) {
							if(!first) {
								headerWriter.append(",");
							}
							headerWriter.append(objCTypeOf(typeArgument).toTypeString());
							first = false;
						}
						headerWriter.append(">\n");
					}
					headerWriter.append("@property(nonatomic");
					if(!property.propertyWritable) {
						headerWriter.append(", readonly");
					}
					if(objCCopyableType(property.type)) {
						headerWriter.append(", copy");
						retainedProperties.add(property);
					} else if(objCRetainableType(property.type)) {
						headerWriter.append(", retain");
						retainedProperties.add(property);
					}
					headerWriter.append(") "+objCTypeOf(property.type).toVariableDefinitionString() +property.name).append(
							";\n");
					implementationWriter.append("@synthesize ").append(property.name);
					if(!property.fieldPublic) {
						implementationWriter.append(" = _").append(property.name);
					}
					implementationWriter.append(";\n");
				}
			}
			headerWriter.append("\n\n");
			implementationWriter.append("\n\n");

			// un-retain any retained properties
			if(!retainedProperties.isEmpty()) {
				implementationWriter.append("- (void) dealloc {").append("\n");
				for(PropertyInformation property : retainedProperties) {
					implementationWriter.append("    self." +property.name +"=nil;").append("\n");
				}
				implementationWriter.append("    [super dealloc];").append("\n");
				implementationWriter.append("}").append("\n");
				implementationWriter.append("\n");
			}

			// required by NSCopying
			if(implementNSCopying) {
				implementationWriter.append("- (id) copyWithZone:(NSZone *)zone {").append("\n");
				implementationWriter.append("    return nil;").append("\n");
				implementationWriter.append("}").append("\n");
				implementationWriter.append("\n");
			}

			headerWriter.append("@end\n");
			implementationWriter.append("@end\n");
			headerWriter.close();
			implementationWriter.close();
		} catch(IOException e) {
			this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
														  "Can write to file: " + e
																  .getLocalizedMessage());
		}
	}

	private DeclaredType asDeclaredType(TypeMirror typeMirror) {
		return processingEnv.getTypeUtils().getDeclaredType(
				(TypeElement)processingEnv.getTypeUtils().asElement(typeMirror));
	}

	private boolean objCRetainableType(TypeMirror type) {
		return objCTypeOf(type).referenceType;
	}

	private boolean objCCopyableType(TypeMirror type) {
		return objCTypeOf(type).name.equals("NSString");
	}

	private static class ObjCType {
		String name;
		boolean referenceType;

		private ObjCType(String name, boolean referenceType) {
			this.name = name;
			this.referenceType = referenceType;
		}

		@Override
		public String toString() {
			return toTypeString();
		}

		public String toTypeString() {
			return name;
		}

		public String toVariableDefinitionString() {
			return name +(referenceType?" *":" ");
		}
	}
	private final Map<TypeMirror,ObjCType> typeMap = new HashMap<TypeMirror, ObjCType>();
	private void mapJavaClassToObjCType(String javaClassName, String objCType, boolean referenceType) {
		TypeMirror typeMirror = processingEnv.getElementUtils().getTypeElement(javaClassName).asType();
		DeclaredType declaredType = asDeclaredType(typeMirror);
		typeMap.put(declaredType, new ObjCType(objCType, referenceType));
	}
	private void populateTypeMap() {
		typeMap.clear();
		mapJavaClassToObjCType("java.lang.String", "NSString", true);
		mapJavaClassToObjCType("java.lang.Object", "NSObject", true);
		mapJavaClassToObjCType("java.util.List", "NSArray", true);
		mapJavaClassToObjCType("java.util.Map", "NSDictionary", true);
		mapJavaClassToObjCType("java.util.Set", "NSSet", true);
		mapJavaClassToObjCType("java.util.Collection", "NSArray", true);
		mapJavaClassToObjCType("java.util.ArrayList", "NSArray", true);
		mapJavaClassToObjCType("java.util.HashMap", "NSDictionary", true);
		mapJavaClassToObjCType("java.util.HashSet", "NSSet", true);
		mapJavaClassToObjCType("java.lang.Integer", "NSInteger", false);
		mapJavaClassToObjCType("java.lang.Long", "NSInteger", false);
		mapJavaClassToObjCType("java.lang.Short", "NSUInteger", false);
		mapJavaClassToObjCType("java.lang.Byte", "NSUInteger", false);
		mapJavaClassToObjCType("java.lang.Double", "double", false);
		mapJavaClassToObjCType("java.lang.Float", "float", false);
	}

	private ObjCType objCTypeOf(TypeMirror type) {
		switch(type.getKind()) {
			case BOOLEAN:
				return new ObjCType("BOOL", false);
			case BYTE:
				return new ObjCType("NSUInteger", false);
			case SHORT:
				return new ObjCType("NSUInteger", false);
			case INT:
				return new ObjCType("NSInteger", false);
			case LONG:
				return new ObjCType("NSInteger", false);
			case CHAR:
				break;
			case FLOAT:
				return new ObjCType("float", false);
			case DOUBLE:
				return new ObjCType("double", false);
			case VOID:
				break;
			case NONE:
				break;
			case NULL:
				break;
			case ARRAY:
				break;
			case DECLARED:
				DeclaredType declaredType = asDeclaredType(type);
				ObjCType objCType = typeMap.get(declaredType);
				this.processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
															  type + " maps to " + objCType);
				if(objCType!=null) {
					return objCType;
				}
				// this is the case where the given type is itself one that is/will be generated by this processor
				if(isObjCDataModel(type)) {
					return new ObjCType(deriveClassName(processingEnv.getTypeUtils().asElement(type)), true);
				}
				break;
			case ERROR:
				break;
			case TYPEVAR:
				break;
			case WILDCARD:
				break;
			case PACKAGE:
				break;
			case EXECUTABLE:
				break;
			case OTHER:
				break;
		}
		throw new IllegalArgumentException("The type " +type +" is not supported for Objective-C conversion");
	}

	private boolean isObjCDataModel(TypeMirror type) {
		Element element = processingEnv.getTypeUtils().asElement(type);
		return element.getAnnotation(ObjCDataModel.class)!=null;
	}

	private String deriveClassName(Element element) {
		ObjCDataModel objCDataModel = element.getAnnotation(ObjCDataModel.class);
		String className = objCDataModel.className();
		if(className.length()==0) {
			className = element.getSimpleName().toString();
		}
		return objCDataModel.classNamePrefix() +className +objCDataModel.classNameSuffix();
	}
}
