package com.mycompany.springapp.springcdi.interceptor;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.METHOD;

import javax.interceptor.InterceptorBinding;

@Inherited
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReturnValueAdopted { }
