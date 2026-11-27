package org.tzi.use.uml.sys;

import junit.framework.TestCase;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.config.Options;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests what {@link MSystemState#check} reports to its user: the printed
 * lines and the returned boolean, for named invariants (issue 10) and for
 * the structure violation count in the summary (issue 11).
 */
public class MSystemStateCheckTest extends TestCase {

    private static final String SHOP =
            "model Shop\n" +
            "class Order\n" +
            "attributes\n" +
            "  qty : Integer\n" +
            "end\n" +
            "class Item end\n" +
            "association Has between\n" +
            "  Order[1] role order\n" +
            "  Item[1..*] role items\n" +
            "end\n" +
            "constraints\n" +
            "context Order inv Positive: qty > 0\n" +
            "context Order inv AlwaysTrue: true\n" +
            "context Item inv AlwaysTrue: true\n";

    private static final Pattern SUMMARY = Pattern.compile(
            "^checked ([0-9]+) invariants?( in [0-9.]+s)?, ([0-9]+) failures?(, ([0-9]+) structure violations?)?\\.$");

    private boolean oldTestMode;
    private boolean result;

    @Override
    protected void setUp() {
        oldTestMode = Options.testMode;
        Options.testMode = true;
    }

    @Override
    protected void tearDown() {
        Options.testMode = oldTestMode;
    }

    private UseSystemApi system(String modelText) throws Exception {
        StringWriter err = new StringWriter();
        MModel model = USECompiler.compileSpecification(modelText, "test", new PrintWriter(err), new ModelFactory());
        assertNotNull("model does not compile: " + err, model);
        return UseSystemApi.create(model, false);
    }

    /** Shop state: Order o with the given qty, optionally linked to Item i. */
    private UseSystemApi shop(int qty, boolean linked) throws Exception {
        UseSystemApi api = system(SHOP);
        api.createObject("Order", "o");
        api.setAttributeValue("o", "qty", Integer.toString(qty));
        if (linked) {
            api.createObject("Item", "i");
            api.createLink("Has", "o", "i");
        }
        return api;
    }

    private String check(UseSystemApi api, boolean all, String... names) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        List<String> invNames = Arrays.asList(names);
        result = api.getSystem().state().check(pw, false, false, all, invNames);
        pw.flush();
        return sw.toString().replace("\r\n", "\n");
    }

    private static List<String> lines(String out) {
        return Arrays.asList(out.split("\n"));
    }

    private static List<String> invariantLines(String out) {
        List<String> res = new java.util.ArrayList<>();
        for (String l : lines(out)) {
            if (l.startsWith("checking invariant (")) {
                res.add(l);
            }
        }
        return res;
    }

    /** The summary line, which must match the documented format. */
    private static Matcher summary(String out) {
        List<String> ls = lines(out);
        String last = ls.get(ls.size() - 1);
        Matcher m = SUMMARY.matcher(last);
        assertTrue("summary line has the wrong format: " + last, m.matches());
        return m;
    }

    // ---- issue 10: named invariants ----

    /** Claim 1: a qualified name selects exactly that invariant and evaluates it. */
    public void testNamedFailingInvariantIsEvaluated() throws Exception {
        String out = check(shop(0, true), false, "Order::Positive");
        assertEquals(Collections.singletonList("checking invariant (1) `Order::Positive': FAILED."),
                invariantLines(out));
        assertTrue(out, lines(out).contains("  -> false : Boolean"));
        assertEquals("checked 1 invariant, 1 failure.", lines(out).get(lines(out).size() - 1));
        assertFalse(result);
    }

    /** Claim 2: the same simple name of another class is not checked. */
    public void testNamedInvariantDoesNotSelectSameSimpleNameOfOtherClass() throws Exception {
        String out = check(shop(1, true), false, "Order::AlwaysTrue");
        assertEquals(Collections.singletonList("checking invariant (1) `Order::AlwaysTrue': OK."),
                invariantLines(out));
        assertEquals("checked 1 invariant, 0 failures.", lines(out).get(lines(out).size() - 1));
        assertTrue(result);
    }

    /** Claim 3: several names are all checked, a repeated name only once. */
    public void testSeveralNamesAndRepeatedName() throws Exception {
        UseSystemApi api = shop(1, true);
        String out = check(api, false, "Order::AlwaysTrue", "Item::AlwaysTrue");
        assertEquals(2, invariantLines(out).size());
        assertTrue(out, out.contains("`Order::AlwaysTrue': OK."));
        assertTrue(out, out.contains("`Item::AlwaysTrue': OK."));
        assertEquals("checked 2 invariants, 0 failures.", lines(out).get(lines(out).size() - 1));

        out = check(api, false, "Order::AlwaysTrue", "Order::AlwaysTrue");
        assertEquals(1, invariantLines(out).size());
        assertEquals("checked 1 invariant, 0 failures.", lines(out).get(lines(out).size() - 1));
    }

    /** Claim 7 (core part): with all=true a named deactivated invariant is checked. */
    public void testNamedDeactivatedInvariantIsCheckedWithAll() throws Exception {
        UseSystemApi api = shop(1, true);
        api.getSystem().model().getClassInvariant("Order::AlwaysTrue").setActive(false);
        String out = check(api, true, "Order::AlwaysTrue");
        assertEquals(Collections.singletonList("checking invariant (1) `Order::AlwaysTrue': OK."),
                invariantLines(out));
        assertEquals("checked 1 invariant, 0 failures.", lines(out).get(lines(out).size() - 1));
    }

    /** Claim 8: no names keeps checking all active invariants, or all with -a. */
    public void testNoNamesChecksAllActiveOrAll() throws Exception {
        UseSystemApi api = shop(1, true);
        api.getSystem().model().getClassInvariant("Order::AlwaysTrue").setActive(false);
        String out = check(api, false);
        assertEquals(2, invariantLines(out).size());
        assertEquals("checked 2 invariants, 0 failures.", lines(out).get(lines(out).size() - 1));
        out = check(api, true);
        assertEquals(3, invariantLines(out).size());
        assertEquals("checked 3 invariants, 0 failures.", lines(out).get(lines(out).size() - 1));
    }

    // ---- issue 11: structure violations in the summary ----

    /** Claim 1: a multiplicity violation adds the structure count; claim 7: result false. */
    public void testMultiplicityViolationIsCountedInSummary() throws Exception {
        String out = check(shop(1, false), false);
        assertEquals("checked 3 invariants, 0 failures, 1 structure violation.",
                lines(out).get(lines(out).size() - 1));
        assertFalse(result);
    }

    /** Claim 2: a valid state keeps today's summary with no suffix; claim 7: result true. */
    public void testValidStateHasNoStructureSuffix() throws Exception {
        String out = check(shop(1, true), false);
        assertEquals("checked 3 invariants, 0 failures.", lines(out).get(lines(out).size() - 1));
        assertTrue(result);
    }

    /** Claim 5: invariant failure and multiplicity violation are counted separately. */
    public void testInvariantFailureAndStructureViolationTogether() throws Exception {
        String out = check(shop(0, false), false);
        assertEquals("checked 3 invariants, 1 failure, 1 structure violation.",
                lines(out).get(lines(out).size() - 1));
        assertFalse(result);
    }

    /** Claim 7: an invariant failure alone (valid structure) is false with no suffix. */
    public void testInvariantFailureAloneHasNoStructureSuffix() throws Exception {
        String out = check(shop(0, true), false);
        assertEquals("checked 3 invariants, 1 failure.", lines(out).get(lines(out).size() - 1));
        assertFalse(result);
    }

    /** Claim 3: the count is the number of reported multiplicity violations (plural). */
    public void testFourMultiplicityViolationsAreCounted() throws Exception {
        UseSystemApi api = system(
                "model T\nclass A end\nclass B end\n" +
                "composition AB between\n  A[1] role owner\n  B[3] role b ordered\nend\n");
        api.createObject("A", "a1");
        api.createObject("B", "b1");
        api.createObject("B", "b2");
        api.createObject("B", "b3");
        String out = check(api, false);
        assertEquals("checked 0 invariants, 0 failures, 4 structure violations.",
                lines(out).get(lines(out).size() - 1));
        assertFalse(result);
    }

    /** Claim 6: an object that is part of itself is counted (K >= 1). */
    public void testPartOfItselfIsCounted() throws Exception {
        UseSystemApi api = system(
                "model Tree\nclass Node end\n" +
                "composition Contains between\n  Node[0..1] role parent\n  Node[*] role children\nend\n");
        api.createObject("Node", "n");
        api.createLink("Contains", "n", "n");
        String out = check(api, false);
        Matcher m = summary(out);
        assertNotNull(out, m.group(5));
        assertTrue(out, Integer.parseInt(m.group(5)) >= 1);
        assertFalse(result);
    }

    /** Claim 6: an object shared by two composites is counted (K >= 1). */
    public void testSharedPartIsCounted() throws Exception {
        UseSystemApi api = system(
                "model Share\nclass Whole end\nclass Part end\n" +
                "composition Owns between\n  Whole[*] role owner\n  Part[*] role parts\nend\n");
        api.createObject("Whole", "a1");
        api.createObject("Whole", "a2");
        api.createObject("Part", "p");
        api.createLink("Owns", "a1", "p");
        api.createLink("Owns", "a2", "p");
        String out = check(api, false);
        Matcher m = summary(out);
        assertNotNull(out, m.group(5));
        assertTrue(out, Integer.parseInt(m.group(5)) >= 1);
        assertFalse(result);
    }

    /** Claim 8: every summary keeps the prefix scripts look for. */
    public void testSummaryKeepsPrefix() throws Exception {
        for (UseSystemApi api : Arrays.asList(shop(1, true), shop(1, false), shop(0, false))) {
            String out = check(api, false);
            assertTrue(out, Pattern.compile("^checked [0-9]+ invariants?", Pattern.MULTILINE).matcher(out).find());
            summary(out);
        }
    }

    /** Claim 9: checkStructure keeps its boolean result and its printed lines. */
    public void testCheckStructureOutputAndResultUnchanged() throws Exception {
        StringWriter sw = new StringWriter();
        assertFalse(shop(1, false).getSystem().state().checkStructure(new PrintWriter(sw)));
        String out = sw.toString().replace("\r\n", "\n");
        assertTrue(out, out.contains("Multiplicity constraint violation in association `Has':"));
        assertFalse(out, out.contains("structure violation"));

        sw = new StringWriter();
        assertTrue(shop(1, true).getSystem().state().checkStructure(new PrintWriter(sw)));
        assertFalse(sw.toString(), sw.toString().contains("violation"));
    }
}
