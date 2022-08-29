/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvndaemon.mvnd.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * <p>
 * Enhancement of the standard <code>Properties</code>
 * managing the maintain of comments, etc.
 * </p>
 *
 * @author gnodet, jbonofre
 */
public class InterpolationHelper {

    private InterpolationHelper() {
    }

    private static final char ESCAPE_CHAR = '\\';
    private static final String DELIM_START = "${";
    private static final String DELIM_STOP = "}";
    private static final String MARKER = "$__";
    private static final String ENV_PREFIX = "env:";

    /**
     * Callback for substitution
     */
    public interface SubstitutionCallback {

        String getValue(String key);

    }

    /**
     * Perform substitution on a property set
     *
     * @param properties the property set to perform substitution on
     */
    public static void performSubstitution(Map<String, String> properties) {
        performSubstitution(properties, new SystemPropertiesSubstitutionCallback());
    }

    /**
     * Perform substitution on a property set
     *
     * @param properties the property set to perform substitution on
     * @param callback   Callback for substituion
     */
    public static void performSubstitution(Map<String, String> properties, SubstitutionCallback callback) {
        performSubstitution(properties, callback, true, true, true);
    }

    /**
     * Perform substitution on a property set
     *
     * @param properties                     the property set to perform substitution on
     * @param callback                       the callback to obtain substitution values
     * @param substituteFromConfig           If substitute from configuration
     * @param substituteFromSystemProperties If substitute from system properties
     * @param defaultsToEmptyString          sets an empty string if a replacement value is not found, leaves intact
     *                                       otherwise
     */
    public static void performSubstitution(Map<String, String> properties,
            SubstitutionCallback callback,
            boolean substituteFromConfig,
            boolean substituteFromSystemProperties,
            boolean defaultsToEmptyString) {
        Map<String, String> org = new HashMap<String, String>(properties);
        for (String name : properties.keySet()) {
            String value = properties.get(name);
            properties.put(name,
                    substVars(value, name, null, org, callback, substituteFromConfig, substituteFromSystemProperties,
                            defaultsToEmptyString));
        }
    }

    /**
     * <p>
     * This method performs property variable substitution on the
     * specified value. If the specified value contains the syntax
     * <tt>${&lt;prop-name&gt;}</tt>, where <tt>&lt;prop-name&gt;</tt>
     * refers to either a configuration property or a system property,
     * then the corresponding property value is substituted for the variable
     * placeholder. Multiple variable placeholders may exist in the
     * specified value as well as nested variable placeholders, which
     * are substituted from inner most to outer most. Configuration
     * properties override system properties.
     * </p>
     *
     * @param  val                      The string on which to perform property substitution.
     * @param  currentKey               The key of the property being evaluated used to
     *                                  detect cycles.
     * @param  cycleMap                 Map of variable references used to detect nested cycles.
     * @param  configProps              Set of configuration properties.
     * @return                          The value of the specified string after system property substitution.
     * @throws IllegalArgumentException If there was a syntax error in the
     *                                  property placeholder syntax or a recursive variable reference.
     **/
    public static String substVars(String val,
            String currentKey,
            Map<String, String> cycleMap,
            Map<String, String> configProps)
            throws IllegalArgumentException {
        return substVars(val, currentKey, cycleMap, configProps, new SystemPropertiesSubstitutionCallback());
    }

    /**
     * <p>
     * This method performs property variable substitution on the
     * specified value. If the specified value contains the syntax
     * <tt>${&lt;prop-name&gt;}</tt>, where <tt>&lt;prop-name&gt;</tt>
     * refers to either a configuration property or a system property,
     * then the corresponding property value is substituted for the variable
     * placeholder. Multiple variable placeholders may exist in the
     * specified value as well as nested variable placeholders, which
     * are substituted from inner most to outer most. Configuration
     * properties override system properties.
     * </p>
     *
     * @param  val                      The string on which to perform property substitution.
     * @param  currentKey               The key of the property being evaluated used to
     *                                  detect cycles.
     * @param  cycleMap                 Map of variable references used to detect nested cycles.
     * @param  configProps              Set of configuration properties.
     * @param  callback                 the callback to obtain substitution values
     * @return                          The value of the specified string after system property substitution.
     * @throws IllegalArgumentException If there was a syntax error in the
     *                                  property placeholder syntax or a recursive variable reference.
     **/
    public static String substVars(String val,
            String currentKey,
            Map<String, String> cycleMap,
            Map<String, String> configProps,
            SubstitutionCallback callback)
            throws IllegalArgumentException {
        return substVars(val, currentKey, cycleMap, configProps, callback, true, true, true);
    }

