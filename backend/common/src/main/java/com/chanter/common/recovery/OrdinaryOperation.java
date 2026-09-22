package com.chanter.common.recovery;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/** Ordinary background work is absent during isolated recovery; private replay handlers remain available. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(OrdinaryOperationCondition.class)
public @interface OrdinaryOperation {}
