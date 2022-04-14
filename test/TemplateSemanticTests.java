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
        successInput("template <typename T> fun oneBool (x: T) {} ; oneBool<Bool> (true)");

        failureInputWith("template <typename T> fun incompatible (x: T) {} ; incompatible<String> (2)","incompatible argument provided for argument 0: expected String but got Int");
        failureInputWith("template <typename T> fun wrongNumberArgs (x: T) {} ; wrongNumberArgs<String, Int> (2)","Wrong number of template arguments in wrongNumberArgs: expected 1 but got 2");
        failureInputWith("template <typename T, typename T1> fun wrongNumberArgsTwo (x: T) {} ; wrongNumberArgsTwo<String> (2)","Wrong number of template arguments in wrongNumberArgsTwo: expected 2 but got 1");
        failureInputWith("template <typename T> fun noTemplateArguments (x: T) {} ; noTemplateArguments<>(2)","Trying to call template function without giving any types in arg: noTemplateArguments");
        failureInputWith("fun noTemplate (x: Int) {} ; noTemplate<Int>(2)","Trying to use template that were not declared: noTemplate");
        failureInputWith("template <typename T> fun wrongArgs (x: T) {} ; wrongArgs<Int>(\"hey\")","incompatible argument provided for argument 0: expected Int but got String");
        failureInputWith("template <typename T, typename T1, typename T2> fun wrongArgsTwo (x: T, y: T1, z: T2) {} ; wrongArgsTwo<String, Int, Int>(\"hey\", 3, true)","incompatible argument provided for argument 2: expected Int but got Bool");




    }

    @Test public void testBinaryExpression() {
        //basic Operation
        successInput("template <typename T> fun basicOperation (x: T): Int {return x + 1} ; basicOperation<Int> (2)");
        successInput("template <typename T> fun basicOperation2 (x: T): Int {return x - 1} ; basicOperation2<Int> (2)");
        successInput("template <typename T> fun basicOperation3 (x: T): Int {return x / 1} ; basicOperation3<Int> (2)");
        successInput("template <typename T> fun basicOperation4 (x: T): Int {return x * 1} ; basicOperation4<Int> (2)");
        successInput("template <typename T, typename T1> fun basicTwoOperation (x: T, y: T1): Int {return x + 1} ; basicTwoOperation<Int, Float> (2, 4.5)");
        successInput("template <typename T, typename T1> fun basicTwoOperation2 (x: T, y: T1): Int {return x - 1} ; basicTwoOperation2<Int, Float> (2, 4.5)");
        successInput("template <typename T, typename T1> fun basicTwoOperation3 (x: T, y: T1): Int {return x / 1} ; basicTwoOperation3<Int, Float> (2, 4.5)");
        successInput("template <typename T, typename T1> fun basicTwoOperation4 (x: T, y: T1): Int {return x * 1} ; basicTwoOperation4<Int, Float> (2, 4.5)");
        successInput("template <typename T> fun basicOperationString (x: T):T { return x + 1}; basicOperationString<String>(\"hey\")");
        failureInputWith("template <typename T> fun basicOperationFail (x: T):T { return x - 1}; basicOperationFail<String>(\"hey\")", "Trying to subtract String with Int");
        failureInputWith("template <typename T> fun basicOperationFail2 (x: T):T { return x + 1}; basicOperationFail2<Bool>(true)", "Trying to add Bool with Int");

        successInput("template <typename T, typename T1> fun basicOperationTemplate (x: T, y: T1):T { return x + y}; basicOperationTemplate<Int, Int>(4, 2)");
        successInput("template <typename T, typename T1> fun basicOperationTemplate2 (x: T, y: T1):T1 { return x * y}; basicOperationTemplate2<Int, Float>(4, 2.5)");
        successInput("template <typename T, typename T1> fun basicOperationTemplate3 (x: T, y: T1):T1 { return x + y}; basicOperationTemplate3<Int, String>(4, \"hey\")");
        failureInputWith("template <typename T, typename T1> fun basicOperationTemplateFail (x: T, y: T1):T { return x * y}; basicOperationTemplateFail<Int, String>(4, \"hey\")", "Trying to multiply Int with String");
        failureInputWith("template <typename T, typename T1> fun basicOperationTemplateFail2 (x: T, y: T1):T { return x + y}; basicOperationTemplateFail2<Int, Bool>(4, true)", "");

        successInput("template <typename T, typename T1> fun otherOperationTemplate (x: T, y: T1):Bool { return x == y}; otherOperationTemplate<Int, Int>(4, 2)");
        successInput("template <typename T, typename T1> fun otherOperationTemplate2 (x: T, y: T1):Bool { return x >= y}; otherOperationTemplate2<Int, Int>(4, 2)");
        successInput("template <typename T, typename T1> fun otherOperationTemplate3 (x: T, y: T1):Bool { return x || y}; otherOperationTemplate3<Bool, Bool>(true, false)");
        successInput("template <typename T, typename T1> fun otherOperationTemplate4 (x: T, y: T1):Bool { return x == y}; otherOperationTemplate4<Bool, Bool>(true, false)");
        failureInputWith("template <typename T, typename T1> fun otherOperationTemplateFail (x: T, y: T1):Bool { return x || y}; otherOperationTemplateFail<Bool, Int>(true, 1)", "Attempting to perform binary logic on non-boolean type: Int");
        failureInputWith("template <typename T, typename T1> fun otherOperationTemplateFail2 (x: T, y: T1):Bool { return x == y}; otherOperationTemplateFail2<Bool, Int>(true, 1)", "Trying to compare incomparable types Bool and Int");

    }

    @Test public void testReturn(){
        successInput("template <typename T> fun f11 (x: T) : T { return x} ; f11<String> (\"hey\")");
        successInput("template <typename T> fun f12 (x: T) : Int { return 2} ; f12<String> (\"hey\")");
        successInput("template <typename T> fun f13 (x: T) : Int { return 2} ; f13<String> (\"hey\")");

        successInput("template <typename T,typename T1> fun f14 (x: T1, y:T, z:Int, a: T1):T { return x}; f14<Int,Int>(1,2,3,4)") ;
        successInput("template <typename T,typename T1> fun f15 (x: T1, y:T, z:Int, a: T1):T1 { return x}; f15<Int,String>(\"hey\",2,3,\"hey\")") ;
        //failureInputWith("template <typename T, typename T1> fun basicOperationTemplate2 (x: T, y: T1):T { return x * y}; basicOperationTemplate2<Int, Float>(4, 2.5)", "Incompatible return type, expected Int but got Float");
        //failureInputWith("template <typename T, typename T1> fun otherOperationTemplate (x: T, y: T1):T { return x == y}; otherOperationTemplate<Int, Int>(4, 2)");


    }
}
