package org.tzi.use.uml.sys.testsuite;

import junit.framework.TestCase;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.config.Options;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystemState;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Tests the verdict of a testsuite assert that names no class and no invariant,
 * that is "assert valid invs" and "assert invalid invs": it speaks about all
 * invariants of the state.
 */
public class MAssertGlobalInvariantsTest extends TestCase {

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

    /** Verdict of "assert valid invs" (shouldBeValid) on a state with one object whose x is given. */
    private boolean verdict(boolean shouldBeValid, int x) throws Exception {
        StringWriter err = new StringWriter();
        MModel model = USECompiler.compileSpecification(MODEL, "test", new PrintWriter(err), new ModelFactory());
        assertNotNull("model does not compile: " + err, model);
        UseSystemApi api = UseSystemApi.create(model, false);
        api.createObject("A", "a");
        api.setAttributeValue("a", "x", Integer.toString(x));
        MSystemState state = api.getSystem().state();
        EvalContext ctx = new EvalContext(state, state, new VarBindings(), null, "");
        return new MAssertGlobalInvariants(null, "invs", null, shouldBeValid).eval(ctx);
    }

    /** All invariants hold: "assert valid invs" holds and "assert invalid invs" does not. */
    public void testHoldingInvariantsVerdicts() throws Exception {
        assertTrue(verdict(true, 1));
        assertFalse(verdict(false, 1));
    }

    /** An invariant is violated: "assert valid invs" fails and "assert invalid invs" holds. */
    public void testViolatedInvariantVerdicts() throws Exception {
        assertFalse(verdict(true, 0));
        assertTrue(verdict(false, 0));
    }
}
