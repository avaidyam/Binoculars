package com.binoculars.system;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Internal Nashorn Environment
import com.binoculars.util.Log;
import jdk.nashorn.api.scripting.*;
import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.debug.ASTWriter;
import jdk.nashorn.internal.ir.debug.PrintVisitor;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.parser.Parser;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.options.Options;

import javax.script.*;

/**
 * Command line Shell for processing JavaScript files.
 */
public final class JavascriptEngine {

    private static final String SHELL_INIT_JS =
            "Object.defineProperty(this, \"input\", {\n" +
            "    value: function input(endMarker, prompt) {\n" +
            "        if (!endMarker) {\n" +
            "            endMarker = \"\";\n" +
            "        }\n" +
            "\n" +
            "        if (!prompt) {\n" +
            "            prompt = \" >> \";\n" +
            "        }\n" +
            "\n" +
            "        var imports = new JavaImporter(java.io, java.lang);\n" +
            "        var str = \"\";\n" +
            "        with (imports) {\n" +
            "            var reader = new BufferedReader(new InputStreamReader(System['in']));\n" +
            "            var line;\n" +
            "            while (true) {\n" +
            "                System.out.print(prompt);\n" +
            "                line = reader.readLine();\n" +
            "                if (line == null || line == endMarker) {\n" +
            "                    break;\n" +
            "                }\n" +
            "                str += line + \"\\n\";\n" +
            "            }\n" +
            "        }\n" +
            "\n" +
            "        return str;\n" +
            "    },\n" +
            "    enumerable: false,\n" +
            "    writable: true,\n" +
            "    configurable: true\n" +
            "});\n" +
            "\n" +
            "Object.defineProperty(this, \"evalinput\", {\n" +
            "    value: function evalinput(endMarker, prompt) {\n" +
            "        var code = input(endMarker, prompt);\n" +
            "        // make sure everything is evaluated in global scope!\n" +
            "        return this.eval(code);\n" +
            "    },\n" +
            "    enumerable: false,\n" +
            "    writable: true,\n" +
            "    configurable: true\n" +
            "});\n";

    // CLI Exit Codes
    public static final int SUCCESS = 0;
    public static final int COMMANDLINE_ERROR = 100;
    public static final int COMPILATION_ERROR = 101;
    public static final int RUNTIME_ERROR = 102;
    public static final int IO_ERROR = 103;
    public static final int INTERNAL_ERROR = 104;

    public static Consumer<Bindings> bindings = n -> {};
    public static Supplier<String> script = () -> "";

    private JavascriptEngine() {}

    /**
     * Starting point for executing a {@code Shell}. Starts a shell with the
     * given arguments and streams and lets it run until exit.
     *
     * @param in input stream for Shell
     * @param out output stream for Shell
     * @param err error stream for Shell
     *
     * @return exit code
     *
     * @throws IOException if there's a problem setting up the streams
     */
    public static int shell(final InputStream in, final OutputStream out, final OutputStream err) throws IOException {
        return new JavascriptEngine().run(in, out, err);
    }

    /**
     * Run method logic.
     *
     * @param in input stream for Shell
     * @param out output stream for Shell
     * @param err error stream for Shell
     *
     * @return exit code
     *
     * @throws IOException if there's a problem setting up the streams
     */
    protected final int run(final InputStream in, final OutputStream out, final OutputStream err) throws IOException {
        final Context context = makeContext(in, out, err);
        if (context == null) {
            return COMMANDLINE_ERROR;
        }

        final ScriptObject global = context.createGlobal();
        final ScriptEnvironment env = context.getEnv();
        final List<String> files = env.getFiles();

        // Prepare bindings here.
        SimpleScriptContext scriptContext = new SimpleScriptContext();
        ScriptObjectMirror mirror = (ScriptObjectMirror)ScriptObjectMirror.wrap(global, global);
        scriptContext.setBindings(mirror, ScriptContext.ENGINE_SCOPE);
        bindings.accept(scriptContext.getBindings(ScriptContext.ENGINE_SCOPE));

        // TODO: JDK 1.8u60 method only. Will invoke old call if fails.
        try {
            ((Global)global).initBuiltinObjects(new ScriptEngineManager().getEngineByName("nashorn"), scriptContext);
        } catch (NoSuchMethodError e) {
            ((Global)global).setScriptContext(scriptContext);
        }

        if (files.isEmpty()) {
            return readEvalPrint(context, global);
        }

        if (env._compile_only) {
            return compileScripts(context, global, files);
        }

        if (env._fx) {
            return runFXScripts(context, global, files);
        }

        return runScripts(context, global, files);
    }

