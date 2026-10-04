/*
 * Module 40: Micronaut - an AOP advice annotation
 */
package com.acptutorial.module40;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.micronaut.aop.Around;

/**
 * Micronaut {@link Around} advice: a method annotated {@code @Audited} runs through
 * {@link AuditInterceptor}. Nothing about it is ACP-specific.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Around
public @interface Audited {
}
