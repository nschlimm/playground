package com.mycompany.jeeapp.scope.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.enterprise.context.NormalScope;

@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@NormalScope
public @interface CustomScoped {

}
