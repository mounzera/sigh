import norswap.autumn.AutumnTestFixture;
import norswap.autumn.Grammar;
import norswap.autumn.Grammar.rule;
import norswap.autumn.ParseResult;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.*;
import norswap.sigh.interpreter.Interpreter;
import norswap.sigh.interpreter.Null;
import norswap.uranium.Reactor;
import norswap.uranium.SemanticError;
import norswap.utils.IO;
import norswap.utils.TestFixture;
import norswap.utils.data.wrappers.Pair;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;
import java.util.HashMap;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.testng.Assert.*;

public final class ArrayInterpreterTests extends TestFixture {

    // TODO peeling

    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final AutumnTestFixture autumnFixture = new AutumnTestFixture();

    {
        autumnFixture.runTwice = false;
        autumnFixture.bottomClass = this.getClass();
    }

    private static IntLiteralNode intlit (long i) {
        return new IntLiteralNode(null, i);
    }

    private static FloatLiteralNode floatlit (double d) {
        return new FloatLiteralNode(null, d);
    }

    private static TemplateTypeNode templit (String t) { return  new TemplateTypeNode(null,t);}

    private static StringLiteralNode stringlit( String s) { return  new StringLiteralNode(null, s);}


    // ---------------------------------------------------------------------------------------------

    private Grammar.rule rule;

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, null);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn, String expectedOutput) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (rule rule, String input, Object expectedReturn, String expectedOutput) {
        // TODO
        // (1) write proper parsing tests
        // (2) write some kind of automated runner, and use it here

        autumnFixture.rule = rule;
        ParseResult parseResult = autumnFixture.success(input);
        SighNode root = parseResult.topValue();

        Reactor reactor = new Reactor();
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        Interpreter interpreter = new Interpreter(reactor);
        walker.walk(root);
        reactor.run();
        Set<SemanticError> errors = reactor.errors();

        if (!errors.isEmpty()) {
            LineMapString map = new LineMapString("<test>", input);
            String report = reactor.reportErrors(it ->
                it.toString() + " (" + ((SighNode) it).span.startString(map) + ")");
            //            String tree = AttributeTreeFormatter.format(root, reactor,
            //                    new ReflectiveFieldWalker<>(SighNode.class, PRE_VISIT, POST_VISIT));
            //            System.err.println(tree);
            throw new AssertionError(report);
        }

        Pair<String, Object> result = IO.captureStdout(() -> interpreter.interpret(root));
        assertEquals(result.b, expectedReturn);
        if (expectedOutput != null) assertEquals(result.a, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn, String expectedOutput) {
        rule = grammar.root;
        check("return " + input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn) {
        rule = grammar.root;
        check("return " + input, expectedReturn);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkThrows (String input, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> check(input, null));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        checkExpr("[1]@(+)[2]",  new ArrayLiteralNode(null, asList(intlit(3))));
        checkExpr("[1]@(-)[2]",  new ArrayLiteralNode(null, asList(intlit(-1))));
        checkExpr("[1]@(*)[2]",  new ArrayLiteralNode(null, asList(intlit(2))));
        checkExpr("[1]@(/)[2]",  new ArrayLiteralNode(null, asList(intlit(0))));
        checkExpr("[1]@(%)[2]",  new ArrayLiteralNode(null, asList(intlit(1))));
        checkExpr("[1]@(>)[2]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "false"))));
        checkExpr("[1]@(>=)[2]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "false"))));
        checkExpr("[1]@(<)[2]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "true"))));
        checkExpr("[1]@(<=)[2]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "true"))));
        checkExpr("[1]@(==)[2]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "false"))));
        checkExpr("[1]@(!=)[2]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "true"))));


        checkExpr("[1.0]@(+)[2.0]",  new ArrayLiteralNode(null, asList(floatlit(3))));
        checkExpr("[1.0]@(-)[2.0]",  new ArrayLiteralNode(null, asList(floatlit(-1))));
        checkExpr("[1.0]@(*)[2.0]",  new ArrayLiteralNode(null, asList(floatlit(2))));
        checkExpr("[1.0]@(/)[2.0]",  new ArrayLiteralNode(null, asList(floatlit(0.5))));
        checkExpr("[1.0]@(%)[2.0]",  new ArrayLiteralNode(null, asList(floatlit(1))));
        checkExpr("[1.0]@(>)[2.0]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "false"))));
        checkExpr("[1.0]@(>=)[2.0]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "false"))));
        checkExpr("[1.0]@(<)[2.0]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "true"))));
        checkExpr("[1.0]@(<=)[2.0]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "true"))));
        checkExpr("[1.0]@(==)[2.0]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "false"))));
        checkExpr("[1.0]@(!=)[2.0]",  new ArrayLiteralNode(null, asList(new ReferenceNode(null, "true"))));



        checkThrows("[true]@(+)[false]",  Error.class);
        checkThrows("[true]@(-)[false]",  Error.class);
        checkThrows("[true]@(*)[false]",  Error.class);
        checkThrows("[true]@(/)[false]",  Error.class);
        checkThrows("[true]@(%)[false]",  Error.class);
        checkThrows("[true]@(>)[false]",  Error.class);
        checkThrows("[true]@(>=)[false]",  Error.class);
        checkThrows("[true]@(<)[false]",  Error.class);
        checkThrows("[true]@(<=)[false]",  Error.class);
        checkThrows("[true]@(==)[false]",  Error.class);
        checkThrows("[true]@(!=)[false]",  Error.class);

        checkExpr("[\"hel\"]@(+)[\"lo\"]",new ArrayLiteralNode(null, asList(stringlit("hello"))));
        checkThrows("[1,2]@(-)[1]",Error.class);

    }


    // ---------------------------------------------------------------------------------------------

    @Test
    public void testVarDecl () {
        check("var x: Int[] = [1]@(+)[2]; return x",
            new ArrayLiteralNode(null,asList(intlit(3))));
        check("var x: Float[] = [1.0]@(+)[2]; return x",
            new ArrayLiteralNode(null,asList(floatlit(3.0))));
        check("var x: String[] = []; return [\"h\"]@(+)[\"ello\"]",
            new ArrayLiteralNode(null,asList(stringlit("hello"))));

        check("var x: Template[] = []; return [\"h\"]@(+)[\"ello\"]",
            new ArrayLiteralNode(null,asList(stringlit("hello"))));
        check("var x: Template[] = []; return [1]@(+)[1]",
            new ArrayLiteralNode(null,asList(intlit(2))));
        check("var x: Template[] = [];  var a: Template[]= [1,2.0,\"hel\"]; var b: Template[] =[1,2.0,\"lo\"]; return x=a@(+)b",
            new ArrayLiteralNode(null,asList(intlit(2),floatlit(4.0),stringlit("hello"))));
        checkThrows("var x: Template[]; return x = [1,\"hel\"]@(+)[2,\"lo\"]",Error.class);



        // implicit conversions
        check("var x: Float = 1; x = 2; return x", 2.0d);
    }



    // ---------------------------------------------------------------------------------------------

    @Test
    public void testCalls () {

        check(
            "fun add (a: Int[], b: Int[]): Int[] { return a@(+)b } " +
                "return add([4], [7])",
            new ArrayLiteralNode(null, asList(intlit(11))));
        check(
            "fun add (a: Int[], b: Int[]): Int[] { return a@(-)b } " +
                "return add([4], [7])",
            new ArrayLiteralNode(null, asList(intlit(-3))));
        check(
            "fun add (a: Int[], b: Int[]): Int[] { return a@(*)b } " +
                "return add([4], [7])",
            new ArrayLiteralNode(null, asList(intlit(28))));
        check(
            "fun add (a: Int[], b: Int[]): Int[] { return a@(/)b } " +
                "return add([4], [7])",
            new ArrayLiteralNode(null, asList(intlit(0))));
        check(
            "fun add (a: Int[], b: Int[]): Int[] { return a@(%)b } " +
                "return add([4], [7])",
            new ArrayLiteralNode(null, asList(intlit(4))));
        check(
            "fun add (a: Int[], b: Int[]): Bool[] { return a@(>)b } " +
                "return add([4], [7])",
            new ArrayLiteralNode(null, asList(new ReferenceNode(null, "false"))));
        check(
            "fun add (a: Int[], b: Int[]): Bool[] { return a@(<)b } " +
                "return add([4], [7])",
            new ArrayLiteralNode(null, asList(new ReferenceNode(null, "true"))));
        check(
            "fun add (a: String[], b: String[]): Bool[] { return a@(>=)b } " +
                "return add([\"h\"], [\"ello\"])",
            new ArrayLiteralNode(null, asList(new ReferenceNode(null, "true"))));
        check(
            "fun add (a: Int[], b: Int[]): Bool[] { return a@(<=)b } " +
                "return add([4], [7])",
            new ArrayLiteralNode(null, asList(new ReferenceNode(null, "true"))));
        check(
            "fun add (a: Int[], b: Int[]): Bool[] { return a@(==)b } " +
                "return add([4], [7])",
            new ArrayLiteralNode(null, asList(new ReferenceNode(null, "false"))));
        check(
            "fun add (a: Float[], b: Int[]): Bool[] { return a@(!=)b } " +
                "return add([4.0], [7])",
            new ArrayLiteralNode(null, asList(new ReferenceNode(null, "true"))));
        //TODO not working
        /*check(
            "template <typename T, typename T1> fun add (a: T[], b: T1[]): Template[] { return a@(+)b } " +
                "return add<Template,Template>([4.0,\"h\"], [5,\"ello\"])",
            new ArrayLiteralNode(null, asList(floatlit(9.0),stringlit("hello")))
        );*/


    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testArrayStructAccess () {
        rule=grammar.root;

        //TODO Not working here but well in template.si
        /*checkExpr("var x: Template[] = [1,2.0,\"hello\",true]; return x[0]",1L);
        // TODO make this legal?
        // successInput("[].length", 0L);

        checkExpr("var x: Template[] = [1,2.0,\"hello\",true]; return x.length",intlit(4));

        checkExpr("var array: Template[] = null; return array[0]",null);
        checkExpr("var array: Template[] = null; return array.length",0);

        checkExpr("var x: Template[] = [0, 1]; x[0] = 3; return x[0]",intlit(3));
        checkExpr("var x: Template[] = []; x[0] = 3; return x[0]",intlit(3));
        checkExpr("var x: Template[] = null; x[0] = 3.0; x[1] =true; x[2]=\"hello\"",null);*/

        checkThrows("var array: Template[] = null; return array[0]", NullPointerException.class);
        checkThrows("var array: Template[] = null; return array.length", NullPointerException.class);

        check("var x: Template[] = [0, 1]; x[0] = 3; return x[0]", 3L);
        checkThrows("var x: Template[] = []; x[0] = 3; return x[0]",
            ArrayIndexOutOfBoundsException.class);
        checkThrows("var x: Template[] = null; x[0] = 3",
            NullPointerException.class);


    }


    // ---------------------------------------------------------------------------------------------

    // NOTE(norswap): Not incredibly complete, but should cover the basics.
}
