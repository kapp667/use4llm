package org.tzi.use.uml.sys;

import junit.framework.TestCase;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.config.Options;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.IntegerValue;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Tests that a copied system state (a test suite variation) keeps its derived
 * attribute values to itself: checking the copy neither fails nor changes the
 * state it was copied from (issue 1).
 */
public class MSystemStateVariationTest extends TestCase {

    private static final String REPRO =
            "model Repro\n" +
            "class C\n" +
            "attributes\n" +
            "  n : Integer\n" +
            "  d : Integer derive = self.n * 2\n" +
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

    private UseSystemApi system() throws Exception {
        StringWriter err = new StringWriter();
        MModel model = USECompiler.compileSpecification(REPRO, "test", new PrintWriter(err), new ModelFactory());
        assertNotNull("model does not compile: " + err, model);
        return UseSystemApi.create(model, false);
    }

    private static boolean checkStructure(MSystemState state) {
        return state.checkStructure(new PrintWriter(new StringWriter()));
    }

    private static IntegerValue storedD(MObject obj, MSystemState state) {
        return (IntegerValue) obj.state(state).attributeValue("d");
    }

    /** Claim 2: checking a variation that holds an object the source state lacks succeeds. */
    public void testCheckStructureOfVariationWithNewObject() throws Exception {
        UseSystemApi api = system();
        api.createObject("C", "c");
        api.setAttributeValue("c", "n", "1");

        api.getSystem().beginVariation();
        api.createObject("C", "e");
        api.setAttributeValue("e", "n", "3");

        assertTrue(checkStructure(api.getSystem().state()));
    }

    /** Claim 3: checking a variation does not change the derived value stored in the source state. */
    public void testCheckStructureOfVariationLeavesSourceDerivedValueAlone() throws Exception {
        UseSystemApi api = system();
        MObject c = api.createObject("C", "c");
        api.setAttributeValue("c", "n", "1");
        MSystemState src = api.getSystem().state();
        assertTrue(checkStructure(src));
        assertEquals(IntegerValue.valueOf(2), storedD(c, src));

        api.getSystem().beginVariation();
        api.setAttributeValue("c", "n", "5");
        MSystemState copy = api.getSystem().state();
        assertNotSame(src, copy);
        assertTrue(checkStructure(copy));

        assertEquals("source state keeps its own derived value",
                IntegerValue.valueOf(2), storedD(c, src));
        assertEquals("the copy holds the value derived from its own n",
                IntegerValue.valueOf(10), storedD(c, copy));
    }

    /** Claim 4: after the variation ends, the source state answers with its own derived value. */
    public void testSourceStateAnswersAfterVariationEnds() throws Exception {
        UseSystemApi api = system();
        MObject c = api.createObject("C", "c");
        api.setAttributeValue("c", "n", "1");
        MSystemState src = api.getSystem().state();
        assertTrue(checkStructure(src));

        api.getSystem().beginVariation();
        api.setAttributeValue("c", "n", "5");
        assertTrue(checkStructure(api.getSystem().state()));
        api.getSystem().endVariation();

        assertSame(src, api.getSystem().state());
        assertEquals(IntegerValue.valueOf(2), storedD(c, src));
        assertEquals(IntegerValue.valueOf(2), api.evaluate("c.d"));
    }
}
