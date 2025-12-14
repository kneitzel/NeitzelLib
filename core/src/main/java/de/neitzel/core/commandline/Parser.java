package de.neitzel.core.commandline;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Parser class is responsible for parsing and handling command-line arguments.
 * It allows for the registration of known parameters and provides mechanisms
 * to validate and process command-line input.
 */
@Slf4j
public class Parser {

    /**
     * A map to store parameters where the key is a string representing
     * the parameter name and the value is the corresponding {@link Parameter} object.
     * <p>
     * This map serves as a registry for defining, organizing, and accessing
     * command-line parameters. Each entry holds a parameter's metadata and
     * its associated configuration to facilitate effective command-line parsing.
     * <p>
     * Key considerations:
     * - The key represents the primary name of the parameter.
     * - The value, encapsulated as a {@link Parameter} object, includes
     * details such as descriptions, value constraints, aliases, and processing logic.
     */
    private Map<String, Parameter> parameters = new HashMap<>();

    /**
     * The `defaultParameter` variable represents the default command-line parameter for a parser.
     * <p>
     * This parameter is used to capture arguments that do not explicitly match any named parameter
     * or alias. It acts as a catch-all for unnamed command-line input, often simplifying the handling
     * of positional arguments or other free-form input provided by the user.
     * <p>
     * It is essential to note:
     * - Only one parameter can be designated as the default parameter in a command-line parser.
     * - The parameter must allow values to be processed (e.g., its `minNumberValues` or `maxNumberValues`
     * should not disallow input).
     * - This parameter is automatically invoked if an input does not match any explicitly named
     * parameter or alias in the parser configuration.
     * <p>
     * When set to `null`, the parser does not handle unmatched arguments, and unrecognized inputs
     * may result in an error or exception, depending on the parser's implementation.
     */
    private Parameter defaultParameter = null;

    /**
     * Constructs a new instance of the `Parser` class.
     * <p>
     * The `Parser` class is responsible for parsing and processing
     * command-line arguments. It interprets input data based on defined
     * parameters and provides structured access to arguments, enabling
     * streamlined application configuration and execution.
     * <p>
     * This constructor initializes the parser without any predefined
     * configuration or parameters. Users can define parameters,
     * add handlers, and parse command-line arguments using available
     * methods in the class.
     */
    public Parser() {
    }

    /**
     * Adds a new parameter to the internal parameter map. The method registers the parameter
     * using its primary name and all specified aliases (case-insensitive). It also sets up
     * specific behaviors for help and default parameters based on the parameter's configuration.
     *
     * @param parameter the Parameter object to be added. It contains the primary name, aliases,
     *                  and other metadata such as whether it is a help command or default parameter.
     *                  The parameter must fulfill specific constraints if defined as the default
     *                  parameter (e.g., accepting values).
     * @throws IllegalArgumentException if the parameter is defined as a default parameter and
     *                                  does not accept values (both minNumberValues and maxNumberValues
     *                                  are set to 0).
     */
    public void addParameter(final Parameter parameter) {
        // Add by name
        parameters.put(parameter.getName().toLowerCase(), parameter);

        // Add for all aliases
        for (String alias : parameter.getAliasList()) {
            parameters.put(alias.toLowerCase(), parameter);
        }

        // Set help Callback for help command.
        if (parameter.isHelpCommand())
            parameter.setCallback(this::helpCommandCallback);

        if (parameter.isDefaultParameter()) {
            if (parameter.getMinNumberValues() == 0 && parameter.getMaxNumberValues() == 0)
                throw new IllegalArgumentException("Default Parameter must accept values!");

            defaultParameter = parameter;
        }
    }