    // These classes are prohibited by the runtime engine.
    // Cannot be invoked without a proper ACL and SecurityManager context.
    // Provide replacement methods of operation where appropriate.
    private static final String[] prohibitedClasses = { "java.lang.System", "java.lang.ClassLoader",
            "java.lang.RuntimePermission", "java.lang.SecurityManager", "java.lang.instrument.ClassDefinition",
            "java.lang.invoke.CallSite", "java.lang.invoke.MethodHandle", "java.lang.invoke.MethodType",
            "java.lang.reflect.Field", "java.lang.reflect.Method", "java.lang.reflect.Proxy", "java.lang.reflect.Constructor",
            "javax.script.ScriptEngine", "javax.script.ScriptEngineFactory", "javax.script.ScriptEngineManager"};

    /**
     * Make a new Nashorn Context to compile and/or run JavaScript files.
     *
     * @param i input stream for Shell
     * @param o output stream for Shell
     * @param e error stream for Shell
     *
     * @return null if there are problems with option parsing.
     */
    @SuppressWarnings("resource")
    private static Context makeContext(final InputStream i, final OutputStream o, final OutputStream e) {
        final PrintWriter out = new PrintWriter(o instanceof PrintStream ? (PrintStream)o : new PrintStream(o), true);
        final PrintWriter err = new PrintWriter(e instanceof PrintStream ? (PrintStream)e : new PrintStream(e), true);

        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final ErrorManager errors = new ErrorManager(err);
        final Options options = new Options("nashorn", err);

        options.set("scripting", true);
        options.set("language", "es6");
        options.set("optimistic.types", true);
        options.set("lazy.compilation", true);

        final ClassFilter filter = (name) -> {
            if(Arrays.stream(prohibitedClasses).parallel().filter(name::equals).findAny().isPresent()) {
                System.err.println("Access Manager: Denied item " + name + ".");
                return false;
            } else return true;
        };

        return new Context(options, errors, out, err, loader, filter);
    }

    /**
     * Compiles the given script files in the command line
     *
     * @param context the nashorn context
     * @param global the global scope
     * @param files the list of script files to compile
     *
     * @return error code
     * @throws IOException when any script file read results in I/O error
     */
    private static int compileScripts(final Context context, final ScriptObject global, final List<String> files) throws IOException {
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        final ScriptEnvironment env = context.getEnv();
        try {
            if (globalChanged) {
                Context.setGlobal(global);
            }
            final ErrorManager errors = context.getErrorManager();

            // For each file on the command line.
            for (final String fileName : files) {
                final FunctionNode functionNode = new Parser(env, Source.sourceFor(fileName, new File(fileName)), errors, env._strict, 0, context.getLogger(Parser.class)).parse();

                if (errors.getNumberOfErrors() != 0) {
                    return COMPILATION_ERROR;
                }

                new Compiler(context,
                        env,
                        null, //null - pass no code installer - this is compile only
                        functionNode.getSource(),
                        context.getErrorManager(),
                        env._strict | functionNode.isStrict()).
                        compile(functionNode, Compiler.CompilationPhases.COMPILE_ALL_NO_INSTALL);

                if (env._print_ast) {
                    context.getErr().println(new ASTWriter(functionNode));
                }

                if (env._print_parse) {
                    context.getErr().println(new PrintVisitor(functionNode));
                }

                if (errors.getNumberOfErrors() != 0) {
                    return COMPILATION_ERROR;
                }
            }
        } finally {
            env.getOut().flush();
            env.getErr().flush();
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }

        return SUCCESS;
    }

