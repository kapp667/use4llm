package org.tzi.use.main.shell;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.tzi.use.config.Options;
import org.tzi.use.main.gui.Main;
import org.tzi.use.util.USEWriter;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * This class implements the shell integration tests.
 *
 * <p>These tests represent nearly the exact behavior of the
 * USE shell. Only some whitespace differences and time outputs are ignored.</p>
 *
 * <p>Each test consists of the following files:
 * <ol>
 *     <li>A model file (suffix {@code .use}) - this file provides the (possible empty) model used
 *     for the tests</li>
 *     <li>An input file (suffix: {@code .in}) - this file can contain any commands USE supports.
 *     The expected output must be specified by starting a line with a star {@code *}</li>
 *     <li>Any other used file from the command line, e.g., ASSL- or command-files.</li>
 * </ol>
 * </p>
 *
 * <p>All {@code .use</code> and </code><code>.in} files must share the same name, e.g., t555.use and t555.in for test
 * case 555. These files must be placed in the folder {@code it/resources/testfiles/shell}.</p>
 *
 * <p>If an integration test fails, two additional files are created:
 * <ol>
 *     <li>{@code {testcasename}.expected</code> - The expected output calculated from the <code>{testcase}.in}-file</li>
 *     <li>{@code {testcasename}.actual} - The output captured while running the test.</li>
 * </ol>
 * These files can be used to easily diff expected and current output.</p>
 */
public class ShellIT {

    /**
     * This TestFactory enumerates the {@code .in</code>-files in th folder <code>testfiles/shell}.
     * For each file a {@code DynamicTest} is created with the name of the file.
     *
     * @return A {@code Stream</code> with one <code>DynamicTest</code> for each <code>*.in}-file.
     */
    @TestFactory
    public Stream<DynamicTest> evaluateExpressionFiles() {
        URL testDirURL = getClass().getClassLoader().getResource("testfiles/shell");
        Path testDirPath = null;

        if (testDirURL == null) {
            fail("Directory for shell integration tests not found!");
        }

        try {
            testDirPath = Path.of(testDirURL.toURI());
        } catch (URISyntaxException e) {
            fail("Directory for shell integration tests not found!");
        }

        try  {
            return Files.walk(testDirPath).filter(
                    path -> path.getFileName().toString().endsWith(".in")
            ).map(mapInFileToTest());
        } catch (IOException e) {
            fail("Error iterating shell integration test input files!");
        }

        return Stream.empty();
    }

    /**
     * This {@code Function} is used to map
     * a given testinput-file given as a {@code Path}
     * to a {@code DynamicTest}.
     *
     * @return A {@code DynamicTest</code> that uses the function <code>assertShellExpression}
     *         to test the given testinput file.
     */
    private Function<Path, DynamicTest> mapInFileToTest() {
        return path -> {
            final String modelFilename = path.getFileName().toString().replace(".in", ".use");
            final Path modelPath = path.resolveSibling(modelFilename);

            return DynamicTest.dynamicTest(path.getFileName().toString(), path.toUri(), () -> assertShellExpressions(path, modelPath));
        };
    }

    /**
     * <p>This function controls the overall process for test for a single testfile.</p>
     *
     * <p>The process is as follows:
     * <ol>
     *     <li>A command file and the expected output are created by examining the input file (via {@code createCommandFile}).</li>
     *     <li>USE is executed using the {@code useFile</code> and the created command file (<code>runUSE}).</li>
     *     <li>The output of USE is compared to the expected output created in 1. ({@code validateOutput}).</li>
     * </ol>
     * </p>
     *
     * @param testFile {@code Path} to the test input file to execute.
     * @param useFile {@code Path} to the USE file containing the model to load for the test.
     */
    private void assertShellExpressions(Path testFile, Path useFile) {

        Path cmdFile = testFile.resolveSibling(testFile.getFileName() + ".cmd");

        List<String> expectedOutput = createCommandFile(testFile, cmdFile);

        List<String> actualOutput = runUSE(useFile, cmdFile).collect(Collectors.toList());

        validateOutput(testFile, expectedOutput, actualOutput);
    }

