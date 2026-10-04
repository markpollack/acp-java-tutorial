/*
 * Module 40: Micronaut - an ordinary application bean
 */
package com.acptutorial.module40;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;

/** An ordinary Micronaut bean, injected into the agent like into any other bean. */
@Singleton
public class Greeter {

    private final String salutation;

    public Greeter(@Property(name = "greeter.salutation", defaultValue = "Hello") String salutation) {
        this.salutation = salutation;
    }

    public String greet(String text) {
        return salutation + ": you said '" + text + "'";
    }
}
