package com.airca.rca.framework;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * agent-framework is a library module with no application entry point of its own;
 * this exists only so @SpringBootTest has a configuration class to bootstrap Spring AI's
 * Ollama autoconfiguration for the tests in this module.
 */
@SpringBootApplication
public class TestApplication {
}