    /**
     * Handles the help command when invoked in the command-line interface, providing information about available parameters.
     * If no arguments are provided, it lists all possible parameters and their short descriptions.
     * If specific arguments are supplied, it provides the detailed description of the corresponding parameter.
     * In case an unknown parameter is specified, it indicates so to the user.
     *
     * @param arguments a list of strings representing the user-provided arguments. If empty or {@code null},
     *                  the method lists all available parameters with their short descriptions. If a parameter name
     *                  is provided in the list, its detailed information is displayed. If an unrecognized parameter
     *                  is provided, a corresponding message is shown.
     */
    public void helpCommandCallback(final List<String> arguments) {
        if (arguments == null || arguments.size() == 0) {
            System.out.println("Moegliche Parameter der Applikation:");
            parameters.values().stream()
                    .distinct()
                    .sorted(Comparator.comparing(Parameter::getName))
                    .forEach(p -> System.out.println(p.getShortDescription()));
        } else {
            Parameter parameter = null;
            if (parameters.containsKey(arguments.get(0))) parameter = parameters.get(arguments.get(0));
            if (parameters.containsKey("-" + arguments.get(0))) parameter = parameters.get("-" + arguments.get(0));

            if (parameter == null) {
                System.out.println("Unbekannter Parameter: " + arguments.get(0));
            } else {
                System.out.println(parameter.getLongDescription());
            }
        }
    }

    /**
     * Parses an array of command-line arguments and processes them as defined by the application's parameters.
     * <p>
     * The method iteratively checks each argument in the provided array, validates it against
     * the registered parameters, and invokes the corresponding processing callback if applicable.
     * Unrecognized parameters or missing required values will result in an {@code IllegalArgumentException}.
     *
     * @param args the array of command-line arguments to be parsed and processed. Each element in this
     *             array represents a single input argument provided to the application. The format and
     *             content of the arguments are expected to conform to the application's parameter definitions.
     */
    public void parse(final String[] args) {
        ArgumentProvider provider = new ArgumentProvider(args);
        while (provider.hasNext()) {
            log.debug("Parsing argument: " + provider.peek());

            // Peek to see the next parameter.
            String next = provider.peek().toLowerCase();
            if (!isParameter(next)) {
                log.error("Unknown Parameter: " + next);
                throw new IllegalArgumentException("Unknown argument: " + next);
            }

            Parameter parameter = parameters.get(next);
            if (parameter == null) {
                parameter = defaultParameter;
            } else {
                provider.next();
            }

            if (!provider.hasNext(parameter.getMinNumberValues())) {
                String message = "Parameter " + next + " requires " + parameter.getMinNumberValues() + " more elements!";
                log.error(message);
                throw new IllegalArgumentException(message);
            }

            parameter.getCallback().accept(getOptions(provider, parameter.getMinNumberValues(), parameter.getMaxNumberValues()));
        }
    }

    /**
     * Checks whether a given parameter string matches a valid parameter within the defined constraints.
     *
     * @param param the parameter string to be checked. This can either be a key defined in the `parameters` map
     *              or a potential default parameter if it does not start with a dash ("-").
     * @return {@code true} if the parameter is a valid entry in the `parameters` map or matches the default parameter;
     * {@code false} otherwise, including when the parameter starts with a dash ("-").
     */
    protected boolean isParameter(final String param) {
        if (parameters.containsKey(param)) return true;
        if (param.startsWith("-")) return false;
        return defaultParameter != null;
    }

    /**
     * Retrieves a list of options by consuming arguments from the provided {@link ArgumentProvider}
     * within the range specified by the minimum and maximum values.
     *
     * @param provider The source providing the command-line arguments. This object allows
     *                 retrieving and examining argument strings in sequence.
     * @param min      The minimum number of arguments to be retrieved. If fewer arguments
     *                 are available, no arguments will be added to the result.
     * @param max      The maximum number of arguments to be retrieved. If `max` is smaller
     *                 than `min`, it will be adjusted to match `min`. Only up to `max`
     *                 arguments will be added to the result.
     * @return A list of argument strings, containing up to `max` elements and at least `min`
     * elements if sufficient arguments are available. The list will not include
     * arguments already recognized in the `parameters` map.
     */
    private List<String> getOptions(ArgumentProvider provider, int min, int max) {
        if (max < min) max = min;
        List<String> result = new ArrayList<>();

        int current = 0;
        while (current < max && provider.hasNext()) {
            if (current < min || !parameters.containsKey(provider.peek())) {
                result.add(provider.next());
                current++;
            } else {
                break;
            }
        }

        return result;
    }
}
