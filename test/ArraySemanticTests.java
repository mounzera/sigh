import norswap.autumn.AutumnTestFixture;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.SighNode;
import norswap.uranium.Reactor;
import norswap.uranium.UraniumTestFixture;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;


public final class ArraySemanticTests extends UraniumTestFixture
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

    @Test public void testNumericBinary() {
        successInput("return [1,1]@(+)[2,2]");
        successInput("return [1,1]@(-)[2,2]");
        successInput("return [1,1]@(*)[2,2]");
        successInput("return [1,1]@(/)[2,2]");
        successInput("return [1,1]@(%)[2,2]");
        successInput("return [1,1]@(>)[2,2]");
        successInput("return [1,1]@(>=)[2,2]");
        successInput("return [1,1]@(<)[2,2]");
        successInput("return [1,1]@(<=)[2,2]");
        successInput("return [1,1]@(==)[2,2]");
        successInput("return [1,1]@(!=)[2,2]");


        successInput("return [1,1]@(+)[2,2]@(*)[1,1]");
        successInput("return [1,1]@(-)[2,2]@(*)[1,1]");
        successInput("return [1,1]@(*)[2,2]@(*)[1,1]");
        successInput("return [1,1]@(/)[2,2]@(/)[1,1]");
        successInput("return [1,1]@(%)[2,2]@(+)[1,1]");
        successInput("return [1,1]@(>)[2,2]@(||)[true,false]");
        successInput("return [1,1]@(>=)[2,2]@(||)[true,false]");
        successInput("return [1,1]@(<)[2,2]@(||)[true,false]");
        successInput("return [1,1]@(<=)[2,2]@(&&)[true,false]");
        successInput("return [1,1]@(==)[2,2]@(&&)[true,false]");
        successInput("return [1,1]@(!=)[2,2]@(&&)[true,false]");

        successInput("return [1,1.0]@(+)[2,2.0]");
        successInput("return [1,1.0]@(-)[2,2.0]");
        successInput("return [1,1.0]@(*)[2,2.0]");
        successInput("return [1,1.0]@(/)[2,2.0]");
        successInput("return [1,1.0]@(%)[2,2.0]");
        successInput("return [1,1.0]@(>)[2,2.0]");
        successInput("return [1,1.0]@(>=)[2,2.0]");
        successInput("return [1,1.0]@(<)[2,2.0]");
        successInput("return [1,1.0]@(<=)[2,2.0]");
        successInput("return [1,1.0]@(==)[2,2.0]");
        successInput("return [1,1.0]@(!=)[2,2.0]");

        successInput("return [\"h\"]@(+)[\"ello\"]");
        successInput("return [\"h\"]@(>)[\"ello\"]");
        successInput("return [\"h\"]@(>=)[\"ello\"]");
        successInput("return [\"h\"]@(<)[\"ello\"]");
        successInput("return [\"h\"]@(<=)[\"ello\"]");
        successInput("return [\"h\"]@(==)[\"ello\"]");
        successInput("return [\"h\"]@(!=)[\"ello\"]");
        failureInputWith("return [\"h\"]@(-)[\"ello\"]", "Trying to use SUBTRACT between arrays of String type");
        failureInputWith("return [\"h\"]@(*)[\"ello\"]", "Trying to use MULTIPLY between arrays of String type");
        failureInputWith("return [\"h\"]@(/)[\"ello\"]", "Trying to use DIVIDE between arrays of String type");
        failureInputWith("return [\"h\"]@(%)[\"ello\"]", "Trying to use REMAINDER between arrays of String type");
        failureInputWith("return [\"h\"]@(-)[\"ello\"]", "Trying to use SUBTRACT between arrays of String type");


        successInput("return [true,true]@(||)[true,false]");
        successInput("return [true,true]@(&&)[true,false]");
        successInput("return [true,true]@(==)[true,false]");
        successInput("return [true,true]@(!=)[true,false]");
        failureInputWith("return [true,true]@(*)[true,false]","Trying to use MULTIPLY between arrays of Bool type");
        failureInputWith("return [true,true]@(/)[true,false]","Trying to use DIVIDE between arrays of Bool type");
        failureInputWith("return [true,true]@(%)[true,false]","Trying to use REMAINDER between arrays of Bool type");
        failureInputWith("return [true,true]@(>)[true,false]","Trying to use GREATER between arrays of Bool type");
        failureInputWith("return [true,true]@(>=)[true,false]","Trying to use GREATER_EQUAL between arrays of Bool type");
        failureInputWith("return [true,true]@(<) [true,false]","Trying to use LOWER between arrays of Bool type");
        failureInputWith("return [true,true]@(<=)[true,false]","Trying to use LOWER_EQUAL between arrays of Bool type");

        failureInputWith("return [1]@(+)[\"ello\"]","Trying to use @ between non compatible ArrayTypes Int[] and String[]");
        failureInputWith("return [1.0]@(+)[\"ello\"]","Trying to use @ between non compatible ArrayTypes Float[] and String[]");
        failureInputWith("return [true]@(+)[\"ello\"]","Trying to use @ between non compatible ArrayTypes Bool[] and String[]");
        failureInputWith("return [1]@(+)[true]","Trying to use @ between non compatible ArrayTypes Int[] and Bool[]");
        failureInputWith("return 2 + [1]", "Trying to add Int with Int[]");
        failureInputWith("return [1] + 2", "Trying to add Int[] with Int");

        failureInputWith("return [1] + [2]","Trying to add Int[] with Int[]");
        successInput("return [1]@(+)[2,3]");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testVarDecl() {
        successInput("var x: Template[] = []; return x");
        successInput("var x: Template[] = [1,2.0,true,\"hello\",true]; return x");
        successInput("var x: Float[] = [2.0];var y: Template[] =[3.0]; return y=x");
        successInput("var x: Float[] = [2];var y: Template[] =[3]; return y=x");
        successInput("var x: Template[] = [1,2.0,\"hello\",true];var y: Template[] =[3.0]; return y=x");


        failureInputWith("var x: Int[] = [0.0]; return x = [3]","incompatible initializer type provided for variable `x`: expected Int[] but got Float[]");
        failureInputWith("var x: Int[] = [true]; return x = [3]","incompatible initializer type provided for variable `x`: expected Int[] but got Bool[]");        failureInputWith("var x: Int[] = [0.0]; return x = [3]","incompatible initializer type provided for variable `x`: expected Int[] but got Float[]");
        failureInputWith("var x: Int[] = [\"hello\"]; return x = [3]","incompatible initializer type provided for variable `x`: expected Int[] but got String[]");
        failureInputWith("var x: Float[] = [1, 2.0, true,\"hello\"]; return x = [3]","Could not find common supertype in array literal.");
        failureInputWith("var x: Float[] = [2];var y: Template[] =[3]; return x=y","Trying to assign a value of type Template[] to a non-compatible lvalue of type Float[].");
        failureInputWith("var x: Int[] = [2];var y: Template[] =[3]; return x=y","Trying to assign a value of type Template[] to a non-compatible lvalue of type Int[].");
        failureInputWith("var x: Bool[] = [true];var y: Template[] =[3]; return x=y","Trying to assign a value of type Template[] to a non-compatible lvalue of type Bool[].");
        failureInputWith("var x: String[] = [\"hello\"];var y: Template[] =[3]; return x=y","Trying to assign a value of type Template[] to a non-compatible lvalue of type String[].");

    }




    // ---------------------------------------------------------------------------------------------

    @Test public void testCalls() {
        successInput(
            "fun add (a: Int[], b: Int[]): Int[] { return a@(+)b } " +
                "return add([4], [7])");
        successInput(
            "fun add (a: Int[], b: Int[]): Int[] { return a@(-)b } " +
                "return add([4], [7])");
        successInput(
            "fun add (a: Int[], b: Int[]): Int[] { return a@(*)b } " +
                "return add([4], [7])");
        successInput(
            "fun add (a: Int[], b: Int[]): Int[] { return a@(/)b } " +
                "return add([4], [7])");
        successInput(
            "fun add (a: Int[], b: Int[]): Int[] { return a@(%)b } " +
                "return add([4], [7])");
        successInput(
            "fun add (a: Int[], b: Int[]): Bool[] { return a@(>)b } " +
                "return add([4], [7])");
        successInput(
            "fun add (a: Int[], b: Int[]): Bool[] { return a@(<)b } " +
                "return add([4], [7])");
        successInput(
            "fun add (a: String[], b: String[]): Bool[] { return a@(>=)b } " +
                "return add([\"h\"], [\"ello\"])");
        successInput(
            "fun add (a: Int[], b: Int[]): Bool[] { return a@(<=)b } " +
                "return add([4], [7])");
        successInput(
            "fun add (a: Int[], b: Int[]): Bool[] { return a@(==)b } " +
                "return add([4], [7])");
        successInput(
            "fun add (a: Float[], b: Int[]): Bool[] { return a@(!=)b } " +
                "return add([4.0], [7])");
        successInput(
            "fun add (a: Bool[], b: Bool[]): Bool[] { return a@(||)b } " +
                "return add([true], [false])");

        successInput(
            "fun add (a: Bool[], b: Bool[]): Bool[] { return a@(&&)b } " +
                "return add([true], [false])");

        successInput(
            "fun add (a: Template[], b: Template[]): Template[] { return a@(+)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Template[] { return a@(-)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Template[] { return a@(*)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Template[] { return a@(%)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Template[] { return a@(/)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Bool[] { return a@(>)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Bool[] { return a@(>=)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Bool[] { return a@(<)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Bool[] { return a@(<=)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Bool[] { return a@(==)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Bool[] { return a@(!=)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Bool[] { return a@(&&)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: Template[], b: Template[]): Bool[] { return a@(||)b } " +
                "return add([4.0], [\"h\"])");
        successInput(
            "fun add (a: String[], b: String[]): String[] { return a@(-)b } " +
                "return add([\"h\"], [\"ello\"])");





        //TODO ok in template.si ???
        /*successInput(
            "template <typename T, typename T1> fun add (a: T[], b: T1[]): Template[] { return a@(+)b } " +
                "return add<Template,Template>([4.0], [\"h\"])");
        */

    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayStructAccess() {
        successInput("var x: Template[] = [1,2.0,\"hello\",true]; return x[0]");
        failureInputWith("var x: Template[] = [1,2.0,\"hello\",true]; return x[true]","Indexing an array using a non-Int-valued expression");

        // TODO make this legal?
        // successInput("[].length", 0L);

        successInput("var x: Template[] = [1,2.0,\"hello\",true]; return x.length");

        successInput("var array: Template[] = null; return array[0]");
        successInput("var array: Template[] = null; return array.length");

        successInput("var x: Template[] = [0, 1]; x[0] = 3; return x[0]");
        successInput("var x: Template[] = []; x[0] = 3; return x[0]");
        successInput("var x: Template[] = null; x[0] = 3.0; x[1] =true; x[2]=\"hello\"");

    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testIfWhile () {
        successInput("if (true) return 1 else return 2");
        successInput("if (false) return 1 else return 2");
        successInput("if (false) return 1 else if (true) return 2 else return 3 ");


    }

    // ---------------------------------------------------------------------------------------------
    //TODO ???
    @Test public void testTypeAsValues() {
        successInput("struct S{} ; return \"\"+ S");
        successInput("struct S{} ; var type: Type = S ; return \"\"+ type");
    }


}