    /**
     * <p>
     * This method performs property variable substitution on the
     * specified value. If the specified value contains the syntax
     * <tt>${&lt;prop-name&gt;}</tt>, where <tt>&lt;prop-name&gt;</tt>
     * refers to either a configuration property or a system property,
     * then the corresponding property value is substituted for the variable
     * placeholder. Multiple variable placeholders may exist in the
     * specified value as well as nested variable placeholders, which
     * are substituted from inner most to outer most. Configuration
     * properties override system properties.
     * </p>
     *
     * @param  val                            The string on which to perform property substitution.
     * @param  currentKey                     The key of the property being evaluated used to
     *                                        detect cycles.
     * @param  cycleMap                       Map of variable references used to detect nested cycles.
     * @param  configProps                    Set of configuration properties.
     * @param  callback                       the callback to obtain substitution values
     * @param  substituteFromConfig           If substitute from configuration
     * @param  substituteFromSystemProperties If substitute from system properties
     * @param  defaultsToEmptyString          sets an empty string if a replacement value is not found, leaves intact
     *                                        otherwise
     * @return                                The value of the specified string after system property substitution.
     * @throws IllegalArgumentException       If there was a syntax error in the
     *                                        property placeholder syntax or a recursive variable reference.
     **/
    public static String substVars(String val,
            String currentKey,
            Map<String, String> cycleMap,
            Map<String, String> configProps,
            SubstitutionCallback callback,
            boolean substituteFromConfig,
            boolean substituteFromSystemProperties,
            boolean defaultsToEmptyString)
            throws IllegalArgumentException {
        return unescape(doSubstVars(val, currentKey, cycleMap, configProps, callback, substituteFromConfig,
                substituteFromSystemProperties, defaultsToEmptyString));
    }

