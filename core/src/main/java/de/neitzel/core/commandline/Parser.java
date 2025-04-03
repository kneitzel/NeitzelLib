package de.neitzel.core.commandline;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * A parser of the CommandLine.
 * <br>
 *     Parameter are defined. Each parameter will start with either - or /.
 */
@Slf4j
public class Parser {

    /**
     * Parameters known by the CommandLineParser.
     */
    private Map<String, Parameter> parameters = new HashMap<>();

    /**
     * The default parameter.
     */
    private Parameter defaultParameter = null;

    /**
     * Adds a parameter to the list of known parameters.
     * @param parameter
     */
    public void addParameter(final Parameter parameter) {
        // Add by name
        parameters.put(parameter.getName().toLowerCase(), parameter);

        // Add for all aliases
        for (String alias: parameter.getAliasList()) {
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
     * Checks if param is a parameter.
     * <br>
     *     - When the parameter is known as a parameter, then it is true.
     *     - Everything that starts with a - must be a known parameter -> false
     *     - Parameter is not known and does not start with a -? -> Default parameter if available.
     * @param param Parameter in lower case to check.
     * @return true if it is either a known parameter or an argument for the default parameter.
     */
    protected boolean isParameter(final String param) {
        if (parameters.containsKey(param)) return true;
        if (param.startsWith("-")) return false;
        return defaultParameter != null;
    }

    /**
     * Parse the given commandline arguments.
     * @param args Commandline Arguments to parse.
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
     * Get the given optional data of a parameter-
     * @param provider Provider of token.
     * @param min Minimum number of elements to get.
     * @param max Maximum number of elements to get.
     * @return List of token of the Parameter.
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

    /**
     * Callback for help command.
     * @param arguments null for general help or name of a command.
     */
    public void helpCommandCallback(final List<String> arguments) {
        if (arguments == null || arguments.size() == 0) {
            System.out.println("Moegliche Parameter der Applikation:");
            parameters.values().stream()
                    .distinct()
                    .sorted(Comparator.comparing(Parameter::getName))
                    .forEach(p -> System.out.println(p.getShortDescription() ));
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
}
