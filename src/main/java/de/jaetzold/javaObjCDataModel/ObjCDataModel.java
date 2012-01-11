package de.jaetzold.javaObjCDataModel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** @author Stephan Jaetzold <p><small>Created at 22.12.11, 17:18</small> */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface ObjCDataModel {
	String className() default "";
	String classNamePrefix() default "";
	String classNameSuffix() default "";

	/**
	 * The name of an environment variable which contains the path where generated Objective-C code should be placed
	 */
	String targetDirectoryVariable();
	boolean implementNSCopying() default false;
}
