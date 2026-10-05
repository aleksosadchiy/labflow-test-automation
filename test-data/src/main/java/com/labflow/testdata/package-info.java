/**
 * The single shared place for test-data recipes: patients, orders and samples with valid
 * defaults, plus the unique-name generator every check uses.
 *
 * <p>Recipes are plain values only. They never call the lab; a check passes their fields to the
 * existing {@code api-client} service methods.
 */
package com.labflow.testdata;
