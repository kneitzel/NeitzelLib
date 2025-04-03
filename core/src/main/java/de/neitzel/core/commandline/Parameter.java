package de.neitzel.core.commandline;

import lombok.*;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

/**
 * The `Parameter` class defines a command-line parameter with associated metadata,
 * such as constraints on the number of values it accepts, descriptions, and aliases.
 * It also allows specifying callbacks to handle parameter processing.
 *
 * This class is intended to be used as part of a command-line parser, enabling
 * structured handling of arguments provided via the command line.
 */
@Slf4j
@Builder
@Getter
@Setter
public class Parameter {

    /**
     * Represents a command-line parameter definition within the application.
     *
     * This class encapsulates the metadata and configuration of a command-line parameter,
     * including its name, aliases, descriptions, value constraints, and behavior. Instances
     * of this class are used to define and manage the parameters that the `Parser` class
     * recognizes and processes.
     *
     * A parameter can be configured as a default parameter or as a help command. The default
     * parameter serves as a catch-all for unmatched command-line inputs, while the help
     * command provides usage information for users.
     */
    public Parameter() {
    }

    /**
     * Constructs a new Parameter with the given attributes.
     *
     * @param name The name of the parameter.
     * @param minNumberValues The minimum number of values allowed for this parameter.
     * @param maxNumberValues The maximum number of values allowed for this parameter.
     * @param multipleEntries Indicates whether multiple entries are allowed for this parameter.
     * @param shortDescription A brief description of the parameter.
     * @param longDescription A detailed description of the parameter.
     * @param callback A consumer function that processes a list of values associated with this parameter.
     * @param isHelpCommand Indicates whether this parameter represents a help command.
     * @param isDefaultParameter Indicates whether this parameter is the default parameter.
     * @param aliasList A list of aliases for this parameter.
     */
    public Parameter(String name, int minNumberValues, int maxNumberValues, boolean multipleEntries, String shortDescription, String longDescription, Consumer<List<String>> callback, boolean isHelpCommand, boolean isDefaultParameter, List<String> aliasList) {
        this.name = name;
        this.minNumberValues = minNumberValues;
        this.maxNumberValues = maxNumberValues;
        this.multipleEntries = multipleEntries;
        this.shortDescription = shortDescription;
        this.longDescription = longDescription;
        this.callback = callback;
        this.isHelpCommand = isHelpCommand;
        this.isDefaultParameter = isDefaultParameter;
        this.aliasList = aliasList;
    }

    /**
     * The `name` variable represents the primary identifier for a parameter.
     * This string is used to uniquely identify a command-line parameter when parsing
     * arguments. It serves as the key for registering and retrieving parameters in a
     * command-line parser.
     *
     * The value of `name` is case-insensitive and can be used alongside aliases to
     * recognize and process parameters provided in the command-line input.
     */
    private String name;

    /**
     * Specifies the minimum number of values required for this parameter.
     *
     * This variable enforces a constraint on the number of arguments that must
     * be provided for the parameter during command-line parsing. If the number of
     * provided arguments is less than this value, an exception will be thrown during parsing.
     *
     * It is primarily used in validation logic within the command-line parser
     * to ensure the required input is received for proper processing of the parameter.
     */
    private int minNumberValues;

    /**
     * Specifies the maximum number of values that the corresponding parameter can accept.
     *
     * This field is used to validate and enforce constraints during command-line parsing.
     * If more values than specified are provided for a parameter, the parser throws an exception
     * to indicate a violation of this constraint.
     *
     * A value of 0 implies that the parameter does not support any values, while a positive
     * value indicates the exact maximum limit of acceptable values.
     */
    private int maxNumberValues;

    /**
     * Indicates whether the parameter allows multiple entries.
     *
     * If set to {@code true}, the parameter can be specified multiple times
     * on the command-line. This allows the association of multiple values
     * or options with the same parameter name.
     *
     * If set to {@code false}, the parameter can be specified only once
     * during command-line parsing.
     */
    private boolean multipleEntries;

    /**
     * A concise, human-readable description of the parameter's purpose and functionality.
     *
     * This description provides a brief summary to help users understand the
     * essential role of the parameter within the command-line parser. It is typically
     * displayed in usage instructions, help messages, or command summaries.
     */
    private String shortDescription;

    /**
     * A detailed, extended description of the parameter's purpose and usage.
     *
     * This description provides deeper context about what the parameter represents,
     * how it fits into the overall command-line application, and any nuances
     * associated with its use. It is displayed when help or additional documentation
     * for a specific parameter is requested.
     */
    private String longDescription;

    /**
     * A callback function that processes a list of string arguments for a specific parameter.
     * The callback is invoked during command-line parsing when a parameter is recognized,
     * passing the associated values of the parameter for further processing.
     *
     * The provided {@link Consumer} implementation defines the logic to
     * handle or validate the values supplied for a command-line parameter. The values
     * passed are determined based on the parameter's configuration (e.g., minimum and
     * maximum number of associated values).
     *
     * This field is typically set for the `Parameter` class to enable custom handling
     * of parameter-specific logic, such as invoking a help command or performing
     * business logic with the values parsed from the command-line arguments.
     */
    private Consumer<List<String>> callback;

    /**
     * Indicates whether the parameter is designated as the help command.
     *
     * When set to {@code true}, this parameter is treated as the special help command
     * within the command-line parser. A parameter marked as the help command typically
     * provides users with information about available options or detailed descriptions
     * of specific commands when invoked. If this flag is enabled, a custom callback
     * method for displaying help context is automatically associated with the parameter.
     */
    private boolean isHelpCommand;

    /**
     * Indicates whether the parameter is designated as the default parameter.
     *
     * The default parameter is a special type of parameter that can accept arguments
     * without an explicit prefix or identifier. It is used when a provided command-line
     * argument does not match any defined parameter and does not start with a special
     * character (e.g., '-') typically used to denote named parameters.
     *
     * If this field is set to {@code true}, the parameter is treated as the default
     * parameter. Only one parameter can be assigned this role within the parser.
     * Attempting to assign multiple default parameters or a default parameter without
     * accepting values (both `minNumberValues` and `maxNumberValues` set to 0) will
     * result in an exception during configuration.
     *
     * This property is utilized during the parsing process to determine whether an
     * unmatched argument should be handled as a default parameter value, simplifying
     * the handling of positional arguments or other unnamed input data.
     */
    private boolean isDefaultParameter;

    /**
     * A list of alias names associated with a parameter.
     *
     * This variable holds alternative names that can be used
     * to reference the parameter in command-line input. Each alias
     * functions as an equivalent to the primary parameter name.
     * Aliases are case-insensitive when used within the command-line parser.
     *
     * The aliases associated with a parameter allow greater flexibility
     * and user convenience when specifying parameters during command-line execution.
     */
    @Singular("alias")
    private List<String> aliasList;
}
