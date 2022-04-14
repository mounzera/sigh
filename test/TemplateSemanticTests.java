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

        //test double calls
        successInput("template <typename T> fun doubleCalls (x: T) {} ; doubleCalls<String> (\"hey\"); doubleCalls<Int> (2)");
        successInput("template <typename T, typename T1> fun doubleCalls2 (x: T1, y: T) {} ; doubleCalls2<Int, Int> (2, 2); doubleCalls2<Int, String> (\"hey\", 2)");
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

        //test double calls
        successInput("template <typename T> fun basicOperationDouble (x: T): T {return x + 1} ; basicOperationDouble<Int> (2); basicOperationDouble<String> (\"hey\")");
    }

    @Test public void testVarDeclAndAssignement() {
        successInput("template <typename T> fun basicVarDecl (x: T) {var a : Int = x} ; basicVarDecl<Int> (2)");
        successInput("template <typename T> fun basicVarDecl2 (x: T) {var a : T = x; a = 2} ; basicVarDecl2<Int> (2)");
        failureInputWith("template <typename T> fun basicVarDeclFail (x: T) {var a : Int = x} ; basicVarDeclFail<String> (\"hey\")", "incompatible initializer type provided for variable `a`: expected Int but got String");
        failureInputWith("template <typename T> fun basicVarDeclFail2 (x: T) {var a : T = 2} ; basicVarDeclFail2<String> (\"hey\")", "incompatible initializer type provided for variable `a`: expected String but got Int");
        successInput("template <typename T, typename T1> fun basicVarDeclTwo (x: T, y: T1) {var a : T = x; a = y} ; basicVarDeclTwo<Int, Int> (2, 4)");
        failureInputWith("template <typename T, typename T1> fun basicVarDeclTwo (x: T, y: T1) {var a : T = x; a = y} ; basicVarDeclTwo<Int, Float> (2, 4.5)", "Trying to assign a value of type Float to a non-compatible lvalue of type Int");

        //test double calls
        successInput("template <typename T> fun basicVarDeclDouble (x: T) {var a : T = x} ; basicVarDeclDouble<Int> (2); basicVarDeclDouble<String> (\"hey\")");

    }

    @Test public void testIfWhile () {
        //if
        successInput("template <typename T> fun ifTest (x: T) {if(x > 3){}} ; ifTest<Int> (2)");
        successInput("template <typename T> fun ifTest5 (x: T) {if(x > 3){}else if(x < 0){}else{}} ; ifTest5<Int> (2)");
        failureInputWith("template <typename T> fun ifTestFail (x: T) {if(x){}} ; ifTestFail<Int> (2)", "If statement with a non-boolean condition of type: Int");

        successInput("template <typename T> fun ifTest1 (x: T) {if(x + 6 > 3){}} ; ifTest1<Int> (2)");
        failureInputWith("template <typename T> fun ifTestFail2 (x: T) {if(x + 6){}} ; ifTestFail2<Int> (2)", "If statement with a non-boolean condition of type: Int");

        successInput("template <typename T, typename T1> fun ifTest2 (x: T, y: T1) {if(x + 6 > y){}} ; ifTest2<Int, Float> (2, 7.5)");
        successInput("template <typename T> fun ifTest3 (x: T) {if(x){}} ; ifTest3<Bool> (true)");
        successInput("template <typename T, typename T1> fun ifTest4 (x: T, y: T1) {if(x || y){}} ; ifTest4<Bool, Bool> (true, false)");

        // while
        successInput("template <typename T> fun whileTest (x: T) {while(x > 3){}} ; whileTest<Int> (2)");
        failureInputWith("template <typename T> fun whileTestFail (x: T) {while(x){}} ; whileTestFail<Int> (2)", "While statement with a non-boolean condition of type: Int");

        successInput("template <typename T> fun whileTest1 (x: T) {while(x + 6 > 3){}} ; whileTest1<Int> (2)");
        failureInputWith("template <typename T> fun whileTestFail2 (x: T) {while(x + 6){}} ; whileTestFail2<Int> (2)", "While statement with a non-boolean condition of type: Int");

        successInput("template <typename T, typename T1> fun whileTest2 (x: T, y: T1) {while(x + 6 > y){}} ; whileTest2<Int, Float> (2, 7.5)");
        successInput("template <typename T> fun whileTest3 (x: T) {while(x){}} ; whileTest3<Bool> (true)");
        successInput("template <typename T, typename T1> fun whileTest4 (x: T, y: T1) {while(x || y){}} ; whileTest4<Bool, Bool> (true, false)");

        //test double calls
        successInput("template <typename T> fun ifTestDouble (x: T) {if(x > 3){}} ; ifTestDouble<Int> (2) ; ifTestDouble<Float> (2.6)");

    }


        @Test public void testReturn(){
        successInput("template <typename T> fun returnBasic (x: T) : T { return x} ; returnBasic<String> (\"hey\")");
        successInput("template <typename T> fun returnBasic1 (x: T) : Int { return 2} ; returnBasic1<String> (\"hey\")");
        failureInputWith("template <typename T> fun returnBasicFail (x: T) : Int { return x} ; returnBasicFail<String> (\"hey\")", "Incompatible return type, expected Int but got String");
        failureInputWith("template <typename T> fun returnBasicFail2 (x: T) : T { return 2} ; returnBasicFail2<String> (\"hey\")", "Incompatible return type, expected String but got Int");

        successInput("template <typename T, typename T1> fun returnBasic2 (x: T, y: T1) : T { return x + y} ; returnBasic2<Int, Int> (2, 6)");
        failureInputWith("template <typename T, typename T1> fun returnBasicFail3 (x: T, y: T1):T { return x * y}; returnBasicFail3<Int, Float>(4, 2.5)", "Incompatible return type, expected Int but got Float");
        successInput("template <typename T, typename T1> fun returnBasic3 (x: T, y: T1) : Bool { return x || y} ; returnBasic3<Bool, Bool> (true, false)");

        successInput("template <typename T,typename T1> fun returnBasic4 (x: T1, y:T, z:Int, a: T1):T { return x}; returnBasic4<Int,Int>(1,2,3,4)") ;
        successInput("template <typename T,typename T1> fun returnBasic5 (x: T1, y:T, z:Int, a: T1):T1 { return x}; returnBasic5<Int,String>(\"hey\",2,3,\"hey\")") ;

        //test double calls
        successInput("template <typename T> fun returnBasicDouble (x: T) : T { return x} ; returnBasicDouble<String> (\"hey\"); returnBasicDouble<Int> (3)");

    }
}