    /**
     * Compares the two lists of strings {@code expectedOutput}
     * and {@code actualOutput}.
     * If they differ, two files are written at the location of the
     * {@code testFile</code>. One with the expected output (<code>.expected})
     * and one with the actual output ({@code .actual}).
     *
     * @param testFile The {@code Path</code> to the <code>testFile}
     * @param expectedOutput List of strings with the expected output (one String per line)
     * @param actualOutput List of strings with the actual output (one String per line)
     */
    private void validateOutput(Path testFile, List<String> expectedOutput, List<String> actualOutput) {
        //create a configured DiffRowGenerator
        DiffRowGenerator generator = DiffRowGenerator.create()
                .showInlineDiffs(true)
                .mergeOriginalRevised(true)
                .inlineDiffByWord(true)
                .ignoreWhiteSpaces(true)
                .lineNormalizer( (s) -> s ) // No normalization required
                .oldTag((f, start) -> start ? "-\033[9m" : "\033[m-")      //introduce style for strikethrough
                .newTag((f, start) -> start ? "+\033[97;42m" : "\033[m+")  //introduce style for bold
                .build();

        //compute the differences for two test texts.
        List<DiffRow> rows = generator.generateDiffRows(expectedOutput, actualOutput);
        Predicate<DiffRow> filter = d -> d.getTag() != DiffRow.Tag.EQUAL;

        if (rows.stream().anyMatch(filter)) {
            StringBuilder diffMsg = new StringBuilder("USE output does not match expected output!").append(System.lineSeparator());

            diffMsg.append("Test file: ").append(testFile).append(System.lineSeparator());

            diffMsg.append(System.lineSeparator()).append("Note: the position is not the position in the input file!");
            diffMsg.append(System.lineSeparator()).append(System.lineSeparator());

            rows.stream().filter(filter).forEach(
                    row ->diffMsg.append(System.lineSeparator()).append(row.getOldLine())
            );

            writeToFile(expectedOutput, testFile.getParent().resolve(testFile.getFileName().toString() + ".expected"));
            writeToFile(actualOutput, testFile.getParent().resolve(testFile.getFileName().toString() + ".actual"));

            fail(diffMsg.toString());
        }
    }

    /**
     * <p>Helper method that writes the list of strings {@code data}
     * to the file located by the {@code Path} {@code file}.</p>
     *
     * <p>If the file is not accessible, i.e., an IOException is thrown,
     * the exception is caught and the test case fails.</p>
     * @param data The {@code List} of string (lines) to write.
     * @param file The path to the file to write (the file is overwritten).
     */
    private void writeToFile(List<String> data, Path file) {
        try (FileWriter writer = new FileWriter(file.toFile())) {

            for (String line : data) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        } catch (IOException e) {
            fail("Test output could not be written!", e);
        }
    }

