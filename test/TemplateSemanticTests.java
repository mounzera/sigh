import norswap.autumn.AutumnTestFixture;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.SighNode;
import norswap.uranium.Reactor;
import norswap.uranium.UraniumTestFixture;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;

/**
 * NOTE(norswap): These tests were derived from the {@link InterpreterTests} and don't test anything
 * more, but show how to idiomatically test semantic analysis. using {@link UraniumTestFixture}.
 */
public final class TemplateSemanticTests extends UraniumTestFixture
{
    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final AutumnTestFixture autumnFixture = new AutumnTestFixture();

    {
        autumnFixture.rule = grammar.root();
        autumnFixture.runTwice = false;
        autumnFixture.bottomClass = this.getClass();
    }

    private String input;

    @Override protected Object parse (String input) {
        this.input = input;
        return autumnFixture.success(input).topValue();
    }

    @Override protected String astNodeToString (Object ast) {
        LineMapString map = new LineMapString("<test>", input);
        return ast.toString() + " (" + ((SighNode) ast).span.startString(map) + ")";
    }

    // ---------------------------------------------------------------------------------------------

    @Override protected void configureSemanticAnalysis (Reactor reactor, Object ast) {
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        walker.walk(((SighNode) ast));
    }

    // ---------------------------------------------------------------------------------------------


    // ---------------------------------------------------------------------------------------------

    @Test public void testDeclaration() {

        successInput("template <typename T> fun f (x: T) {}");

        successInput("template <typename T> fun f (x: T):T { return 2}");

        successInput("template <typename T> fun f (x: T):T { return x}");

        successInput("template <typename T,typename T1> fun f (x: T):T { return x}");

        successInput("template <typename T,typename T1> fun f (x: T1, y:T, z:Int, a: T1):T { return x}");

        failureInputWith("template <typename T,typename T1> fun f (x: T1, y:T, z:Int, a: T1):Int { return x}",
            "Incompatible return type, expected Int but got Template");

        successInput(
            "fun add (a: Int, b: Int): Int { return a + b } " +
                "return add(4, 7)");
    }



    // ---------------------------------------------------------------------------------------------

    @Test public void testCalls() {
        successInput("fun use_array (array: Int[]) {} ; use_array([])");

        successInput("template <typename T> fun f (x: T) {} ; f<String> (\"hey\")");
        successInput("template <typename T> fun f (x: T) {} ; f<Int> (2)");
        successInput("template <typename T> fun f (x: T) {} ; f<Float> (2)");
        successInput("template <typename T> fun f (x: T) {} ; f<Bool> (true)");
        failureInputWith("template <typename T> fun f (x: T) {} ; f<String> (2)","incompatible argument provided for argument 0: expected String but got Int");

    }

    @Test public void testReturn(){
        successInput("template <typename T> fun f (x: T) : T { return x} ; f<String> (\"hey\")");
        successInput("template <typename T> fun f (x: T) : T { return 2} ; f<String> (\"hey\")");
        successInput("template <typename T> fun f (x: T) : Int { return 2} ; f<String> (\"hey\")");

        successInput("template <typename T,typename T1> fun f (x: T1, y:T, z:Int, a: T1):T { return x}; f<Int,Int>(1,2,3,4)") ;


    }
}
