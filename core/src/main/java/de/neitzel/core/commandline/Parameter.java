package de.neitzel.core.commandline;

import lombok.*;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

/**
 * A parameter on the command line.
 * <br>
 *     Each parameter has to start with either - or /!
 */
@Slf4j
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Parameter {

    /**
     * Name of the parameter.
     */
    private String name;

    /**
     * Min number of values given.
     */
    private int minNumberValues;

    /**
     * Max number of values given.
     */
    private int maxNumberValues;

    /**
     * Defines if the parameter can be given multiple times.
     */
    private boolean multipleEntries;

    /**
     * Short description of the parameter.
     */
    private String shortDescription;

    /**
     * Long description of the parameter.
     */
    private String longDescription;

    /**
     * Callback wenn der Parameter gefunden wurde.
     */
    private Consumer<List<String>> callback;

    /**
     * Determines if this Element is the parameter for help.
     */
    private boolean isHelpCommand;

    /**
     * Determines if this Parameter is the default parameter.
     * <br>
     *     A default parameter must take at least one additional value.
     */
    private boolean isDefaultParameter;

    /**
     * List of aliases.
     */
    @Singular("alias")
    private List<String> aliasList;
}