    /**
     * Creates a USE-command file at the position located by the path {@code cmdFile}.
     * The file contains all commands that are specified in the {@code inFile}.
     * The expected output, i.e., lines starting with a {@code *} are added to the list {@code expectedOutput}.
     *
     * @param inFile The {@code Path} to the test input file.
     * @param cmdFile The {@code Path} where to create the command file.
     * @return  A {@code List} which is filled with the expected output of USE.
     */
    private List<String> createCommandFile(Path inFile, Path cmdFile) {
        List<String> expectedOutput = new LinkedList<>();

        // Build USE command file and build expected output
        try (
                Stream<String> linesStream = Files.lines(inFile, StandardCharsets.UTF_8);
                FileWriter cmdWriter = new FileWriter(cmdFile.toFile(), StandardCharsets.UTF_8, false)
        ) {

            linesStream.forEach(inputLine -> {

                // Ignore empty lines in expected, since they are also suppressed in the actual output
                if (inputLine.isBlank())
                    return;

                if ((inputLine.startsWith("*") || inputLine.startsWith("#"))
                        && inputLine.substring(1).isBlank()) {
                    return;
                }

                if (inputLine.startsWith("*")) {
                    // Input line minus prefix(*) is expected output
                    expectedOutput.add(inputLine.substring(1).trim());
                } else if (!inputLine.startsWith("#")) { // Not a comment
                    try {
                        cmdWriter.write(inputLine);
                        cmdWriter.write(System.lineSeparator());

                        // Multi-line commands (backslash and dot) are ignored
                        if (!inputLine.matches("^[\\\\.]$")) {
                            expectedOutput.add(inputLine);
                        }
                    } catch (IOException e1) {
                        fail("Could not write USE command file for test!", e1);
                    }
                }
            });
        } catch (IOException e) {
            fail("Could not write USE command file for test!", e);
        }

        return expectedOutput;
    }

    /**
     * Executes USE with the given {@code useFile} as the model
     * and the {@code cmdFile} to execute commands.
     * The output is captured from the output and error streams.
     *
     * @param useFile Path to the USE model to load on startup
     * @param cmdFile Path to the commands file to execute
     * @return A {@code List} of strings. Each string is one line of output.
     */
    private Stream<String> runUSE(Path useFile, Path cmdFile) {

        // We need to specify a concrete locale to always get the same formatted result
        Locale.setDefault(Locale.forLanguageTag("en-US"));

        Options.resetOptions();
        USEWriter.getInstance().clearLog();

        String homeDir;
        try {
            homeDir = useFile.getParent().resolve("../../../../../use-core/target/classes").toFile().getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String[] args = new String[] {
                "-nogui",
                "-nr",
                "-t",
                "-it",
                "-q",
                "-oclAnyCollectionsChecks:E",
                "-extendedTypeSystemChecks:E",
                /* This is currently an unstable workaround
                   USE determines the plugin and the extensions to OCL by fixed paths.
                   For now, the use-core module contains the directories including the extensions
                   and an empty plugins folder.
                   The folder is located: use/use-core/target/classes
                   Therefore, this is used as the USE home
                 */
                "-H=" + homeDir,
                useFile.toString(),
                cmdFile.toString()};

        Main.main(args);

        try (ByteArrayOutputStream protocol = new ByteArrayOutputStream()) {
            USEWriter.getInstance().writeProtocolFile(protocol);
            String output = protocol.toString();
            return  output.lines().filter(l -> !l.isBlank());
        } catch (IOException e) {
            fail(e);
        }

        return Stream.empty();
    }

    /**
     * Runs USE as its own process, as a script or a CI job does, and returns
     * its exit code. Output is discarded: only the code is observed.
     *
     * @param args The command line arguments in front of the model and script files.
     * @param files The model and script file names, relative to {@code testfiles/exitcode}.
     */
    private int exitCodeOfUSE(List<String> args, String... files) throws Exception {
        URL dirURL = getClass().getClassLoader().getResource("testfiles/exitcode");
        if (dirURL == null) {
            fail("Directory for exit code tests not found!");
        }
        Path dir = Path.of(dirURL.toURI());
        String homeDir = dir.resolve("../../../../../use-core/target/classes").toFile().getCanonicalPath();

        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("org.tzi.use.main.gui.Main");
        cmd.add("-nogui");
        cmd.add("-nr");
        cmd.add("-H=" + homeDir);
        cmd.addAll(args);
        for (String f : files) {
            cmd.add(dir.resolve(f).toString());
        }

        Process p = new ProcessBuilder(cmd)
                .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            fail("USE did not end: " + cmd);
        }
        return p.exitValue();
    }

    private DynamicTest exitCodeTest(String name, int expected, List<String> args, String... files) {
        return DynamicTest.dynamicTest(name, () ->
                org.junit.jupiter.api.Assertions.assertEquals(expected, exitCodeOfUSE(args, files), name));
    }

