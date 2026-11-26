package org.tzi.use.uml.sys.testsuite;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.tzi.use.parser.testsuite.TestSuiteCompiler;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

class MTestSuiteTest {

	private static final String MODEL = String.join("\n",
			"model Repro",
			"class C",
			"attributes",
			"  n : Integer",
			"end");

	private static String run(String model, String suite) {
		StringWriter errors = new StringWriter();
		MModel m = USECompiler.compileSpecification(model, "Repro.use", new PrintWriter(errors, true), new ModelFactory());
		assertNotNull(m, errors.toString());
		MTestSuite s = TestSuiteCompiler.compileTestSuite(suite, "Repro.suite", new PrintWriter(errors, true), m);
		assertNotNull(s, errors.toString());
		StringWriter out = new StringWriter();
		s.run(new PrintWriter(out, true));
		return out.toString();
	}

	@Test
	void globalInvariantsAssertionChecksTheState() {
		String out = run(MODEL, String.join("\n",
				"testsuite Repro for model Repro.use",
				"setup",
				"  !create c : C",
				"end",
				"testcase globalInvariants",
				"  assert valid invs",
				"end"));
		assertTrue(out.contains("### OK ###"), out);
	}

	@Test
	void globalInvariantsAssertionSeesAFailingInvariant() {
		String model = MODEL + "\nconstraints\ncontext C inv positive: n > 0\n";
		String out = run(model, String.join("\n",
				"testsuite Repro for model Repro.use",
				"setup",
				"  !create c : C",
				"  !set c.n := 0",
				"end",
				"testcase invalidState",
				"  assert invalid invs",
				"end",
				"testcase validStateFails",
				"  assert valid invs",
				"end"));
		assertTrue(out.contains("`invalidState'... success"), out);
		assertTrue(out.contains("`validStateFails'... failure"), out);
	}
}