    /**
     * Runs the given JavaScript files in the command line
     *
     * @param context the nashorn context
     * @param global the global scope
     * @param files the list of script files to run
     *
     * @return error code
     * @throws IOException when any script file read results in I/O error
     */
    private int runScripts(final Context context, final ScriptObject global, final List<String> files) throws IOException {
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        try {
            if (globalChanged) {
                Context.setGlobal(global);
            }
            final ErrorManager errors = context.getErrorManager();

            // For each file on the command line.
            for (final String fileName : files) {
                if ("-".equals(fileName)) {
                    final int res = readEvalPrint(context, global);
                    if (res != SUCCESS) {
                        return res;
                    }
                    continue;
                }

                final File file = new File(fileName);
                final ScriptFunction script = context.compileScript(Source.sourceFor(fileName, file.toURI().toURL()), global);
                if (script == null || errors.getNumberOfErrors() != 0) {
                    return COMPILATION_ERROR;
                }

                try {
                    apply(script, global);
                } catch (final NashornException e) {
                    errors.error(e.toString());
                    if (context.getEnv()._dump_on_error) {
                        e.printStackTrace(context.getErr());
                    }

                    return RUNTIME_ERROR;
                }
            }
        } finally {
            context.getOut().flush();
            context.getErr().flush();
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }

        return SUCCESS;
    }

    /**
     * Runs launches "fx:bootstrap.js" with the given JavaScript files provided
     * as arguments.
     *
     * @param context the nashorn context
     * @param global the global scope
     * @param files the list of script files to provide
     *
     * @return error code
     * @throws IOException when any script file read results in I/O error
     */
    private static int runFXScripts(final Context context, final ScriptObject global, final List<String> files) throws IOException {
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        try {
            if (globalChanged) {
                Context.setGlobal(global);
            }

            global.addOwnProperty("$GLOBAL", Property.NOT_ENUMERABLE, global);
            global.addOwnProperty("$SCRIPTS", Property.NOT_ENUMERABLE, files);
            context.load(global, "fx:bootstrap.js");
        } catch (final NashornException e) {
            context.getErrorManager().error(e.toString());
            if (context.getEnv()._dump_on_error) {
                e.printStackTrace(context.getErr());
            }

            return RUNTIME_ERROR;
        } finally {
            context.getOut().flush();
            context.getErr().flush();
            if (globalChanged) {
                Context.setGlobal(oldGlobal);
            }
        }

        return SUCCESS;
    }

    /**
     * Hook to ScriptFunction "apply". A performance metering shell may
     * introduce enter/exit timing here.
     *
     * @param target target function for apply
     * @param self self reference for apply
     *
     * @return result of the function apply
     */
    protected Object apply(final ScriptFunction target, final Object self) {
        return ScriptRuntime.apply(target, self);
    }

    /**
     * read-eval-print loop for Nashorn shell.
     *
     * @param context the nashorn context
     * @param global  global scope object to use
     * @return return code
     */
    @SuppressWarnings("resource")
    private static int readEvalPrint(final Context context, final ScriptObject global) {
        final String prompt = "> ";//bundle.getString("shell.prompt");
        final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        final PrintWriter err = context.getErr();
        final ScriptObject oldGlobal = Context.getGlobal();
        final boolean globalChanged = (oldGlobal != global);
        final ScriptEnvironment env = context.getEnv();

        try {
            if (globalChanged) {
                Context.setGlobal(global);
            }

            // initialize with "shell.js" script
            try {
                final Source source = Source.sourceFor("<shell.js>", JavascriptEngine.SHELL_INIT_JS);
                context.eval(global, source.getString(), global, "<shell.js>", false, false);

                // custom scripts
                context.eval(global, script.get(), global, "<shell.js>", false, false);
            } catch (final Exception e) {
                err.println(e);
                if (env._dump_on_error) {
                    e.printStackTrace(err);
                }

                return INTERNAL_ERROR;
            }

            while (true) {
                err.print(prompt);
                err.flush();

                String source = "";
                try {
                    source = in.readLine();
                } catch (final IOException ioe) {
                    err.println(ioe.toString());
                }

                if (source == null) {
                    break;
                }

                if (source.isEmpty()) {
                    continue;
                }

                Object res;
                try {
                    res = context.eval(global, source, global, "<shell>", env._strict, false);
                } catch (final Exception e) {
                    err.println(e);
                    if (env._dump_on_error) {
                        e.printStackTrace(err);
                    }
                    continue;
                }

                if (res != ScriptRuntime.UNDEFINED) {
                    err.println(JSType.toString(res));
                }
            }
        } finally {
            if (globalChanged) {
                Context.setGlobal(global);
            }
        }

        return SUCCESS;
    }
}
