/*
 * Module 41: Quarkus - an ordinary CDI bean
 */
package com.acptutorial.module41;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/** An ordinary CDI bean, injected into the agent like into any other bean. */
@ApplicationScoped
public class Greeting {

    @ConfigProperty(name = "greeting.salutation", defaultValue = "Hello")
    String salutation;

    public String greet(String text) {
        return salutation + ": you said '" + text + "'";
    }
}