    private static String doSubstVars(String val,
            String currentKey,
            Map<String, String> cycleMap,
            Map<String, String> configProps,
            SubstitutionCallback callback,
            boolean substituteFromConfig,
            boolean substituteFromSystemProperties,
            boolean defaultsToEmptyString)
            throws IllegalArgumentException {
        if (cycleMap == null) {
            cycleMap = new HashMap<String, String>();
        }

        // Put the current key in the cycle map.
        cycleMap.put(currentKey, currentKey);

        // Assume we have a value that is something like:
        // "leading ${foo.${bar}} middle ${baz} trailing"

        // Find the first ending '}' variable delimiter, which
        // will correspond to the first deepest nested variable
        // placeholder.
        int startDelim;
        int stopDelim = -1;
        do {
            stopDelim = val.indexOf(DELIM_STOP, stopDelim + 1);
            while (stopDelim > 0 && val.charAt(stopDelim - 1) == ESCAPE_CHAR) {
                stopDelim = val.indexOf(DELIM_STOP, stopDelim + 1);
            }

            // Find the matching starting "${" variable delimiter
            // by looping until we find a start delimiter that is
            // greater than the stop delimiter we have found.
            startDelim = val.indexOf(DELIM_START);
            while (stopDelim >= 0) {
                int idx = val.indexOf(DELIM_START, startDelim + DELIM_START.length());
                if ((idx < 0) || (idx > stopDelim)) {
                    break;
                } else if (idx < stopDelim) {
                    startDelim = idx;
                }
            }
        } while (startDelim >= 0 && stopDelim >= 0 && stopDelim < startDelim + DELIM_START.length());

        // If we do not have a start or stop delimiter, then just
        // return the existing value.
        if ((startDelim < 0) || (stopDelim < 0)) {
            cycleMap.remove(currentKey);
            return val;
        }

        // At this point, we have found a variable placeholder so
        // we must perform a variable substitution on it.
        // Using the start and stop delimiter indices, extract
        // the first, deepest nested variable placeholder.
        String variable = val.substring(startDelim + DELIM_START.length(), stopDelim);
        String org = variable;

        // Strip expansion modifiers
        int idx1 = variable.lastIndexOf(":-");
        int idx2 = variable.lastIndexOf(":+");
        int idx = idx1 >= 0 && idx2 >= 0 ? Math.min(idx1, idx2) : idx1 >= 0 ? idx1 : idx2;
        String op = null;
        if (idx >= 0 && idx < variable.length()) {
            op = variable.substring(idx);
            variable = variable.substring(0, idx);
        }

        // Verify that this is not a recursive variable reference.
        if (cycleMap.get(variable) != null) {
            throw new IllegalArgumentException("recursive variable reference: " + variable);
        }

        String substValue = null;
        // Get the value of the deepest nested variable placeholder.
        // Try to configuration properties first.
        if (substituteFromConfig && configProps != null) {
            substValue = configProps.get(variable);
        }
        if (substValue == null) {
            if (variable.length() > 0) {
                if (callback != null) {
                    substValue = callback.getValue(variable);
                }
                if (substValue == null && substituteFromSystemProperties) {
                    substValue = System.getProperty(variable);
                }
            }
        }

        if (op != null) {
            if (op.startsWith(":-")) {
                if (substValue == null || substValue.length() == 0) {
                    substValue = op.substring(":-".length());
                }
            } else if (op.startsWith(":+")) {
                if (substValue != null && substValue.length() != 0) {
                    substValue = op.substring(":+".length());
                }
            } else {
                throw new IllegalArgumentException("Bad substitution: ${" + org + "}");
            }
        }

        if (substValue == null) {
            if (defaultsToEmptyString) {
                substValue = "";
            } else {
                // alters the original token to avoid infinite recursion
                // altered tokens are reverted in substVarsPreserveUnresolved()
                substValue = MARKER + "{" + variable + "}";
            }
        }

        // Remove the found variable from the cycle map, since
        // it may appear more than once in the value and we don't
        // want such situations to appear as a recursive reference.
        cycleMap.remove(variable);

        // Append the leading characters, the substituted value of
        // the variable, and the trailing characters to get the new
        // value.
        val = val.substring(0, startDelim) + substValue + val.substring(stopDelim + DELIM_STOP.length(),
                val.length());

        // Now perform substitution again, since there could still
        // be substitutions to make.
        val = doSubstVars(val, currentKey, cycleMap, configProps, callback, substituteFromConfig,
                substituteFromSystemProperties, defaultsToEmptyString);

        cycleMap.remove(currentKey);

        // Return the value.
        return val;
    }

    private static String unescape(String val) {
        val = val.replaceAll("\\" + MARKER, "\\$");
        int escape = val.indexOf(ESCAPE_CHAR);
        while (escape >= 0 && escape < val.length() - 1) {
            char c = val.charAt(escape + 1);
            if (c == '{' || c == '}' || c == ESCAPE_CHAR) {
                val = val.substring(0, escape) + val.substring(escape + 1);
            }
            escape = val.indexOf(ESCAPE_CHAR, escape + 1);
        }
        return val;
    }

    public static class SystemPropertiesSubstitutionCallback implements SubstitutionCallback {
        private final Properties systemProperties;

        public SystemPropertiesSubstitutionCallback() {
            this(null);
        }

        public SystemPropertiesSubstitutionCallback(Properties systemProperties) {
            this.systemProperties = systemProperties == null ? System.getProperties() : systemProperties;
        }

        public String getValue(String key) {
            String value = null;
            if (key.startsWith(ENV_PREFIX)) {
                value = System.getenv(key.substring(ENV_PREFIX.length()));
            } else {
                value = systemProperties.getProperty(key);
            }
            return value;
        }
    }

}