    /**
     * Exit codes of {@code use -q}: 0 = script ran without error and the final check passed,
     * 1 = script ran without error and the final check failed, 2 = any read or command error
     * (wins over 0 and 1). Without {@code -q} the exit code is 0 after a normal exit.
     */
    @TestFactory
    public Stream<DynamicTest> exitCodeOfQuietRun() {
        List<String> q = List.of("-q");
        return Stream.of(
                // 1-6: an error during the run gives 2
                exitCodeTest("refused commands exit 2", 2, q, "m.use", "refused.cmd"),
                exitCodeTest("missing script file exits 2", 2, q, "m.use", "missing.cmd"),
                exitCodeTest("nested open of a missing file exits 2", 2, q, "m.use", "qopen.cmd"),
                exitCodeTest("check of an unknown invariant name exits 2", 2, q, "m.use", "badinv.cmd"),
                exitCodeTest("refused command beats a failing check", 2, q, "m.use", "refusedviolating.cmd"),
                exitCodeTest("unknown shell command exits 2", 2, q, "m.use", "unknowncmd.cmd"),
                // 7: the codes that were right stay
                exitCodeTest("valid state exits 0", 0, q, "m.use", "ok.cmd"),
                exitCodeTest("failing invariant exits 1", 1, q, "m.use", "fail.cmd"),
                exitCodeTest("empty script exits 0", 0, q, "m.use", "empty.cmd"),
                exitCodeTest("missing model exits 1", 1, q, "missing.use", "ok.cmd"),
                exitCodeTest("model with a type error exits 1", 1, q, "bad.use", "ok.cmd"),
                // a test suite that did not pass gives 2, whatever the state of the shell is
                exitCodeTest("suite with an assert on all invariants exits 0", 0, q, "invmodel.use", "sGlobal.cmd"),
                exitCodeTest("suite with a failed global assert exits 2", 2, q, "invmodel.use", "sGlobalViolated.cmd"),
                exitCodeTest("suite with two passing tests exits 0", 0, q, "invmodel.use", "sTwo.cmd"),
                exitCodeTest("suite with a failed test exits 2", 2, q, "invmodel.use", "sFail.cmd"),
                exitCodeTest("suite with a failed and a passing test exits 2", 2, q, "invmodel.use", "sTwoFail.cmd"),
                exitCodeTest("suite with a test that ends in error exits 2", 2, q, "invmodel.use", "sBadExpr.cmd"),
                exitCodeTest("suite with a setup error exits 2", 2, q, "invmodel.use", "sSetup.cmd"),
                exitCodeTest("suite that does not compile exits 2", 2, q, "invmodel.use", "sParse.cmd"),
                exitCodeTest("suite with a passing class assert exits 0", 0, q, "invmodel.use", "sClass.cmd"),
                exitCodeTest("open of a missing suite exits 2", 2, q, "invmodel.use", "sMissing.cmd"),
                exitCodeTest("false postcondition of opexit exits 2", 2, q, "opmodel.use", "post.cmd"),
                exitCodeTest("no valid transition after opexit exits 2", 2, q, "opmodel.use", "trans.cmd"),
                exitCodeTest("false precondition of openter exits 2", 2, q, "opmodel.use", "pre.cmd"),
                exitCodeTest("valid operation call exits 0", 0, q, "opmodel.use", "opok.cmd"),
                exitCodeTest("failing check without a suite exits 1", 1, q, "invmodel.use", "checkfail.cmd"),
                exitCodeTest("without -q a failed suite still exits 0", 0, List.of(), "invmodel.use", "sFail.cmd"),
                // 9: without -q the exit code is unchanged
                exitCodeTest("without -q refused commands still exit 0", 0, List.of(), "m.use", "refused.cmd"),
                exitCodeTest("without -q a failing check still exits 0", 0, List.of(), "m.use", "fail.cmd")
        );
    }
}
