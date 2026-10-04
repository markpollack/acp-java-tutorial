/*
 * Module 41: Quarkus - a CDI interceptor binding
 */
package com.acptutorial.module41;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.interceptor.InterceptorBinding;

/**
 * A CDI interceptor binding: a bean method annotated {@code @Audited} runs through
 * {@link AuditInterceptor}. Nothing about it is ACP-specific.
 */
@Documented
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface Audited {
}
