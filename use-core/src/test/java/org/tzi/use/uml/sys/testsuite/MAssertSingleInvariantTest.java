package org.tzi.use.uml.sys.testsuite;

import junit.framework.TestCase;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.config.Options;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystemState;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Tests the verdict of a testsuite assert that names one invariant. An explicit
 * name is a request to check that invariant, so it is evaluated even when it
 * is deactivated.
 */
public class MAssertSingleInvariantTest extends TestCase {

    private static final String MODEL =
            "model M\n" +
            "class A\n" +
            "attributes\n" +
            "  x : Integer\n" +
            "end\n" +
            "constraints\n" +
            "context A inv pos: self.x > 0\n";

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

    /** Verdict of "assert valid A::pos" (shouldBeValid) on a state with the given x. */
    private boolean verdict(boolean shouldBeValid, int x, boolean deactivated) throws Exception {
        StringWriter err = new StringWriter();
        MModel model = USECompiler.compileSpecification(MODEL, "test", new PrintWriter(err), new ModelFactory());
        assertNotNull("model does not compile: " + err, model);
        UseSystemApi api = UseSystemApi.create(model, false);
        api.createObject("A", "a");
        api.setAttributeValue("a", "x", Integer.toString(x));
        MClassInvariant inv = model.getClassInvariant("A::pos");
        inv.setActive(!deactivated);
        MSystemState state = api.getSystem().state();
        EvalContext ctx = new EvalContext(state, state, new VarBindings(), null, "");
        return new MAssertSingleInvariant(null, "A::pos", null, shouldBeValid, inv).eval(ctx);
    }

    /** An active invariant: "assert valid" holds when it holds, "assert invalid" when it fails. */
    public void testActiveInvariantVerdicts() throws Exception {
        assertTrue(verdict(true, 1, false));
        assertFalse(verdict(true, 0, false));
        assertFalse(verdict(false, 1, false));
        assertTrue(verdict(false, 0, false));
    }

    /** A deactivated invariant that holds is not refused: "assert valid" holds, "assert invalid" does not. */
    public void testDeactivatedHoldingInvariantVerdicts() throws Exception {
        assertTrue(verdict(true, 1, true));
        assertFalse(verdict(false, 1, true));
    }

    /** A deactivated invariant that fails is evaluated, not skipped: its failure is seen. */
    public void testDeactivatedFailingInvariantIsEvaluated() throws Exception {
        assertFalse("assert valid on a failing deactivated invariant must fail", verdict(true, 0, true));
        assertTrue("assert invalid on a failing deactivated invariant must hold", verdict(false, 0, true));
    }
}
