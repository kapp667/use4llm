package org.tzi.use.uml.sys.testsuite;

import junit.framework.TestCase;
import org.tzi.use.config.Options;
import org.tzi.use.parser.testsuite.TestSuiteCompiler;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Tests the closing line of the output of a test suite run. A suite whose
 * run ended in an error must not close like a suite that passed.
 */
public class MTestSuiteVerdictTest extends TestCase {

    private static final String MODEL =
            "model M\n" +
            "class C\n" +
            "attributes\n" +
            "  n : Integer\n" +
            "constraints\n" +
            "  inv positive: n > 0\n" +
            "end\n";

    private boolean oldTestMode;

    @Override
    protected void setUp() {
        oldTestMode = Options.testMode;
        Options.testMode = true;
    }

    @Override
    protected void tearDown() {
        Options.testMode = oldTestMode;
    }

    /** Compiles and runs a suite over the model and returns what it wrote on its output. */
    private String run(String setup, String asserts) {
        StringWriter err = new StringWriter();
        MModel model = USECompiler.compileSpecification(MODEL, "test", new PrintWriter(err), new ModelFactory());
        assertNotNull("model does not compile: " + err, model);
        String suite = "testsuite s for model m.use\n" +
                "setup\n" + setup + "end\n" +
                "testcase t\n" + asserts + "end\n";
        MTestSuite testSuite = TestSuiteCompiler.compileTestSuite(suite, "s.suite", new PrintWriter(err), model);
        assertNotNull("suite does not compile: " + err, testSuite);
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        testSuite.run(pw);
        pw.flush();
        return out.toString();
    }

    private static final String GOOD_SETUP = "  !create c : C\n  !set c.n := 1\n";

    /** Control: a suite whose tests pass closes with OK and not with ERROR. */
    public void testPassingSuiteEndsInOk() {
        String out = run(GOOD_SETUP, "  assert valid invs C, 'holds'\n");
        assertTrue(out, out.contains("... success"));
        assertTrue(out, out.contains("### OK ###"));
        assertFalse(out, out.contains("### ERROR ###"));
    }

    /** Claim 6: a test that ends in error closes the output with the line ERROR, never with OK. */
    public void testTestThatEndsInErrorEndsInErrorLine() {
        String out = run(GOOD_SETUP, "  assert valid c.nosuch = 1, 'bad expr'\n");
        assertTrue(out, out.contains("### ERROR ###"));
        assertFalse(out, out.contains("### OK ###"));
    }

    /** Claim 7: a suite whose setup fails never closes with OK, it closes with ERROR. */
    public void testSetupErrorEndsInErrorLine() {
        String out = run("  !create c : C\n  !set c.nosuch := 1\n", "  assert valid invs C, 'holds'\n");
        assertTrue(out, out.contains("### ERROR ###"));
        assertFalse(out, out.contains("### OK ###"));
    }
}
