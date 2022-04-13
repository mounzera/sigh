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

        successInput("template <typename T> fun classic (x: T) {}");
        successInput("template <typename T> fun classicTwo (x: T, y: T) {}");
        successInput("template <typename T, typename T1> fun classicThree (x: T, y: T1) {}");
        successInput("template <typename T, typename T1, typename T2> fun classicFour (x: T1) {}");

        failureInputWith("fun noTemplate (x: T):T { return x}",
            "No template declaration was made");
        failureInputWith("template <typename T1> fun wrongTemplate (x: T):T { return x}",
            "Wrong template declaration T was not found");
        failureInputWith("template <typename A1> fun notAllowedTemplate (x: T):T { return x}",
            "A1 is not an allowed name for template");

        successInput("template <typename T> fun returnInt (x: T):T { return 2}");
        successInput("template <typename T> fun returnTemplate (x: T):T { return x}");
        successInput("template <typename T,typename T1> fun returnTemplateTwo (x: T):T { return x}");
        successInput("template <typename T,typename T1> fun returnTemplateThree (x: T1, y:T, z:Int, a: T1):T { return x}");

        // Following tests shows that we are allowed to declare any template function without having trouble with types
        successInput("template <typename T> fun binaryExpression (x: T):T { return x + 1}");
        successInput("template <typename T> fun binaryExpressionTwo (x: T):T { return x + x}");
        successInput("template <typename T> fun binaryExpressionThree (x: T):T { return x * (x + 1)}");
        successInput("template <typename T> fun binaryExpressionFour (x: T):T { return x == 2}");
        successInput("template <typename T> fun binaryExpressionFive (x: T):T { return x && 2}");
        successInput("template <typename T> fun binaryExpressionSix (x: T):T { return x + \"\"}");

        successInput("template <typename T> fun varDecl (x: T) {var a: Int = x}");
        successInput("template <typename T> fun varDeclTwo (x: T) {var a: String = x}");
        successInput("template <typename T> fun varDeclThree (x: T) {var a: Int = x + 1}");
        successInput("template <typename T> fun varDeclTemplate (x: T) {var a: T = x}");
        successInput("template <typename T, typename T1> fun varDeclTwoTemplate (x: T, y: T1) {var a: T = x; var b: T1 = y}");
        successInput("template <typename T, typename T1> fun varDeclTwoTemplateTwo (x: T, y: T1) {var a: T = x; var b: T = y}");
        successInput("template <typename T, typename T1> fun varDeclTwoTemplateThree (x: T, y: T1, z: Int) {var a: T = x; var b: T1 = y; var c: T = z}");

        successInput("template <typename T> fun assign (x: T) {var a : Int = 6; a = x}");
        successInput("template <typename T, typename T1> fun assignTwo (x: T, y: T) {var a : Int = x; a = y}");
        failureInputWith("template <typename T> fun assignThree (x: T) {var a : Int = x; a = \"hey\"}", "Trying to assign a value of type String to a non-compatible lvalue of type Int");


        successInput("template <typename T> fun ifStmt (x: T) {if(x){}}");
        successInput("template <typename T> fun ifStmtTwo (x: T) {if(x + 1 < 6){}}");
        successInput("template <typename T> fun ifStmtThree (x: T): Int {if(x){return 0};else{return 1}}");
        successInput("template <typename T> fun whileStmt (x: T) {while(x){}}");
        successInput("template <typename T> fun whileStmtTwo (x: T) {while(x + 2 < 6){}}");
    }



    // ---------------------------------------------------------------------------------------------

    @Test public void testCalls() {
        //successInput("fun use_array (array: Int[]) {} ; use_array([])");//TODO : error

        successInput("template <typename T> fun oneString (x: T) {} ; oneString<String> (\"hey\")");
        successInput("template <typename T> fun oneInt (x: T) {} ; oneInt<Int> (2)");
        successInput("template <typename T> fun oneFloat (x: T) {} ; oneFloat<Float> (2)");
        successInput("template <typename T, typename T1> fun twoInt (x: T1, y: T) {} ; twoInt<Int, Int> (2, 2)");
        successInput("template <typename T, typename T1> fun intAndString (x: T1, y: T) {} ; intAndString<Int, String> (\"hey\", 2)");


        successInput("template <typename T> fun f9 (x: T) {} ; f9<Bool> (true)");
        failureInputWith("template <typename T> fun f10 (x: T) {} ; f10<String> (2)","incompatible argument provided for argument 0: expected String but got Int");

    }

    @Test public void testReturn(){
        successInput("template <typename T> fun f11 (x: T) : T { return x} ; f11<String> (\"hey\")");
        successInput("template <typename T> fun f12 (x: T) : Int { return 2} ; f12<String> (\"hey\")");
        successInput("template <typename T> fun f13 (x: T) : Int { return 2} ; f13<String> (\"hey\")");

        successInput("template <typename T,typename T1> fun f14 (x: T1, y:T, z:Int, a: T1):T { return x}; f14<Int,Int>(1,2,3,4)") ;
        successInput("template <typename T,typename T1> fun f15 (x: T1, y:T, z:Int, a: T1):T1 { return x}; f15<Int,String>(\"hey\",2,3,\"hey\")") ;

    }
}
